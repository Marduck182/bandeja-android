package com.bandeja.app.data.repo

import com.bandeja.app.data.local.CategoryEntity
import com.bandeja.app.data.local.ContentEntity
import com.bandeja.app.data.local.ContentWithRelations
import com.bandeja.app.domain.model.Category
import com.bandeja.app.domain.model.Content
import com.bandeja.app.domain.model.ContentItem
import com.bandeja.app.domain.model.ContentType

internal fun CategoryEntity.toDomain() = Category(id, name, icon, color, sortOrder, createdAt, updatedAt)

internal fun ContentEntity.toDomain() = Content(
    id = id,
    url = url,
    normalizedUrl = normalizedUrl,
    title = title,
    description = description,
    thumbnailUrl = thumbnailUrl,
    source = source,
    // A value written by a future version must not crash an older one.
    contentType = runCatching { ContentType.valueOf(contentType) }.getOrDefault(ContentType.LINK),
    categoryId = categoryId,
    isFavorite = isFavorite,
    publishedAt = publishedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun Content.toEntity() = ContentEntity(
    id = id,
    url = url,
    normalizedUrl = normalizedUrl,
    title = title,
    description = description,
    thumbnailUrl = thumbnailUrl,
    source = source,
    contentType = contentType.name,
    categoryId = categoryId,
    isFavorite = isFavorite,
    publishedAt = publishedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun ContentWithRelations.toDomain() = ContentItem(
    content = content.toDomain(),
    category = category?.toDomain(),
    tags = tags.map { it.name }.sorted(),
)
