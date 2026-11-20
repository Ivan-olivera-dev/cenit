package io.github.ivan_olivera_dev.cenit.ui.planner

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.data.SavedPlace

/**
 * Resolves the user-facing display name and region of a stored place in the current app locale
 * without changing persisted schemas (`places.json`, `alerts.json`).
 *
 * Places created from GPS or manual coordinates persist the localized default label active at
 * creation time. If the stored name still equals one of Cenit's known default labels in any
 * shipped language, it is rendered with the current language's label; user-renamed places and
 * GeoNames cities always keep their stored names.
 */
object PlaceDisplayName {
    val GPS_DEFAULT_NAMES: Set<String> = setOf(
        "My location",
        "Mi ubicación",
    )

    val MANUAL_DEFAULT_NAMES: Set<String> = setOf(
        "Manual coordinates",
        "Coordenadas manuales",
    )

    val GPS_DEFAULT_REGIONS: Set<String> = setOf(
        "Phone GPS · offline",
        "GPS del móvil · sin conexión",
    )

    /**
     * Pure JVM display-name resolution.
     *
     * When [source] is `null` (for [io.github.ivan_olivera_dev.cenit.alerts.AlertSubscription],
     * which does not persist a [PlaceSource]), any known GPS or manual default label is mapped to
     * its corresponding current-locale label while custom, city, and blank names stay untouched.
     */
    fun resolve(
        storedName: String,
        source: PlaceSource?,
        gpsLabel: String,
        manualLabel: String,
    ): String {
        val trimmed = storedName.trim()
        return when (source) {
            PlaceSource.GPS -> if (trimmed in GPS_DEFAULT_NAMES) gpsLabel else storedName
            PlaceSource.MANUAL -> if (trimmed in MANUAL_DEFAULT_NAMES) manualLabel else storedName
            PlaceSource.CITY, PlaceSource.DEFAULT -> storedName
            null -> when (trimmed) {
                in GPS_DEFAULT_NAMES -> gpsLabel
                in MANUAL_DEFAULT_NAMES -> manualLabel
                else -> storedName
            }
        }
    }

    /**
     * Pure JVM region subtitle resolution for GPS places whose subtitle was persisted in another
     * language.
     */
    fun resolveRegion(
        storedRegion: String,
        source: PlaceSource,
        gpsSubtitle: String,
    ): String {
        if (source != PlaceSource.GPS) return storedRegion
        return if (storedRegion.trim() in GPS_DEFAULT_REGIONS) gpsSubtitle else storedRegion
    }

    /**
     * Convenience wrapper using a localized [Context] (e.g. from `AlertNotifier`).
     */
    fun resolve(
        context: Context,
        storedName: String,
        source: PlaceSource? = null,
    ): String = resolve(
        storedName = storedName,
        source = source,
        gpsLabel = context.getString(R.string.places_my_location),
        manualLabel = context.getString(R.string.places_manual_default_name),
    )
}

@Composable
fun PlannerPlace.displayName(): String = PlaceDisplayName.resolve(
    storedName = name,
    source = source,
    gpsLabel = stringResource(R.string.places_my_location),
    manualLabel = stringResource(R.string.places_manual_default_name),
)

@Composable
fun PlannerPlace.displayRegion(): String = PlaceDisplayName.resolveRegion(
    storedRegion = region,
    source = source,
    gpsSubtitle = stringResource(R.string.places_gps_subtitle),
)

@Composable
fun SavedPlace.displayName(): String = PlaceDisplayName.resolve(
    storedName = name,
    source = source,
    gpsLabel = stringResource(R.string.places_my_location),
    manualLabel = stringResource(R.string.places_manual_default_name),
)

@Composable
fun SavedPlace.displayRegion(): String = PlaceDisplayName.resolveRegion(
    storedRegion = region,
    source = source,
    gpsSubtitle = stringResource(R.string.places_gps_subtitle),
)
