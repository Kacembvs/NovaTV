package com.nova.iptv.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nova.iptv.core.SecretBox
import com.nova.iptv.data.db.FavoriteEntity
import com.nova.iptv.data.db.HistoryEntity
import com.nova.iptv.data.db.NovaDatabase
import com.nova.iptv.data.db.ProfileEntity
import com.nova.iptv.data.db.RatingEntity
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.ProfileType
import com.nova.iptv.ui.theme.TvInterface
import com.nova.iptv.ui.theme.NovaThemeId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = SettingsStore(context)

    /** Stand-in for the Android Keystore cipher (not available under Robolectric). */
    private val box = object : SecretBox {
        override fun encrypt(plain: String) = "enc:$plain"
        override fun decrypt(encoded: String) = encoded.removePrefix("enc:")
    }

    private fun newDb() = Room.inMemoryDatabaseBuilder(context, NovaDatabase::class.java).allowMainThreadQueries().build()

    private suspend fun seed(db: NovaDatabase): Long {
        val id = db.profileDao().insert(
            ProfileEntity(name = "Mon Xtream", type = ProfileType.XTREAM, url = "http://host:80", username = "bob", encryptedPassword = "enc:s3cret")
        )
        db.libraryDao().addFavorite(FavoriteEntity(id, "live/1.ts", ContentKind.LIVE, "TF1", null))
        db.libraryDao().saveHistory(HistoryEntity(id, "movie/9.mkv", ContentKind.MOVIE, "Film", null, null, 60_000, 120_000, 10L))
        db.libraryDao().setRating(RatingEntity(id, "movie/9.mkv", ContentKind.MOVIE, "Film", null, 1))
        val groupId = db.customGroupDao().insert(com.nova.iptv.data.db.CustomGroupEntity(profileId = id, name = "Sport"))
        db.customGroupDao().addItem(com.nova.iptv.data.db.CustomGroupItemEntity(groupId, "live/1.ts"))
        return id
    }

    @Test
    fun applyingCinemaAppliesItsColoursAndStillAllowsAPersonalPalette() = runBlocking {
        settings.setTheme(NovaThemeId.PURE_LIGHT)
        settings.applyTvTheme(TvInterface.CINEMA)
        val saved = SettingsStore(context).theme.first()
        assertEquals(TvInterface.CINEMA, saved.tvInterface)
        assertEquals(NovaThemeId.STREAM_RED, saved.theme)
        settings.setTheme(NovaThemeId.CUSTOM, 0xFF2ED47A.toInt())
        assertEquals(TvInterface.CINEMA, settings.theme.first().tvInterface)
        settings.applyTvTheme(TvInterface.CINEMA)
        assertEquals(NovaThemeId.STREAM_RED, settings.theme.first().theme)
    }

    @Test
    fun tvThemeBackupRestoresTheSavedPaletteRatherThanReapplyingItsDefault() = runBlocking {
        settings.setTheme(NovaThemeId.STREAM_RED)
        settings.applyTvTheme(TvInterface.PLUS)
        assertEquals(NovaThemeId.MONOCHROME, settings.theme.first().theme)
        assertEquals(TvInterface.PLUS, SettingsStore(context).theme.first().tvInterface)
        settings.setTheme(NovaThemeId.CINEMA_GOLD)
        assertEquals(TvInterface.PLUS, settings.theme.first().tvInterface)
        val source = newDb()
        val target = newDb()
        try {
            val json = BackupManager(source, box, settings).export(null)
            settings.setTvInterface(TvInterface.GUIDE)
            assertEquals(NovaThemeId.CINEMA_GOLD, settings.theme.first().theme)
            BackupManager(target, box, settings).restore(json, null)
            assertEquals(TvInterface.PLUS, settings.theme.first().tvInterface)
            assertEquals(NovaThemeId.CINEMA_GOLD, settings.theme.first().theme)
            val legacy = org.json.JSONObject(json)
            legacy.getJSONObject("settings").remove("tvInterface")
            settings.setTvInterface(TvInterface.GUIDE)
            BackupManager(target, box, settings).restore(legacy.toString(), null)
            assertEquals(TvInterface.GUIDE, settings.theme.first().tvInterface)
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun exportThenRestoreWithCodeRestoresEverythingAndEncryptsPasswords() = runBlocking {
        val source = newDb()
        seed(source)
        val json = BackupManager(source, box, settings).export("1234")
        assertFalse("password never in clear", "s3cret" in json)

        val target = newDb()
        val result = BackupManager(target, box, settings).restore(json, "1234")
        assertEquals(1, result.profilesAdded)
        assertEquals(0, result.missingPasswords)
        assertEquals(1, result.favorites)
        assertEquals(1, result.history)
        assertEquals(1, result.ratings)
        assertEquals(1, result.customGroups)
        val restored = target.profileDao().all().single()
        assertEquals("enc:s3cret", restored.encryptedPassword)
        assertEquals(60_000, target.libraryDao().history(restored.id, "movie/9.mkv")!!.positionMs)
        assertEquals(1, target.customGroupDao().observe(restored.id).first().single().count)

        // Restoring twice reuses the existing profile instead of duplicating it.
        val again = BackupManager(target, box, settings).restore(json, "1234")
        assertEquals(0, again.profilesAdded)
        assertEquals(1, again.profilesMerged)
        assertEquals(1, target.profileDao().all().size)
    }

    @Test
    fun wrongOrMissingCodeIsRejected() = runBlocking {
        val source = newDb()
        seed(source)
        val json = BackupManager(source, box, settings).export("1234")
        val manager = BackupManager(newDb(), box, settings)
        assertTrue(runCatching { manager.restore(json, "0000") }.exceptionOrNull() is BackupManager.WrongCodeException)
        assertTrue(runCatching { manager.restore(json, null) }.exceptionOrNull() is BackupManager.CodeRequiredException)
    }

    @Test
    fun backupWithoutCodeOmitsPasswords() = runBlocking {
        val source = newDb()
        seed(source)
        val json = BackupManager(source, box, settings).export(null)
        assertFalse("s3cret" in json)
        val result = BackupManager(newDb(), box, settings).restore(json, null)
        assertEquals(1, result.missingPasswords)
    }
}
