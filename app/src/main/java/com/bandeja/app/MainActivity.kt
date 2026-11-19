package com.bandeja.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandeja.app.domain.model.AppSettings
import com.bandeja.app.ui.appContainer
import com.bandeja.app.ui.appViewModelFactory
import com.bandeja.app.ui.navigation.BandejaNavHost
import com.bandeja.app.ui.navigation.MainViewModel
import com.bandeja.app.ui.theme.BandejaTheme
import com.bandeja.app.ui.theme.SystemBarsEffect

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        appViewModelFactory(applicationContext.appContainer) { MainViewModel(it.settingsRepository) }
    }

    /** Set when another screen of the app (the share sheet) asks to show a saved item. */
    private var pendingContentId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash stays until the stored settings are read (the real "initialization"), no fixed delay.
        installSplashScreen().setKeepOnScreenCondition { viewModel.startDestination.value == null }
        super.onCreate(savedInstanceState)
        // Only on a fresh launch: after rotation or process death the same Intent would navigate again.
        if (savedInstanceState == null) pendingContentId = intent.contentIdOrNull()

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()
            val current = settings ?: AppSettings()

            SystemBarsEffect(current.themeMode)
            BandejaTheme(themeMode = current.themeMode, dynamicColor = current.dynamicColor) {
                // Nothing is drawn until the start destination is known; the splash covers that moment.
                startDestination?.let { start ->
                    BandejaNavHost(
                        startDestination = start,
                        pendingContentId = pendingContentId,
                        onPendingContentHandled = { pendingContentId = null },
                        onOnboardingFinished = viewModel::completeOnboarding,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingContentId = intent.contentIdOrNull()
    }

    companion object {
        private const val EXTRA_CONTENT_ID = "com.bandeja.app.extra.CONTENT_ID"

        /** Intent that opens the app straight on a saved item. */
        fun openContentIntent(context: Context, contentId: Long): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_CONTENT_ID, contentId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        private fun Intent.contentIdOrNull(): Long? =
            runCatching { getLongExtra(EXTRA_CONTENT_ID, -1L) }.getOrDefault(-1L).takeIf { it > 0 }
    }
}
