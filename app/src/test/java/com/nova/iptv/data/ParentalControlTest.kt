package com.nova.iptv.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.data.db.NovaDatabase
import com.nova.iptv.data.db.ProfileEntity
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.ProfileType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ParentalControlTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun pinIsHashedWithSaltAndVerified() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("1234", salt)
        assertNotEquals("1234", hash)
        assertTrue(PinHasher.matches("1234", salt, hash))
        assertFalse(PinHasher.matches("4321", salt, hash))
        assertNotEquals(hash, PinHasher.hash("1234", PinHasher.newSalt()))
        assertFalse(PinHasher.isValidPin("12a4"))
        assertFalse(PinHasher.isValidPin("123"))
    }

    @Test
    fun lockedGroupsAreHiddenUntilUnlocked() = runBlocking {
        val parental = ParentalControl(SettingsStore(context))
        parental.removePin()
        parental.setPin("2468")
        parental.setGroupLocked(1, "Adultes", true)
        parental.relock()
        assertEquals(setOf("Adultes"), parental.hiddenGroups(1).first())
        assertEquals(emptySet<String>(), parental.hiddenGroups(2).first())
        assertFalse(parental.verify("0000"))
        assertTrue(parental.verify("2468"))
        assertEquals(emptySet<String>(), parental.hiddenGroups(1).first())
        parental.removePin()
    }

    @Test
    fun concurrentCategorySelectionsAreAllSaved() = runBlocking {
        val parental = ParentalControl(SettingsStore(context))
        parental.removePin()
        parental.setPin("2468")
        val names = (1..20).map { "Catégorie $it" }
        names.map { name -> async { parental.setGroupLocked(1, name, true) } }.awaitAll()
        parental.relock()
        assertEquals(names.toSet(), parental.hiddenGroups(1).first())
        parental.removePin()
    }

    @Test
    fun searchExcludesHiddenGroupsAndAcceptsEmptyList() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, NovaDatabase::class.java).allowMainThreadQueries().build()
        val id = db.profileDao().insert(ProfileEntity(name = "P", type = ProfileType.M3U, url = "http://x"))
        db.contentDao().insertAll(
            listOf(
                ContentEntity(profileId = id, kind = ContentKind.LIVE, name = "Sport 1", groupTitle = "Sport", logo = null, streamRef = "a", sortIndex = 0),
                ContentEntity(profileId = id, kind = ContentKind.LIVE, name = "Sport X", groupTitle = "Adultes", logo = null, streamRef = "b", sortIndex = 1),
            )
        )
        assertEquals(2, db.contentDao().search(id, ContentKind.LIVE, "Sport", 10, emptyList()).size)
        assertEquals(listOf("a"), db.contentDao().search(id, ContentKind.LIVE, "Sport", 10, listOf("Adultes")).map { it.streamRef })
        db.close()
    }

    @Test
    fun refreshWorkerSucceedsWithoutProfiles() = runBlocking {
        val worker = TestListenableWorkerBuilder<RefreshWorker>(context).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }
}
