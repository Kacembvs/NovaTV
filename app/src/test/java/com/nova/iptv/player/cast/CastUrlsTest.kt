package com.nova.iptv.player.cast

import org.junit.Assert.assertEquals
import org.junit.Test

class CastUrlsTest {
    @Test
    fun xtreamLiveTsIsCastAsHls() {
        val url = CastUrls.castUrl("http://h:80/live/u/p/123.ts", live = true)
        assertEquals("http://h:80/live/u/p/123.m3u8", url)
        assertEquals(CastUrls.HLS, CastUrls.mimeType(url, live = true))
    }

    @Test
    fun otherUrlsAreKeptWithMatchingMimeType() {
        assertEquals("http://h/movie/u/p/9.mkv", CastUrls.castUrl("http://h/movie/u/p/9.mkv", live = false))
        assertEquals("video/x-matroska", CastUrls.mimeType("http://h/movie/u/p/9.mkv", live = false))
        assertEquals("video/mp4", CastUrls.mimeType("https://cdn/film.MP4?sig=1", live = false))
        assertEquals(CastUrls.HLS, CastUrls.mimeType("http://h/u/p/56", live = true))
    }
}
