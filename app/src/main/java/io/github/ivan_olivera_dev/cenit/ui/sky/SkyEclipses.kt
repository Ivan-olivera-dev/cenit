package io.github.ivan_olivera_dev.cenit.ui.sky

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.TimeRange
import io.github.ivan_olivera_dev.cenit.core.eclipses.CenitEclipse
import io.github.ivan_olivera_dev.cenit.core.eclipses.EclipseBody
import io.github.ivan_olivera_dev.cenit.core.eclipses.EclipseKind
import io.github.ivan_olivera_dev.cenit.core.format.CenitFormat
import io.github.ivan_olivera_dev.cenit.core.format.ClockFormat
import io.github.ivan_olivera_dev.cenit.settings.LocalAppSettings
import io.github.ivan_olivera_dev.cenit.ui.theme.CenitPalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal const val SKY_ECLIPSES_MAX_SHOWN = 4
internal const val SKY_ECLIPSES_FETCH_COUNT = 16
internal const val SKY_ECLIPSES_HORIZON_YEARS = 10

/**
 * Cache key for upcoming eclipses: scoped by observer [place], local calendar [anchorDate],
 * and [zoneId] so date stepping or tab switches never recompute for the same place.
 */
data class EclipseCacheKey(
    val place: Place,
    val anchorDate: LocalDate,
    val zoneId: String,
)

/**
 * Thread-safe bounded LRU cache of upcoming visible eclipses keyed by [EclipseCacheKey].
 */
class SkyEclipseCache(private val maxEntries: Int = 16) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive, was $maxEntries" }
    }

    private val entries = object : LinkedHashMap<EclipseCacheKey, List<CenitEclipse>>(
        maxEntries + 1,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<EclipseCacheKey, List<CenitEclipse>>,
        ): Boolean = size > maxEntries
    }

    @Synchronized
    fun get(key: EclipseCacheKey): List<CenitEclipse>? = entries[key]

    @Synchronized
    fun put(key: EclipseCacheKey, eclipses: List<CenitEclipse>) {
        entries[key] = eclipses
    }

    fun getOrCompute(
        key: EclipseCacheKey,
        compute: () -> List<CenitEclipse>,
    ): List<CenitEclipse> {
        get(key)?.let { return it }
        val computed = compute()
        put(key, computed)
        return computed
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}

/** Shared in-memory eclipse cache across Cielo tab visits within the process. */
val DefaultSkyEclipseCache = SkyEclipseCache(maxEntries = 16)

/**
 * Filters cached upcoming eclipses for the active Cielo segmented [filter], capping at [limit].
 */
fun filterEclipsesForTab(
    eclipses: List<CenitEclipse>,
    filter: SkyTimesFilter,
    limit: Int = SKY_ECLIPSES_MAX_SHOWN,
): List<CenitEclipse> = when (filter) {
    SkyTimesFilter.ALL -> eclipses.take(limit)
    SkyTimesFilter.SUN -> eclipses.filter { it.body == EclipseBody.SUN }.take(limit)
    SkyTimesFilter.MOON -> eclipses.filter { it.body == EclipseBody.MOON }.take(limit)
}

/**
 * Rounded obscuration percentage (`1..99`) to display alongside partial or annular eclipse titles,
 * or `null` for total/penumbral eclipses or unavailable values.
 */
fun eclipseObscurationPercent(eclipse: CenitEclipse): Int? {
    if (eclipse.kind != EclipseKind.PARTIAL && eclipse.kind != EclipseKind.ANNULAR) return null
    val fraction = eclipse.obscuration ?: return null
    if (!fraction.isFinite()) return null
    val pct = (fraction * 100.0).roundToInt().coerceIn(0, 100)
    return pct.takeIf { it in 1..99 }
}

/**
 * Formats an eclipse peak's local calendar date (e.g. `"2 ago 2027"` / `"2 Aug 2027"`),
 * stripping trailing abbreviation dots produced by some ICU month tables.
 */
fun formatEclipseDate(instant: Instant, zone: ZoneId, locale: Locale): String {
    val localDate = instant.atZone(zone).toLocalDate()
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    return formatter.format(localDate).replace(".", "")
}

