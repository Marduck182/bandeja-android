package com.bandeja.app.domain.model

data class Category(
    val id: Long,
    val name: String,
    /** An emoji picked from a curated set. */
    val icon: String,
    /** Key of an accent (see `ui.theme.CategoryAccent`), not a raw color: tones can change without a migration. */
    val color: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class CategoryWithCount(val category: Category, val contentCount: Int)

enum class ContentType { LINK, VIDEO, ARTICLE, SOCIAL, IMAGE, CODE }

data class Content(
    val id: Long,
    val url: String,
    val normalizedUrl: String,
    val title: String,
    /** The user's notes. */
    val description: String,
    val thumbnailUrl: String?,
    /** Domain the content comes from, e.g. `youtube.com`. */
    val source: String,
    val contentType: ContentType,
    /** `null` means "Sin categoría". */
    val categoryId: Long?,
    val isFavorite: Boolean,
    val publishedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A saved item together with what the UI needs to show it. */
data class ContentItem(
    val content: Content,
    val category: Category?,
    val tags: List<String>,
)

/** Everything needed to insert a new item. Built by [com.bandeja.app.domain.usecase.SaveContent]. */
data class NewContent(
    val url: String,
    val normalizedUrl: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String?,
    val source: String,
    val contentType: ContentType,
    val categoryId: Long?,
    val isFavorite: Boolean,
    val publishedAt: Long?,
    val tags: List<String>,
)

/** What could be learned about a link from the network. Every field is optional. */
data class LinkMetadata(
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val contentType: ContentType? = null,
    val publishedAt: Long? = null,
)

sealed interface CategoryFilter {
    data object Any : CategoryFilter
    data object Uncategorized : CategoryFilter
    data class Specific(val id: Long) : CategoryFilter
}

enum class DatePeriod(val days: Int?) {
    ANY(null), LAST_7_DAYS(7), LAST_30_DAYS(30), LAST_YEAR(365);

    fun sinceMillis(now: Long): Long? = days?.let { now - it * MILLIS_PER_DAY }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}

data class SearchFilters(
    val query: String = "",
    val category: CategoryFilter = CategoryFilter.Any,
    val favoritesOnly: Boolean = false,
    val period: DatePeriod = DatePeriod.ANY,
    val source: String? = null,
) {
    val hasActiveFilters: Boolean
        get() = category != CategoryFilter.Any || favoritesOnly || period != DatePeriod.ANY || source != null
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Preselected in the save sheet: saving is then a single tap. */
    val lastCategoryId: Long? = null,
)
