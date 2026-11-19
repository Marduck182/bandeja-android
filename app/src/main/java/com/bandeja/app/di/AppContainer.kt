package com.bandeja.app.di

import android.content.Context
import com.bandeja.app.data.local.BandejaDatabase
import com.bandeja.app.data.remote.OpenGraphMetadataFetcher
import com.bandeja.app.data.repo.DataStoreSettingsRepository
import com.bandeja.app.data.repo.RoomCategoryRepository
import com.bandeja.app.data.repo.RoomContentRepository
import com.bandeja.app.domain.repository.CategoryRepository
import com.bandeja.app.domain.repository.ContentRepository
import com.bandeja.app.domain.repository.MetadataFetcher
import com.bandeja.app.domain.repository.SettingsRepository
import com.bandeja.app.domain.usecase.SaveContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual composition root (see D-003). Everything is lazy so the process start (and the share sheet,
 * which must open instantly) only pays for what it uses.
 */
class AppContainer(private val context: Context) {
    /** Outlives any screen: finishes work such as completing a saved link's preview after the share sheet closed. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: BandejaDatabase by lazy { BandejaDatabase.create(context) }

    val categoryRepository: CategoryRepository by lazy { RoomCategoryRepository(database.categoryDao()) }
    val contentRepository: ContentRepository by lazy { RoomContentRepository(database) }
    val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository(context) }
    val metadataFetcher: MetadataFetcher by lazy { OpenGraphMetadataFetcher() }
    val saveContent: SaveContent by lazy { SaveContent(contentRepository, appScope) }
}
