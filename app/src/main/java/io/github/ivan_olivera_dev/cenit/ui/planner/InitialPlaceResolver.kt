package io.github.ivan_olivera_dev.cenit.ui.planner

import android.content.Context
import androidx.compose.runtime.saveable.Saver
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.data.DeviceLocationProvider
import io.github.ivan_olivera_dev.cenit.data.PlacesStore
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure startup place priority:
 * 1. Last active place if saved locally,
 * 2. Device location if location permission is already granted and a fix is available,
 * 3. [fallback] (Madrid).
 */
fun resolveInitialPlace(
    lastActive: PlannerPlace?,
    permittedDevicePlace: PlannerPlace?,
    fallback: PlannerPlace = DefaultMadridPlace,
): PlannerPlace = lastActive ?: permittedDevicePlace ?: fallback

/**
 * Resolves the initial [PlannerState] on app startup without prompting for runtime permissions.
 */
fun resolveInitialPlannerState(
    context: Context,
    clock: Clock = Clock.systemDefaultZone(),
    deviceZone: ZoneId = ZoneId.systemDefault(),
): PlannerState {
    val store = PlacesStore.fromContext(context)
    val lastActive = store.load().activePlace?.toPlannerPlace()
    val permittedDevice = if (lastActive == null) {
        DeviceLocationProvider.lastKnownIfPermitted(context)
    } else {
        null
    }
    val initialPlace = resolveInitialPlace(
        lastActive = lastActive,
        permittedDevicePlace = permittedDevice,
    )
    return PlannerState.forPlaceNow(
        activePlace = initialPlace,
        clock = clock,
        deviceZone = deviceZone,
    )
}

/**
 * [Saver] that preserves the active place, its resolved zone, selected date, and minute offset
 * across configuration changes.
 */
fun plannerStateSaver(deviceZone: ZoneId = ZoneId.systemDefault()): Saver<PlannerState, List<Any>> =
    Saver(
        save = { state ->
            listOf(
                state.activePlace.name,
                state.activePlace.place.latitude,
                state.activePlace.place.longitude,
                state.activePlace.region,
                state.activePlace.zoneId ?: "",
                state.activePlace.id ?: "",
                state.activePlace.source.name,
                state.zone.id,
                state.selectedDate.toEpochDay(),
                state.minuteOffset,
            )
        },
        restore = { saved ->
            val name = saved[0] as String
            val lat = saved[1] as Double
            val lon = saved[2] as Double
            val region = saved[3] as String
            val placeZoneId = (saved[4] as String).ifEmpty { null }
            val id = (saved[5] as String).ifEmpty { null }
            val source = runCatching { PlaceSource.valueOf(saved[6] as String) }
                .getOrDefault(PlaceSource.DEFAULT)
            val zone = runCatching { ZoneId.of(saved[7] as String) }.getOrDefault(deviceZone)
            val date = LocalDate.ofEpochDay(saved[8] as Long)
            val minuteOffset = saved[9] as Int
            PlannerState(
                activePlace = PlannerPlace(
                    name = name,
                    place = Place(latitude = lat, longitude = lon),
                    region = region,
                    zoneId = placeZoneId,
                    id = id,
                    source = source,
                ),
                zone = zone,
                selectedDate = date,
                minuteOffset = minuteOffset,
            )
        },
    )
