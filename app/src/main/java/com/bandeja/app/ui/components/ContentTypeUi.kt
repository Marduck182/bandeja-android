package com.bandeja.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.ui.graphics.vector.ImageVector
import com.bandeja.app.R
import com.bandeja.app.domain.model.ContentType

fun ContentType.icon(): ImageVector = when (this) {
    ContentType.VIDEO -> Icons.Rounded.PlayCircle
    ContentType.ARTICLE -> Icons.AutoMirrored.Rounded.Article
    ContentType.SOCIAL -> Icons.Rounded.Forum
    ContentType.IMAGE -> Icons.Rounded.Image
    ContentType.CODE -> Icons.Rounded.Code
    ContentType.LINK -> Icons.Rounded.Link
}

@StringRes
fun ContentType.labelRes(): Int = when (this) {
    ContentType.VIDEO -> R.string.type_video
    ContentType.ARTICLE -> R.string.type_article
    ContentType.SOCIAL -> R.string.type_social
    ContentType.IMAGE -> R.string.type_image
    ContentType.CODE -> R.string.type_code
    ContentType.LINK -> R.string.type_link
}
