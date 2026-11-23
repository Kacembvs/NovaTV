package com.nova.iptv.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.PlayableItem
import com.nova.iptv.domain.ProfileType

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: ProfileType,
    /** M3U: playlist URL. Xtream: server base URL. */
    val url: String,
    val username: String? = null,
    /** Encrypted with the Android Keystore, never stored in clear. */
    val encryptedPassword: String? = null,
    val epgUrl: String? = null,
    val lastSyncAt: Long? = null,
    val lastSyncError: String? = null,
    val contentCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** User-provided XMLTV URL; overrides the playlist header / Xtream xmltv.php. */
    val customEpgUrl: String? = null,
    val epgSyncedAt: Long? = null,
    val epgError: String? = null,
)

@Entity(
    tableName = "epg_programmes",
    primaryKeys = ["profileId", "channelId", "startMs"],
    foreignKeys = [ForeignKey(
        entity = ProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("profileId", "endMs")],
)
data class EpgProgrammeEntity(
    val profileId: Long,
    /** Matches [ContentEntity.tvgId]. */
    val channelId: String,
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val description: String?,
)

data class IdName(val id: Long, val name: String)

@Entity(
    tableName = "contents",
    foreignKeys = [ForeignKey(
        entity = ProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("profileId", "kind", "sortIndex"),
        Index("profileId", "kind", "groupTitle"),
        Index("profileId", "streamRef"),
    ],
)
data class ContentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val kind: ContentKind,
    val name: String,
    val groupTitle: String,
    val logo: String?,
    val streamRef: String,
    val tvgId: String? = null,
    val sortIndex: Int,
    val year: String? = null,
    val rating: String? = null,
    val description: String? = null,
)

fun ContentEntity.toPlayable() = PlayableItem(
    ref = streamRef,
    title = name,
    kind = if (kind == ContentKind.SERIES) ContentKind.EPISODE else kind,
    logo = logo,
    subtitle = groupTitle,
)

@Entity(
    tableName = "favorites",
    primaryKeys = ["profileId", "itemKey"],
    foreignKeys = [ForeignKey(
        entity = ProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class FavoriteEntity(
    val profileId: Long,
    val itemKey: String,
    val kind: ContentKind,
    val title: String,
    val logo: String?,
    val createdAt: Long = System.currentTimeMillis(),
)

fun FavoriteEntity.toPlayable() = PlayableItem(itemKey, title, kind, logo)

@Entity(
    tableName = "history",
    primaryKeys = ["profileId", "itemKey"],
    foreignKeys = [ForeignKey(
        entity = ProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("profileId", "kind", "updatedAt")],
)
data class HistoryEntity(
    val profileId: Long,
    val itemKey: String,
    val kind: ContentKind,
    val title: String,
    val subtitle: String?,
    val logo: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

fun HistoryEntity.toPlayable() = PlayableItem(itemKey, title, kind, logo, subtitle)

data class GroupCount(val name: String, val count: Int)

data class KindCount(val kind: ContentKind, val count: Int)
