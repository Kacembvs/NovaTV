package com.nova.iptv.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN hashing: PBKDF2 with a random salt; the PIN itself is never stored. */
object PinHasher {
    private const val ITERATIONS = 20_000

    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt.hexToBytes(), ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    fun matches(pin: String, salt: String, expected: String): Boolean =
        MessageDigest.isEqual(hash(pin, salt).toByteArray(), expected.toByteArray())

    fun isValidPin(pin: String) = pin.length in 4..8 && pin.all { it.isDigit() }
}

/**
 * Parental control: a PIN hides locked categories and can protect the settings.
 * Entering the PIN unlocks everything until [relock] or the app process ends.
 */
class ParentalControl(private val settings: SettingsStore) {
    val unlocked = MutableStateFlow(false)
    val state: Flow<ParentalSettings> = settings.parental

    /** Categories to hide for a profile right now (empty when no PIN or unlocked). */
    fun hiddenGroups(profileId: Long): Flow<Set<String>> = combine(settings.parental, unlocked) { p, open ->
        if (!p.pinSet || open) emptySet() else groupsOf(p, profileId)
    }.distinctUntilChanged()

    /** Locked categories of a profile, whatever the unlock state (for the settings screen). */
    fun lockedGroups(p: ParentalSettings, profileId: Long): Set<String> = groupsOf(p, profileId)

    /** Settings tab is behind the PIN. */
    fun settingsBlocked(): Flow<Boolean> = combine(settings.parental, unlocked) { p, open -> p.pinSet && p.lockSettings && !open }

    suspend fun verify(pin: String): Boolean {
        val p = settings.parental.first()
        val ok = p.pinSet && PinHasher.matches(pin, p.pinSalt!!, p.pinHash!!)
        if (ok) unlocked.value = true
        return ok
    }

    suspend fun setPin(pin: String) {
        require(PinHasher.isValidPin(pin))
        val salt = PinHasher.newSalt()
        settings.setPin(PinHasher.hash(pin, salt), salt)
        unlocked.value = true
    }

    suspend fun removePin() {
        settings.setPin(null, null)
        unlocked.value = false
    }

    fun relock() {
        unlocked.value = false
    }

    suspend fun setGroupLocked(profileId: Long, group: String, locked: Boolean) {
        settings.setGroupLocked(key(profileId, group), locked)
    }

    suspend fun setLockSettings(enabled: Boolean) = settings.setLockSettings(enabled)

    private fun groupsOf(p: ParentalSettings, profileId: Long): Set<String> {
        val prefix = "$profileId$SEP"
        return p.lockedGroups.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet()
    }

    companion object {
        private const val SEP = '\u001F'
        fun key(profileId: Long, group: String) = "$profileId$SEP$group"
    }
}
