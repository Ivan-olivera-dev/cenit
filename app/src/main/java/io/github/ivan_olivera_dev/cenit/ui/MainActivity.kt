package io.github.ivan_olivera_dev.cenit.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.github.ivan_olivera_dev.cenit.alerts.ExactAlarmSync
import io.github.ivan_olivera_dev.cenit.settings.AppLocaleController
import io.github.ivan_olivera_dev.cenit.settings.LocalAppSettings
import io.github.ivan_olivera_dev.cenit.settings.SettingsStore
import io.github.ivan_olivera_dev.cenit.settings.resolveDarkTheme
import io.github.ivan_olivera_dev.cenit.ui.shell.CenitApp
import io.github.ivan_olivera_dev.cenit.ui.theme.CenitTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleController.wrapBaseContext(newBase))
    }

    override fun onResume() {
        super.onResume()
        ExactAlarmSync.rescheduleIfPermissionChanged(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsStore = SettingsStore.fromContext(this)
        val initialStored = settingsStore.load()
        val initialSettings = initialStored.copy(
            language = AppLocaleController.readEffectiveLanguage(this, initialStored.language),
        )
        setContent {
            var settings by remember { mutableStateOf(initialSettings) }
            val darkTheme = resolveDarkTheme(settings.theme, isSystemInDarkTheme())
            val view = LocalView.current
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
            CompositionLocalProvider(LocalAppSettings provides settings) {
                CenitTheme(darkTheme = darkTheme) {
                    CenitApp(
                        onSettingsChange = { updated ->
                            val languageChanged = updated.language != settings.language
                            settings = updated
                            settingsStore.save(updated)
                            if (languageChanged) {
                                AppLocaleController.applyLanguage(this@MainActivity, updated.language)
                            }
                        },
                    )
                }
            }
        }
    }
}
