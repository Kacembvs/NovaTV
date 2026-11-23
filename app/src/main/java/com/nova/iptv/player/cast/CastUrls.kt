package com.nova.iptv.player.cast

/** Chooses the URL and MIME type sent to the Cast receiver (which cannot play raw MPEG-TS). */
object CastUrls {
    const val HLS = "application/x-mpegURL"

    /** Xtream live channels ("…/live/user/pass/123.ts") are also served as HLS by the same server. */
    fun castUrl(url: String, live: Boolean): String {
        val path = url.substringBefore('?')
        return if (live && "/live/" in path && path.endsWith(".ts", ignoreCase = true)) {
            path.dropLast(3) + ".m3u8" + url.removePrefix(path)
        } else {
            url
        }
    }

    fun mimeType(url: String, live: Boolean): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") -> HLS
            path.endsWith(".mp4") || path.endsWith(".m4v") -> "video/mp4"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".ts") -> "video/mp2t"
            live -> HLS
            else -> "video/mp4"
        }
    }
}
