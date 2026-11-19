package com.bandeja.app.ui.save

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandeja.app.domain.model.Category
import com.bandeja.app.domain.model.ContentItem
import com.bandeja.app.domain.model.ContentType
import com.bandeja.app.domain.model.LinkMetadata
import com.bandeja.app.domain.repository.CategoryOutcome
import com.bandeja.app.domain.repository.CategoryRepository
import com.bandeja.app.domain.repository.MetadataFetcher
import com.bandeja.app.domain.repository.SettingsRepository
import com.bandeja.app.domain.share.ShareInput
import com.bandeja.app.domain.share.ShareParseResult
import com.bandeja.app.domain.share.SharedContentParser
import com.bandeja.app.domain.url.ContentTypeDetector
import com.bandeja.app.domain.url.InvalidUrlReason
import com.bandeja.app.domain.url.UrlCheck
import com.bandeja.app.domain.url.UrlNormalizer
import com.bandeja.app.domain.url.UrlValidator
import com.bandeja.app.domain.usecase.ContentDraft
import com.bandeja.app.domain.usecase.SaveContent
import com.bandeja.app.domain.usecase.SaveResult
import com.bandeja.app.ui.theme.CategoryAccent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SaveStep { EDITING, INVALID, SAVED }

data class SaveUiState(
    val step: SaveStep = SaveStep.EDITING,
    /** Why the shared content could not be used (step [SaveStep.INVALID]). */
    val invalidReason: InvalidUrlReason? = null,

    val url: String = "",
    /** `true` when saving by hand (the user types or pastes); `false` for content received from Share. */
    val urlEditable: Boolean = false,
    val urlError: InvalidUrlReason? = null,
    val isUrlValid: Boolean = false,
    /** The exact URL that [isUrlValid] and the preview/duplicate data below belong to. */
    val validatedUrl: String = "",
    val source: String = "",
    val contentType: ContentType = ContentType.LINK,

    val title: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val categoryId: Long? = null,
    val categories: List<Category> = emptyList(),

    val metadata: LinkMetadata? = null,
    val isLoadingMetadata: Boolean = false,

    val duplicate: ContentItem? = null,
    val isCheckingDuplicate: Boolean = false,
    val allowDuplicate: Boolean = false,

    val isSaving: Boolean = false,
    val savedCategory: Category? = null,
) {
    val canSave: Boolean
        // validatedUrl must match what is in the field: while a freshly typed URL is still being checked,
        // the title/thumbnail/duplicate data on screen belong to the previous one and must not be saved with it.
        get() = step == SaveStep.EDITING && isUrlValid && validatedUrl == url.trim() && !isCheckingDuplicate &&
            !isSaving && (duplicate == null || allowDuplicate)
}

/**
 * Drives the save sheet, for both entry points: content received through Share and a link pasted by hand.
 * It owns no business rules: parsing, validation, duplicates and persistence live in the domain layer.
 */
