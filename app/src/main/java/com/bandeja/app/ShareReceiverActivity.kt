package com.bandeja.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandeja.app.domain.model.AppSettings
import com.bandeja.app.domain.share.ShareInput
import com.bandeja.app.ui.appContainer
import com.bandeja.app.ui.appViewModelFactory
import com.bandeja.app.ui.save.SaveContentSheet
import com.bandeja.app.ui.save.SaveViewModel
import com.bandeja.app.ui.theme.BandejaTheme
import com.bandeja.app.ui.theme.SystemBarsEffect

/**
 * Target of Android's "Compartir". Shows only the save sheet over the app the user came from and
 * closes itself: sharing → pick a category → Save, without ever opening the full app.
 */
class ShareReceiverActivity : ComponentActivity() {

    private val viewModel: SaveViewModel by viewModels {
        appViewModelFactory(applicationContext.appContainer) { c ->
            SaveViewModel(c.saveContent, c.categoryRepository, c.settingsRepository, c.metadataFetcher, c.appScope)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Idempotent: after rotation the ViewModel already has the work, after process death it is new and needs it.
        viewModel.startFromShare(intent.toShareInput())

        val settingsFlow = applicationContext.appContainer.settingsRepository.settings
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
            SystemBarsEffect(settings.themeMode)
            BandejaTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                SaveContentSheet(
                    viewModel = viewModel,
                    onDismiss = ::finish,
                    onOpenExisting = { id ->
                        startActivity(MainActivity.openContentIntent(this, id))
                        finish()
                    },
                )
            }
        }
    }
}

/**
 * Reads the Intent as plain text only. Extras come from another app, so a malformed one must never crash
 * the receiver, and a huge one is capped before any parsing.
 */
internal fun Intent.toShareInput(): ShareInput {
    if (action != Intent.ACTION_SEND) return ShareInput(null, null, null)
    fun text(name: String): String? =
        runCatching { getCharSequenceExtra(name)?.toString()?.take(MAX_SHARED_CHARS) }.getOrNull()
    return ShareInput(
        text = text(Intent.EXTRA_TEXT),
        subject = text(Intent.EXTRA_SUBJECT),
        title = text(Intent.EXTRA_TITLE),
    )
}

private const val MAX_SHARED_CHARS = 4_096
