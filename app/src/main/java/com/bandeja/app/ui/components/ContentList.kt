package com.bandeja.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bandeja.app.domain.model.ContentItem
import com.bandeja.app.ui.theme.Spacing

/**
 * The one list used by Category, Favorites and Search, so all three look and behave the same:
 * skeleton while loading, [empty] when there is nothing, rows with animated reordering otherwise.
 */
@Composable
fun ContentList(
    items: List<ContentItem>,
    isLoading: Boolean,
    onOpen: (Long) -> Unit,
    onToggleFavorite: (ContentItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = Spacing.sm),
    showCategory: Boolean = true,
    /** When set, rows can be swiped left to delete (the caller shows the undo). */
    onDelete: ((ContentItem) -> Unit)? = null,
    header: (LazyListScope.() -> Unit)? = null,
    empty: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        header?.invoke(this)
        when {
            isLoading -> item(key = "skeleton") { ContentListSkeleton() }
            items.isEmpty() -> item(key = "empty") { empty() }
            else -> {
                items(items, key = { it.content.id }) { item ->
                    val card = @Composable {
                        ContentCard(
                            item = item,
                            onClick = { onOpen(item.content.id) },
                            onToggleFavorite = { onToggleFavorite(item) },
                            showCategory = showCategory,
                        )
                    }
                    if (onDelete == null) {
                        Box(Modifier.animateItem()) { card() }
                    } else {
                        SwipeToDeleteBox(onDelete = { onDelete(item) }, modifier = Modifier.animateItem()) { card() }
                    }
                }
                item(key = "end-space") { Spacer(Modifier.height(Spacing.xl)) }
            }
        }
    }
}
