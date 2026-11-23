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
        return id
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
        val restored = target.profileDao().all().single()
        assertEquals("enc:s3cret", restored.encryptedPassword)
        assertEquals(60_000, target.libraryDao().history(restored.id, "movie/9.mkv")!!.positionMs)

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
