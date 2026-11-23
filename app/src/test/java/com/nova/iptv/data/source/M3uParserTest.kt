package com.nova.iptv.data.source

import com.nova.iptv.domain.ContentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {

    private fun parse(text: String): Pair<List<M3uEntry>, M3uParser> {
        val parser = M3uParser()
        return parser.parse(text.reader().buffered()).toList() to parser
    }

    @Test
    fun parsesAttributesNameAndHeaderEpg() {
        val (entries, parser) = parse(
            """
            ﻿#EXTM3U url-tvg="http://epg.example/guide.xml"
            #EXTINF:-1 tvg-id="tf1.fr" tvg-logo="http://logo/tf1.png" group-title="France",TF1 HD
            http://host/live/1.ts
            """.trimIndent()
        )
        assertEquals("http://epg.example/guide.xml", parser.epgUrl)
        val e = entries.single()
        assertEquals("TF1 HD", e.name)
        assertEquals("France", e.group)
        assertEquals("tf1.fr", e.tvgId)
        assertEquals("http://logo/tf1.png", e.logo)
        assertEquals(ContentKind.LIVE, e.kind)
    }

    @Test
    fun commaInsideQuotedAttributeDoesNotSplitName() {
        val (entries, _) = parse(
            """
            #EXTM3U
            #EXTINF:-1 group-title="News, Info" tvg-name="X",Channel, with comma
            http://host/a.m3u8
            """.trimIndent()
        )
        assertEquals("Channel, with comma", entries.single().name)
        assertEquals("News, Info", entries.single().group)
    }

    @Test
    fun usesExtGrpAndFallbacksWhenAttributesMissing() {
        val (entries, _) = parse(
            """
            #EXTM3U
            #EXTINF:-1,
            #EXTGRP:Sport
            http://host/stream/sport.ts?token=abc
            http://host/raw/without-info.ts
            """.trimIndent()
        )
        assertEquals("sport.ts", entries[0].name)
        assertEquals("Sport", entries[0].group)
        assertEquals("without-info.ts", entries[1].name)
        assertEquals(M3uParser.DEFAULT_GROUP, entries[1].group)
        assertNull(entries[1].logo)
    }

    @Test
    fun classifiesMoviesAndSeriesFromUrl() {
        assertEquals(ContentKind.MOVIE, M3uParser.classify("http://h:80/movie/u/p/12.mkv"))
        assertEquals(ContentKind.SERIES, M3uParser.classify("http://h:80/series/u/p/34.mp4"))
        assertEquals(ContentKind.MOVIE, M3uParser.classify("https://cdn/film.MP4?sig=1"))
        assertEquals(ContentKind.LIVE, M3uParser.classify("http://h:80/u/p/56"))
        assertEquals(ContentKind.LIVE, M3uParser.classify("https://cdn/live/index.m3u8"))
    }
}
