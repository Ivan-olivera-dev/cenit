package io.github.ivan_olivera_dev.cenit.alerts

import android.content.Context
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.alerts.AlertKind
import java.io.File
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Persisted alert subscription: an [AlertKind] paired with the active place and its resolved
 * IANA [zoneId] at subscription time.
 */
data class AlertSubscription(
    val id: String,
    val kind: AlertKind,
    val placeName: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String,
) {
    fun toPlace(): Place = Place(latitude = latitude, longitude = longitude)

    fun resolveZoneOrNull(): ZoneId? = runCatching { ZoneId.of(zoneId) }.getOrNull()

    fun matches(kind: AlertKind, place: Place): Boolean =
        this.kind == kind &&
            abs(latitude - place.latitude) < COORD_EPSILON &&
            abs(longitude - place.longitude) < COORD_EPSILON

    companion object {
        internal const val COORD_EPSILON = 1e-4

        fun create(
            kind: AlertKind,
            placeName: String,
            place: Place,
            zoneId: String,
        ): AlertSubscription = AlertSubscription(
            id = stableId(kind, place.latitude, place.longitude),
            kind = kind,
            placeName = placeName.trim(),
            latitude = place.latitude,
            longitude = place.longitude,
            zoneId = zoneId,
        )

        /**
         * Stable identifier derived from [kind] and coordinates rounded to 4 decimal places
         * (~11 m), so re-subscribing the same event and place keeps the same id and request code.
         */
        fun stableId(kind: AlertKind, latitude: Double, longitude: Double): String {
            val latScaled = (latitude * 10_000.0).roundToLong()
            val lonScaled = (longitude * 10_000.0).roundToLong()
            return "${kind.name}@$latScaled,$lonScaled"
        }
    }
}

/**
 * Local JSON file persistence for alert subscriptions in `filesDir/alerts.json`.
 *
 * Mirrors [io.github.ivan_olivera_dev.cenit.data.PlacesStore]: zero external dependencies,
 * strict RFC 8259 JSON codec, atomic temp-file-then-rename writes, and corrupt-file recovery.
 */
class AlertSubscriptionsStore(private val file: File) {

    @Synchronized
    fun load(): List<AlertSubscription> {
        if (!file.isFile) return emptyList()
        return runCatching {
            AlertSubscriptionsJsonCodec.decode(file.readText(Charsets.UTF_8))
        }.getOrElse {
            emptyList()
        }
    }

    @Synchronized
    fun findById(id: String): AlertSubscription? =
        load().firstOrNull { it.id == id }

    @Synchronized
    fun findMatching(kind: AlertKind, place: Place): AlertSubscription? =
        load().firstOrNull { it.matches(kind, place) }

    @Synchronized
    fun add(subscription: AlertSubscription): List<AlertSubscription> {
        val current = load().toMutableList()
        val place = subscription.toPlace()
        val idx = current.indexOfFirst { it.id == subscription.id || it.matches(subscription.kind, place) }
        if (idx >= 0) {
            current[idx] = subscription
        } else {
            current.add(subscription)
        }
        writeList(current)
        return current
    }

    @Synchronized
    fun removeById(id: String): List<AlertSubscription> {
        val updated = load().filterNot { it.id == id }
        writeList(updated)
        return updated
    }

    @Synchronized
    fun removeMatching(kind: AlertKind, place: Place): List<AlertSubscription> {
        val updated = load().filterNot { it.matches(kind, place) }
        writeList(updated)
        return updated
    }

    private fun writeList(subscriptions: List<AlertSubscription>) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val encoded = AlertSubscriptionsJsonCodec.encode(subscriptions)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(encoded, Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.writeText(encoded, Charsets.UTF_8)
            tmp.delete()
        }
    }

    companion object {
        private const val FILE_NAME = "alerts.json"

        fun fromContext(context: Context): AlertSubscriptionsStore =
            AlertSubscriptionsStore(File(context.applicationContext.filesDir, FILE_NAME))
    }
}

/**
 * Minimal RFC 8259 JSON codec for [AlertSubscription] lists.
 */
internal object AlertSubscriptionsJsonCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(subscriptions: List<AlertSubscription>): String = buildString {
        append("{\"version\":")
        append(SCHEMA_VERSION)
        append(",\"subscriptions\":[")
        subscriptions.forEachIndexed { idx, sub ->
            if (idx > 0) append(',')
            append("{\"id\":")
            appendJsonString(sub.id)
            append(",\"kind\":")
            appendJsonString(sub.kind.name)
            append(",\"placeName\":")
            appendJsonString(sub.placeName)
            append(",\"lat\":")
            append(sub.latitude)
            append(",\"lon\":")
            append(sub.longitude)
            append(",\"zoneId\":")
            appendJsonString(sub.zoneId)
            append('}')
        }
        append("]}")
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

    fun decode(json: String): List<AlertSubscription> {
        val root = JsonReader(json).parseObject()
        val rawList = root["subscriptions"] as? List<*> ?: return emptyList()
        return rawList.mapNotNull { item ->
            (item as? Map<*, *>)?.let(::mapToSubscription)
        }
    }

    private fun mapToSubscription(map: Map<*, *>): AlertSubscription? {
        val id = (map["id"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val kindName = map["kind"] as? String ?: return null
        val kind = runCatching { AlertKind.valueOf(kindName) }.getOrNull() ?: return null
        val placeName = map["placeName"] as? String ?: return null
        val lat = (map["lat"] as? Number)?.toDouble() ?: return null
        val lon = (map["lon"] as? Number)?.toDouble() ?: return null
        if (!lat.isFinite() || lat !in -90.0..90.0) return null
        if (!lon.isFinite() || lon !in -180.0..180.0) return null
        val zoneId = (map["zoneId"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        return AlertSubscription(
            id = id,
            kind = kind,
            placeName = placeName,
            latitude = lat,
            longitude = lon,
            zoneId = zoneId,
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
