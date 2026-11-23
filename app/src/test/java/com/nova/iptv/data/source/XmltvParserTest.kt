package com.nova.iptv.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.StringReader

class XmltvParserTest {

    @Test
    fun parsesTimesWithAndWithoutOffset() {
        // 2026-11-23 20:00:00 UTC
        val utc = 1_795_464_000_000L
        assertEquals(utc, XmltvTime.parse("20261123200000 +0000"))
        assertEquals(utc, XmltvTime.parse("20261123210000 +0100"))
        assertEquals(utc, XmltvTime.parse("20261123150000 -0500"))
        assertEquals(utc, XmltvTime.parse("202611232000"))
        assertNull(XmltvTime.parse("2026"))
        assertNull(XmltvTime.parse(null))
    }

    @Test
    fun readsChannelsAndProgrammesSkippingUnknownElements() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv generator-info-name="test">
              <channel id="TF1.fr"><display-name>TF1</display-name><display-name>TF1 HD</display-name><icon src="x"/></channel>
              <programme start="20261123200000 +0100" stop="20261123210000 +0100" channel="TF1.fr">
                <title lang="fr">Le JT</title>
                <desc lang="fr">Les informations.</desc>
                <credits><director>X</director></credits>
                <category>News</category>
              </programme>
              <programme start="20261123210000 +0100" channel="TF1.fr"><title>Film</title></programme>
              <programme channel="TF1.fr"><title>Sans date</title></programme>
            </tv>
        """.trimIndent()
        val parser = KXmlParser().apply { setInput(StringReader(xml)) }
        val items = XmltvParser().parse(parser).toList()

        assertEquals(XmltvParser.Item.Channel("TF1.fr", listOf("TF1", "TF1 HD")), items[0])
        val jt = items[1] as XmltvParser.Item.Programme
        assertEquals("Le JT", jt.title)
        assertEquals("Les informations.", jt.description)
        assertEquals(3_600_000L, jt.endMs - jt.startMs)
        val film = items[2] as XmltvParser.Item.Programme
        assertEquals("Missing stop falls back to 30 minutes", 30 * 60_000L, film.endMs - film.startMs)
        assertEquals("Programme without start is ignored", 3, items.size)
    }
}
