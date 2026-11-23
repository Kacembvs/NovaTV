package com.nova.iptv.data

import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.domain.ContentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {

    private fun item(ref: String, group: String) =
        ContentEntity(profileId = 1, kind = ContentKind.MOVIE, name = ref, groupTitle = group, logo = null, streamRef = ref, sortIndex = 0)

    @Test
    fun dislikesCanRemoveACategory() {
        val scores = Recommender.groupScores(
            listOf(
                Recommender.Signal("Action", Recommender.LIKE),
                Recommender.Signal("Horreur", Recommender.WATCHED),
                Recommender.Signal("Horreur", Recommender.DISLIKE),
            )
        )
        assertEquals(mapOf("Action" to 3), scores)
    }

    @Test
    fun picksUnseenTitlesFromPreferredCategoriesAndMixesThem() {
        val candidates = listOf(
            item("a1", "Action"), item("a2", "Action"), item("a3", "Action"), item("a4", "Action"),
            item("c1", "Comédie"), item("c2", "Comédie"),
            item("h1", "Horreur"),
        )
        val scores = mapOf("Action" to 9, "Comédie" to 2)
        val picked = Recommender.pick(candidates, scores, exclude = setOf("a1"), limit = 5).map { it.streamRef }

        assertFalse("already seen", "a1" in picked)
        assertFalse("category without positive score", "h1" in picked)
        assertTrue("mixes categories", "c1" in picked)
        assertEquals(listOf("a2", "a3", "a4", "c1", "c2"), picked)
    }
}