class SaveViewModel(
    private val saveContent: SaveContent,
    private val categories: CategoryRepository,
    private val settings: SettingsRepository,
    private val fetcher: MetadataFetcher,
    /** Metadata is fetched here, not in [viewModelScope], so it can still complete after the sheet closes. */
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow(SaveUiState())
    val state: StateFlow<SaveUiState> = _state.asStateFlow()

    private var started = false
    private var categoryChosen = false
    private var titleEdited = false
    private var metadataRequest: Deferred<LinkMetadata?>? = null
    private var requestedUrl: String? = null
    private var urlJob: Job? = null
    private var lastCategoryId: Long? = null

    init {
        viewModelScope.launch {
            lastCategoryId = settings.settings.first().lastCategoryId
            categories.observeAll().collect { list -> onCategories(list) }
        }
    }

    /** Called once per share; configuration changes (rotation) do not restart it. */
    fun startFromShare(input: ShareInput) {
        if (started) return
        started = true
        when (val parsed = SharedContentParser.parse(input)) {
            is ShareParseResult.Invalid -> _state.update { it.copy(step = SaveStep.INVALID, invalidReason = parsed.reason) }
            is ShareParseResult.Parsed -> {
                titleEdited = false
                _state.update { it.copy(urlEditable = false, title = parsed.titleHint.orEmpty()) }
                evaluateUrl(parsed.url)
            }
        }
    }

    /** Fresh, empty form for saving a link by hand. */
    fun startManual() {
        started = true
        urlJob?.cancel()
        metadataRequest?.cancel()
        metadataRequest = null
        requestedUrl = null
        titleEdited = false
        categoryChosen = false
        _state.update {
            SaveUiState(urlEditable = true, categories = it.categories, categoryId = preselectedCategory(it.categories))
        }
    }

    fun onUrlChange(raw: String) {
        _state.update { it.copy(url = raw) }
        urlJob?.cancel()
        urlJob = viewModelScope.launch {
            // Wait for the typing/pasting to settle before validating, so errors do not flash while typing.
            if (raw.isNotBlank()) delay(URL_DEBOUNCE_MS)
            evaluateUrl(raw)
        }
    }

    fun onTitleChange(value: String) {
        titleEdited = true
        _state.update { it.copy(title = value) }
    }

    fun onNotesChange(value: String) = _state.update { it.copy(notes = value) }
    fun onTagsChange(value: List<String>) = _state.update { it.copy(tags = value) }
    fun onFavoriteChange(value: Boolean) = _state.update { it.copy(isFavorite = value) }
    fun allowDuplicate() = _state.update { it.copy(allowDuplicate = true) }

    fun onCategorySelected(id: Long?) {
        categoryChosen = true
        _state.update { it.copy(categoryId = id) }
    }

    /** Quick category creation from the sheet; the new category is selected on success. */
    suspend fun createCategory(name: String, icon: String): CategoryOutcome {
        val accent = CategoryAccent.forIndex(_state.value.categories.size)
        val outcome = categories.save(id = null, name = name, icon = icon, color = accent.key)
        if (outcome is CategoryOutcome.Saved) onCategorySelected(outcome.id)
        return outcome
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val draft = ContentDraft(s.url, s.title, s.notes, s.categoryId, s.tags, s.isFavorite)
            // If the preview has not arrived yet it is handed over so it can finish after the sheet closes.
            val pending = metadataRequest.takeIf { s.metadata == null && s.isLoadingMetadata }
            when (val result = saveContent.save(draft, s.metadata, pending)) {
                is SaveResult.Saved -> {
                    settings.setLastCategoryId(s.categoryId)
                    _state.update {
                        it.copy(
                            step = SaveStep.SAVED,
                            isSaving = false,
                            savedCategory = it.categories.firstOrNull { c -> c.id == s.categoryId },
                        )
                    }
                }
                is SaveResult.Invalid -> _state.update { it.copy(isSaving = false, urlError = result.reason, isUrlValid = false) }
            }
        }
    }

    private fun onCategories(list: List<Category>) = _state.update { current ->
        val stillExists = current.categoryId == null || list.any { it.id == current.categoryId }
        val selected = when {
            !stillExists -> null
            categoryChosen -> current.categoryId
            else -> preselectedCategory(list)
        }
        current.copy(categories = list, categoryId = selected)
    }

    /** The category used last time, so the usual save is a single tap. */
    private fun preselectedCategory(list: List<Category>): Long? = lastCategoryId?.takeIf { id -> list.any { it.id == id } }

    private fun evaluateUrl(raw: String) {
        val text = raw.trim()
        _state.update { it.copy(url = if (it.urlEditable) it.url else text) }
        if (text.isEmpty()) return resetUrlDerivedState(error = null)

        when (val check = UrlValidator.validate(text)) {
            is UrlCheck.Invalid -> resetUrlDerivedState(error = check.reason)
            is UrlCheck.Valid -> {
                val isNew = text != requestedUrl
                // One atomic update: the instant the URL counts as valid, its preview and duplicate check
                // already count as pending, so Save can never be enabled in between.
                _state.update {
                    val base = it.copy(
                        urlError = null,
                        isUrlValid = true,
                        validatedUrl = text,
                        source = UrlNormalizer.sourceOf(check.url),
                        contentType = ContentTypeDetector.detect(check.url),
                    )
                    if (isNew) {
                        base.copy(
                            metadata = null,
                            isLoadingMetadata = true,
                            duplicate = null,
                            allowDuplicate = false,
                            isCheckingDuplicate = true,
                        )
                    } else {
                        base
                    }
                }
                if (isNew) {
                    requestedUrl = text
                    loadPreview(text)
                    checkDuplicate(text)
                }
            }
        }
    }

    private fun resetUrlDerivedState(error: InvalidUrlReason?) {
        requestedUrl = null
        metadataRequest?.cancel()
        metadataRequest = null
        _state.update {
            it.copy(
                urlError = error,
                isUrlValid = false,
                validatedUrl = "",
                isCheckingDuplicate = false,
                metadata = null,
                isLoadingMetadata = false,
                duplicate = null,
                allowDuplicate = false,
                source = "",
            )
        }
    }

    private fun loadPreview(url: String) {
        metadataRequest?.cancel() // a slow fetch for the previous link must not keep running
        val request = appScope.async { fetcher.fetch(url) }
        metadataRequest = request
        _state.update { it.copy(metadata = null, isLoadingMetadata = true) }
        viewModelScope.launch {
            val metadata = request.await()
            if (metadataRequest !== request) return@launch // the user moved on to another link
            _state.update {
                it.copy(
                    metadata = metadata,
                    isLoadingMetadata = false,
                    title = metadata?.title?.takeIf { title -> !titleEdited && title.isNotBlank() } ?: it.title,
                    contentType = metadata?.contentType ?: it.contentType,
                )
            }
        }
    }

    private fun checkDuplicate(url: String) {
        _state.update { it.copy(isCheckingDuplicate = true) }
        viewModelScope.launch {
            val duplicate = saveContent.findDuplicate(url)
            if (requestedUrl == url) {
                _state.update { it.copy(duplicate = duplicate, isCheckingDuplicate = false, allowDuplicate = false) }
            }
        }
    }

    private companion object {
        const val URL_DEBOUNCE_MS = 450L
    }
}
