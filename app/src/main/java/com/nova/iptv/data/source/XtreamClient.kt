package com.nova.iptv.data.source

import android.util.JsonReader
import android.util.JsonToken
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Minimal Xtream Codes "player_api.php" client using streaming JSON (large catalogues stay cheap in memory). */
class XtreamClient(private val http: OkHttpClient) {

    data class Credentials(val baseUrl: String, val username: String, val password: String)

    data class Episode(
        val id: String,
        val season: Int,
        val number: Int,
        val title: String,
        val extension: String,
    )

    data class SeriesInfo(
        val name: String?,
        val plot: String?,
        val cover: String?,
        val genre: String?,
        val releaseDate: String?,
        val episodes: List<Episode>,
    )

    data class VodInfo(
        val plot: String?,
        val genre: String?,
        val duration: String?,
        val releaseDate: String?,
        val rating: String?,
        val director: String?,
        val cast: String?,
        val cover: String?,
        val backdrop: String?,
    )

    fun vodInfo(credentials: Credentials, vodId: String): VodInfo =
        read(apiUrl(credentials, "get_vod_info", mapOf("vod_id" to vodId))) { r ->
            var info: Map<String, String> = emptyMap()
            var backdrop: String? = null
            if (r.peek() != JsonToken.BEGIN_OBJECT) {
                r.skipValue()
            } else {
                r.beginObject()
                while (r.hasNext()) {
                    if (r.nextName() == "info" && r.peek() == JsonToken.BEGIN_OBJECT) {
                        val values = HashMap<String, String>()
                        r.beginObject()
                        while (r.hasNext()) {
                            val name = r.nextName()
                            when {
                                name == "backdrop_path" && r.peek() == JsonToken.BEGIN_ARRAY -> {
                                    r.beginArray()
                                    while (r.hasNext()) {
                                        if (backdrop == null && r.peek() == JsonToken.STRING) backdrop = r.nextString() else r.skipValue()
                                    }
                                    r.endArray()
                                }
                                r.peek() == JsonToken.STRING || r.peek() == JsonToken.NUMBER -> values[name] = r.nextString()
                                else -> r.skipValue()
                            }
                        }
                        r.endObject()
                        info = values
                    } else {
                        r.skipValue()
                    }
                }
                r.endObject()
            }
            fun field(vararg keys: String) = keys.firstNotNullOfOrNull { info[it]?.trim()?.ifEmpty { null } }
            VodInfo(
                plot = field("plot", "description"),
                genre = field("genre"),
                duration = field("duration"),
                releaseDate = field("releasedate", "release_date"),
                rating = field("rating"),
                director = field("director"),
                cast = field("cast", "actors"),
                cover = field("cover_big", "movie_image"),
                backdrop = backdrop?.ifBlank { null },
            )
        }

    fun authenticate(credentials: Credentials) {
        read(apiUrl(credentials, null)) { r ->
            if (r.peek() != JsonToken.BEGIN_OBJECT) throw IOException("Réponse inattendue du serveur Xtream.")
            var auth: String? = null
            var status: String? = null
            r.beginObject()
            while (r.hasNext()) {
                if (r.nextName() == "user_info" && r.peek() == JsonToken.BEGIN_OBJECT) {
                    val info = r.readFlatObject()
                    auth = info["auth"]
                    status = info["status"]
                } else {
                    r.skipValue()
                }
            }
            r.endObject()
            if (auth != "1") throw IOException("Identifiants refusés par le serveur.")
            if (status != null && !status.equals("Active", ignoreCase = true)) {
                throw IOException("Compte non actif ($status).")
            }
        }
    }

    fun categories(credentials: Credentials, action: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        forEachObject(apiUrl(credentials, action)) { item ->
            val id = item["category_id"]
            if (id != null) result[id] = item["category_name"].orEmpty()
        }
        return result
    }

    suspend fun forEachStream(credentials: Credentials, action: String, onItem: suspend (Map<String, String>) -> Unit) {
        forEachObject(apiUrl(credentials, action)) { onItem(it) }
    }

