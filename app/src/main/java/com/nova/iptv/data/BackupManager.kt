package com.nova.iptv.data

import android.util.Base64
import androidx.room.withTransaction
import com.nova.iptv.core.SecretBox
import com.nova.iptv.data.db.CustomGroupEntity
import com.nova.iptv.data.db.CustomGroupItemEntity
import com.nova.iptv.data.db.FavoriteEntity
import com.nova.iptv.data.db.HistoryEntity
import com.nova.iptv.data.db.NovaDatabase
import com.nova.iptv.data.db.ProfileEntity
import com.nova.iptv.data.db.RatingEntity
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.ProfileType
import com.nova.iptv.ui.theme.NovaThemeId
import com.nova.iptv.ui.theme.TvInterface
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Exports / restores profiles, favourites, likes, history and settings as JSON.
 * Catalogues are not exported (they are re-downloaded). Passwords are only exported when the user
 * gives a backup code, encrypted with a key derived from it (PBKDF2 + AES-GCM); never in clear.
 */
class BackupManager(
    private val db: NovaDatabase,
    private val box: SecretBox,
    private val settings: SettingsStore,
) {
    data class RestoreResult(
        val profilesAdded: Int,
        val profilesMerged: Int,
        val favorites: Int,
        val history: Int,
        val ratings: Int,
        /** Profiles to synchronise after the restore. */
        val newProfileIds: List<Long>,
        /** Xtream profiles restored without password (backup made without code). */
        val missingPasswords: Int,
        val customGroups: Int = 0,
    )

    class WrongCodeException : IOException("Code de sauvegarde incorrect.")
    class CodeRequiredException : IOException("Cette sauvegarde contient des mots de passe : indiquez son code.")

    suspend fun export(code: String?): String {
        val withPasswords = !code.isNullOrEmpty()
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = if (withPasswords) deriveKey(code!!, salt) else null
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", System.currentTimeMillis())

        val playback = settings.playback.first()
        val theme = settings.theme.first()
        root.put(
            "settings",
            JSONObject()
                .put("autoplayNext", playback.autoplayNext)
                .put("countdownSeconds", playback.countdownSeconds)
                .put("gesturesEnabled", playback.gesturesEnabled)
                .put("freeRotation", playback.freeRotation)
                .put("pictureInPicture", playback.pictureInPicture)
                .put("theme", theme.theme.name)
                .put("tvInterface", theme.tvInterface.name)
                .putOpt("customAccent", theme.customAccent),
        )
        if (key != null) {
            root.put("security", JSONObject().put("salt", b64(salt)).put("check", seal(key, CHECK_VALUE)))
        }

        val profiles = JSONArray()
        db.profileDao().all().forEach { p ->
            val password = p.encryptedPassword?.let { runCatching { box.decrypt(it) }.getOrNull() }
            profiles.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("type", p.type.name)
                    .put("url", p.url)
                    .putOpt("username", p.username)
                    .putOpt("password", if (key != null && password != null) seal(key, password) else null)
                    .putOpt("customEpgUrl", p.customEpgUrl)
                    .put("createdAt", p.createdAt),
            )
        }
        root.put("profiles", profiles)

        val library = db.libraryDao()
        root.put("favorites", JSONArray().apply {
            library.allFavorites().forEach { f ->
                put(item(f.profileId, f.itemKey, f.kind, f.title, f.logo).put("createdAt", f.createdAt))
            }
        })
        root.put("history", JSONArray().apply {
            library.allHistory().forEach { h ->
                put(
                    item(h.profileId, h.itemKey, h.kind, h.title, h.logo)
                        .putOpt("subtitle", h.subtitle)
                        .put("positionMs", h.positionMs)
                        .put("durationMs", h.durationMs)
                        .put("updatedAt", h.updatedAt)
                        .putOpt("parentKey", h.parentKey),
                )
            }
        })
        root.put("ratings", JSONArray().apply {
            library.allRatings().forEach { r ->
                put(item(r.profileId, r.itemKey, r.kind, r.title, r.logo).put("value", r.value).put("updatedAt", r.updatedAt))
            }
        })
        val groupItems = db.customGroupDao().allItems().groupBy { it.groupId }
        root.put("customGroups", JSONArray().apply {
            db.customGroupDao().all().forEach { g ->
                put(
                    JSONObject()
                        .put("profileId", g.profileId)
                        .put("name", g.name)
                        .put("createdAt", g.createdAt)
                        .put("items", JSONArray().apply { groupItems[g.id].orEmpty().forEach { put(it.itemKey) } }),
                )
            }
        })
        return root.toString(2)
    }

    /** Merges the backup into the current data: existing profiles (same type / address / user) are reused. */
    suspend fun restore(json: String, code: String?): RestoreResult {
        val root = runCatching { JSONObject(json) }.getOrNull()
        if (root == null || root.optString("format") != FORMAT) throw IOException("Ce fichier n'est pas une sauvegarde NOVA IPTV.")
        if (root.optInt("version") > VERSION) throw IOException("Sauvegarde créée par une version plus récente de NOVA.")

        val key: SecretKey? = root.optJSONObject("security")?.let { security ->
            if (code.isNullOrEmpty()) throw CodeRequiredException()
            val derived = deriveKey(code, unb64(security.getString("salt")))
            val check = runCatching { open(derived, security.getString("check")) }.getOrNull()
            if (check != CHECK_VALUE) throw WrongCodeException()
            derived
        }

        var added = 0
        var merged = 0
        var missingPasswords = 0
        val newIds = ArrayList<Long>()
        val idMap = HashMap<Long, Long>()
        var favorites = 0
        var history = 0
        var ratings = 0
        var customGroups = 0

        db.withTransaction {
            val dao = db.profileDao()
            val existing = dao.all()
            val profiles = root.optJSONArray("profiles") ?: JSONArray()
            for (i in 0 until profiles.length()) {
                val p = profiles.getJSONObject(i)
                val type = runCatching { ProfileType.valueOf(p.getString("type")) }.getOrNull() ?: continue
                val url = p.getString("url").trim()
                val username = p.optStringOrNull("username")
                val match = existing.firstOrNull { it.type == type && it.url.trim() == url && it.username == username }
                if (match != null) {
                    idMap[p.getLong("id")] = match.id
                    merged++
                    continue
                }
                val password = p.optStringOrNull("password")?.let { sealed -> key?.let { open(it, sealed) } }
                if (type == ProfileType.XTREAM && password == null) missingPasswords++
                val id = dao.insert(
                    ProfileEntity(
                        name = p.getString("name"),
                        type = type,
                        url = url,
                        username = username,
                        encryptedPassword = password?.let(box::encrypt),
                        customEpgUrl = p.optStringOrNull("customEpgUrl"),
                        createdAt = p.optLong("createdAt", System.currentTimeMillis()),
                    )
                )
                idMap[p.getLong("id")] = id
                newIds += id
                added++
            }

            val library = db.libraryDao()
            root.optJSONArray("favorites").forEachObject { f ->
                val profileId = idMap[f.getLong("profileId")] ?: return@forEachObject
                library.addFavorite(
                    FavoriteEntity(profileId, f.getString("itemKey"), f.kind(), f.getString("title"), f.optStringOrNull("logo"), f.optLong("createdAt"))
                )
                favorites++
            }
            root.optJSONArray("history").forEachObject { h ->
                val profileId = idMap[h.getLong("profileId")] ?: return@forEachObject
                val itemKey = h.getString("itemKey")
                val updatedAt = h.optLong("updatedAt")
                // Never overwrite a more recent local progress.
                val local = library.history(profileId, itemKey)
                if (local != null && local.updatedAt >= updatedAt) return@forEachObject
                library.saveHistory(
                    HistoryEntity(
                        profileId = profileId,
                        itemKey = itemKey,
                        kind = h.kind(),
                        title = h.getString("title"),
                        subtitle = h.optStringOrNull("subtitle"),
                        logo = h.optStringOrNull("logo"),
                        positionMs = h.optLong("positionMs"),
                        durationMs = h.optLong("durationMs"),
                        updatedAt = updatedAt,
                        parentKey = h.optStringOrNull("parentKey"),
                    )
                )
                history++
            }
            root.optJSONArray("ratings").forEachObject { r ->
                val profileId = idMap[r.getLong("profileId")] ?: return@forEachObject
                library.setRating(
                    RatingEntity(
                        profileId, r.getString("itemKey"), r.kind(), r.getString("title"), r.optStringOrNull("logo"),
                        r.getInt("value"), r.optLong("updatedAt"),
                    )
                )
                ratings++
            }
            val groups = db.customGroupDao()
            root.optJSONArray("customGroups").forEachObject { g ->
                val profileId = idMap[g.getLong("profileId")] ?: return@forEachObject
                val name = g.getString("name")
                val groupId = groups.findByName(profileId, name)?.id
                    ?: groups.insert(CustomGroupEntity(profileId = profileId, name = name, createdAt = g.optLong("createdAt")))
                val items = g.optJSONArray("items") ?: JSONArray()
                for (i in 0 until items.length()) groups.addItem(CustomGroupItemEntity(groupId, items.getString(i)))
                customGroups++
            }
        }

        root.optJSONObject("settings")?.let { s ->
            settings.setPlayback(
                PlaybackSettings(
                    autoplayNext = s.optBoolean("autoplayNext", true),
                    countdownSeconds = s.optInt("countdownSeconds", 20),
                    gesturesEnabled = s.optBoolean("gesturesEnabled", true),
                    freeRotation = s.optBoolean("freeRotation", false),
                    pictureInPicture = s.optBoolean("pictureInPicture", true),
                )
            )
            val theme = NovaThemeId.entries.firstOrNull { it.name == s.optString("theme") }
            if (theme != null) settings.setTheme(theme, if (s.has("customAccent")) s.getInt("customAccent") else null)
            TvInterface.entries.firstOrNull { it.name == s.optString("tvInterface") }?.let {
                settings.setTvInterface(it)
            }
        }
        return RestoreResult(added, merged, favorites, history, ratings, newIds, missingPasswords, customGroups)
    }

    private fun item(profileId: Long, key: String, kind: ContentKind, title: String, logo: String?) =
        JSONObject().put("profileId", profileId).put("itemKey", key).put("kind", kind.name).put("title", title).putOpt("logo", logo)

    private fun JSONObject.kind() = ContentKind.valueOf(getString("kind"))

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).ifEmpty { null }

    private inline fun JSONArray?.forEachObject(block: (JSONObject) -> Unit) {
        if (this == null) return
        for (i in 0 until length()) block(getJSONObject(i))
    }

    private fun deriveKey(code: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(code.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun seal(key: SecretKey, plain: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        return b64(iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    private fun open(key: SecretKey, sealed: String): String {
        val data = unb64(sealed)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, 12)) }
        return String(cipher.doFinal(data, 12, data.size - 12), Charsets.UTF_8)
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(text: String) = Base64.decode(text, Base64.NO_WRAP)

    companion object {
        const val FORMAT = "nova-iptv-backup"
        const val VERSION = 1
        private const val CHECK_VALUE = "NOVA"
        private const val PBKDF2_ITERATIONS = 60_000
    }
}
