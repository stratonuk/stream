package com.strata.tv.data.repo

/**
 * Which catalogue slice to download.  Loading Live / Movies / Shows
 * separately keeps peak memory down on Fire Stick when a panel's
 * `get_vod_streams` JSON is huge enough to OOM a full sync.
 */
enum class SyncScope {
    Live,
    Movies,
    Shows,
    All,
    ;

    val label: String
        get() = when (this) {
            Live -> "Live TV"
            Movies -> "Movies"
            Shows -> "TV Shows"
            All -> "Everything"
        }
}