    fun seriesInfo(credentials: Credentials, seriesId: String): SeriesInfo =
        read(apiUrl(credentials, "get_series_info", mapOf("series_id" to seriesId))) { r ->
            if (r.peek() != JsonToken.BEGIN_OBJECT) throw IOException("Série introuvable sur le serveur.")
            var info: Map<String, String> = emptyMap()
            val episodes = ArrayList<Episode>()
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "info" -> if (r.peek() == JsonToken.BEGIN_OBJECT) info = r.readFlatObject() else r.skipValue()
                    "episodes" -> when (r.peek()) {
                        JsonToken.BEGIN_OBJECT -> {
                            r.beginObject()
                            while (r.hasNext()) {
                                val season = r.nextName().toIntOrNull() ?: 0
                                readEpisodes(r, season, episodes)
                            }
                            r.endObject()
                        }
                        JsonToken.BEGIN_ARRAY -> {
                            r.beginArray()
                            while (r.hasNext()) readEpisodes(r, 0, episodes)
                            r.endArray()
                        }
                        else -> r.skipValue()
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
            SeriesInfo(
                name = info["name"],
                plot = info["plot"],
                cover = info["cover"],
                genre = info["genre"],
                releaseDate = info["releaseDate"] ?: info["release_date"],
                episodes = episodes.sortedWith(compareBy({ it.season }, { it.number })),
            )
        }

    private fun readEpisodes(r: JsonReader, season: Int, out: MutableList<Episode>) {
        if (r.peek() != JsonToken.BEGIN_ARRAY) {
            r.skipValue()
            return
        }
        r.beginArray()
        while (r.hasNext()) {
            if (r.peek() != JsonToken.BEGIN_OBJECT) {
                r.skipValue()
                continue
            }
            val item = r.readFlatObject()
            val id = item["id"] ?: continue
            out += Episode(
                id = id,
                season = item["season"]?.toIntOrNull() ?: season,
                number = item["episode_num"]?.toIntOrNull() ?: (out.size + 1),
                title = item["title"].orEmpty(),
                extension = item["container_extension"]?.ifBlank { null } ?: "mp4",
            )
        }
        r.endArray()
    }

    private fun apiUrl(credentials: Credentials, action: String?, extra: Map<String, String> = emptyMap()): HttpUrl {
        val builder = (XtreamUrls.normalizeBase(credentials.baseUrl) + "/player_api.php").toHttpUrlOrNull()
            ?.newBuilder() ?: throw IOException("Adresse du serveur invalide.")
        builder.addQueryParameter("username", credentials.username)
        builder.addQueryParameter("password", credentials.password)
        if (action != null) builder.addQueryParameter("action", action)
        extra.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    private inline fun forEachObject(url: HttpUrl, onItem: (Map<String, String>) -> Unit) {
        read(url) { r ->
            if (r.peek() != JsonToken.BEGIN_ARRAY) {
                r.skipValue()
                return@read
            }
            r.beginArray()
            while (r.hasNext()) {
                if (r.peek() == JsonToken.BEGIN_OBJECT) onItem(r.readFlatObject()) else r.skipValue()
            }
            r.endArray()
        }
    }

    private inline fun <T> read(url: HttpUrl, block: (JsonReader) -> T): T {
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Le serveur a répondu HTTP ${response.code}.")
            val body = response.body ?: throw IOException("Réponse vide du serveur.")
            return JsonReader(body.charStream()).use { reader ->
                reader.isLenient = true
                block(reader)
            }
        }
    }
}

/** Reads an object keeping only primitive values; nested objects/arrays are skipped. */
private fun JsonReader.readFlatObject(): Map<String, String> {
    val values = HashMap<String, String>()
    beginObject()
    while (hasNext()) {
        val name = nextName()
        when (peek()) {
            JsonToken.STRING, JsonToken.NUMBER -> values[name] = nextString()
            JsonToken.BOOLEAN -> values[name] = nextBoolean().toString()
            else -> skipValue()
        }
    }
    endObject()
    return values
}
