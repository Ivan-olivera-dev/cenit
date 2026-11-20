package io.github.ivan_olivera_dev.cenit.data

import android.content.Context
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.ui.planner.PlaceSource
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerPlace
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/**
 * Persisted location entry stored locally in `filesDir/places.json`.
 */
data class SavedPlace(
    val id: String,
    val name: String,
    val region: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String?,
    val source: PlaceSource,
) {
    fun toPlannerPlace(): PlannerPlace = PlannerPlace(
        name = name,
        place = Place(latitude = latitude, longitude = longitude),
        region = region,
        zoneId = zoneId,
        id = id.ifEmpty { null },
        source = source,
    )

    companion object {
        fun fromPlannerPlace(
            place: PlannerPlace,
            id: String = place.id ?: "",
        ): SavedPlace = SavedPlace(
            id = id,
            name = place.name,
            region = place.region,
            latitude = place.place.latitude,
            longitude = place.place.longitude,
            zoneId = place.zoneId,
            source = place.source,
        )
    }
}

data class PlacesSnapshot(
    val activePlace: SavedPlace?,
    val savedPlaces: List<SavedPlace>,
)

/**
 * Local JSON file persistence for saved places and the last active place.
 *
 * Uses a pure-Kotlin JSON codec (zero external dependencies, runs cleanly in JVM unit tests
 * without Android stubs) and atomic temp-file-then-rename writes.
 */
class PlacesStore(private val file: File) {

    @Synchronized
    fun load(): PlacesSnapshot {
        if (!file.isFile) return PlacesSnapshot(activePlace = null, savedPlaces = emptyList())
        return runCatching {
            val text = file.readText(Charsets.UTF_8)
            PlacesJsonCodec.decode(text)
        }.getOrElse {
            PlacesSnapshot(activePlace = null, savedPlaces = emptyList())
        }
    }

    @Synchronized
    fun saveActivePlace(place: PlannerPlace): PlacesSnapshot {
        val current = load()
        val active = SavedPlace.fromPlannerPlace(place, id = place.id ?: "")
        val updated = current.copy(activePlace = active)
        writeSnapshot(updated)
        return updated
    }

    @Synchronized
    fun savePlace(place: SavedPlace): PlacesSnapshot {
        val current = load()
        val assignedId = place.id.ifBlank { UUID.randomUUID().toString() }
        val normalized = place.copy(id = assignedId, name = place.name.trim())
        val existingIdx = current.savedPlaces.indexOfFirst { it.id == assignedId }
        val newList = current.savedPlaces.toMutableList()
        if (existingIdx >= 0) {
            newList[existingIdx] = normalized
        } else {
            newList.add(normalized)
        }
        val updatedActive = current.activePlace?.let { active ->
            if (active.id == assignedId || sameCoords(active, normalized)) {
                normalized
            } else {
                active
            }
        }
        val updated = PlacesSnapshot(activePlace = updatedActive, savedPlaces = newList)
        writeSnapshot(updated)
        return updated
    }

    @Synchronized
    fun renamePlace(id: String, newName: String): PlacesSnapshot {
        val trimmed = newName.trim()
        val current = load()
        if (trimmed.isEmpty()) return current
        val newList = current.savedPlaces.map { item ->
            if (item.id == id) item.copy(name = trimmed) else item
        }
        val updatedActive = current.activePlace?.let { active ->
            if (active.id == id) active.copy(name = trimmed) else active
        }
        val updated = PlacesSnapshot(activePlace = updatedActive, savedPlaces = newList)
        writeSnapshot(updated)
        return updated
    }

    @Synchronized
    fun deletePlace(id: String): PlacesSnapshot {
        val current = load()
        val newList = current.savedPlaces.filterNot { it.id == id }
        val updatedActive = current.activePlace?.let { active ->
            if (active.id == id) active.copy(id = "") else active
        }
        val updated = PlacesSnapshot(activePlace = updatedActive, savedPlaces = newList)
        writeSnapshot(updated)
        return updated
    }

    private fun writeSnapshot(snapshot: PlacesSnapshot) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val encoded = PlacesJsonCodec.encode(snapshot)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(encoded, Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.writeText(encoded, Charsets.UTF_8)
            tmp.delete()
        }
    }

    companion object {
        private const val FILE_NAME = "places.json"
        private const val COORD_EPSILON = 1e-4

        fun fromContext(context: Context): PlacesStore =
            PlacesStore(File(context.applicationContext.filesDir, FILE_NAME))

        fun findMatchingSaved(
            active: PlannerPlace,
            savedPlaces: List<SavedPlace>,
        ): SavedPlace? {
            if (!active.id.isNullOrEmpty()) {
                savedPlaces.firstOrNull { it.id == active.id }?.let { return it }
            }
            return savedPlaces.firstOrNull { saved ->
                abs(saved.latitude - active.place.latitude) < COORD_EPSILON &&
                    abs(saved.longitude - active.place.longitude) < COORD_EPSILON
            }
        }

        private fun sameCoords(a: SavedPlace, b: SavedPlace): Boolean =
            abs(a.latitude - b.latitude) < COORD_EPSILON &&
                abs(a.longitude - b.longitude) < COORD_EPSILON
    }
}

/**
 * Minimal RFC 8259 JSON encoder/decoder for [PlacesSnapshot].
 */
