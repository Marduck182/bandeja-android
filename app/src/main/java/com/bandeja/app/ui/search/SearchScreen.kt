package com.bandeja.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandeja.app.R
import com.bandeja.app.domain.model.CategoryFilter
import com.bandeja.app.domain.model.DatePeriod
import com.bandeja.app.ui.appViewModel
import com.bandeja.app.ui.components.ContentList
import com.bandeja.app.ui.components.EmptyState
import com.bandeja.app.ui.components.ScreenTitle
import com.bandeja.app.ui.components.rememberUndoableDelete
import com.bandeja.app.ui.theme.Spacing

@Composable
fun SearchScreen(
    onOpenContent: (Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = appViewModel { SearchViewModel(it.categoryRepository, it.contentRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The text lives in the field (not only in the ViewModel) so typing never waits for a state round trip.
    var query by rememberSaveable { mutableStateOf(state.filters.query) }

    val snackbar = remember { SnackbarHostState() }
    val deleteItem = rememberUndoableDelete(snackbar, viewModel::deleteItem, viewModel::restoreItem)

    Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        ScreenTitle(stringResource(R.string.nav_search))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; viewModel.onQueryChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; viewModel.onQueryChange("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.search_clear))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.extraLarge,
        )
        FilterRow(state, viewModel)

        ContentList(
            items = state.results,
            isLoading = state.isSearching && state.results.isEmpty(),
            onOpen = onOpenContent,
            onToggleFavorite = viewModel::toggleFavorite,
            onDelete = deleteItem,
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding(), top = Spacing.xs),
            header = resultCountHeader(state),
            empty = {
                if (state.isIdle) {
                    EmptyState(
                        emoji = "🔎",
                        title = stringResource(R.string.search_initial_title),
                        message = stringResource(R.string.search_initial_body),
                    )
                } else {
                    EmptyState(
                        emoji = "🫥",
                        title = stringResource(R.string.search_empty_title),
                        message = stringResource(R.string.search_empty_body),
                    )
                }
            },
        )
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding()))
    }
}

@Composable
private fun resultCountHeader(state: SearchUiState): (LazyListScope.() -> Unit)? {
    if (state.isIdle || state.results.isEmpty()) return null
    val label = pluralStringResource(R.plurals.search_results, state.results.size, state.results.size)
    return {
        item(key = "count") {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screen),
            )
        }
    }
}

@Composable
private fun FilterRow(state: SearchUiState, viewModel: SearchViewModel) {
    val filters = state.filters
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            FilterChip(
                selected = filters.favoritesOnly,
                onClick = { viewModel.onFavoritesOnly(!filters.favoritesOnly) },
                label = { Text(stringResource(R.string.filter_favorites)) },
                leadingIcon = {
                    Icon(if (filters.favoritesOnly) Icons.Rounded.Star else Icons.Rounded.StarBorder, contentDescription = null)
                },
            )
        }
        item {
            val selected = state.categories.firstOrNull { (filters.category as? CategoryFilter.Specific)?.id == it.id }
            MenuChip(
                selected = filters.category != CategoryFilter.Any,
                label = when (filters.category) {
                    CategoryFilter.Any -> stringResource(R.string.filter_category)
                    CategoryFilter.Uncategorized -> stringResource(R.string.category_none)
                    is CategoryFilter.Specific -> selected?.let { "${it.icon} ${it.name}" } ?: stringResource(R.string.filter_category)
                },
            ) { close ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.filter_all_categories)) },
                    onClick = { viewModel.onCategoryFilter(CategoryFilter.Any); close() },
                )
                state.categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text("${category.icon}  ${category.name}") },
                        onClick = { viewModel.onCategoryFilter(CategoryFilter.Specific(category.id)); close() },
                    )
                }
                DropdownMenuItem(
                    text = { Text("📥  ${stringResource(R.string.category_none)}") },
                    onClick = { viewModel.onCategoryFilter(CategoryFilter.Uncategorized); close() },
                )
            }
        }
        item {
            MenuChip(
                selected = filters.period != DatePeriod.ANY,
                label = if (filters.period == DatePeriod.ANY) stringResource(R.string.filter_date) else stringResource(filters.period.labelRes()),
            ) { close ->
                DatePeriod.entries.forEach { period ->
                    DropdownMenuItem(
                        text = { Text(stringResource(period.labelRes())) },
                        onClick = { viewModel.onPeriod(period); close() },
                    )
                }
            }
        }
        if (state.sources.isNotEmpty()) {
            item {
                MenuChip(
                    selected = filters.source != null,
                    label = filters.source ?: stringResource(R.string.filter_source),
                ) { close ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.filter_all_sources)) },
                        onClick = { viewModel.onSource(null); close() },
                    )
                    state.sources.forEach { source ->
                        DropdownMenuItem(text = { Text(source) }, onClick = { viewModel.onSource(source); close() })
                    }
                }
            }
        }
        if (filters.hasActiveFilters) {
            item {
                TextButton(onClick = viewModel::clearFilters) { Text(stringResource(R.string.filter_clear)) }
            }
        }
    }
}

/** A filter chip that opens a dropdown. */
@Composable
private fun MenuChip(
    selected: Boolean,
    label: String,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { expanded = true },
            label = { Text(label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menu { expanded = false }
        }
    }
}

private fun DatePeriod.labelRes(): Int = when (this) {
    DatePeriod.ANY -> R.string.period_any
    DatePeriod.LAST_7_DAYS -> R.string.period_7
    DatePeriod.LAST_30_DAYS -> R.string.period_30
    DatePeriod.LAST_YEAR -> R.string.period_year
}
