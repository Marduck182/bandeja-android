package com.bandeja.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandeja.app.domain.model.Category
import com.bandeja.app.domain.model.ContentItem
import com.bandeja.app.domain.repository.CategoryRepository
import com.bandeja.app.domain.repository.ContentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val isLoading: Boolean = true,
    val item: ContentItem? = null,
    val categories: List<Category> = emptyList(),
) {
    val notFound: Boolean get() = !isLoading && item == null
}

class DetailViewModel(
    private val contentId: Long,
    private val contents: ContentRepository,
    categories: CategoryRepository,
) : ViewModel() {

    val state: StateFlow<DetailUiState> = combine(
        contents.observeItem(contentId),
        categories.observeAll(),
    ) { item, cats -> DetailUiState(isLoading = false, item = item, categories = cats) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    fun toggleFavorite() {
        val item = state.value.item ?: return
        viewModelScope.launch { contents.setFavorite(contentId, !item.content.isFavorite) }
    }

    fun moveToCategory(categoryId: Long?) {
        viewModelScope.launch { contents.moveToCategory(contentId, categoryId) }
    }

    fun update(title: String, notes: String, categoryId: Long?, tags: List<String>) {
        viewModelScope.launch { contents.update(contentId, title, notes, categoryId, tags) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            contents.delete(contentId)
            onDeleted()
        }
    }
}