/**
 * Formats a phase [range] as minute-truncated `(start, end)` strings in [zone] using [clock]
 * and [locale] (defaults to 24-hour `HH:mm`).
 */
fun formatEclipseTimeRange(
    range: TimeRange,
    zone: ZoneId,
    clock: ClockFormat = ClockFormat.H24,
    locale: Locale = Locale.ROOT,
): Pair<String, String> = CenitFormat.formatTimeRangeParts(range.start, range.end, zone, clock, locale)

@Composable
internal fun EclipseEventRow(
    eclipse: CenitEclipse,
    zone: ZoneId,
    locale: Locale,
    palette: CenitPalette,
    showDivider: Boolean,
) {
    val clock = LocalAppSettings.current.clock
    val baseTitle = eclipseKindTitle(eclipse.body, eclipse.kind)
    val obscurationPct = eclipseObscurationPercent(eclipse)
    val titleText = if (obscurationPct != null) {
        stringResource(R.string.eclipse_title_with_obscuration, baseTitle, obscurationPct)
    } else {
        baseTitle
    }
    val dateText = formatEclipseDate(eclipse.peak, zone, locale)
    val peakText = stringResource(
        R.string.eclipse_peak_time,
        CenitFormat.formatTime(eclipse.peak, zone, clock, locale),
    )

    val totalPhaseText = eclipse.totalRange?.let { range ->
        val (start, end) = formatEclipseTimeRange(range, zone, clock, locale)
        val resId = if (eclipse.kind == EclipseKind.ANNULAR) {
            R.string.eclipse_phase_annular
        } else {
            R.string.eclipse_phase_total
        }
        stringResource(resId, start, end)
    }
    val partialPhaseText = eclipse.partialRange?.let { range ->
        val (start, end) = formatEclipseTimeRange(range, zone, clock, locale)
        stringResource(R.string.eclipse_phase_partial, start, end)
    }

    val primaryTimesText = when {
        totalPhaseText != null -> stringResource(R.string.eclipse_times_pair, peakText, totalPhaseText)
        partialPhaseText != null -> stringResource(R.string.eclipse_times_pair, peakText, partialPhaseText)
        else -> peakText
    }
    val secondaryPhaseText = if (totalPhaseText != null) partialPhaseText else null

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(vertical = 10.dp)
                .semantics(mergeDescendants = true) {},
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    EclipseBodyDot(body = eclipse.body, palette = palette)
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = palette.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = palette.textSecondary,
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = primaryTimesText,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = palette.textSecondary,
                modifier = Modifier.padding(start = 15.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (secondaryPhaseText != null) {
                Text(
                    text = secondaryPhaseText,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = palette.textSecondary,
                    modifier = Modifier.padding(start = 15.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
        }
    }
}

@Composable
internal fun EclipseQuietLine(
    text: String,
    palette: CenitPalette,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = palette.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    )
}

@Composable
private fun EclipseBodyDot(body: EclipseBody, palette: CenitPalette) {
    when (body) {
        EclipseBody.SUN -> Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(palette.sun),
        )
        EclipseBody.MOON -> Box(
            modifier = Modifier
                .size(8.dp)
                .border(1.5.dp, palette.moon, CircleShape),
        )
    }
}

@Composable
private fun eclipseKindTitle(body: EclipseBody, kind: EclipseKind): String = stringResource(
    when (body) {
        EclipseBody.SUN -> when (kind) {
            EclipseKind.PARTIAL -> R.string.eclipse_solar_partial
            EclipseKind.ANNULAR -> R.string.eclipse_solar_annular
            EclipseKind.TOTAL -> R.string.eclipse_solar_total
            EclipseKind.PENUMBRAL -> R.string.eclipse_solar_partial
        }
        EclipseBody.MOON -> when (kind) {
            EclipseKind.PENUMBRAL -> R.string.eclipse_lunar_penumbral
            EclipseKind.PARTIAL -> R.string.eclipse_lunar_partial
            EclipseKind.TOTAL -> R.string.eclipse_lunar_total
            EclipseKind.ANNULAR -> R.string.eclipse_lunar_partial
        }
    },
)
