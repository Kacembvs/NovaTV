package com.nova.iptv.data

import com.nova.iptv.data.db.ContentEntity

/**
 * Local, explainable recommendation engine (no server): categories are scored from the user's
 * likes, favourites and viewing history, then unseen titles are picked from the best categories.
 */
object Recommender {
    const val LIKE = 3
    const val DISLIKE = -4
    const val FAVORITE = 2
    const val WATCHED = 1
    const val FINISHED = 2

    data class Signal(val group: String, val weight: Int)

    fun groupScores(signals: List<Signal>): Map<String, Int> =
        signals.groupBy { it.group }
            .mapValues { (_, list) -> list.sumOf { it.weight } }
            .filterValues { it > 0 }

    /** Round-robin over categories ordered by score, so one genre does not fill the whole row. */
    fun pick(
        candidates: List<ContentEntity>,
        scores: Map<String, Int>,
        exclude: Set<String>,
        limit: Int = 20,
    ): List<ContentEntity> {
        val byGroup = candidates
            .filter { it.streamRef !in exclude && it.groupTitle in scores }
            .distinctBy { it.streamRef }
            .groupBy { it.groupTitle }
            .mapValues { it.value.toMutableList() }
        val order = scores.entries.sortedByDescending { it.value }.map { it.key }.filter { it in byGroup }
        val result = ArrayList<ContentEntity>(limit)
        while (result.size < limit) {
            var added = false
            for (group in order) {
                // Higher-scored groups get more slots per round.
                val share = (scores.getValue(group) / 3).coerceIn(1, 3)
                val list = byGroup.getValue(group)
                repeat(share) {
                    if (list.isNotEmpty() && result.size < limit) {
                        result += list.removeAt(0)
                        added = true
                    }
                }
            }
            if (!added) break
        }
        return result
    }
}
