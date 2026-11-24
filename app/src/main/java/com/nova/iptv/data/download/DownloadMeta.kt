package com.nova.iptv.data.download

import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.PlayableItem
import org.json.JSONObject

/** What the downloads screen needs to show and replay an item without any network access. */
data class DownloadMeta(
    val profileId: Long,
    val ref: String,
    val title: String,
    val kind: ContentKind,
    val logo: String? = null,
    val subtitle: String? = null,
    /** Series reference, so episodes are grouped under their series. */
    val parentKey: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
) {
    val id get() = downloadId(profileId, ref)

    fun toPlayable() = PlayableItem(ref, title, kind, logo, subtitle, parentKey)

    fun toBytes(): ByteArray = JSONObject().apply {
        put("profileId", profileId)
        put("ref", ref)
        put("title", title)
        put("kind", kind.name)
        putOpt("logo", logo)
        putOpt("subtitle", subtitle)
        putOpt("parentKey", parentKey)
        putOpt("season", season)
        putOpt("episode", episode)
    }.toString().toByteArray(Charsets.UTF_8)

    companion object {
        fun downloadId(profileId: Long, ref: String) = "$profileId|$ref"

        fun from(profileId: Long, item: PlayableItem, season: Int? = null, episode: Int? = null) = DownloadMeta(
            profileId, item.ref, item.title, item.kind, item.logo, item.subtitle, item.parentKey,
            season ?: item.subtitle?.let(::parseSeason), episode ?: item.subtitle?.let(::parseEpisode),
        )

        fun fromBytes(bytes: ByteArray): DownloadMeta? = runCatching {
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            DownloadMeta(
                profileId = json.getLong("profileId"),
                ref = json.getString("ref"),
                title = json.getString("title"),
                kind = ContentKind.valueOf(json.getString("kind")),
                logo = json.optString("logo").ifEmpty { null },
                subtitle = json.optString("subtitle").ifEmpty { null },
                parentKey = json.optString("parentKey").ifEmpty { null },
                season = json.optInt("season", -1).takeIf { it >= 0 },
                episode = json.optInt("episode", -1).takeIf { it >= 0 },
            )
        }.getOrNull()

        // Episode labels look like "S2 · É5 — Titre".
        private fun parseSeason(label: String) = Regex("""S(\d+)""").find(label)?.groupValues?.get(1)?.toIntOrNull()
        private fun parseEpisode(label: String) = Regex("""É(\d+)""").find(label)?.groupValues?.get(1)?.toIntOrNull()
    }
}
