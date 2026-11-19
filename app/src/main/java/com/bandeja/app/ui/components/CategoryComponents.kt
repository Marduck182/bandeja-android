package com.bandeja.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandeja.app.R
import com.bandeja.app.domain.model.Category
import com.bandeja.app.ui.theme.CategoryAccent
import com.bandeja.app.ui.theme.Spacing
import com.bandeja.app.ui.theme.container
import com.bandeja.app.ui.theme.onContainer

/** Emoji on its accent color: the identity of a category everywhere it appears. */
@Composable
fun CategoryBadge(icon: String, accent: CategoryAccent, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    // toSp ignores the user's font scale on purpose: the emoji must stay inside its fixed-size tile.
    val emojiSize = with(LocalDensity.current) { (size * 0.5f).toSp() }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(accent.container()),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon, fontSize = emojiSize)
    }
}

@OptIn(ExperimentalFoundationApi::class)
/** Compact tile of the Home grid: icon on the left, name and count on the right. */
@Composable
fun CategoryCard(
    name: String,
    icon: String,
    accent: CategoryAccent,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    // Card(onClick) has no long press, so the card itself is not clickable and the gesture is added on top.
    Card(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = accent.container()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(icon, fontSize = 28.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    color = accent.onContainer(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.items_count, count, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = accent.onContainer().copy(alpha = 0.85f),
                )
            }
        }
    }
}

/** Small "icon + name" label; the neutral "Sin categoría" variant when [category] is `null`. */
@Composable
fun CategoryPill(category: Category?, modifier: Modifier = Modifier) {
    val accent = category?.let { CategoryAccent.fromKey(it.color) }
    val background = accent?.container() ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val foreground = accent?.onContainer() ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(category?.icon ?: "📥", style = MaterialTheme.typography.labelSmall)
        Text(
            category?.name ?: stringResource(R.string.category_none),
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Curated set: the emoji of the brief's examples plus common topics. A grid, not a free keyboard, keeps data clean. */
val CategoryEmojis = listOf(
    "🍔", "🍕", "🥗", "🍳", "☕", "🍰", "🏋️", "🏃", "🧘", "⚽", "🚴", "🏊",
    "💻", "🧑‍💻", "📱", "🔧", "🤖", "🔬", "📚", "🎓", "📝", "🧠", "🗣️", "🎨",
    "🎮", "🎬", "🎵", "🎧", "📷", "🎭", "✈️", "🏖️", "🏔️", "🗺️", "🏨", "🚗",
    "💡", "⭐", "❤️", "🔥", "🎁", "🛒", "💰", "📈", "🏠", "🌱", "🐶", "📌",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmojiPicker(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        CategoryEmojis.forEach { emoji ->
            val isSelected = emoji == selected
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(
                        BorderStroke(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent),
                        MaterialTheme.shapes.medium,
                    )
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(emoji) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 24.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentPicker(selectedKey: String, onSelect: (CategoryAccent) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        CategoryAccent.entries.forEach { accent ->
            val isSelected = accent.key == selectedKey
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accent.container())
                    .border(
                        BorderStroke(if (isSelected) 3.dp else 1.dp, if (isSelected) accent.onContainer() else MaterialTheme.colorScheme.outlineVariant),
                        CircleShape,
                    )
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(accent) })
                    .semantics { contentDescription = accent.label },
                contentAlignment = Alignment.Center,
            ) {
                // Color is never the only signal: the selected one also gets a check mark.
                if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null, tint = accent.onContainer())
            }
        }
    }
}
