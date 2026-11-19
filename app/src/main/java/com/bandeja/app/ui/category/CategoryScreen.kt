package com.bandeja.app.ui.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandeja.app.R
import com.bandeja.app.ui.appViewModel
import com.bandeja.app.ui.components.BandejaTopBar
import com.bandeja.app.ui.components.CategoryBadge
import com.bandeja.app.ui.components.ConfirmDialog
import com.bandeja.app.ui.components.ContentList
import com.bandeja.app.ui.components.EmptyState
import com.bandeja.app.ui.components.ErrorState
import com.bandeja.app.ui.components.rememberUndoableDelete
import com.bandeja.app.ui.theme.CategoryAccent
import com.bandeja.app.ui.theme.Spacing

@Composable
fun CategoryScreen(
    categoryId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenContent: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryViewModel = appViewModel(key = "category-$categoryId") {
        CategoryViewModel(categoryId, it.categoryRepository, it.contentRepository)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Between the category disappearing from the database and this screen closing there is a moment with
    // nothing to show; it must not flash the "no longer exists" error.
    var deleting by remember { mutableStateOf(false) }
    val category = state.category
    val snackbar = remember { SnackbarHostState() }
    val deleteItem = rememberUndoableDelete(snackbar, viewModel::deleteItem, viewModel::restoreItem)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            BandejaTopBar(
                title = if (state.isUncategorized) stringResource(R.string.category_none) else category?.name.orEmpty(),
                onBack = onBack,
                actions = {
                    if (category != null) CategoryMenu(onEdit = { onEdit(category.id) }, onDelete = { confirmDelete = true })
                },
            )
        },
    ) { padding ->
        if (deleting) return@Scaffold
        if (state.notFound) {
            ErrorState(
                title = stringResource(R.string.detail_not_found_title),
                message = stringResource(R.string.detail_not_found_body),
                modifier = Modifier.padding(padding),
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack,
            )
            return@Scaffold
        }
        ContentList(
            items = state.items,
            isLoading = state.isLoading,
            onOpen = onOpenContent,
            onToggleFavorite = viewModel::toggleFavorite,
            modifier = Modifier.fillMaxWidth(),
            showCategory = false,
            onDelete = deleteItem,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + Spacing.sm, bottom = padding.calculateBottomPadding() + Spacing.lg),
            header = {
                if (category != null) {
                    item(key = "header") {
                        CategoryHeader(category.icon, CategoryAccent.fromKey(category.color), state.items.size)
                    }
                }
            },
            empty = {
                EmptyState(
                    emoji = category?.icon ?: "📥",
                    title = stringResource(R.string.category_empty_title),
                    message = stringResource(R.string.category_empty_body, category?.name ?: stringResource(R.string.category_none)),
                )
            },
        )
    }

    if (confirmDelete && category != null) {
        val count = state.items.size
        ConfirmDialog(
            title = stringResource(R.string.category_delete_title, category.name),
            message = if (count == 0) {
                stringResource(R.string.category_delete_empty)
            } else {
                pluralStringResource(R.plurals.category_delete_with_items, count, count)
            },
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                deleting = true
                viewModel.delete(onDeleted = onBack)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun CategoryHeader(icon: String, accent: CategoryAccent, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CategoryBadge(icon = icon, accent = accent, size = 56.dp)
        Text(
            pluralStringResource(R.plurals.items_count, count, count),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit)) },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = { expanded = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}
