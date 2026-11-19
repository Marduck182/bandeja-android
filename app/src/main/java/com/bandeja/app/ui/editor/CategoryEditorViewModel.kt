package com.bandeja.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandeja.app.domain.repository.CategoryOutcome
import com.bandeja.app.domain.repository.CategoryRepository
import com.bandeja.app.ui.theme.CategoryAccent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryEditorUiState(
    val isLoaded: Boolean = false,
    val name: String = "",
    val icon: String = DEFAULT_ICON,
    val color: String = CategoryAccent.Indigo.key,
    val error: CategoryOutcome? = null,
    val isSaving: Boolean = false,
    /** How many items would move to "Sin categoría" if this category is deleted. */
    val itemCount: Int = 0,
) {
    val canSave: Boolean get() = isLoaded && name.isNotBlank() && !isSaving

    companion object {
        const val DEFAULT_ICON = "📌"
    }
}

class CategoryEditorViewModel(
    private val categoryId: Long?,
    private val categories: CategoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CategoryEditorUiState())
    val state: StateFlow<CategoryEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = categoryId?.let { categories.observe(it).first() }
            _state.value = if (existing != null) {
                val count = categories.observeAllWithCounts().first().firstOrNull { it.category.id == existing.id }?.contentCount ?: 0
                CategoryEditorUiState(isLoaded = true, name = existing.name, icon = existing.icon, color = existing.color, itemCount = count)
            } else {
                // New category: start with the next accent in the cycle so consecutive ones differ.
                val index = categories.observeAll().first().size
                CategoryEditorUiState(isLoaded = true, color = CategoryAccent.forIndex(index).key)
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, error = null) }
    fun onIconChange(value: String) = _state.update { it.copy(icon = value) }
    fun onColorChange(value: String) = _state.update { it.copy(color = value) }

    fun delete(onDeleted: () -> Unit) {
        val id = categoryId ?: return
        viewModelScope.launch {
            categories.delete(id)
            onDeleted()
        }
    }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val outcome = categories.save(categoryId, s.name, s.icon, s.color)) {
                is CategoryOutcome.Saved -> onSaved()
                else -> _state.update { it.copy(isSaving = false, error = outcome) }
            }
        }
    }
}
