package com.strata.tv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.strata.tv.data.settings.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

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
class StrataApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var httpClient: OkHttpClient

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching { settingsRepository.finalizeCredentialMigration() }
        }
    }

    /**
     * Coil loader tuned for IPTV provider artwork: many CDNs 403 the
     * default Coil UA, and posters are often plain HTTP.
     */
    override fun newImageLoader(context: android.content.Context): ImageLoader {
        val imageHttp = httpClient.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "VLC/3.0.20 LibVLC/3.0.20")
                        .build(),
                )
            }
            .build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageHttp })) }
            .crossfade(true)
            .build()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
