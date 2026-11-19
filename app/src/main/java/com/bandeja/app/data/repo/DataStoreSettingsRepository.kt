package com.bandeja.app.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bandeja.app.domain.model.AppSettings
import com.bandeja.app.domain.model.ThemeMode
import com.bandeja.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    override val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            onboardingCompleted = prefs[ONBOARDING] ?: false,
            themeMode = prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[DYNAMIC_COLOR] ?: false,
            lastCategoryId = prefs[LAST_CATEGORY],
        )
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        store.edit { it[ONBOARDING] = completed }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setLastCategoryId(id: Long?) {
        store.edit { if (id == null) it.remove(LAST_CATEGORY) else it[LAST_CATEGORY] = id }
    }

    private companion object {
        val ONBOARDING = booleanPreferencesKey("onboarding_completed")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LAST_CATEGORY = longPreferencesKey("last_category_id")
    }
}
