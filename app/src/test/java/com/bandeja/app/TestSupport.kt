package com.bandeja.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bandeja.app.data.local.BandejaDatabase
import com.bandeja.app.domain.model.AppSettings
import com.bandeja.app.domain.model.ContentType
import com.bandeja.app.domain.model.LinkMetadata
import com.bandeja.app.domain.model.NewContent
import com.bandeja.app.domain.model.ThemeMode
import com.bandeja.app.domain.repository.MetadataFetcher
import com.bandeja.app.domain.repository.SettingsRepository
import com.bandeja.app.domain.url.UrlNormalizer
import com.bandeja.app.domain.url.UrlValidator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

fun appContext(): Context = ApplicationProvider.getApplicationContext()

/** Real Room (in memory), same schema and queries as the app; no seed categories. */
fun newInMemoryDatabase(): BandejaDatabase =
    Room.inMemoryDatabaseBuilder(appContext(), BandejaDatabase::class.java).allowMainThreadQueries().build()

fun newContent(
    url: String = "https://example.com/a",
    title: String = "Title",
    notes: String = "",
    categoryId: Long? = null,
    tags: List<String> = emptyList(),
    favorite: Boolean = false,
    source: String = "example.com",
    type: ContentType = ContentType.LINK,
    thumbnail: String? = null,
) = NewContent(
    url = url,
    normalizedUrl = UrlNormalizer.normalize(url) ?: error("test url must be valid: $url"),
    title = title,
    description = notes,
    thumbnailUrl = thumbnail,
    source = source,
    contentType = type,
    categoryId = categoryId,
    isFavorite = favorite,
    publishedAt = null,
    tags = tags,
)

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun setOnboardingCompleted(completed: Boolean) { state.value = state.value.copy(onboardingCompleted = completed) }
    override suspend fun setThemeMode(mode: ThemeMode) { state.value = state.value.copy(themeMode = mode) }
    override suspend fun setDynamicColor(enabled: Boolean) { state.value = state.value.copy(dynamicColor = enabled) }
    override suspend fun setLastCategoryId(id: Long?) { state.value = state.value.copy(lastCategoryId = id) }
    val current: AppSettings get() = state.value
}

/** Answers immediately, or only after [release] when a [gate] is used: simulates a slow network. */
class FakeMetadataFetcher(
    private val result: LinkMetadata? = null,
    private val gate: CompletableDeferred<Unit>? = null,
) : MetadataFetcher {
    val requested = mutableListOf<String>()
    override suspend fun fetch(url: String): LinkMetadata? {
        requested += url
        gate?.await()
        return result
    }
    fun release() { gate?.complete(Unit) }
}

/** Waits (real time) until [predicate] holds, so tests do not depend on arbitrary sleeps. */
suspend fun <T> Flow<T>.await(timeoutMs: Long = 5_000, predicate: (T) -> Boolean): T =
    withTimeout(timeoutMs) { first(predicate) }

fun isValidUrl(raw: String) = UrlValidator.isValid(raw)
