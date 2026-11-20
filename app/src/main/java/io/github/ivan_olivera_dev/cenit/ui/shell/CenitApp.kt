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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ivan_olivera_dev.cenit.ui.planner.DefaultMadridPlace
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerState
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette
import io.github.ivan_olivera_dev.cenit.ui.today.TodayScreen
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Root app shell: holds shared [PlannerState] across tabs and switches between Hoy and
 * placeholder screens for Cielo (T6b), Brújula (T7), and Lugares (T8).
 */
@Composable
fun CenitApp(
    clock: Clock = Clock.systemDefaultZone(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val palette = LocalCenitPalette.current
    var selectedTab by rememberSaveable { mutableStateOf(CenitTab.HOY) }
    val plannerSaver = remember(zone) {
        Saver<PlannerState, List<Long>>(
            save = { listOf(it.selectedDate.toEpochDay(), it.minuteOffset.toLong()) },
            restore = { saved ->
                PlannerState(
                    activePlace = DefaultMadridPlace,
                    zone = zone,
                    selectedDate = LocalDate.ofEpochDay(saved[0]),
                    minuteOffset = saved[1].toInt(),
                )
            },
        )
    }
    var plannerState by rememberSaveable(zone.id, stateSaver = plannerSaver) {
        mutableStateOf(PlannerState.now(clock, zone))
    }
    val today = remember(clock, zone, plannerState.selectedDate) {
        LocalDate.now(clock.withZone(zone))
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
                CenitTab.CIELO, CenitTab.BRUJULA, CenitTab.LUGARES -> PlaceholderTabScreen(
                    title = stringResource(selectedTab.labelRes),
                )
            }
        }

        CenitTabBar(
            selectedTab = selectedTab,
            onSelectTab = { selectedTab = it },
        )
    }
}

@Composable
private fun PlaceholderTabScreen(title: String) {
    val palette = LocalCenitPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgColor)
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = palette.textPrimary,
        )
    }
}
