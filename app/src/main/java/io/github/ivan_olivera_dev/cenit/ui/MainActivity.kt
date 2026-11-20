package io.github.ivan_olivera_dev.cenit.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.ui.theme.CenitTheme
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CenitTheme {
                PlaceholderScreen()
            }
        }
    }
}

/** Temporary screen until the Hoy screen of the chosen 02 · Cúpula direction lands (T6). */
@Composable
private fun PlaceholderScreen() {
    val palette = LocalCenitPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgColor)
            .safeDrawingPadding()
            .padding(horizontal = 22.dp, vertical = 44.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = palette.textPrimary
        )
        Text(
            text = stringResource(R.string.placeholder_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary
        )
    }
}
