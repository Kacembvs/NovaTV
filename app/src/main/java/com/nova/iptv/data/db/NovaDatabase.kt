package com.nova.iptv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.ProfileType

class Converters {
    @TypeConverter fun kindToString(value: ContentKind): String = value.name
    @TypeConverter fun stringToKind(value: String): ContentKind = ContentKind.valueOf(value)
    @TypeConverter fun typeToString(value: ProfileType): String = value.name
    @TypeConverter fun stringToType(value: String): ProfileType = ProfileType.valueOf(value)
}

@Database(
    entities = [
        ProfileEntity::class,
        ContentEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        EpgProgrammeEntity::class,
        RatingEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class NovaDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun contentDao(): ContentDao
    abstract fun libraryDao(): LibraryDao
    abstract fun epgDao(): EpgDao

    companion object {
        /** v2: XMLTV guide (custom EPG URL, EPG sync status, programmes table). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `profiles` ADD COLUMN `customEpgUrl` TEXT")
                db.execSQL("ALTER TABLE `profiles` ADD COLUMN `epgSyncedAt` INTEGER")
                db.execSQL("ALTER TABLE `profiles` ADD COLUMN `epgError` TEXT")
                db.execSQL(EPG_TABLE_SQL)
                db.execSQL(EPG_INDEX_SQL)
            }
        }

        const val EPG_TABLE_SQL = "CREATE TABLE IF NOT EXISTS `epg_programmes` (`profileId` INTEGER NOT NULL, " +
            "`channelId` TEXT NOT NULL, `startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
            "`description` TEXT, PRIMARY KEY(`profileId`, `channelId`, `startMs`), FOREIGN KEY(`profileId`) " +
            "REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        const val EPG_INDEX_SQL =
            "CREATE INDEX IF NOT EXISTS `index_epg_programmes_profileId_endMs` ON `epg_programmes` (`profileId`, `endMs`)"

        /** v3: likes/dislikes, provider "added" date (Nouveautés), series link in history (Séries en cours). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `contents` ADD COLUMN `addedAt` INTEGER")
                db.execSQL("ALTER TABLE `history` ADD COLUMN `parentKey` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ratings` (`profileId` INTEGER NOT NULL, `itemKey` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `title` TEXT NOT NULL, `logo` TEXT, `value` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `itemKey`), FOREIGN KEY(`profileId`) " +
                        "REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun create(context: Context): NovaDatabase =
            Room.databaseBuilder(context, NovaDatabase::class.java, "nova.db")
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
