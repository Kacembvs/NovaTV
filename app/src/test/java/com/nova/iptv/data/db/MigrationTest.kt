package com.nova.iptv.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Builds real old-version databases from the exported schemas, then opens them with the current code. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun createDatabase(name: String, version: Int, seed: (SQLiteDatabase) -> Unit) {
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/com.nova.iptv.data.db.NovaDatabase/$version.json").readText())
            .getJSONObject("database")
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.insert("profiles", null, ContentValues().apply {
                put("name", "Mon IPTV")
                put("type", "M3U")
                put("url", "http://example.com/list.m3u")
                put("contentCount", 42)
                put("createdAt", 1L)
            })
            seed(db)
            db.version = version
        }
    }

    private fun open(name: String) = Room.databaseBuilder(context, NovaDatabase::class.java, name)
        .addMigrations(*NovaDatabase.ALL_MIGRATIONS)
        .allowMainThreadQueries()
        .build()

    @Test
    fun migrationFrom1KeepsProfilesAndPassesRoomValidation() = runBlocking {
        createDatabase("migration-v1.db", 1) {}
        val room = open("migration-v1.db")
        val profile = room.profileDao().get(1)!!
        assertEquals("Mon IPTV", profile.name)
        assertEquals(42, profile.contentCount)
        assertNull(profile.customEpgUrl)
        room.epgDao().insertAll(listOf(EpgProgrammeEntity(1, "tf1.fr", 0, 1000, "JT", null)))
        room.close()
    }

    @Test
    fun migrationFrom2KeepsHistoryAndAddsRatings() = runBlocking {
        createDatabase("migration-v2.db", 2) { db ->
            db.insert("history", null, ContentValues().apply {
                put("profileId", 1)
                put("itemKey", "movie/1.mp4")
                put("kind", "MOVIE")
                put("title", "Film")
                put("positionMs", 60_000)
                put("durationMs", 120_000)
                put("updatedAt", 5L)
            })
        }
        val room = open("migration-v2.db")
        val history = room.libraryDao().history(1, "movie/1.mp4")!!
        assertEquals(60_000, history.positionMs)
        assertNull(history.parentKey)
        room.libraryDao().setRating(RatingEntity(1, "movie/1.mp4", com.nova.iptv.domain.ContentKind.MOVIE, "Film", null, 1))
        assertEquals(1, room.libraryDao().rating(1, "movie/1.mp4").first())
        room.close()
    }

    @Test
    fun migrationFrom3AddsCustomGroups() = runBlocking {
        createDatabase("migration-v3.db", 3) {}
        val room = open("migration-v3.db")
        val groupId = room.customGroupDao().insert(CustomGroupEntity(profileId = 1, name = "Sport"))
        room.customGroupDao().addItem(CustomGroupItemEntity(groupId, "live/1.ts"))
        assertEquals(1, room.customGroupDao().observe(1).first().single().count)
        room.close()
    }
}
