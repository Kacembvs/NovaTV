package com.nova.iptv.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nova.iptv.domain.ContentKind
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdAt")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE profiles SET lastSyncAt = :at, lastSyncError = NULL, contentCount = :count WHERE id = :id")
    suspend fun markSynced(id: Long, at: Long, count: Int)

    @Query("UPDATE profiles SET lastSyncError = :error WHERE id = :id")
    suspend fun markSyncFailed(id: Long, error: String)

    @Query("UPDATE profiles SET epgUrl = :epgUrl WHERE id = :id")
    suspend fun setEpgUrl(id: Long, epgUrl: String?)
}

@Dao
interface ContentDao {
    @Insert
    suspend fun insertAll(items: List<ContentEntity>)

    @Query("DELETE FROM contents WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)

    @Query(
        "SELECT * FROM contents WHERE profileId = :profileId AND kind = :kind " +
            "AND (:group IS NULL OR groupTitle = :group) AND name LIKE '%' || :query || '%' " +
            "ORDER BY sortIndex"
    )
    fun page(profileId: Long, kind: ContentKind, group: String?, query: String): PagingSource<Int, ContentEntity>

    @Query(
        "SELECT c.* FROM contents c INNER JOIN favorites f " +
            "ON f.profileId = c.profileId AND f.itemKey = c.streamRef " +
            "WHERE c.profileId = :profileId AND c.kind = :kind AND c.name LIKE '%' || :query || '%' " +
            "ORDER BY c.sortIndex"
    )
    fun pageFavorites(profileId: Long, kind: ContentKind, query: String): PagingSource<Int, ContentEntity>

    @Query(
        "SELECT groupTitle AS name, COUNT(*) AS count FROM contents " +
            "WHERE profileId = :profileId AND kind = :kind GROUP BY groupTitle ORDER BY MIN(sortIndex)"
    )
    fun groups(profileId: Long, kind: ContentKind): Flow<List<GroupCount>>

    @Query(
        "SELECT * FROM contents WHERE profileId = :profileId AND kind = :kind AND groupTitle = :group " +
            "ORDER BY sortIndex LIMIT 5000"
    )
    suspend fun listGroup(profileId: Long, kind: ContentKind, group: String): List<ContentEntity>

    @Query("SELECT kind, COUNT(*) AS count FROM contents WHERE profileId = :profileId GROUP BY kind")
    fun kindCounts(profileId: Long): Flow<List<KindCount>>

    @Query("SELECT * FROM contents WHERE profileId = :profileId AND streamRef = :ref LIMIT 1")
    suspend fun getByRef(profileId: Long, ref: String): ContentEntity?
}

@Dao
interface LibraryDao {
    @Query("SELECT itemKey FROM favorites WHERE profileId = :profileId")
    fun favoriteKeys(profileId: Long): Flow<List<String>>

    @Query("SELECT * FROM favorites WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun favorites(profileId: Long): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE profileId = :profileId AND itemKey = :key")
    suspend fun removeFavorite(profileId: Long, key: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHistory(entry: HistoryEntity)

    @Query("SELECT * FROM history WHERE profileId = :profileId AND itemKey = :key")
    suspend fun history(profileId: Long, key: String): HistoryEntity?

    @Query("SELECT * FROM history WHERE profileId = :profileId AND kind = 'LIVE' ORDER BY updatedAt DESC LIMIT 20")
    fun recentLive(profileId: Long): Flow<List<HistoryEntity>>

    @Query(
        "SELECT * FROM history WHERE profileId = :profileId AND kind != 'LIVE' AND positionMs > 10000 " +
            "AND (durationMs <= 0 OR positionMs < durationMs * 0.95) ORDER BY updatedAt DESC LIMIT 20"
    )
    fun continueWatching(profileId: Long): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE profileId = :profileId AND kind = 'EPISODE'")
    fun episodeHistory(profileId: Long): Flow<List<HistoryEntity>>
}
