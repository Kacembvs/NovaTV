package com.nova.iptv.data.source

import org.junit.Assert.assertEquals
import org.junit.Test

class XtreamUrlsTest {

    @Test
    fun normalizesServerAddresses() {
        assertEquals("http://host:8080", XtreamUrls.normalizeBase("host:8080"))
        assertEquals("https://host", XtreamUrls.normalizeBase(" https://host/ "))
        assertEquals("http://host:80", XtreamUrls.normalizeBase("http://host:80/player_api.php"))
        assertEquals("http://host:80", XtreamUrls.normalizeBase("http://host:80/get.php?username=a&password=b&type=m3u"))
    }

    @Test
    fun buildsStreamUrlWithEncodedCredentials() {
        assertEquals(
            "http://host:80/live/user%40mail/p%20w%2Fd/123.ts",
            XtreamUrls.streamUrl("host:80", "user@mail", "p w/d", "live/123.ts"),
        )
        assertEquals(
            "http://host:80/series/u/p/987.mkv",
            XtreamUrls.streamUrl("http://host:80/", "u", "p", "series/987.mkv"),
        )
    }
}
