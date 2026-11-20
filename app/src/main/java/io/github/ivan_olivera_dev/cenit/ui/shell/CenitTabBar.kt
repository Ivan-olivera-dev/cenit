package io.github.ivan_olivera_dev.cenit.ui.shell

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.ui.components.CenitIcon
import io.github.ivan_olivera_dev.cenit.ui.components.CenitIconKind
import io.github.ivan_olivera_dev.cenit.ui.components.cenitFocusRing
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette

enum class CenitTab(
    @StringRes val labelRes: Int,
    val icon: CenitIconKind,
) {
    HOY(R.string.tab_today, CenitIconKind.HOY),
    CIELO(R.string.tab_sky, CenitIconKind.CIELO),
    BRUJULA(R.string.tab_compass, CenitIconKind.BRUJULA),
    LUGARES(R.string.tab_places, CenitIconKind.LUGARES),
}

/**
 * Bottom navigation tab bar (`tab-bar` token): `64 dp` high, hairline top (`borderColor`),
 * 4 items (`Hoy · Cielo · Brújula · Lugares`), `22 dp` icons + `bodySmall` labels,
 * selected item in `textPrimary` with a `2 dp sun` indicator bar above the icon.
 */
@Composable
fun CenitTabBar(
    selectedTab: CenitTab,
    onSelectTab: (CenitTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalCenitPalette.current
    val tabBarDesc = stringResource(R.string.cd_tab_bar)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.bgColor)
            .navigationBarsPadding()
            .semantics { contentDescription = tabBarDesc },
    ) {
        HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            CenitTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                val tint = if (isSelected) palette.textPrimary else palette.textSecondary
                val label = stringResource(tab.labelRes)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .cenitFocusRing(palette.focusRing, cornerRadius = 8.dp)
                        .clickable(role = Role.Tab) { onSelectTab(tab) }
                        .semantics(mergeDescendants = true) {
                            selected = isSelected
                            contentDescription = label
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .width(28.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(palette.sun),
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CenitIcon(kind = tab.icon, tint = tint, size = 22.dp)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = tint,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
