package com.bandeja.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandeja.app.domain.model.Category
import com.bandeja.app.domain.model.CategoryFilter
import com.bandeja.app.domain.model.ContentItem
import com.bandeja.app.domain.model.DatePeriod
import com.bandeja.app.domain.model.SearchFilters
import com.bandeja.app.domain.repository.CategoryRepository
import com.bandeja.app.domain.repository.ContentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val filters: SearchFilters = SearchFilters(),
    val categories: List<Category> = emptyList(),
    val sources: List<String> = emptyList(),
    val results: List<ContentItem> = emptyList(),
    val isSearching: Boolean = false,
) {
    /** Nothing typed and no filter chosen: the screen explains what can be searched instead of listing everything. */
    val isIdle: Boolean get() = filters.query.isBlank() && !filters.hasActiveFilters
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    categoryRepository: CategoryRepository,
    private val contents: ContentRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(SearchFilters())

    private val results = filters
        // Typing is debounced (a chip change while text is present also waits that long: fine at 200 ms).
        .debounce { if (it.query.isBlank()) 0L else QUERY_DEBOUNCE_MS }
        .distinctUntilChanged()
        .flatMapLatest { f ->
            if (f.query.isBlank() && !f.hasActiveFilters) {
                flowOf(Searched(emptyList(), f))
            } else {
                contents.search(f).map { Searched(it, f) }
            }
        }

    val state: StateFlow<SearchUiState> = combine(
        filters,
        results,
        categoryRepository.observeAll(),
        contents.observeSources(),
    ) { f, searched, categories, sources ->
        // Results of older filters stay on screen until the new ones arrive: no flicker between keystrokes.
        SearchUiState(f, categories, sources, searched.items, isSearching = searched.filters != f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    private data class Searched(val items: List<ContentItem>, val filters: SearchFilters)

    fun onQueryChange(query: String) = filters.update { it.copy(query = query) }
    fun onCategoryFilter(filter: CategoryFilter) = filters.update { it.copy(category = filter) }
    fun onFavoritesOnly(enabled: Boolean) = filters.update { it.copy(favoritesOnly = enabled) }
    fun onPeriod(period: DatePeriod) = filters.update { it.copy(period = period) }
    fun onSource(source: String?) = filters.update { it.copy(source = source) }
    fun clearFilters() = filters.update { SearchFilters(query = it.query) }

    fun deleteItem(item: ContentItem) {
        viewModelScope.launch { contents.delete(item.content.id) }
    }

    fun restoreItem(item: ContentItem) {
        viewModelScope.launch { contents.restore(item) }
    }

    fun toggleFavorite(item: ContentItem) {
        viewModelScope.launch { contents.setFavorite(item.content.id, !item.content.isFavorite) }
    }

    private companion object {
        const val QUERY_DEBOUNCE_MS = 200L
    }
}
