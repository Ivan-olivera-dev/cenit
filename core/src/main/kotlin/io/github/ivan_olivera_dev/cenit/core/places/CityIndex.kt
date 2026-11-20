package io.github.ivan_olivera_dev.cenit.core.places

import java.text.Normalizer
import java.util.Locale
import kotlin.math.cos

/**
 * In-memory offline city prefix search index.
 *
 * Matches accent- and case-insensitively on the start of the full name or any word within
 * `name` / `asciiName`, ranking exact matches first, then full-name prefixes, then non-leading
 * word-start prefixes, preferring cities in `preferredCountryCode` within each match-quality
 * tier, and breaking remaining ties by descending population.
 */
class CityIndex private constructor(
    private val entries: List<IndexedCity>,
    private val firstCharBuckets: Map<Char, IntArray>,
) {
    val size: Int
        get() = entries.size

    fun search(
        query: String,
        limit: Int = DEFAULT_LIMIT,
        preferredCountryCode: String? = null,
        countryNameForCode: (String) -> String = { it },
    ): List<CitySearchResult> {
        if (limit <= 0) return emptyList()
        val normalizedQuery = normalizeForSearch(query)
        if (normalizedQuery.isEmpty()) return emptyList()

        val candidates = firstCharBuckets[normalizedQuery[0]] ?: return emptyList()
        val preferred = preferredCountryCode?.trim()?.uppercase(Locale.ROOT)?.ifEmpty { null }

        val preferredExact = ArrayList<CityEntry>(minOf(limit, 8))
        val otherExact = ArrayList<CityEntry>(minOf(limit, 8))
        val preferredFullPrefix = ArrayList<CityEntry>(limit)
        val otherFullPrefix = ArrayList<CityEntry>(limit)
        val preferredWordPrefix = ArrayList<CityEntry>(limit)
        val otherWordPrefix = ArrayList<CityEntry>(limit)

        for (idx in candidates) {
            val item = entries[idx]
            val tier = matchTier(item, normalizedQuery)
            if (tier == TIER_NONE) continue
            val isPreferred = preferred != null &&
                item.entry.countryCode.equals(preferred, ignoreCase = true)

            when (tier) {
                TIER_EXACT -> {
                    if (isPreferred) {
                        if (preferredExact.size < limit) preferredExact.add(item.entry)
                    } else if (preferredExact.size + otherExact.size < limit) {
                        otherExact.add(item.entry)
                    }
                }
                TIER_FULL_PREFIX -> {
                    val higher = preferredExact.size + otherExact.size
                    if (isPreferred) {
                        if (higher + preferredFullPrefix.size < limit) {
                            preferredFullPrefix.add(item.entry)
                        }
                    } else if (higher + preferredFullPrefix.size + otherFullPrefix.size < limit) {
                        otherFullPrefix.add(item.entry)
                    }
                }
                TIER_WORD_PREFIX -> {
                    val higher = preferredExact.size + otherExact.size +
                        preferredFullPrefix.size + otherFullPrefix.size
                    if (isPreferred) {
                        if (higher + preferredWordPrefix.size < limit) {
                            preferredWordPrefix.add(item.entry)
                        }
                    } else if (higher + preferredWordPrefix.size + otherWordPrefix.size < limit) {
                        otherWordPrefix.add(item.entry)
                    }
                }
            }

            val exactFull = if (preferred != null) {
                preferredExact.size >= limit
            } else {
                otherExact.size >= limit
            }
            if (exactFull) break
        }

        val combined = ArrayList<CityEntry>(limit)
        fun appendUpToLimit(source: List<CityEntry>) {
            val remaining = limit - combined.size
            if (remaining <= 0) return
            combined.addAll(if (source.size <= remaining) source else source.subList(0, remaining))
        }
        appendUpToLimit(preferredExact)
        appendUpToLimit(otherExact)
        appendUpToLimit(preferredFullPrefix)
        appendUpToLimit(otherFullPrefix)
        appendUpToLimit(preferredWordPrefix)
        appendUpToLimit(otherWordPrefix)

        val nameCounts = HashMap<String, Int>(combined.size * 2)
        for (city in combined) {
            val key = normalizeForSearch(city.name)
            nameCounts[key] = (nameCounts[key] ?: 0) + 1
        }

        return combined.map { city ->
            val sharedName = (nameCounts[normalizeForSearch(city.name)] ?: 0) > 1
            CitySearchResult(
                city = city,
                regionLabel = formatRegionLabel(
                    city = city,
                    countryNameForCode = countryNameForCode,
                    includeAdmin1WhenEqualToName = sharedName,
                ),
            )
        }
    }

    /**
     * Returns the ISO country code of the nearest indexed city to ([latitude], [longitude]),
     * or `null` when the index is empty. Used as a fallback preferred country when the device
     * locale carries no country region.
     */
    fun countryCodeFor(latitude: Double, longitude: Double): String? {
        if (entries.isEmpty()) return null
        val cosLat = cos(Math.toRadians(latitude))
        var bestCode: String? = null
        var bestDistSq = Double.POSITIVE_INFINITY
        for (item in entries) {
            val entry = item.entry
            val dLat = entry.latitude - latitude
            var dLon = entry.longitude - longitude
            if (dLon > 180.0) dLon -= 360.0 else if (dLon < -180.0) dLon += 360.0
            val scaledLon = dLon * cosLat
            val distSq = dLat * dLat + scaledLon * scaledLon
            if (distSq < bestDistSq) {
                bestDistSq = distSq
                bestCode = entry.countryCode
                if (distSq == 0.0) break
            }
        }
        return bestCode
    }

    private fun matchTier(item: IndexedCity, q: String): Int {
        if (item.normName == q || item.normAscii == q) return TIER_EXACT
        if (item.normName.startsWith(q) || item.normAscii.startsWith(q)) return TIER_FULL_PREFIX
        if (matchesWordStart(item.normName, item.nameWordStarts, q) ||
            (item.normAscii != item.normName && matchesWordStart(item.normAscii, item.asciiWordStarts, q))
        ) {
            return TIER_WORD_PREFIX
        }
        return TIER_NONE
    }

    private fun matchesWordStart(text: String, wordStarts: IntArray, q: String): Boolean {
        for (start in wordStarts) {
            if (start == 0) continue
            if (text.regionMatches(start, q, 0, q.length)) return true
        }
        return false
    }

    private class IndexedCity(
        val entry: CityEntry,
        val normName: String,
        val normAscii: String,
        val nameWordStarts: IntArray,
        val asciiWordStarts: IntArray,
    )

    companion object {
        const val DEFAULT_LIMIT: Int = 20

        private const val TIER_NONE = -1
        private const val TIER_EXACT = 0
        private const val TIER_FULL_PREFIX = 1
        private const val TIER_WORD_PREFIX = 2

        fun empty(): CityIndex = CityIndex(emptyList(), emptyMap())

        fun fromEntries(rawEntries: List<CityEntry>): CityIndex {
            val sorted = rawEntries.sortedWith(
                compareByDescending<CityEntry> { it.population }
                    .thenBy { it.name }
                    .thenBy { it.countryCode }
                    .thenBy { it.admin1 },
            )
            val indexed = ArrayList<IndexedCity>(sorted.size)
            val bucketLists = HashMap<Char, MutableList<Int>>(64)

            for (i in sorted.indices) {
                val entry = sorted[i]
                val normName = normalizeForSearch(entry.name)
                val normAscii = normalizeForSearch(entry.asciiName)
                val nameStarts = findWordStarts(normName)
                val asciiStarts = if (normAscii == normName) nameStarts else findWordStarts(normAscii)
                indexed.add(IndexedCity(entry, normName, normAscii, nameStarts, asciiStarts))

                val seenChars = HashSet<Char>(4)
                for (pos in nameStarts) {
                    val c = normName[pos]
                    if (seenChars.add(c)) {
                        bucketLists.getOrPut(c) { ArrayList() }.add(i)
                    }
                }
                if (normAscii != normName) {
                    for (pos in asciiStarts) {
                        val c = normAscii[pos]
                        if (seenChars.add(c)) {
                            bucketLists.getOrPut(c) { ArrayList() }.add(i)
                        }
                    }
                }
            }

            val buckets = HashMap<Char, IntArray>(bucketLists.size * 2)
            for ((ch, list) in bucketLists) {
                buckets[ch] = list.toIntArray()
            }
            return CityIndex(indexed, buckets)
        }

        /**
         * Formats the secondary disambiguation label for [city] using `admin1` and country.
         *
         * Omits `admin1` when it is blank or matches the city's own name (e.g. Melilla, Melilla),
         * unless [includeAdmin1WhenEqualToName] is `true` (used when multiple search results share
         * the same city name so every row shows its region consistently).
         */
        fun formatRegionLabel(
            city: CityEntry,
            countryNameForCode: (String) -> String = { it },
            includeAdmin1WhenEqualToName: Boolean = false,
        ): String {
            val country = countryNameForCode(city.countryCode).trim().ifEmpty { city.countryCode }
            val admin1 = city.admin1.trim()
            val showAdmin1 = admin1.isNotEmpty() &&
                (includeAdmin1WhenEqualToName || normalizeForSearch(admin1) != normalizeForSearch(city.name))
            return if (showAdmin1) {
                "$admin1 · $country"
            } else {
                country
            }
        }

        /**
         * Normalizes text for accent- and case-insensitive prefix matching.
         */
        fun normalizeForSearch(text: String): String {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return ""
            val decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
            val sb = StringBuilder(decomposed.length)
            var lastWasSpace = false
            for (ch in decomposed) {
                if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
                if (ch.isWhitespace()) {
                    if (!lastWasSpace) {
                        sb.append(' ')
                        lastWasSpace = true
                    }
                    continue
                }
                lastWasSpace = false
                when (ch) {
                    'ø', 'Ø' -> sb.append('o')
                    'ł', 'Ł' -> sb.append('l')
                    'đ', 'Đ', 'ð', 'Ð' -> sb.append('d')
                    'ß' -> sb.append("ss")
                    'æ', 'Æ' -> sb.append("ae")
                    'œ', 'Œ' -> sb.append("oe")
                    'þ', 'Þ' -> sb.append("th")
                    else -> sb.append(ch.lowercaseChar())
                }
            }
            return sb.toString().lowercase(Locale.ROOT).trim()
        }

        private fun findWordStarts(normalized: String): IntArray {
            if (normalized.isEmpty()) return IntArray(0)
            val starts = ArrayList<Int>(4)
            var previousWasBoundary = true
            for (i in normalized.indices) {
                val c = normalized[i]
                val isWordChar = c.isLetterOrDigit()
                if (isWordChar && previousWasBoundary) {
                    starts.add(i)
                }
                previousWasBoundary = !isWordChar
            }
            return starts.toIntArray()
        }
    }
}
