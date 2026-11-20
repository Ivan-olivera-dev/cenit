package io.github.ivan_olivera_dev.cenit.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.ivan_olivera_dev.cenit.ui.compass.CompassScreen
import io.github.ivan_olivera_dev.cenit.ui.places.PlacesScreen
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerState
import io.github.ivan_olivera_dev.cenit.ui.planner.plannerStateSaver
import io.github.ivan_olivera_dev.cenit.ui.planner.resolveInitialPlannerState
import io.github.ivan_olivera_dev.cenit.ui.sky.SkyScreen
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette
import io.github.ivan_olivera_dev.cenit.ui.today.TodayScreen
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Root app shell: holds the shared [PlannerState] (active place and its time zone, date,
 * time) across the Hoy, Cielo, Brújula and Lugares tabs.
 */
@Composable
fun CenitApp(
    clock: Clock = Clock.systemDefaultZone(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val palette = LocalCenitPalette.current
    var selectedTab by rememberSaveable { mutableStateOf(CenitTab.HOY) }
    val context = LocalContext.current
    val plannerSaver = remember(zone) { plannerStateSaver(zone) }
    var plannerState by rememberSaveable(zone.id, stateSaver = plannerSaver) {
        // Last active saved place, else the permitted device location, else Madrid.
        mutableStateOf(resolveInitialPlannerState(context, clock, zone))
    }
    val today = remember(clock, plannerState.zone, plannerState.selectedDate) {
        LocalDate.now(clock.withZone(plannerState.zone))
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
                )
                CenitTab.CIELO -> SkyScreen(
                    state = plannerState,
                    onStateChange = { plannerState = it },
                )
                CenitTab.BRUJULA -> CompassScreen(state = plannerState)
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
