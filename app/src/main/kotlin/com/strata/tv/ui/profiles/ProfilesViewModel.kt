package com.strata.tv.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strata.tv.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val activeProfileId: StateFlow<String> = flow {
        emit(settingsRepository.activeProfileId())
        // Re-emit when settings change by polling lightly via settings flow.
        settingsRepository.settings.collect {
            emit(settingsRepository.activeProfileId())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "profile_1")

    fun selectProfile(id: String) {
        viewModelScope.launch {
            settingsRepository.setActiveProfileId(id)
        }
    }
}
