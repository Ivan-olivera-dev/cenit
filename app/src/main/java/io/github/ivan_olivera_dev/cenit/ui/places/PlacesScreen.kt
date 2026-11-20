package io.github.ivan_olivera_dev.cenit.ui.places

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.places.CityIndex
import io.github.ivan_olivera_dev.cenit.core.places.CitySearchResult
import io.github.ivan_olivera_dev.cenit.core.sky.SkyFormatting
import io.github.ivan_olivera_dev.cenit.data.CityRepository
import io.github.ivan_olivera_dev.cenit.data.DeviceLocationProvider
import io.github.ivan_olivera_dev.cenit.data.DeviceLocationResult
import io.github.ivan_olivera_dev.cenit.data.PlacesStore
import io.github.ivan_olivera_dev.cenit.data.SavedPlace
import io.github.ivan_olivera_dev.cenit.ui.components.cenitFocusRing
import io.github.ivan_olivera_dev.cenit.ui.planner.PlaceSource
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerPlace
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerState
import io.github.ivan_olivera_dev.cenit.ui.planner.displayName
import io.github.ivan_olivera_dev.cenit.ui.planner.displayRegion
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

private enum class GpsNotice {
    DENIED,
    DISABLED,
    UNAVAILABLE,
}

/**
 * Lugares screen of Artboard 02 (Cúpula): offline GeoNames city search, phone GPS via
 * LocationManager, saved places (save / rename / delete with confirmation), and manual
 * coordinate entry.
 */
