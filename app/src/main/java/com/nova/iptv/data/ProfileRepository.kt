package com.nova.iptv.data

import com.nova.iptv.core.CredentialCipher
import com.nova.iptv.data.db.ProfileDao
import com.nova.iptv.data.db.ProfileEntity
import com.nova.iptv.data.source.XtreamClient
import com.nova.iptv.data.source.XtreamUrls
import com.nova.iptv.domain.ProfileType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

class ProfileRepository(
    private val dao: ProfileDao,
    private val cipher: CredentialCipher,
    private val settings: SettingsStore,
) {
    val profiles: Flow<List<ProfileEntity>> = dao.observeAll()

    /** The selected profile, falling back to the first one when nothing is selected. */
    val activeProfile: Flow<ProfileEntity?> =
        combine(profiles, settings.activeProfileId) { list, id -> list.firstOrNull { it.id == id } ?: list.firstOrNull() }
            .distinctUntilChanged()

    suspend fun get(id: Long): ProfileEntity? = dao.get(id)

    /** Creates or updates a profile. A blank password on update keeps the stored one. Returns the profile id. */
    suspend fun save(
        id: Long?,
        type: ProfileType,
        name: String,
        url: String,
        username: String,
        password: String,
    ): Long {
        val existing = id?.let { dao.get(it) }
        val encryptedPassword = when {
            type == ProfileType.M3U -> null
            password.isNotEmpty() -> cipher.encrypt(password)
            else -> existing?.encryptedPassword
        }
        val profile = ProfileEntity(
            id = existing?.id ?: 0,
            name = name.trim(),
            type = type,
            url = url.trim(),
            username = if (type == ProfileType.XTREAM) username.trim() else null,
            encryptedPassword = encryptedPassword,
            epgUrl = existing?.epgUrl,
            lastSyncAt = existing?.lastSyncAt,
            lastSyncError = existing?.lastSyncError,
            contentCount = existing?.contentCount ?: 0,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        return if (existing == null) {
            dao.insert(profile).also { settings.setActiveProfile(it) }
        } else {
            dao.update(profile)
            profile.id
        }
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
        if (settings.activeProfileId.first() == id) {
            settings.setActiveProfile(profiles.first().firstOrNull()?.id)
        }
    }

    suspend fun setActive(id: Long) = settings.setActiveProfile(id)

    fun credentials(profile: ProfileEntity) = XtreamClient.Credentials(
        baseUrl = profile.url,
        username = profile.username.orEmpty(),
        password = profile.encryptedPassword?.let(cipher::decrypt).orEmpty(),
    )

    /** Resolves stored references to playable URLs (credentials are only injected here, in memory). */
    fun streamUrls(profile: ProfileEntity, refs: List<String>): List<String> = when (profile.type) {
        ProfileType.M3U -> refs
        ProfileType.XTREAM -> {
            val c = credentials(profile)
            refs.map { XtreamUrls.streamUrl(c.baseUrl, c.username, c.password, it) }
        }
    }
}
