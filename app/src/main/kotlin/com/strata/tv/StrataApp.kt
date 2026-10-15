package com.strata.tv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.strata.tv.data.settings.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application root.
 *
 * Hilt's [HiltAndroidApp] entry point — required so the DI graph is
 * available to every Activity / ViewModel / Worker in the app.
 *
 * Implements [Configuration.Provider] so that WorkManager uses the
 * [HiltWorkerFactory] to create @HiltWorker-annotated workers with
 * their injected dependencies.
 *
 * Startup never clears user data. Credential migration only moves a
 * legacy plaintext password into EncryptedSharedPreferences.
 */
@HiltAndroidApp
class StrataApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching { settingsRepository.finalizeCredentialMigration() }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
