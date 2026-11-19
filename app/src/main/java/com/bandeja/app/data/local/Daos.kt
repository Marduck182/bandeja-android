package com.bandeja.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT cat.*, COUNT(c.id) AS contentCount
        FROM categories cat LEFT JOIN contents c ON c.categoryId = cat.id
        GROUP BY cat.id
        ORDER BY cat.sortOrder, cat.id
        """,
    )
    fun observeAllWithCounts(): Flow<List<CategoryWithCountRow>>

    @Query("SELECT * FROM categories WHERE id = :id")
    fun observe(id: Long): Flow<CategoryEntity?>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM categories")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ContentDao {
    @Transaction
    @Query("SELECT * FROM contents WHERE id = :id")
    fun observeById(id: Long): Flow<ContentWithRelations?>

    @Transaction
    @Query("SELECT * FROM contents ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ContentWithRelations>>

    @Transaction
    // LIMIT -1 means "no limit" in SQLite.
    @Query("SELECT * FROM contents WHERE isFavorite = 1 ORDER BY updatedAt DESC, id DESC LIMIT :limit")
    fun observeFavorites(limit: Int): Flow<List<ContentWithRelations>>

    @Transaction
    @Query("SELECT * FROM contents WHERE categoryId = :categoryId ORDER BY createdAt DESC, id DESC")
    fun observeByCategory(categoryId: Long): Flow<List<ContentWithRelations>>

    @Transaction
    @Query("SELECT * FROM contents WHERE categoryId IS NULL ORDER BY createdAt DESC, id DESC")
    fun observeUncategorized(): Flow<List<ContentWithRelations>>

    @Query("SELECT COUNT(*) FROM contents WHERE categoryId IS NULL")
    fun observeUncategorizedCount(): Flow<Int>

    @Query("SELECT DISTINCT source FROM contents ORDER BY source")
    fun observeSources(): Flow<List<String>>

    @Transaction
    @Query("SELECT * FROM contents WHERE normalizedUrl = :normalizedUrl ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun findByNormalizedUrl(normalizedUrl: String): ContentWithRelations?

    @Transaction
    @Query("SELECT * FROM contents WHERE id = :id")
    suspend fun get(id: Long): ContentWithRelations?

    @Transaction
    @RawQuery(
        observedEntities = [ContentEntity::class, CategoryEntity::class, TagEntity::class, ContentTagCrossRef::class],
    )
    fun search(query: SupportSQLiteQuery): Flow<List<ContentWithRelations>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(content: ContentEntity): Long

    @Update
    suspend fun update(content: ContentEntity)

    @Query("UPDATE contents SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long)

    @Query("UPDATE contents SET categoryId = :categoryId, updatedAt = :now WHERE id = :id")
    suspend fun setCategory(id: Long, categoryId: Long?, now: Long)

    @Query("DELETE FROM contents WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(tag: TagEntity): Long

    @Query("SELECT * FROM tags WHERE name IN (:names)")
    suspend fun findByNames(names: List<String>): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRefs(refs: List<ContentTagCrossRef>)

    @Query("DELETE FROM content_tags WHERE contentId = :contentId")
    suspend fun deleteRefs(contentId: Long)

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tagId FROM content_tags)")
    suspend fun deleteOrphans()
}
