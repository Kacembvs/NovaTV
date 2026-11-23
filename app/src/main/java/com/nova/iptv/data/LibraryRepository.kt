package com.nova.iptv.data

import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.data.db.FavoriteEntity
import com.nova.iptv.data.db.HistoryEntity
import com.nova.iptv.data.db.NovaDatabase
import com.nova.iptv.data.db.RatingEntity
import com.nova.iptv.domain.ContentKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

/** Favourites, likes and viewing history of a profile, plus what is derived from them. */
class LibraryRepository(private val db: NovaDatabase) {
    private val library get() = db.libraryDao()

    fun isFavorite(profileId: Long, key: String): Flow<Boolean> = library.favoriteKeys(profileId).map { key in it }

    fun rating(profileId: Long, key: String): Flow<Int?> = library.rating(profileId, key)

    suspend fun setFavorite(profileId: Long, key: String, kind: ContentKind, title: String, logo: String?, favorite: Boolean) {
        if (favorite) library.addFavorite(FavoriteEntity(profileId, key, kind, title, logo))
        else library.removeFavorite(profileId, key)
    }

    /** Applies a like (+1) or dislike (-1); choosing the current value again removes it. */
    suspend fun rate(profileId: Long, key: String, kind: ContentKind, title: String, logo: String?, value: Int, current: Int?) {
        if (current == value) library.clearRating(profileId, key)
        else library.setRating(RatingEntity(profileId, key, kind, title, logo, value))
    }

    fun liked(profileId: Long): Flow<List<RatingEntity>> =
        library.ratings(profileId).map { list -> list.filter { it.value > 0 }.sortedByDescending { it.updatedAt }.take(20) }

    /** One entry per series, most recent first. */
    fun seriesInProgress(profileId: Long): Flow<List<HistoryEntity>> =
        library.seriesHistory(profileId).map { list -> list.distinctBy { it.parentKey }.take(20) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun recommendations(profileId: Long): Flow<List<ContentEntity>> =
        combine(library.watched(profileId), library.favorites(profileId), library.ratings(profileId)) { watched, favorites, ratings ->
            Triple(watched, favorites.filter { it.kind != ContentKind.LIVE }, ratings)
        }.mapLatest { (watched, favorites, ratings) -> compute(profileId, watched, favorites, ratings) }

    private suspend fun compute(
        profileId: Long,
        watched: List<HistoryEntity>,
        favorites: List<FavoriteEntity>,
        ratings: List<RatingEntity>,
    ): List<ContentEntity> {
        val seen = buildSet {
            watched.forEach { add(it.itemKey); it.parentKey?.let(::add) }
            favorites.forEach { add(it.itemKey) }
            ratings.forEach { add(it.itemKey) }
        }
        if (seen.isEmpty()) return emptyList()
        val contents = db.contentDao().byRefs(profileId, seen.take(900))
        val groupOf = contents.associate { it.streamRef to it.groupTitle }

        val signals = ArrayList<Recommender.Signal>()
        watched.forEach { h ->
            val group = groupOf[h.parentKey ?: h.itemKey] ?: return@forEach
            val finished = h.durationMs > 0 && h.positionMs >= h.durationMs * 0.9
            signals += Recommender.Signal(group, if (finished) Recommender.FINISHED else Recommender.WATCHED)
        }
        favorites.forEach { f -> groupOf[f.itemKey]?.let { signals += Recommender.Signal(it, Recommender.FAVORITE) } }
        ratings.forEach { r ->
            groupOf[r.itemKey]?.let { signals += Recommender.Signal(it, if (r.value > 0) Recommender.LIKE else Recommender.DISLIKE) }
        }
        val scores = Recommender.groupScores(signals)
        if (scores.isEmpty()) return emptyList()
        val candidates = db.contentDao().recommendationCandidates(profileId, scores.keys.toList())
        return Recommender.pick(candidates, scores, exclude = seen)
    }
}
