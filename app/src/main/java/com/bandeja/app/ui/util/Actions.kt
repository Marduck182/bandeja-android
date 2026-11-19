package com.bandeja.app.ui.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import com.bandeja.app.domain.url.UrlValidator

/**
 * Hand-offs to the rest of Android. A saved URL is data from the outside world, so it is only ever opened
 * after [UrlValidator] confirms it is plain http(s): never `intent:`, `file:`, `content:` or `javascript:`.
 */
object Actions {

    /** Returns `false` when the link is invalid or no app can show it. */
    fun openUrl(context: Context, url: String): Boolean {
        if (!UrlValidator.isValid(url)) return false
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    fun shareText(context: Context, title: String, url: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(Intent.createChooser(send, null))
    }

    /** Returns `true` when the app should confirm the copy itself (Android 13+ shows its own confirmation). */
    fun copyToClipboard(context: Context, label: String, text: String): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    }
}
