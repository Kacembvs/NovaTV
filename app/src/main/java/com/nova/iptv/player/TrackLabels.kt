package com.nova.iptv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import java.util.Locale

/** Human-readable (French) labels for audio / subtitle / video tracks and technical info. */
object TrackLabels {
    val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    fun speed(value: Float): String =
        if (value == 1f) "Normale (1x)" else (if (value % 1f == 0f) "${value.toInt()}x" else "${value}x").replace('.', ',')

    fun language(code: String?): String? {
        if (code.isNullOrBlank() || code == "und" || code == C.LANGUAGE_UNDETERMINED) return null
        val tag = code.substringBefore('-').lowercase()
        // HLS / MKV often use ISO 639-2 codes ("fra", "eng"): map them to a 2-letter locale.
        val locale = if (tag.length == 3) {
            Locale.getISOLanguages().map { Locale(it) }.firstOrNull { runCatching { it.isO3Language }.getOrNull() == tag }
                ?: Locale.forLanguageTag(code)
        } else {
            Locale.forLanguageTag(code)
        }
        val name = locale.getDisplayLanguage(Locale.FRENCH)
        return name.takeIf { it.isNotBlank() && !it.equals(tag, ignoreCase = true) }?.replaceFirstChar { it.uppercase() } ?: code
    }

    fun channels(count: Int): String? = when (count) {
        Format.NO_VALUE, 0 -> null
        1 -> "Mono"
        2 -> "Stéréo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "$count canaux"
    }

    fun codec(mimeType: String?): String? = when (mimeType) {
        null -> null
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_AC3 -> "Dolby Digital"
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital+"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD -> "DTS"
        MimeTypes.AUDIO_MPEG -> "MP3"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.VIDEO_H264 -> "H.264"
        MimeTypes.VIDEO_H265 -> "H.265 / HEVC"
        MimeTypes.VIDEO_AV1 -> "AV1"
        MimeTypes.VIDEO_VP9 -> "VP9"
        MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
        else -> mimeType.substringAfter('/').uppercase()
    }

    fun bitrate(bitsPerSecond: Int): String? =
        if (bitsPerSecond <= 0) null else String.format(Locale.FRANCE, "%.1f Mb/s", bitsPerSecond / 1_000_000f)

    fun audio(format: Format, index: Int): String =
        listOfNotNull(format.label, language(format.language), channels(format.channelCount), codec(format.sampleMimeType))
            .distinct().joinToString(" · ").ifBlank { "Piste ${index + 1}" }

    fun text(format: Format, index: Int): String {
        val base = listOfNotNull(format.label, language(format.language)).distinct().joinToString(" · ")
            .ifBlank { "Sous-titres ${index + 1}" }
        return if (format.selectionFlags and C.SELECTION_FLAG_FORCED != 0) "$base (forcés)" else base
    }

    fun video(format: Format): String =
        listOfNotNull(
            if (format.height > 0) "${format.height}p" else null,
            bitrate(format.bitrate),
        ).joinToString(" · ").ifBlank { "Qualité inconnue" }
}
