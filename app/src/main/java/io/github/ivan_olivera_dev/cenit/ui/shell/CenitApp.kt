package io.github.ivan_olivera_dev.cenit.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.ivan_olivera_dev.cenit.alerts.AlarmScheduler
import io.github.ivan_olivera_dev.cenit.alerts.AlertSubscription
import io.github.ivan_olivera_dev.cenit.alerts.AlertSubscriptionsStore
import io.github.ivan_olivera_dev.cenit.settings.AppSettings
import io.github.ivan_olivera_dev.cenit.settings.LocalAppSettings
import io.github.ivan_olivera_dev.cenit.ui.camera.CameraDebugOverlay
import io.github.ivan_olivera_dev.cenit.ui.camera.CameraScreen
import io.github.ivan_olivera_dev.cenit.ui.compass.CompassScreen
import io.github.ivan_olivera_dev.cenit.ui.places.PlacesScreen
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerState
import io.github.ivan_olivera_dev.cenit.ui.planner.plannerStateSaver
import io.github.ivan_olivera_dev.cenit.ui.planner.resolveInitialPlannerState
import io.github.ivan_olivera_dev.cenit.ui.settings.SettingsScreen
import io.github.ivan_olivera_dev.cenit.ui.sky.SkyScreen
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette
import io.github.ivan_olivera_dev.cenit.ui.today.TodayScreen
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Root app shell: holds the shared [PlannerState] (active place and its time zone, date,
 * time) across the Hoy, Cielo, Brújula and Lugares tabs, plus the full-screen Settings view.
 */
@Composable
fun CenitApp(
    clock: Clock = Clock.systemDefaultZone(),
    zone: ZoneId = ZoneId.systemDefault(),
    onSettingsChange: (AppSettings) -> Unit = {},
) {
    val palette = LocalCenitPalette.current
    val settings = LocalAppSettings.current
    var selectedTab by rememberSaveable { mutableStateOf(CenitTab.HOY) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCamera by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val plannerSaver = remember(zone) { plannerStateSaver(zone) }
    var plannerState by rememberSaveable(zone.id, stateSaver = plannerSaver) {
        // Last active saved place, else the permitted device location, else Madrid.
        mutableStateOf(resolveInitialPlannerState(context, clock, zone))
    }
    val today = remember(clock, plannerState.zone, plannerState.selectedDate) {
        LocalDate.now(clock.withZone(plannerState.zone))
    }
    val alertStore = remember(context) { AlertSubscriptionsStore.fromContext(context) }
    val alarmScheduler = remember(context) { AlarmScheduler(context) }
    var alertSubscriptions by remember(alertStore) { mutableStateOf(alertStore.load()) }
    val subscribedAlertKinds = remember(alertSubscriptions, plannerState.activePlace.place) {
        val activePlace = plannerState.activePlace.place
        alertSubscriptions
            .filter { it.matches(it.kind, activePlace) }
            .mapTo(LinkedHashSet()) { it.kind }
    }

    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(
            settings = settings,
            onSettingsChange = onSettingsChange,
            onBack = { showSettings = false },
        )
        return
    }

    if (showCamera) {
        BackHandler { showCamera = false }
        CameraScreen(
            state = plannerState,
            onBack = { showCamera = false },
            overlay = { arInputs -> CameraDebugOverlay(arInputs = arInputs) },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgColor),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .statusBarsPadding(),
        ) {
            when (selectedTab) {
                CenitTab.HOY -> TodayScreen(
                    state = plannerState,
                    today = today,
                    onSelectDate = { date -> plannerState = plannerState.selectDate(date) },
                    onShiftDays = { days -> plannerState = plannerState.shiftDays(days) },
                    onResetNow = { plannerState = plannerState.resetToNow(clock) },
                    onScrubFraction = { fraction -> plannerState = plannerState.selectFraction(fraction) },
                    onStepMinutes = { delta -> plannerState = plannerState.stepMinutes(delta) },
                    onOpenSettings = { showSettings = true },
                    subscribedAlertKinds = subscribedAlertKinds,
                    onSubscribeAlert = { kind ->
                        val subscription = AlertSubscription.create(
                            kind = kind,
                            placeName = plannerState.activePlace.name,
                            place = plannerState.activePlace.place,
                            zoneId = plannerState.zone.id,
                        )
                        val scheduled = alarmScheduler.schedule(subscription, now = Instant.now(clock))
                        if (scheduled != null) {
                            alertSubscriptions = alertStore.add(subscription)
                            true
                        } else {
                            alarmScheduler.cancel(subscription.id)
                            false
                        }
                    },
                    onUnsubscribeAlert = { kind ->
                        val activePlace = plannerState.activePlace.place
                        val existing = alertSubscriptions.firstOrNull { it.matches(kind, activePlace) }
                        val cancelId = existing?.id ?: AlertSubscription.stableId(
                            kind = kind,
                            latitude = activePlace.latitude,
                            longitude = activePlace.longitude,
                        )
                        alarmScheduler.cancel(cancelId)
                        alertSubscriptions = alertStore.removeMatching(kind, activePlace)
                    },
                )
                CenitTab.CIELO -> SkyScreen(
                    state = plannerState,
                    onStateChange = { plannerState = it },
                )
                CenitTab.BRUJULA -> CompassScreen(
                    state = plannerState,
                    onOpenCamera = { showCamera = true },
                )
                CenitTab.LUGARES -> PlacesScreen(
                    state = plannerState,
                    onStateChange = { plannerState = it },
                )
            }
        }

        CenitTabBar(
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it },
        )
    }
}
