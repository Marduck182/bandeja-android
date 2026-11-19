package com.bandeja.app.domain.repository

import com.bandeja.app.domain.model.AppSettings
import com.bandeja.app.domain.model.Category
import com.bandeja.app.domain.model.CategoryWithCount
import com.bandeja.app.domain.model.ContentItem
import com.bandeja.app.domain.model.LinkMetadata
import com.bandeja.app.domain.model.NewContent
import com.bandeja.app.domain.model.SearchFilters
import com.bandeja.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** Result of creating or editing a category. Validation lives with the repository so every caller gets it. */
sealed interface CategoryOutcome {
    data class Saved(val id: Long) : CategoryOutcome
    data object NameBlank : CategoryOutcome
    data object NameTooLong : CategoryOutcome
    data object NameTaken : CategoryOutcome
}

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    fun observeAllWithCounts(): Flow<List<CategoryWithCount>>
    fun observe(id: Long): Flow<Category?>

    /** Creates the category when [id] is `null`, otherwise edits it. */
    suspend fun save(id: Long?, name: String, icon: String, color: String): CategoryOutcome

    /** Its content is kept and moves to "Sin categoría". */
    suspend fun delete(id: Long)

    companion object {
        const val MAX_NAME_LENGTH = 30
    }
}

interface ContentRepository {
    companion object {
        const val NO_LIMIT = -1
    }

    fun observeItem(id: Long): Flow<ContentItem?>
    fun observeRecent(limit: Int): Flow<List<ContentItem>>
    fun observeFavorites(limit: Int = NO_LIMIT): Flow<List<ContentItem>>

    /** `null` selects the items without category. */
    fun observeByCategory(categoryId: Long?): Flow<List<ContentItem>>
    fun observeUncategorizedCount(): Flow<Int>
    fun observeSources(): Flow<List<String>>
    fun search(filters: SearchFilters): Flow<List<ContentItem>>

    /** Most recent item with that normalized URL, if any. */
    suspend fun findByNormalizedUrl(normalizedUrl: String): ContentItem?

    suspend fun add(content: NewContent): Long
    suspend fun update(id: Long, title: String, notes: String, categoryId: Long?, tags: List<String>)
    suspend fun setFavorite(id: Long, favorite: Boolean)
    suspend fun moveToCategory(id: Long, categoryId: Long?)
    suspend fun delete(id: Long)

    /** Puts a deleted item back (undo). Keeps its id and dates; a category that no longer exists becomes none. */
    suspend fun restore(item: ContentItem)

    /** Completes what the save left empty; never overwrites something the user already has. */
    suspend fun fillMissingMetadata(id: Long, metadata: LinkMetadata)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setLastCategoryId(id: Long?)
}

interface MetadataFetcher {
    /** Never throws: any failure (offline, blocked, malformed) is `null`. */
    suspend fun fetch(url: String): LinkMetadata?
}
