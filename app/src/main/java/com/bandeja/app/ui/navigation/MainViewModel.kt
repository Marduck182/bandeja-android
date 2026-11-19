package com.bandeja.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandeja.app.domain.model.AppSettings
import com.bandeja.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    /** `null` until the stored settings are read: the splash screen stays up meanwhile. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Decided once: it must not change when the user finishes onboarding while the graph is alive. */
    val startDestination: StateFlow<String?> = flow {
        emit(if (settingsRepository.settings.first().onboardingCompleted) Routes.HOME else Routes.ONBOARDING)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingCompleted(true) }
    }
}
