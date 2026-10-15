package com.strata.tv.data.settings

/**
 * The IPTV provider the user has configured.  Either a built-in
 * Xtream Codes provider (MyBunny.TV, GTV, etc.) where we have the
 * host URL baked in and the user supplies credentials, or a fully
 * custom entry where the user supplies everything.
 *
 * The settings layer stores this; [ProviderConfig.toM3uUrl] /
 * [ProviderConfig.toPlayerApiUrl] resolve the runtime URLs.
 */
data class ProviderConfig(
    /**
     * Connection type id. Universal Xtream login uses `custom_xtream`
     * (IPTV Smarters Pro "Xtreme Codes" equivalent). Empty = not set up.
     */
    val providerId: String = "",
    /** Optional display name for this playlist / connection. */
    val connectionName: String = "",
    /** Xtream host (scheme + host + optional port), no trailing slash. */
    val host: String = "",
    val username: String = "",
    val password: String = "",
    /**
     * Used when [providerId] = "custom_m3u": a raw M3U URL with
     * credentials baked in.  [host]/[username]/[password] are unused
     * in this case.
     */
    val customM3uUrl: String = "",
    /**
     * When true, pull the user's website-curated filtered playlist
     * from `{host}/client/download.php?u=…&p=…` instead of the full
     * provider catalogue.
     */
    val useFilteredPlaylist: Boolean = false,
) {
    val isConfigured: Boolean
        get() = when (providerId) {
            "" -> false
            "custom_m3u" -> customM3uUrl.isNotBlank()
            else -> host.isNotBlank() && username.isNotBlank() && password.isNotBlank()
        }

    private fun enc(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    /** Resolve the M3U playlist URL for [SyncService.syncFromUrl]. */
    fun toM3uUrl(): String = when {
        providerId == "custom_m3u" -> customM3uUrl
        useFilteredPlaylist -> {
            val base = host.trimEnd('/')
            "$base/client/download.php?u=${enc(username)}&p=${enc(password)}"
        }
        else -> {
            // Standard Xtream get.php — same family of endpoints Smarters uses.
            val base = host.trimEnd('/')
            "$base/get.php?username=${enc(username)}&password=${enc(password)}&type=m3u_plus"
        }
    }

    /**
     * Resolve the XMLTV EPG URL for this provider.
     *
     * - **MyBunny.TV** publishes a public EPG at `/epg.xml`, no auth.
     * - **Other Xtream Codes** providers typically host the EPG at the
     *   standard `/xmltv.php` endpoint behind the user's credentials.
     * - **Raw M3U** providers don't expose an EPG; returns null.
     */
    fun toEpgUrl(): String? = when (providerId) {
        "custom_m3u" -> null
        "mybunny_tv" -> "https://mybunny.tv/epg.xml"
        else -> {
            val base = host.trimEnd('/')
            "$base/xmltv.php?username=${enc(username)}&password=${enc(password)}"
        }
    }

    /** Resolve the Xtream `player_api.php` URL for account info / subscription. */
    fun toPlayerApiUrl(action: String? = null): String? {
        if (providerId == "custom_m3u") return null
        val base = host.trimEnd('/')
        if (base.isBlank()) return null
        val core = "$base/player_api.php?username=${enc(username)}&password=${enc(password)}"
        return if (action != null) "$core&action=$action" else core
    }

    /**
     * User-Agent for Xtream API calls. VLC is widely accepted by panels
     * that IPTV Smarters Pro also reaches; okhttp alone is rejected by
     * some XCIPTV-style hosts.
     */
    fun apiUserAgent(): String = "VLC/3.0.20 LibVLC/3.0.20"
}

/**
 * Catalogue of built-in providers shown in the first-run wizard
 * and Settings provider picker.  Hosts are public knowledge —
 * credentials are entered by the user at runtime, never embedded.
 */
object BuiltInProviders {
    data class Entry(
        val id: String,
        val displayName: String,
        val host: String,
        val description: String,
    )

    val ALL: List<Entry> = listOf(
        Entry(
            id = "mybunny_tv",
            displayName = "MyBunny.TV",
            host = "https://mybunny.tv",
            description = "Xtream Codes. Enter your MyBunny.TV username and password.",
        ),
        Entry(
            id = "skyglass",
            displayName = "SkyGlass (auto-detect)",
            // No baked default — [SkyGlassLicenseClient] hits the
            // SkyGlass app's own license server at test-and-continue
            // time, decrypts the portal list (B1G / B1G 2 / Ultimate /
            // GTV) and picks whichever portal accepts the user's
            // username + password.  The user never types a URL.
            //
            // The individual B1G / Ultimate / GTV presets below are
            // shortcuts for users who already know which panel their
            // subscription is on — they skip the license-server hop.
            host = "",
            description = "Don't know which panel? Just enter the same " +
                "username and password as the SkyGlass app — Strata " +
                "tries B1G, B1G 2, Ultimate and GTV automatically.",
        ),
        Entry(
            id = "me2u",
            displayName = "Me2u Ultra Sky (auto-detect)",
            // Same auto-detect approach as SkyGlass but for the DHM Streams
            // Me2u Ultra Sky variant (com.dhmstreams.Me2uultrasky).
            // Hits a different license server and uses AES-128-CBC decryption.
            host = "",
            description = "For Me2u Ultra Sky subscribers — enter the same " +
                "username and password as the Me2u app. Strata detects " +
                "the right panel (Ultra / Ultra1) automatically.",
        ),
        // Hosts below were extracted from the SkyGlass license server's
        // `portal` response (see SkyGlassLicenseClient).  They're the
        // direct Xtream Codes endpoints for each panel.
        Entry(
            id = "b1g",
            displayName = "B1G",
            host = "http://rev-tv.com",
            description = "B1G Xtream Codes panel served via SkyGlass.",
        ),
        Entry(
            id = "b1g2",
            displayName = "B1G 2",
            host = "http://msresel.one",
            description = "B1G 2 Xtream Codes panel served via SkyGlass.",
        ),
        Entry(
            id = "ultimate",
            displayName = "Ultimate",
            host = "http://sharkvpn.eltanke.xyz",
            description = "Ultimate Xtream Codes panel served via SkyGlass.",
        ),
        Entry(
            id = "gtv",
            displayName = "GTV",
            host = "http://glass-premium.site",
            description = "GTV Xtream Codes panel served via SkyGlass.",
        ),
        Entry(
            id = "custom_xtream",
            displayName = "Custom Xtream",
            host = "",
            description = "For B1G, GTV, Ultimate, and other Xtream providers. " +
                "Enter host URL, username and password.",
        ),
        Entry(
            id = "custom_m3u",
            displayName = "Custom M3U URL",
            host = "",
            description = "Paste a raw M3U URL that already has credentials baked in.",
        ),
    )

    fun byId(id: String): Entry? = ALL.firstOrNull { it.id == id }
}
