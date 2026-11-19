package com.bandeja.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandeja.app.domain.model.CategoryWithCount
import com.bandeja.app.domain.model.ContentItem
import com.bandeja.app.domain.repository.CategoryRepository
import com.bandeja.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val categories: List<CategoryWithCount> = emptyList(),
    val uncategorizedCount: Int = 0,
    val recent: List<ContentItem> = emptyList(),
    val favorites: List<ContentItem> = emptyList(),
)

class HomeViewModel(
    private val categories: CategoryRepository,
    contents: ContentRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        categories.observeAllWithCounts(),
        contents.observeUncategorizedCount(),
        contents.observeRecent(RECENT_LIMIT),
        contents.observeFavorites(RECENT_LIMIT),
    ) { cats, uncategorized, recent, favorites ->
        HomeUiState(
            isLoading = false,
            categories = cats,
            uncategorizedCount = uncategorized,
            recent = recent,
            favorites = favorites,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** The category's content is kept: it moves to "Sin categoría". */
    fun deleteCategory(id: Long) {
        viewModelScope.launch { categories.delete(id) }
    }

    private companion object {
        const val RECENT_LIMIT = 10
    }
}