@Composable
fun PlacesScreen(
    state: PlannerState,
    onStateChange: (PlannerState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalCenitPalette.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) {
        configuration.locales[0] ?: Locale.getDefault()
    }
    val scope = rememberCoroutineScope()
    val store = remember(context) { PlacesStore.fromContext(context) }

    var query by remember { mutableStateOf("") }
    var cityIndex by remember { mutableStateOf<CityIndex?>(null) }
    var savedPlaces by remember { mutableStateOf(store.load().savedPlaces) }
    var locating by remember { mutableStateOf(false) }
    var usedLastKnown by remember { mutableStateOf(false) }
    var gpsNotice by remember { mutableStateOf<GpsNotice?>(null) }
    var showSavedConfirmation by remember { mutableStateOf(false) }

    var showManualDialog by remember { mutableStateOf(false) }
    var showRationaleDialog by remember { mutableStateOf(false) }
    var editingPlace by remember { mutableStateOf<SavedPlace?>(null) }
    var pendingDeletePlace by remember { mutableStateOf<SavedPlace?>(null) }

    LaunchedEffect(context) {
        cityIndex = CityRepository.loadIndex(context)
    }

    fun activatePlace(place: PlannerPlace) {
        val deviceZone = ZoneId.systemDefault()
        val updated = state.selectPlace(place, deviceZone = deviceZone)
        store.saveActivePlace(updated.activePlace)
        showSavedConfirmation = false
        onStateChange(updated)
    }

    fun fetchCurrentGps() {
        if (locating) return
        locating = true
        gpsNotice = null
        usedLastKnown = false
        scope.launch {
            when (val result = DeviceLocationProvider.requestCurrentLocation(context)) {
                is DeviceLocationResult.Success -> {
                    val gpsPlace = DeviceLocationProvider.toGpsPlannerPlace(
                        context = context,
                        latitude = result.latitude,
                        longitude = result.longitude,
                    )
                    usedLastKnown = result.isLastKnownFallback
                    activatePlace(gpsPlace)
                }
                DeviceLocationResult.PermissionDenied -> {
                    gpsNotice = GpsNotice.DENIED
                }
                DeviceLocationResult.ProvidersDisabled -> {
                    gpsNotice = GpsNotice.DISABLED
                }
                DeviceLocationResult.Unavailable -> {
                    gpsNotice = GpsNotice.UNAVAILABLE
                }
            }
            locating = false
        }
    }

    val locationPermissions = remember {
        arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            DeviceLocationProvider.hasLocationPermission(context)
        if (granted) {
            fetchCurrentGps()
        } else {
            gpsNotice = GpsNotice.DENIED
        }
    }

    fun onGpsClick() {
        if (DeviceLocationProvider.hasLocationPermission(context)) {
            fetchCurrentGps()
            return
        }
        val activity = context.findActivity()
        val shouldShowRationale = activity != null && (
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) || ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
            )
        if (shouldShowRationale || gpsNotice == null) {
            showRationaleDialog = true
        } else {
            permissionLauncher.launch(locationPermissions)
        }
    }

    val preferredCountryCode: String? = remember(locale, state.activePlace.place, cityIndex) {
        CityRepository.resolvePreferredCountryCode(
            locale = locale,
            fallbackCountryCode = {
                cityIndex?.countryCodeFor(
                    latitude = state.activePlace.place.latitude,
                    longitude = state.activePlace.place.longitude,
                )
            },
        )
    }

    val searchResults: List<CitySearchResult> = remember(query, cityIndex, locale, preferredCountryCode) {
        val index = cityIndex ?: return@remember emptyList()
        if (query.isBlank()) {
            emptyList()
        } else {
            index.search(
                query = query,
                preferredCountryCode = preferredCountryCode,
                countryNameForCode = { code -> CityRepository.localizedCountryName(code, locale) },
            )
        }
    }

    val matchingSaved = remember(state.activePlace, savedPlaces) {
        PlacesStore.findMatchingSaved(state.activePlace, savedPlaces)
    }
    val activeIsGps = state.activePlace.source == PlaceSource.GPS

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bgColor),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 14.dp, bottom = 16.dp),
        ) {
            // Headline
            Text(
                text = stringResource(R.string.places_title),
                style = MaterialTheme.typography.headlineLarge,
                color = palette.textPrimary,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Offline search field
            PlacesSearchField(
                query = query,
                onQueryChange = { query = it },
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (query.isNotBlank()) {
                SearchResultsSection(
                    query = query,
                    indexLoaded = cityIndex != null,
                    results = searchResults,
                    activePlace = state.activePlace,
                    locale = locale,
                    onSelectCity = { hit ->
                        val place = PlannerPlace(
                            name = hit.city.name,
                            place = hit.city.toPlace(),
                            region = hit.regionLabel,
                            zoneId = hit.city.timezoneId,
                            source = PlaceSource.CITY,
                        )
                        query = ""
                        activatePlace(place)
                    },
                )
            } else {
                // GPS action card
                GpsLocationCard(
                    locating = locating,
                    activeIsGps = activeIsGps,
                    usedLastKnown = usedLastKnown,
                    activePlace = state.activePlace,
                    locale = locale,
                    onClick = ::onGpsClick,
                )

                // GPS status notice (permission denied / disabled / unavailable)
                val noticeText = when (gpsNotice) {
                    GpsNotice.DENIED -> stringResource(R.string.places_gps_denied_notice)
                    GpsNotice.DISABLED -> stringResource(R.string.places_gps_disabled_notice)
                    GpsNotice.UNAVAILABLE -> stringResource(R.string.places_gps_unavailable_notice)
                    null -> null
                }
                if (noticeText != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    PlacesNotice(text = noticeText)
                }

                // Active unsaved place + Save button (at most one button-primary on screen)
                if (matchingSaved == null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Eyebrow(text = stringResource(R.string.places_active_eyebrow))
                    Spacer(modifier = Modifier.height(6.dp))
                    PlaceRow(
                        name = state.activePlace.displayName(),
                        subtitle = formatPlaceSubtitle(
                            region = state.activePlace.displayRegion(),
                            place = state.activePlace.place,
                            locale = locale,
                        ),
                        isActive = true,
                        onClick = {},
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CenitPrimaryButton(
                        text = stringResource(R.string.places_save_action),
                        onClick = {
                            val snapshot = store.savePlace(
                                SavedPlace.fromPlannerPlace(state.activePlace),
                            )
                            savedPlaces = snapshot.savedPlaces
                            val newlySaved = PlacesStore.findMatchingSaved(
                                state.activePlace,
                                snapshot.savedPlaces,
                            )
                            if (newlySaved != null) {
                                onStateChange(
                                    state.copy(
                                        activePlace = state.activePlace.copy(id = newlySaved.id),
                                    ),
                                )
                            }
                            showSavedConfirmation = true
                        },
                    )
                }

                if (showSavedConfirmation) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlacesIcon(
                            kind = PlacesIconKind.CHECK,
                            tint = palette.success,
                            size = 18.dp,
                        )
                        Text(
                            text = stringResource(R.string.places_saved_confirmation),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.success,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Saved places list
                Eyebrow(text = stringResource(R.string.places_saved_eyebrow))
                Spacer(modifier = Modifier.height(6.dp))

                if (savedPlaces.isEmpty()) {
                    Text(
                        text = stringResource(R.string.places_saved_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    savedPlaces.forEach { saved ->
                        val isActive = matchingSaved?.id == saved.id
                        PlaceRow(
                            name = saved.displayName(),
                            subtitle = formatPlaceSubtitle(
                                region = saved.displayRegion(),
                                place = Place(saved.latitude, saved.longitude),
                                locale = locale,
                            ),
                            isActive = isActive,
                            onClick = { activatePlace(saved.toPlannerPlace()) },
                            onEdit = { editingPlace = saved },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Manual coordinates secondary button (always available even if GPS denied)
            CenitSecondaryButton(
                text = stringResource(R.string.places_manual_action),
                onClick = { showManualDialog = true },
                leadingIcon = {
                    PlacesIcon(
                        kind = PlacesIconKind.EDIT,
                        tint = palette.textPrimary,
                        size = 18.dp,
                    )
                },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Required GeoNames CC BY 4.0 attribution
            Text(
                text = stringResource(R.string.places_attribution),
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
            )
        }
    }

    if (showRationaleDialog) {
        LocationRationaleDialog(
            onDismiss = { showRationaleDialog = false },
            onAllow = {
                showRationaleDialog = false
                permissionLauncher.launch(locationPermissions)
            },
        )
    }

    if (showManualDialog) {
        ManualCoordinatesDialog(
            onDismiss = { showManualDialog = false },
            onConfirm = { name, place ->
                showManualDialog = false
                val manualPlace = PlannerPlace(
                    name = name,
                    place = place,
                    region = "",
                    zoneId = null, // Manual coordinates use device ZoneId
                    source = PlaceSource.MANUAL,
                )
                activatePlace(manualPlace)
            },
        )
    }

    editingPlace?.let { target ->
        EditSavedPlaceDialog(
            place = target,
            onDismiss = { editingPlace = null },
            onRename = { newName ->
                val snapshot = store.renamePlace(target.id, newName)
                savedPlaces = snapshot.savedPlaces
                if (matchingSaved?.id == target.id) {
                    onStateChange(
                        state.copy(
                            activePlace = state.activePlace.copy(name = newName),
                        ),
                    )
                }
                editingPlace = null
            },
            onRequestDelete = {
                editingPlace = null
                pendingDeletePlace = target
            },
        )
    }

    pendingDeletePlace?.let { target ->
        DeleteConfirmationDialog(
            placeName = target.displayName(),
            onDismiss = { pendingDeletePlace = null },
            onConfirmDelete = {
                val snapshot = store.deletePlace(target.id)
                savedPlaces = snapshot.savedPlaces
                if (state.activePlace.id == target.id) {
                    onStateChange(
                        state.copy(
                            activePlace = state.activePlace.copy(id = null),
                        ),
                    )
                }
                pendingDeletePlace = null
            },
        )
    }
}

@Composable
private fun PlacesSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val palette = LocalCenitPalette.current
    val shape = RoundedCornerShape(14.dp)
    val searchCd = stringResource(R.string.places_search_cd)
    val clearCd = stringResource(R.string.places_search_clear_cd)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .cenitFocusRing(palette.focusRing, cornerRadius = 14.dp)
            .clip(shape)
            .border(1.dp, palette.controlBorder, shape)
            .padding(start = 14.dp, end = if (query.isNotEmpty()) 4.dp else 14.dp)
            .semantics { contentDescription = searchCd },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlacesIcon(
            kind = PlacesIconKind.SEARCH,
            tint = palette.textSecondary,
            size = 20.dp,
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.textPrimary),
            cursorBrush = SolidColor(palette.textPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.places_search_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.textSecondary,
                    )
                }
                inner()
            },
        )
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .cenitFocusRing(palette.focusRing, cornerRadius = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = { onQueryChange("") })
                    .semantics { contentDescription = clearCd },
                contentAlignment = Alignment.Center,
            ) {
                PlacesIcon(
                    kind = PlacesIconKind.CLOSE,
                    tint = palette.textSecondary,
                    size = 18.dp,
                )
            }
        }
    }
}

@Composable
private fun GpsLocationCard(
    locating: Boolean,
    activeIsGps: Boolean,
    usedLastKnown: Boolean,
    activePlace: PlannerPlace,
    locale: Locale,
    onClick: () -> Unit,
) {
    val palette = LocalCenitPalette.current
    val shape = RoundedCornerShape(14.dp)
    val activeCd = stringResource(R.string.places_active_cd)

    val subtitle = when {
        locating -> stringResource(R.string.places_gps_locating)
        activeIsGps && usedLastKnown -> {
            val coords = formatCoordinates(activePlace.place, locale)
            "${stringResource(R.string.places_gps_last_known)} · $coords"
        }
        activeIsGps -> formatCoordinates(activePlace.place, locale)
        else -> stringResource(R.string.places_gps_subtitle)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .cenitFocusRing(palette.focusRing, cornerRadius = 14.dp)
            .clip(shape)
            .border(1.dp, palette.controlBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlacesIcon(
            kind = PlacesIconKind.GPS,
            tint = palette.sun,
            size = 20.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.places_my_location),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = palette.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (activeIsGps) {
            PlacesIcon(
                kind = PlacesIconKind.CHECK,
                tint = palette.sun,
                size = 20.dp,
                modifier = Modifier.semantics { contentDescription = activeCd },
            )
        }
    }
}

@Composable
private fun SearchResultsSection(
    query: String,
    indexLoaded: Boolean,
    results: List<CitySearchResult>,
    activePlace: PlannerPlace,
    locale: Locale,
    onSelectCity: (CitySearchResult) -> Unit,
) {
    val palette = LocalCenitPalette.current
    if (!indexLoaded) {
        Text(
            text = stringResource(R.string.places_loading_cities),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
        )
        return
    }

    val eyebrowText = when (results.size) {
        0 -> stringResource(R.string.places_results_none)
        1 -> stringResource(R.string.places_results_one)
        else -> stringResource(R.string.places_results_many, results.size)
    }
    Eyebrow(text = eyebrowText)
    Spacer(modifier = Modifier.height(6.dp))

    if (results.isEmpty()) {
        Text(
            text = stringResource(R.string.places_search_empty, query.trim()),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    } else {
        results.forEach { hit ->
            val isActive = abs(hit.city.latitude - activePlace.place.latitude) < 1e-4 &&
                abs(hit.city.longitude - activePlace.place.longitude) < 1e-4
            PlaceRow(
                name = hit.city.name,
                subtitle = formatPlaceSubtitle(
                    region = hit.regionLabel,
                    place = hit.city.toPlace(),
                    locale = locale,
                ),
                isActive = isActive,
                onClick = { onSelectCity(hit) },
            )
        }
    }
}

@Composable
private fun PlaceRow(
    name: String,
    subtitle: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    val palette = LocalCenitPalette.current
    val activeCd = stringResource(R.string.places_active_cd)
    val editCd = stringResource(R.string.places_edit_cd, name)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .cenitFocusRing(palette.focusRing, cornerRadius = 10.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = palette.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isActive) {
                PlacesIcon(
                    kind = PlacesIconKind.CHECK,
                    tint = palette.sun,
                    size = 20.dp,
                    modifier = Modifier.semantics { contentDescription = activeCd },
                )
            }
            if (onEdit != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .cenitFocusRing(palette.focusRing, cornerRadius = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = onEdit)
                        .semantics { contentDescription = editCd },
                    contentAlignment = Alignment.Center,
                ) {
                    PlacesIcon(
                        kind = PlacesIconKind.EDIT,
                        tint = palette.textSecondary,
                        size = 18.dp,
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
    }
}

@Composable
private fun PlacesNotice(text: String) {
    val palette = LocalCenitPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
            .background(palette.surfaceColor),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(palette.warning),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun Eyebrow(text: String) {
    val palette = LocalCenitPalette.current
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = palette.textSecondary,
    )
}

@Composable
private fun formatPlaceSubtitle(
    region: String,
    place: Place,
    locale: Locale,
): String {
    val coords = formatCoordinates(place, locale)
    return if (region.isNotBlank()) "$region · $coords" else coords
}

@Composable
private fun formatCoordinates(place: Place, locale: Locale): String {
    val latStr = SkyFormatting.formatDecimal(abs(place.latitude), 2, locale)
    val lonStr = SkyFormatting.formatDecimal(abs(place.longitude), 2, locale)
    val ns = stringResource(if (place.latitude >= 0.0) R.string.hemisphere_north else R.string.hemisphere_south)
    val ew = stringResource(if (place.longitude >= 0.0) R.string.hemisphere_east else R.string.hemisphere_west)
    return stringResource(R.string.coords_format, latStr, ns, lonStr, ew)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
