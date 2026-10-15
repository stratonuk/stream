package com.strata.tv.data.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** In-memory last-sync diagnostics for Settings / developer screen. */
@Singleton
class SyncDiagnosticsStore @Inject constructor() {
    private val _snapshot = MutableStateFlow(SyncDiagnostics.Empty)
    val snapshot: StateFlow<SyncDiagnostics> = _snapshot.asStateFlow()

    fun update(diagnostics: SyncDiagnostics) {
        _snapshot.value = diagnostics
    }
}
