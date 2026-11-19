package com.bandeja.app.data.repo

import android.database.sqlite.SQLiteConstraintException
import com.bandeja.app.data.local.CategoryDao
import com.bandeja.app.data.local.CategoryEntity
import com.bandeja.app.domain.model.Category
import com.bandeja.app.domain.model.CategoryWithCount
import com.bandeja.app.domain.repository.CategoryOutcome
import com.bandeja.app.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCategoryRepository(
    private val dao: CategoryDao,
    private val now: () -> Long = System::currentTimeMillis,
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeAllWithCounts(): Flow<List<CategoryWithCount>> =
        dao.observeAllWithCounts().map { rows -> rows.map { CategoryWithCount(it.category.toDomain(), it.contentCount) } }

    override fun observe(id: Long): Flow<Category?> = dao.observe(id).map { it?.toDomain() }

    override suspend fun save(id: Long?, name: String, icon: String, color: String): CategoryOutcome {
        val cleanName = name.replace(Regex("\\s+"), " ").trim()
        if (cleanName.isEmpty()) return CategoryOutcome.NameBlank
        if (cleanName.length > CategoryRepository.MAX_NAME_LENGTH) return CategoryOutcome.NameTooLong

        // SQLite's NOCASE only folds ASCII, so "Música" vs "música" is also checked here.
        if (dao.getAll().any { it.id != id && it.name.equals(cleanName, ignoreCase = true) }) {
            return CategoryOutcome.NameTaken
        }
        val timestamp = now()
        return try {
            if (id == null) {
                val newId = dao.insert(
                    CategoryEntity(
                        name = cleanName,
                        icon = icon,
                        color = color,
                        sortOrder = dao.maxSortOrder() + 1,
                        createdAt = timestamp,
                        updatedAt = timestamp,
                    ),
                )
                CategoryOutcome.Saved(newId)
            } else {
                val current = dao.get(id) ?: return CategoryOutcome.Saved(id)
                dao.update(current.copy(name = cleanName, icon = icon, color = color, updatedAt = timestamp))
                CategoryOutcome.Saved(id)
            }
        } catch (_: SQLiteConstraintException) {
            CategoryOutcome.NameTaken
        }
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}
