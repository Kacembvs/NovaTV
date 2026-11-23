package com.nova.iptv.data.source

import org.xmlpull.v1.XmlPullParser
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * Streaming XMLTV reader. Works on any [XmlPullParser] (Android's on device, kXML in unit tests),
 * so multi-hundred-megabyte guides are processed without loading them in memory.
 */
class XmltvParser {

    sealed interface Item {
        data class Channel(val id: String, val names: List<String>) : Item
        data class Programme(
            val channelId: String,
            val startMs: Long,
            val endMs: Long,
            val title: String,
            val description: String?,
        ) : Item
    }

    fun parse(xpp: XmlPullParser): Sequence<Item> = sequence {
        var event = xpp.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (xpp.name) {
                    "channel" -> readChannel(xpp)?.let { yield(it) }
                    "programme" -> readProgramme(xpp)?.let { yield(it) }
                }
            }
            event = xpp.next()
        }
    }

    private fun readChannel(xpp: XmlPullParser): Item.Channel? {
        val id = xpp.getAttributeValue(null, "id")?.trim()
        val names = ArrayList<String>(2)
        while (!(xpp.next() == XmlPullParser.END_TAG && xpp.name == "channel")) {
            if (xpp.eventType == XmlPullParser.END_DOCUMENT) break
            if (xpp.eventType == XmlPullParser.START_TAG && xpp.name == "display-name") {
                xpp.nextText().trim().takeIf { it.isNotEmpty() }?.let(names::add)
            }
        }
        return if (id.isNullOrEmpty()) null else Item.Channel(id, names)
    }

    private fun readProgramme(xpp: XmlPullParser): Item.Programme? {
        val channel = xpp.getAttributeValue(null, "channel")?.trim()
        val start = XmltvTime.parse(xpp.getAttributeValue(null, "start"))
        val stop = XmltvTime.parse(xpp.getAttributeValue(null, "stop"))
        var title: String? = null
        var description: String? = null
        while (!(xpp.next() == XmlPullParser.END_TAG && xpp.name == "programme")) {
            if (xpp.eventType == XmlPullParser.END_DOCUMENT) break
            if (xpp.eventType == XmlPullParser.START_TAG) {
                when (xpp.name) {
                    "title" -> if (title == null) title = xpp.nextText().trim()
                    "desc" -> if (description == null) description = xpp.nextText().trim().ifEmpty { null }
                }
            }
        }
        if (channel.isNullOrEmpty() || start == null) return null
        val end = stop?.takeIf { it > start } ?: (start + DEFAULT_DURATION_MS)
        return Item.Programme(channel, start, end, title.orEmpty().ifEmpty { "Programme" }, description)
    }

    private companion object {
        const val DEFAULT_DURATION_MS = 30 * 60_000L
    }
}

/** XMLTV dates: "yyyyMMddHHmmss +hhmm" (seconds and offset optional; no offset means UTC). */
object XmltvTime {
    private val OFFSET = Regex("""([+-])(\d{2}):?(\d{2})""")

    fun parse(value: String?): Long? {
        val text = value?.trim() ?: return null
        val digits = text.takeWhile { it.isDigit() }
        if (digits.length < 12) return null
        val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                digits.substring(0, 4).toInt(),
                digits.substring(4, 6).toInt() - 1,
                digits.substring(6, 8).toInt(),
                digits.substring(8, 10).toInt(),
                digits.substring(10, 12).toInt(),
                if (digits.length >= 14) digits.substring(12, 14).toInt() else 0,
            )
        }
        var millis = calendar.timeInMillis
        OFFSET.find(text.substring(digits.length))?.let { match ->
            val sign = if (match.groupValues[1] == "-") -1 else 1
            val minutes = match.groupValues[2].toInt() * 60 + match.groupValues[3].toInt()
            millis -= sign * minutes * 60_000L
        }
        return millis
    }
}
