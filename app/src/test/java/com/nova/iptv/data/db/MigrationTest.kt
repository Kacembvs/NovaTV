package com.nova.iptv.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Builds a real version-1 database from the exported schema, then opens it with the current code. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    @Test
    fun migrationFrom1To2KeepsProfilesAndPassesRoomValidation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)

        val schema = JSONObject(File("schemas/com.nova.iptv.data.db.NovaDatabase/1.json").readText())
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
            db.version = 1
        }

        val room = Room.databaseBuilder(context, NovaDatabase::class.java, name)
            .addMigrations(NovaDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        val profile = room.profileDao().get(1)!!
        assertEquals("Mon IPTV", profile.name)
        assertEquals(42, profile.contentCount)
        assertNull(profile.customEpgUrl)
        room.epgDao().insertAll(listOf(EpgProgrammeEntity(1, "tf1.fr", 0, 1000, "JT", null)))
        room.close()
    }
}
