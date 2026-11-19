package com.bandeja.app.data.repo

import androidx.room.withTransaction
import com.bandeja.app.data.local.BandejaDatabase
import com.bandeja.app.data.local.ContentEntity
import com.bandeja.app.data.local.ContentTagCrossRef
import com.bandeja.app.data.local.SearchQueryBuilder
import com.bandeja.app.data.local.TagEntity
import com.bandeja.app.domain.model.ContentItem
import com.bandeja.app.domain.model.ContentType
import com.bandeja.app.domain.model.LinkMetadata
import com.bandeja.app.domain.model.NewContent
import com.bandeja.app.domain.model.SearchFilters
import com.bandeja.app.domain.repository.ContentRepository
import com.bandeja.app.domain.usecase.TextRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomContentRepository(
    private val db: BandejaDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : ContentRepository {
    private val contents = db.contentDao()
    private val categories = db.categoryDao()
    private val tags = db.tagDao()

    override fun observeItem(id: Long): Flow<ContentItem?> = contents.observeById(id).map { it?.toDomain() }

    override fun observeRecent(limit: Int): Flow<List<ContentItem>> =
        contents.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    override fun observeFavorites(limit: Int): Flow<List<ContentItem>> =
        contents.observeFavorites(limit).map { rows -> rows.map { it.toDomain() } }

    override fun observeByCategory(categoryId: Long?): Flow<List<ContentItem>> =
        (if (categoryId == null) contents.observeUncategorized() else contents.observeByCategory(categoryId))
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeUncategorizedCount(): Flow<Int> = contents.observeUncategorizedCount()

    override fun observeSources(): Flow<List<String>> = contents.observeSources()

    override fun search(filters: SearchFilters): Flow<List<ContentItem>> =
        contents.search(SearchQueryBuilder.build(filters, now())).map { rows -> rows.map { it.toDomain() } }

    override suspend fun findByNormalizedUrl(normalizedUrl: String): ContentItem? =
        contents.findByNormalizedUrl(normalizedUrl)?.toDomain()

    override suspend fun add(content: NewContent): Long = db.withTransaction {
        val timestamp = now()
        val id = contents.insert(
            ContentEntity(
                url = content.url,
                normalizedUrl = content.normalizedUrl,
                title = content.title,
                description = content.description,
                thumbnailUrl = content.thumbnailUrl,
                source = content.source,
                contentType = content.contentType.name,
                categoryId = content.categoryId.takeIfExists(),
                isFavorite = content.isFavorite,
                publishedAt = content.publishedAt,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        replaceTags(id, content.tags)
        id
    }

    override suspend fun update(id: Long, title: String, notes: String, categoryId: Long?, tags: List<String>) {
        db.withTransaction {
            val current = contents.get(id)?.content ?: return@withTransaction
            contents.update(
                current.copy(
                    title = TextRules.title(title).ifEmpty { current.source },
                    description = TextRules.notes(notes),
                    categoryId = categoryId.takeIfExists(),
                    updatedAt = now(),
                ),
            )
            replaceTags(id, tags)
        }
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean) = contents.setFavorite(id, favorite, now())

    override suspend fun moveToCategory(id: Long, categoryId: Long?) =
        contents.setCategory(id, categoryId.takeIfExists(), now())

    override suspend fun delete(id: Long) {
        db.withTransaction {
            contents.delete(id)
            tags.deleteOrphans()
        }
    }

    override suspend fun restore(item: ContentItem) {
        db.withTransaction {
            contents.insert(item.content.toEntity().copy(categoryId = item.content.categoryId.takeIfExists()))
            replaceTags(item.content.id, item.tags)
        }
    }

    override suspend fun fillMissingMetadata(id: Long, metadata: LinkMetadata) {
        db.withTransaction {
            val current = contents.get(id)?.content ?: return@withTransaction
            val newTitle = metadata.title?.let(TextRules::title)
            val updated = current.copy(
                thumbnailUrl = current.thumbnailUrl ?: metadata.thumbnailUrl,
                // The title is only replaced while it is still the placeholder (the domain): never the user's.
                title = if (newTitle != null && newTitle.isNotEmpty() &&
                    (current.title.isBlank() || current.title == current.source)
                ) newTitle else current.title,
                contentType = if (current.contentType == ContentType.LINK.name) {
                    (metadata.contentType ?: ContentType.LINK).name
                } else current.contentType,
                publishedAt = current.publishedAt ?: metadata.publishedAt,
            )
            if (updated != current) contents.update(updated.copy(updatedAt = now()))
        }
    }

    private suspend fun replaceTags(contentId: Long, names: List<String>) {
        val clean = TextRules.tags(names)
        tags.deleteRefs(contentId)
        if (clean.isNotEmpty()) {
            clean.forEach { tags.insertIgnore(TagEntity(name = it)) }
            tags.insertRefs(tags.findByNames(clean).map { ContentTagCrossRef(contentId, it.id) })
        }
        tags.deleteOrphans()
    }

    /** A category that no longer exists becomes "no category" instead of failing the foreign key. */
    private suspend fun Long?.takeIfExists(): Long? = this?.takeIf { categories.get(it) != null }
}
