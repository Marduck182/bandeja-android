package com.bandeja.app.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandeja.app.domain.model.Category
import com.bandeja.app.domain.model.ContentItem
import com.bandeja.app.domain.repository.CategoryRepository
import com.bandeja.app.domain.repository.ContentRepository
import com.bandeja.app.ui.home.UNCATEGORIZED_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryUiState(
    val isLoading: Boolean = true,
    /** `null` for "Sin categoría" (a virtual category) and while loading. */
    val category: Category? = null,
    val isUncategorized: Boolean = false,
    /** The id does not exist (anymore). */
    val notFound: Boolean = false,
    val items: List<ContentItem> = emptyList(),
)

class CategoryViewModel(
    private val categoryId: Long,
    private val categories: CategoryRepository,
    private val contents: ContentRepository,
) : ViewModel() {

    private val isUncategorized = categoryId == UNCATEGORIZED_ID

    private val categoryFlow: Flow<Category?> = if (isUncategorized) flowOf(null) else categories.observe(categoryId)
    private val itemsFlow = contents.observeByCategory(if (isUncategorized) null else categoryId)

    val state: StateFlow<CategoryUiState> = combine(categoryFlow, itemsFlow) { category, items ->
        CategoryUiState(
            isLoading = false,
            category = category,
            isUncategorized = isUncategorized,
            notFound = !isUncategorized && category == null,
            items = items,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryUiState(isUncategorized = isUncategorized))

    fun toggleFavorite(item: ContentItem) {
        viewModelScope.launch { contents.setFavorite(item.content.id, !item.content.isFavorite) }
    }

    fun deleteItem(item: ContentItem) {
        viewModelScope.launch { contents.delete(item.content.id) }
    }

    fun restoreItem(item: ContentItem) {
        viewModelScope.launch { contents.restore(item) }
    }

    fun delete(onDeleted: () -> Unit) {
        if (isUncategorized) return
        viewModelScope.launch {
            categories.delete(categoryId)
            onDeleted()
        }
    }
}
