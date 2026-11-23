package com.nova.iptv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackLabelsTest {
    @Test
    fun formatsSpeedsInFrench() {
        assertEquals(listOf("0,5x", "0,75x", "Normale (1x)", "1,25x", "1,5x", "1,75x", "2x"), TrackLabels.SPEEDS.map(TrackLabels::speed))
    }

    @Test
    fun translatesLanguagesAndChannels() {
        assertEquals("Anglais", TrackLabels.language("en"))
        assertEquals("Français", TrackLabels.language("fra"))
        assertNull(TrackLabels.language("und"))
        assertEquals("5.1", TrackLabels.channels(6))
        assertEquals("Stéréo", TrackLabels.channels(2))
    }
}
