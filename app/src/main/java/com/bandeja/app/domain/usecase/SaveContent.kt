package com.bandeja.app.domain.usecase

import com.bandeja.app.domain.model.ContentItem
import com.bandeja.app.domain.model.LinkMetadata
import com.bandeja.app.domain.model.NewContent
import com.bandeja.app.domain.repository.ContentRepository
import com.bandeja.app.domain.url.ContentTypeDetector
import com.bandeja.app.domain.url.InvalidUrlReason
import com.bandeja.app.domain.url.UrlCheck
import com.bandeja.app.domain.url.UrlNormalizer
import com.bandeja.app.domain.url.UrlValidator
import com.bandeja.app.domain.url.YouTubeLinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

/** What the user filled in the save sheet. */
data class ContentDraft(
    val url: String,
    val title: String,
    val notes: String,
    val categoryId: Long?,
    val tags: List<String>,
    val isFavorite: Boolean,
)

sealed interface SaveResult {
    data class Saved(val id: Long) : SaveResult
    data class Invalid(val reason: InvalidUrlReason) : SaveResult
}

/**
 * The one piece of real business logic of the main flow: validate → build → persist → enrich.
 * Saving never waits for the network: if the page's metadata is still loading, the item is stored right away
 * and [appScope] (which outlives the share sheet) completes the empty fields when it arrives.
 */
class SaveContent(
    private val contents: ContentRepository,
    private val appScope: CoroutineScope,
) {
    /** Existing item with the same normalized URL, if any. */
    suspend fun findDuplicate(url: String): ContentItem? {
        val normalized = UrlNormalizer.normalize(url) ?: return null
        return contents.findByNormalizedUrl(normalized)
    }

    suspend fun save(draft: ContentDraft, metadata: LinkMetadata?, pending: Deferred<LinkMetadata?>? = null): SaveResult {
        val check = UrlValidator.validate(draft.url)
        val parsed = (check as? UrlCheck.Valid)?.url
            ?: return SaveResult.Invalid((check as UrlCheck.Invalid).reason)

        val url = draft.url.trim()
        val source = UrlNormalizer.sourceOf(parsed)
        val title = TextRules.title(draft.title).ifEmpty { TextRules.title(metadata?.title.orEmpty()) }.ifEmpty { source }
        val id = contents.add(
            NewContent(
                url = url,
                normalizedUrl = UrlNormalizer.normalize(parsed),
                title = title,
                description = TextRules.notes(draft.notes),
                thumbnailUrl = metadata?.thumbnailUrl ?: YouTubeLinks.videoId(url)?.let(YouTubeLinks::thumbnailUrl),
                source = source,
                contentType = metadata?.contentType ?: ContentTypeDetector.detect(parsed),
                categoryId = draft.categoryId,
                isFavorite = draft.isFavorite,
                publishedAt = metadata?.publishedAt,
                tags = TextRules.tags(draft.tags),
            ),
        )
        if (pending != null && metadata == null) {
            appScope.launch { pending.await()?.let { contents.fillMissingMetadata(id, it) } }
        }
        return SaveResult.Saved(id)
    }
}
