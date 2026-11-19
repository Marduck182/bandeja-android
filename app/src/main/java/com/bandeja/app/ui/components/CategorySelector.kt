package com.bandeja.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bandeja.app.R
import com.bandeja.app.domain.model.Category
import com.bandeja.app.ui.theme.Spacing

/**
 * Chips to pick a category (or none). One horizontal line by default so the save sheet stays short;
 * [wrap] lays them out in several lines for roomier places.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategorySelector(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    onCreate: (() -> Unit)? = null,
    wrap: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val noneLabel = stringResource(R.string.category_none)
    val chips: @Composable (Category?) -> Unit = { category ->
        FilterChip(
            selected = category?.id == selectedId,
            onClick = { onSelect(category?.id) },
            label = { Text(if (category == null) "📥  $noneLabel" else "${category.icon}  ${category.name}", maxLines = 1) },
        )
    }
    val createChip: @Composable () -> Unit = {
        if (onCreate != null) {
            AssistChip(
                onClick = onCreate,
                label = { Text(stringResource(R.string.save_new_category)) },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            )
        }
    }

    if (wrap) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            categories.forEach { chips(it) }
            chips(null)
            createChip()
        }
    } else {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(categories, key = { it.id }) { chips(it) }
            item(key = "none") { chips(null) }
            item(key = "create") { createChip() }
        }
    }
}
