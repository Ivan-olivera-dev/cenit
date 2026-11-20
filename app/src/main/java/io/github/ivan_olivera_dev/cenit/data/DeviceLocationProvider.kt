package io.github.ivan_olivera_dev.cenit.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.ui.planner.PlaceSource
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerPlace
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed interface DeviceLocationResult {
    data class Success(
        val latitude: Double,
        val longitude: Double,
        val isLastKnownFallback: Boolean = false,
    ) : DeviceLocationResult

    data object PermissionDenied : DeviceLocationResult
    data object ProvidersDisabled : DeviceLocationResult
    data object Unavailable : DeviceLocationResult
}

/**
 * Offline device location via Android's [LocationManager] only (no Google Play Services).
 *
 * Uses `ACCESS_COARSE_LOCATION` (and `ACCESS_FINE_LOCATION` when granted), performs a single
 * current-location request with timeout, and falls back to the freshest last-known fix.
 */
object DeviceLocationProvider {
    private const val DEFAULT_TIMEOUT_MS = 10_000L

    fun hasLocationPermission(context: Context): Boolean =
        hasCoarsePermission(context) || hasFinePermission(context)

    fun hasCoarsePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    fun hasFinePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Reads the freshest last-known device location ONLY if permission is already granted.
     * Never prompts the user; used for startup place resolution.
     */
    fun lastKnownIfPermitted(context: Context): PlannerPlace? {
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val best = bestLastKnownLocation(manager) ?: return null
        return toGpsPlannerPlace(context, best.latitude, best.longitude)
    }

    /**
     * Requests a single current location fix with [timeoutMs] timeout, falling back to the
     * freshest last-known location if a fresh fix cannot be obtained in time.
     */
    suspend fun requestCurrentLocation(
        context: Context,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): DeviceLocationResult {
        if (!hasLocationPermission(context)) return DeviceLocationResult.PermissionDenied
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return DeviceLocationResult.Unavailable

        val provider = selectBestEnabledProvider(manager, fineGranted = hasFinePermission(context))
        if (provider == null) {
            val cached = bestLastKnownLocation(manager)
            return if (cached != null) {
                DeviceLocationResult.Success(
                    latitude = cached.latitude,
                    longitude = cached.longitude,
                    isLastKnownFallback = true,
                )
            } else {
                DeviceLocationResult.ProvidersDisabled
            }
        }

        val fresh = withTimeoutOrNull(timeoutMs) {
            requestSingleFix(context, manager, provider)
        }
        if (fresh != null) {
            return DeviceLocationResult.Success(
                latitude = fresh.latitude,
                longitude = fresh.longitude,
                isLastKnownFallback = false,
            )
        }

        val fallback = bestLastKnownLocation(manager)
        return if (fallback != null) {
            DeviceLocationResult.Success(
                latitude = fallback.latitude,
                longitude = fallback.longitude,
                isLastKnownFallback = true,
            )
        } else {
            DeviceLocationResult.Unavailable
        }
    }

    fun toGpsPlannerPlace(context: Context, latitude: Double, longitude: Double): PlannerPlace =
        PlannerPlace(
            name = context.getString(R.string.places_my_location),
            place = Place(latitude = latitude, longitude = longitude),
            region = context.getString(R.string.places_gps_subtitle),
            zoneId = null, // GPS always uses device ZoneId
            source = PlaceSource.GPS,
        )

    private fun selectBestEnabledProvider(
        manager: LocationManager,
        fineGranted: Boolean,
    ): String? {
        val candidates = buildList {
            if (fineGranted) {
                add(LocationManager.GPS_PROVIDER)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(LocationManager.FUSED_PROVIDER)
                }
                add(LocationManager.NETWORK_PROVIDER)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(LocationManager.FUSED_PROVIDER)
                }
                add(LocationManager.NETWORK_PROVIDER)
                add(LocationManager.GPS_PROVIDER)
            }
        }
        return candidates.firstOrNull { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleFix(
        context: Context,
        manager: LocationManager,
        provider: String,
    ): Location? = suspendCancellableCoroutine { cont ->
        val signal = CancellationSignal()
        cont.invokeOnCancellation { signal.cancel() }
        try {
            LocationManagerCompat.getCurrentLocation(
                manager,
                provider,
                signal,
                ContextCompat.getMainExecutor(context),
            ) { location ->
                if (cont.isActive) {
                    cont.resume(location)
                }
            }
        } catch (_: SecurityException) {
            if (cont.isActive) cont.resume(null)
        } catch (_: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnownLocation(manager: LocationManager): Location? {
        val providers = buildList {
            add(LocationManager.GPS_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        var best: Location? = null
        for (provider in providers) {
            val loc = runCatching { manager.getLastKnownLocation(provider) }.getOrNull() ?: continue
            if (!loc.latitude.isFinite() || loc.latitude !in -90.0..90.0) continue
            if (!loc.longitude.isFinite() || loc.longitude !in -180.0..180.0) continue
            if (best == null || loc.time > best.time) {
                best = loc
            }
        }
        return best
    }
}
