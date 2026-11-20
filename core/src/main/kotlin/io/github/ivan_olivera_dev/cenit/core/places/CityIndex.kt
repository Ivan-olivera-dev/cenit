package io.github.ivan_olivera_dev.cenit.core.places

import java.text.Normalizer
import java.util.Locale

/**
 * In-memory offline city prefix search index.
 *
 * Matches accent- and case-insensitively on the start of the full name or any word within
 * `name` / `asciiName`, ranking exact matches first, then full-name prefixes, then non-leading
 * word-start prefixes, and breaking ties by descending population.
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
        countryNameForCode: (String) -> String = { it },
    ): List<CitySearchResult> {
        if (limit <= 0) return emptyList()
        val normalizedQuery = normalizeForSearch(query)
        if (normalizedQuery.isEmpty()) return emptyList()

        val candidates = firstCharBuckets[normalizedQuery[0]] ?: return emptyList()

        val exactMatches = ArrayList<CityEntry>(minOf(limit, 8))
        val fullPrefixMatches = ArrayList<CityEntry>(limit)
        val wordPrefixMatches = ArrayList<CityEntry>(limit)

        for (idx in candidates) {
            val item = entries[idx]
            val tier = matchTier(item, normalizedQuery)
            when (tier) {
                TIER_EXACT -> {
                    if (exactMatches.size < limit) exactMatches.add(item.entry)
                }
                TIER_FULL_PREFIX -> {
                    if (exactMatches.size + fullPrefixMatches.size < limit) {
                        fullPrefixMatches.add(item.entry)
                    }
                }
                TIER_WORD_PREFIX -> {
                    if (exactMatches.size + fullPrefixMatches.size + wordPrefixMatches.size < limit) {
                        wordPrefixMatches.add(item.entry)
                    }
                }
            }
            if (exactMatches.size >= limit) break
        }

        val combined = ArrayList<CityEntry>(limit)
        combined.addAll(exactMatches)
        if (combined.size < limit) {
            combined.addAll(fullPrefixMatches.take(limit - combined.size))
        }
        if (combined.size < limit) {
            combined.addAll(wordPrefixMatches.take(limit - combined.size))
        }

        return combined.map { city ->
            CitySearchResult(
                city = city,
                regionLabel = formatRegionLabel(city, countryNameForCode),
            )
        }
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
         * Omits `admin1` when it is blank or matches the city's own name (e.g. Madrid, Madrid).
         */
        fun formatRegionLabel(
            city: CityEntry,
            countryNameForCode: (String) -> String = { it },
        ): String {
            val country = countryNameForCode(city.countryCode).trim().ifEmpty { city.countryCode }
            val admin1 = city.admin1.trim()
            return if (admin1.isNotEmpty() && normalizeForSearch(admin1) != normalizeForSearch(city.name)) {
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
