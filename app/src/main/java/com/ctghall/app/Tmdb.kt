package com.ctghall.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class TmdbAuthException : IOException("Invalid TMDB API key")

object Tmdb {
    private val GENRES = mapOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime",
        99 to "Documentary", 18 to "Drama", 10751 to "Family", 14 to "Fantasy", 36 to "History",
        27 to "Horror", 10402 to "Music", 9648 to "Mystery", 10749 to "Romance",
        878 to "Sci-Fi", 10770 to "TV Movie", 53 to "Thriller", 10752 to "War", 37 to "Western",
        10759 to "Action & Adventure", 10762 to "Kids", 10763 to "News", 10764 to "Reality",
        10765 to "Sci-Fi & Fantasy", 10766 to "Soap", 10767 to "Talk", 10768 to "War & Politics",
    )

    /** Returns null when TMDB has no match. Throws on network / key errors. */
    suspend fun lookup(key: String, m: Movie): Meta? = withContext(Dispatchers.IO) {
        val kind = if (m.tv) "tv" else "movie"
        val other = if (m.tv) "movie" else "tv"

        val id = m.tmdbId
        if (id != null) {
            val direct = byId(key, kind, id)
            if (direct != null) return@withContext direct
        }
        search(key, kind, m.title, m.year)
            ?: (if (m.year != null) search(key, kind, m.title, null) else null)
            ?: search(key, other, m.title, null)
    }

    private fun byId(key: String, kind: String, id: Long): Meta? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api.themoviedb.org")
            .addPathSegments("3/$kind/$id")
            .addQueryParameter("api_key", key)
            .build()
        Net.client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (resp.code == 401) throw TmdbAuthException()
            if (resp.code == 404) return null
            if (!resp.isSuccessful) throw IOException("TMDB HTTP ${resp.code}")
            return toMeta(JSONObject(resp.body?.string() ?: "{}"), kind == "tv")
        }
    }

    private fun search(key: String, kind: String, title: String, year: String?): Meta? {
        val builder = HttpUrl.Builder()
            .scheme("https")
            .host("api.themoviedb.org")
            .addPathSegments("3/search/$kind")
            .addQueryParameter("api_key", key)
            .addQueryParameter("query", title)
            .addQueryParameter("include_adult", "false")
        if (year != null) {
            builder.addQueryParameter(if (kind == "tv") "first_air_date_year" else "year", year)
        }

        Net.client.newCall(Request.Builder().url(builder.build()).build()).execute().use { resp ->
            if (resp.code == 401) throw TmdbAuthException()
            if (!resp.isSuccessful) throw IOException("TMDB HTTP ${resp.code}")
            val results = JSONObject(resp.body?.string() ?: "{}").optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            return toMeta(results.getJSONObject(0), kind == "tv")
        }
    }

    private fun toMeta(o: JSONObject, tv: Boolean): Meta {
        fun s(k: String): String? = if (o.isNull(k)) null else o.optString(k).takeIf { it.isNotBlank() }

        val names = ArrayList<String>()
        o.optJSONArray("genres")?.let { a ->
            for (i in 0 until a.length()) names.add(a.getJSONObject(i).optString("name"))
        }
        if (names.isEmpty()) {
            o.optJSONArray("genre_ids")?.let { a ->
                for (i in 0 until a.length()) GENRES[a.getInt(i)]?.let { names.add(it) }
            }
        }
        val genres = names.filter { it.isNotBlank() }.take(3).joinToString(" • ").ifEmpty { null }

        return Meta(
            found = true,
            title = s(if (tv) "name" else "title"),
            overview = s("overview"),
            rating = if (o.isNull("vote_average")) null else o.getDouble("vote_average"),
            poster = s("poster_path")?.let { "https://image.tmdb.org/t/p/w342$it" },
            backdrop = s("backdrop_path")?.let { "https://image.tmdb.org/t/p/w780$it" },
            genres = genres,
        )
    }
}

/** Memory + disk cache in front of TMDB, with a small concurrency limit. */
object MetaRepo {
    private val mem = ConcurrentHashMap<String, Meta>()
    private val gate = Semaphore(4)

    @Volatile
    var authFailed = false

    fun clearMem() = mem.clear()

    suspend fun get(m: Movie): Meta? {
        mem[m.url]?.let { return if (it.found) it else null }
        Store.getMeta(m.url)?.let {
            mem[m.url] = it
            return if (it.found) it else null
        }
        val key = Store.tmdbKey
        if (key.isBlank() || authFailed) return null

        return gate.withPermit {
            mem[m.url]?.let { return@withPermit if (it.found) it else null }
            try {
                val found = Tmdb.lookup(key, m)
                val meta = found ?: Meta.NONE
                mem[m.url] = meta
                Store.putMeta(m.url, meta)
                found
            } catch (e: CancellationException) {
                throw e
            } catch (e: TmdbAuthException) {
                authFailed = true
                null
            } catch (e: Exception) {
                null // network hiccup: don't cache, try again next time
            }
        }
    }
}
