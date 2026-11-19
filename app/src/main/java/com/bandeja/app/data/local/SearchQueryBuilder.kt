package com.bandeja.app.data.local

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.bandeja.app.domain.model.CategoryFilter
import com.bandeja.app.domain.model.SearchFilters

/**
 * Builds the global-search SQL. Every word typed must match (AND) in at least one of:
 * title, url, source, notes, category name or tag. User text only ever travels as bound arguments,
 * never concatenated into the SQL, and `%` `_` `\` typed by the user are escaped so they match literally.
 */
object SearchQueryBuilder {
    const val MAX_TERMS = 8

    fun build(filters: SearchFilters, now: Long): SupportSQLiteQuery {
        val sql = StringBuilder("SELECT c.* FROM contents c WHERE 1 = 1")
        val args = mutableListOf<Any>()

        for (term in terms(filters.query)) {
            val like = "%${escapeLike(term)}%"
            // Leading space matters: without it the clause would glue onto the previous token ("1AND").
            sql.append(
                " AND (c.title LIKE ? ESCAPE '\\' OR c.url LIKE ? ESCAPE '\\' OR c.source LIKE ? ESCAPE '\\'" +
                    " OR c.description LIKE ? ESCAPE '\\'" +
                    " OR EXISTS (SELECT 1 FROM categories cat WHERE cat.id = c.categoryId AND cat.name LIKE ? ESCAPE '\\')" +
                    " OR EXISTS (SELECT 1 FROM content_tags ct JOIN tags t ON t.id = ct.tagId" +
                    " WHERE ct.contentId = c.id AND t.name LIKE ? ESCAPE '\\'))",
            )
            repeat(6) { args += like }
        }

        when (val category = filters.category) {
            CategoryFilter.Any -> Unit
            CategoryFilter.Uncategorized -> sql.append(" AND c.categoryId IS NULL")
            is CategoryFilter.Specific -> {
                sql.append(" AND c.categoryId = ?")
                args += category.id
            }
        }
        if (filters.favoritesOnly) sql.append(" AND c.isFavorite = 1")
        filters.period.sinceMillis(now)?.let {
            sql.append(" AND c.createdAt >= ?")
            args += it
        }
        filters.source?.let {
            sql.append(" AND c.source = ?")
            args += it
        }
        sql.append(" ORDER BY c.createdAt DESC, c.id DESC")
        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }

    fun terms(query: String): List<String> =
        query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(MAX_TERMS)

    fun escapeLike(text: String): String =
        text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