internal object PlacesJsonCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(snapshot: PlacesSnapshot): String = buildString {
        append("{\"version\":")
        append(SCHEMA_VERSION)
        append(",\"active\":")
        if (snapshot.activePlace == null) {
            append("null")
        } else {
            appendPlace(snapshot.activePlace)
        }
        append(",\"saved\":[")
        snapshot.savedPlaces.forEachIndexed { idx, place ->
            if (idx > 0) append(',')
            appendPlace(place)
        }
        append("]}")
    }

    private fun StringBuilder.appendPlace(place: SavedPlace) {
        append("{\"id\":")
        appendJsonString(place.id)
        append(",\"name\":")
        appendJsonString(place.name)
        append(",\"region\":")
        appendJsonString(place.region)
        append(",\"lat\":")
        append(place.latitude)
        append(",\"lon\":")
        append(place.longitude)
        append(",\"zoneId\":")
        if (place.zoneId == null) append("null") else appendJsonString(place.zoneId)
        append(",\"source\":")
        appendJsonString(place.source.name)
        append('}')
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        append(String.format(Locale.ROOT, "\\u%04x", ch.code))
                    } else {
                        append(ch)
                    }
                }
            }
        }
        append('"')
    }

    fun decode(json: String): PlacesSnapshot {
        val root = JsonReader(json).parseObject()
        val activeObj = root["active"] as? Map<*, *>
        val active = activeObj?.let(::mapToSavedPlace)
        val savedRaw = root["saved"] as? List<*> ?: emptyList<Any?>()
        val saved = savedRaw.mapNotNull { item ->
            (item as? Map<*, *>)?.let(::mapToSavedPlace)
        }
        return PlacesSnapshot(activePlace = active, savedPlaces = saved)
    }

    private fun mapToSavedPlace(map: Map<*, *>): SavedPlace? {
        val name = map["name"] as? String ?: return null
        val lat = (map["lat"] as? Number)?.toDouble() ?: return null
        val lon = (map["lon"] as? Number)?.toDouble() ?: return null
        if (!lat.isFinite() || lat !in -90.0..90.0) return null
        if (!lon.isFinite() || lon !in -180.0..180.0) return null
        val id = map["id"] as? String ?: ""
        val region = map["region"] as? String ?: ""
        val zoneId = (map["zoneId"] as? String)?.takeIf { it.isNotBlank() }
        val sourceName = map["source"] as? String ?: PlaceSource.DEFAULT.name
        val source = runCatching { PlaceSource.valueOf(sourceName) }.getOrDefault(PlaceSource.DEFAULT)
        return SavedPlace(
            id = id,
            name = name,
            region = region,
            latitude = lat,
            longitude = lon,
            zoneId = zoneId,
            source = source,
        )
    }

    private class JsonReader(private val s: String) {
        private var pos = 0

        fun parseObject(): Map<String, Any?> {
            skipWs()
            expect('{')
            val map = LinkedHashMap<String, Any?>()
            skipWs()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                expect(':')
                val value = parseValue()
                map[key] = value
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> error("Expected ',' or '}' at $pos")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val list = ArrayList<Any?>()
            skipWs()
            if (peek() == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(parseValue())
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> error("Expected ',' or ']' at $pos")
                }
            }
        }

        private fun parseValue(): Any? {
            skipWs()
            return when (val ch = peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> {
                    expectLiteral("true")
                    true
                }
                'f' -> {
                    expectLiteral("false")
                    false
                }
                'n' -> {
                    expectLiteral("null")
                    null
                }
                '-', in '0'..'9' -> parseNumber()
                else -> error("Unexpected character '$ch' at $pos")
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < s.length) {
                val ch = s[pos++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= s.length) error("Unterminated escape")
                        when (val esc = s[pos++]) {
                            '"', '\\', '/' -> sb.append(esc)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) error("Invalid unicode escape")
                                val hex = s.substring(pos, pos + 4)
                                // toInt(16) alone would accept a sign, e.g. "\\u-001".
                                if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                                    error("Invalid unicode escape")
                                }
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                            else -> error("Invalid escape '\\$esc'")
                        }
                    }
                    else -> sb.append(ch)
                }
            }
            error("Unterminated string")
        }

        private fun parseNumber(): Double {
            val start = pos
            if (pos < s.length && s[pos] == '-') pos++
            while (pos < s.length && s[pos] in '0'..'9') pos++
            if (pos < s.length && s[pos] == '.') {
                pos++
                while (pos < s.length && s[pos] in '0'..'9') pos++
            }
            if (pos < s.length && (s[pos] == 'e' || s[pos] == 'E')) {
                pos++
                if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) pos++
                while (pos < s.length && s[pos] in '0'..'9') pos++
            }
            return s.substring(start, pos).toDouble()
        }

        private fun expectLiteral(lit: String) {
            if (!s.regionMatches(pos, lit, 0, lit.length)) error("Expected '$lit' at $pos")
            pos += lit.length
        }

        private fun expect(ch: Char) {
            if (pos >= s.length || s[pos] != ch) error("Expected '$ch' at $pos")
            pos++
        }

        private fun peek(): Char {
            if (pos >= s.length) error("Unexpected EOF")
            return s[pos]
        }

        private fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }
    }
}
