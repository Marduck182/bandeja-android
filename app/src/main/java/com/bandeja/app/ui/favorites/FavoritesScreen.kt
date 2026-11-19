package com.bandeja.app.ui.favorites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bandeja.app.R
import com.bandeja.app.domain.model.ContentItem
import com.bandeja.app.domain.repository.ContentRepository
import com.bandeja.app.ui.appViewModel
import com.bandeja.app.ui.components.ContentList
import com.bandeja.app.ui.components.EmptyState
import com.bandeja.app.ui.components.ScreenTitle
import com.bandeja.app.ui.components.rememberUndoableDelete
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val isLoading: Boolean = true,
    val items: List<ContentItem> = emptyList(),
)

class FavoritesViewModel(private val contents: ContentRepository) : ViewModel() {
    val state: StateFlow<FavoritesUiState> = contents.observeFavorites()
        .map { FavoritesUiState(isLoading = false, items = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState())

    fun deleteItem(item: ContentItem) {
        viewModelScope.launch { contents.delete(item.content.id) }
    }

    fun restoreItem(item: ContentItem) {
        viewModelScope.launch { contents.restore(item) }
    }

    fun toggleFavorite(item: ContentItem) {
        viewModelScope.launch { contents.setFavorite(item.content.id, !item.content.isFavorite) }
    }
}

@Composable
fun FavoritesScreen(
    onOpenContent: (Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = appViewModel { FavoritesViewModel(it.contentRepository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val deleteItem = rememberUndoableDelete(snackbar, viewModel::deleteItem, viewModel::restoreItem)

    Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        ScreenTitle(stringResource(R.string.favorites_title))
        ContentList(
            items = state.items,
            isLoading = state.isLoading,
            onOpen = onOpenContent,
            onToggleFavorite = viewModel::toggleFavorite,
            onDelete = deleteItem,
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            empty = {
                EmptyState(
                    emoji = "⭐",
                    title = stringResource(R.string.favorites_empty_title),
                    message = stringResource(R.string.favorites_empty_body),
                )
            },
        )
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding()))
    }
}
