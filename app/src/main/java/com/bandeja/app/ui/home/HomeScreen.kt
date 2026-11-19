package com.bandeja.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import com.bandeja.app.ui.components.ConfirmDialog
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandeja.app.R
import com.bandeja.app.domain.model.Category
import com.bandeja.app.ui.appViewModel
import com.bandeja.app.ui.components.CategoryCard
import com.bandeja.app.ui.components.ContentTile
import com.bandeja.app.ui.components.EmptyState
import com.bandeja.app.ui.components.ScreenTitle
import com.bandeja.app.ui.components.SectionHeader
import com.bandeja.app.ui.components.ShimmerBox
import com.bandeja.app.ui.theme.CategoryAccent
import com.bandeja.app.ui.theme.Spacing
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.bandeja.app.domain.model.CategoryWithCount

/** Home is a list of rows so a single scroll covers categories, recents and favorites. */
@Composable
fun HomeScreen(
    onOpenCategory: (Long) -> Unit,
    onOpenContent: (Long) -> Unit,
    onNewCategory: () -> Unit,
    onEditCategory: (Long) -> Unit,
    onSeeFavorites: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = appViewModel { HomeViewModel(it.categoryRepository, it.contentRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<CategoryWithCount?>(null) }
    // With a large system font two columns would cut words in half ("Comida/s"): one column then.
    val columns = if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) 1 else 2

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            // Extra room so the floating button never covers the last row.
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item(key = "title") { ScreenTitle(stringResource(R.string.app_name)) }

        item(key = "categories-header") {
            SectionHeader(
                title = stringResource(R.string.home_categories),
                actionLabel = stringResource(R.string.home_new_category),
                onAction = onNewCategory,
            )
        }

        if (state.isLoading) {
            item(key = "categories-skeleton") { CategoryGridSkeleton() }
        } else {
            val tiles = buildList {
                addAll(state.categories.map { CategoryTile.Real(it) })
                if (state.uncategorizedCount > 0) add(CategoryTile.Uncategorized(state.uncategorizedCount))
            }
            items(tiles.chunked(columns), key = { row -> row.first().key }) { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screen),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    row.forEach { tile ->
                        CategoryTileCard(
                            tile = tile,
                            onOpen = onOpenCategory,
                            onEdit = onEditCategory,
                            onDelete = { pendingDelete = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (columns == 2 && row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (!state.isLoading) {
            item(key = "recent-header") { SectionHeader(stringResource(R.string.home_recent)) }
            if (state.recent.isEmpty()) {
                item(key = "recent-empty") {
                    EmptyState(
                        emoji = "📥",
                        title = stringResource(R.string.home_empty_title),
                        message = stringResource(R.string.home_empty_body),
                    )
                }
            } else {
                item(key = "recent-row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Spacing.screen),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        items(state.recent, key = { it.content.id }) { item ->
                            ContentTile(item, onClick = { onOpenContent(item.content.id) })
                        }
                    }
                }
            }

            if (state.favorites.isNotEmpty()) {
                item(key = "favorites-header") {
                    SectionHeader(
                        title = stringResource(R.string.home_favorites),
                        actionLabel = stringResource(R.string.action_see_all),
                        onAction = onSeeFavorites,
                    )
                }
                item(key = "favorites-row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Spacing.screen),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        items(state.favorites, key = { it.content.id }) { item ->
                            ContentTile(item, onClick = { onOpenContent(item.content.id) })
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.category_delete_title, target.category.name),
            message = if (target.contentCount == 0) {
                stringResource(R.string.category_delete_empty)
            } else {
                pluralStringResource(R.plurals.category_delete_with_items, target.contentCount, target.contentCount)
            },
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                viewModel.deleteCategory(target.category.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

private sealed interface CategoryTile {
    val key: String

    data class Real(val value: CategoryWithCount) : CategoryTile {
        override val key get() = "c${value.category.id}"
    }

    data class Uncategorized(val count: Int) : CategoryTile {
        override val key get() = "uncategorized"
    }
}

@Composable
private fun CategoryTileCard(
    tile: CategoryTile,
    onOpen: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (CategoryWithCount) -> Unit,
    modifier: Modifier,
) {
    when (tile) {
        is CategoryTile.Real -> {
            val category: Category = tile.value.category
            var menuOpen by remember { mutableStateOf(false) }
            Box(modifier) {
                CategoryCard(
                    name = category.name,
                    icon = category.icon,
                    accent = CategoryAccent.fromKey(category.color),
                    count = tile.value.contentCount,
                    onClick = { onOpen(category.id) },
                    onLongClick = { menuOpen = true },
                    onLongClickLabel = stringResource(R.string.home_category_options),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onEdit(category.id) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onDelete(tile.value) },
                    )
                }
            }
        }
        is CategoryTile.Uncategorized -> CategoryCard(
            name = stringResource(R.string.category_none),
            icon = "📥",
            accent = CategoryAccent.Slate,
            count = tile.count,
            onClick = { onOpen(UNCATEGORIZED_ID) },
            modifier = modifier,
        )
    }
}

@Composable
private fun CategoryGridSkeleton() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ShimmerBox(Modifier.weight(1f).height(112.dp))
        ShimmerBox(Modifier.weight(1f).height(112.dp))
    }
}

private const val LARGE_FONT_SCALE = 1.25f

/** Route id meaning "items without category". Real ids start at 1 (autogenerated). */
const val UNCATEGORIZED_ID = 0L
