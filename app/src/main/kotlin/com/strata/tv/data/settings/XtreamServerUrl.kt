package com.strata.tv.data.settings

/**
 * Normalise a user-entered Xtream server URL the way IPTV Smarters Pro
 * expects for "Xtreme Codes" login — preserve scheme, host and port;
 * never rewrite HTTP↔HTTPS; never invent ports.
 */
object XtreamServerUrl {

    /**
     * @return cleaned base URL like `http://host:8080` or an error message.
     */
    fun normalize(raw: String): Result<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Server URL is required"))
        }

        var candidate = trimmed
        // Strip accidental player_api / get.php paths pasted from docs.
        candidate = candidate
            .replace(Regex("""/player_api\.php.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""/get\.php.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""/xmltv\.php.*$""", RegexOption.IGNORE_CASE), "")
            .trimEnd('/')

        if (!candidate.contains("://")) {
            // Do not force HTTPS — many panels are HTTP-only like Smarters.
            candidate = "http://$candidate"
        }

        return try {
            val uri = java.net.URI(candidate)
            if (uri.host.isNullOrBlank()) {
                Result.failure(IllegalArgumentException("Could not parse host from server URL"))
            } else {
                val scheme = uri.scheme?.lowercase() ?: "http"
                if (scheme != "http" && scheme != "https") {
                    return Result.failure(
                        IllegalArgumentException("Server URL must start with http:// or https://"),
                    )
                }
                val port = if (uri.port > 0) ":${uri.port}" else ""
                // Preserve path prefix if the panel is mounted under a subpath
                // (rare but legal). Drop only trailing slash.
                val path = uri.path?.trimEnd('/').orEmpty()
                val base = buildString {
                    append(scheme)
                    append("://")
                    append(uri.host)
                    append(port)
                    if (path.isNotBlank() && path != "/") append(path)
                }
                Result.success(base)
            }
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Invalid server URL: ${e.message}"))
        }
    }

    fun isHttp(url: String): Boolean =
        url.trim().lowercase().startsWith("http://")
}
