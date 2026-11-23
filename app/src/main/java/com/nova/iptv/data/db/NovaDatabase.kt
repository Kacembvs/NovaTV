package com.nova.iptv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.domain.ProfileType

class Converters {
    @TypeConverter fun kindToString(value: ContentKind): String = value.name
    @TypeConverter fun stringToKind(value: String): ContentKind = ContentKind.valueOf(value)
    @TypeConverter fun typeToString(value: ProfileType): String = value.name
    @TypeConverter fun stringToType(value: String): ProfileType = ProfileType.valueOf(value)
}

@Database(
    entities = [ProfileEntity::class, ContentEntity::class, FavoriteEntity::class, HistoryEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class NovaDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun contentDao(): ContentDao
    abstract fun libraryDao(): LibraryDao

    companion object {
        fun create(context: Context): NovaDatabase =
            Room.databaseBuilder(context, NovaDatabase::class.java, "nova.db").build()
    }
}
