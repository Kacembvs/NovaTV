package com.nova.iptv.data.source

import com.nova.iptv.domain.ContentKind
import java.io.BufferedReader

data class M3uEntry(
    val name: String,
    val url: String,
    val group: String,
    val logo: String?,
    val tvgId: String?,
    val kind: ContentKind,
)

/**
 * Streaming M3U/M3U8 parser: entries are produced lazily while the playlist downloads,
 * so the first channels can be stored and displayed before the whole file is read.
 */
class M3uParser {
    /** XMLTV URL declared in the #EXTM3U header (url-tvg / x-tvg-url), if any. */
    var epgUrl: String? = null
        private set

    fun parse(reader: BufferedReader): Sequence<M3uEntry> = sequence {
        var info: String? = null
        var extGroup: String? = null
        var first = true
        while (true) {
            var line = reader.readLine() ?: break
            if (first) {
                line = line.removePrefix("\uFEFF")
                first = false
            }
            line = line.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val attrs = parseAttributes(line)
                    epgUrl = (attrs["url-tvg"] ?: attrs["x-tvg-url"])?.ifBlank { null }
                }
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    info = line
                    extGroup = null
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> extGroup = line.substringAfter(':').trim()
                line.startsWith("#") -> Unit
                else -> {
                    yield(buildEntry(info, extGroup, line))
                    info = null
                    extGroup = null
                }
            }
        }
    }

    private fun buildEntry(info: String?, extGroup: String?, url: String): M3uEntry {
        val separator = info?.let { nameSeparatorIndex(it) } ?: -1
        val header = when {
            info == null -> ""
            separator >= 0 -> info.substring(0, separator)
            else -> info
        }
        val attrs = parseAttributes(header)
        val name = (if (info != null && separator >= 0) info.substring(separator + 1).trim() else "")
            .ifBlank { attrs["tvg-name"].orEmpty() }
            .ifBlank { url.substringBefore('?').substringAfterLast('/') }
        val group = attrs["group-title"].orEmpty().ifBlank { extGroup.orEmpty() }.ifBlank { DEFAULT_GROUP }
        return M3uEntry(
            name = name,
            url = url,
            group = group,
            logo = attrs["tvg-logo"]?.ifBlank { null },
            tvgId = attrs["tvg-id"]?.ifBlank { null },
            kind = classify(url),
        )
    }

    companion object {
        const val DEFAULT_GROUP = "Sans catégorie"

        private val ATTRIBUTE = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
        private val VOD_EXTENSIONS = listOf(".mp4", ".mkv", ".avi", ".mov", ".wmv", ".m4v", ".webm")

        fun parseAttributes(text: String): Map<String, String> =
            ATTRIBUTE.findAll(text).associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }

        /** Index of the comma separating the #EXTINF attributes from the display name (ignores commas inside quotes). */
        fun nameSeparatorIndex(info: String): Int {
            var inQuotes = false
            info.forEachIndexed { index, ch ->
                if (ch == '"') inQuotes = !inQuotes
                else if (ch == ',' && !inQuotes) return index
            }
            return -1
        }

        fun classify(url: String): ContentKind {
            val path = url.substringBefore('?').lowercase()
            return when {
                "/series/" in path -> ContentKind.SERIES
                "/movie/" in path -> ContentKind.MOVIE
                VOD_EXTENSIONS.any { path.endsWith(it) } -> ContentKind.MOVIE
                else -> ContentKind.LIVE
            }
        }
    }
}
