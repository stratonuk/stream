package com.strata.tv.data.backup

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class StreamBackupSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `round trips backup payload`() {
        val original = StreamBackup(
            schemaVersion = 1,
            exportedAtEpochMs = 1_700_000_000_000L,
            appVersionName = "1.0.1",
            appVersionCode = 2,
            preferences = mapOf(
                "provider_host" to "https://example.invalid",
                "provider_username" to "user",
            ),
            continueWatching = listOf(
                CwBackup(
                    contentId = "abc",
                    contentType = "movie",
                    resumePositionMs = 12_000,
                    lastUpdatedEpochMs = 1_700_000_000_000L,
                    title = "Demo",
                ),
            ),
            favouriteChannelIds = listOf("ch1", "ch2"),
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<StreamBackup>(encoded)

        assertThat(decoded.schemaVersion).isEqualTo(1)
        assertThat(decoded.preferences["provider_username"]).isEqualTo("user")
        assertThat(decoded.continueWatching).hasSize(1)
        assertThat(decoded.continueWatching[0].title).isEqualTo("Demo")
        assertThat(decoded.favouriteChannelIds).containsExactly("ch1", "ch2")
        assertThat(decoded.itemCount()).isAtLeast(3)
    }
}
