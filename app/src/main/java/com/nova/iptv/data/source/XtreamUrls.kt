package com.nova.iptv.data.source

import java.net.URLEncoder

object XtreamUrls {
    /** Accepts "host:port", "http://host:port/", ".../player_api.php" or a pasted ".../get.php?..." link. */
    fun normalizeBase(raw: String): String {
        var s = raw.trim().substringBefore('?')
        if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
            s = "http://$s"
        }
        s = s.trimEnd('/')
        for (suffix in listOf("/player_api.php", "/get.php", "/xmltv.php")) {
            if (s.endsWith(suffix, ignoreCase = true)) s = s.dropLast(suffix.length)
        }
        return s.trimEnd('/')
    }

    /** Builds the playable URL from a credential-free reference such as "live/123.ts". */
    fun streamUrl(base: String, username: String, password: String, ref: String): String {
        val type = ref.substringBefore('/')
        val file = ref.substringAfter('/')
        return "${normalizeBase(base)}/$type/${encode(username)}/${encode(password)}/$file"
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
