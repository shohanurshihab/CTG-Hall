package com.ctghall.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object Store {
    const val BASE_URL = "https://ctghall.com/Data/"

    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (!::sp.isInitialized) {
            sp = ctx.applicationContext.getSharedPreferences("ctghall", Context.MODE_PRIVATE)
        }
    }

    // ---- TMDB key ----
    var tmdbKeyOverride: String
        get() = sp.getString("tmdb", "") ?: ""
        set(v) {
            sp.edit().putString("tmdb", v.trim()).apply()
        }

    /** Key typed in Settings, otherwise the one built into the APK. */
    val tmdbKey: String
        get() = tmdbKeyOverride.ifBlank { BuildConfig.TMDB_API_KEY }

    // ---- Subtitles on by default ----
    var subsDefault: Boolean
        get() = sp.getBoolean("subs", false)
        set(v) {
            sp.edit().putBoolean("subs", v).apply()
        }

    // ---- Home rows ----
    private fun defaultRoots() = listOf(
        Root("Latest English Movies", BASE_URL + "Movies/English/2026/"),
        Root("Anime & Animated Series", BASE_URL + "TV-Series/Japanese-Anime-Animated/"),
    )

    fun roots(): List<Root> {
        val s = sp.getString("roots", null) ?: return defaultRoots()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Root(o.getString("name"), o.getString("url"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveRoots(list: List<Root>) {
        val a = JSONArray(list.map { JSONObject().put("name", it.name).put("url", it.url) })
        sp.edit().putString("roots", a.toString()).apply()
    }

    fun addRoot(r: Root) {
        val cur = roots()
        if (cur.none { it.url == r.url }) saveRoots(cur + r)
    }

    fun removeRoot(url: String) = saveRoots(roots().filter { it.url != url })

    fun resetRoots() {
        sp.edit().remove("roots").apply()
    }

    // ---- Poster / description cache ----
    fun getMeta(key: String): Meta? {
        val s = sp.getString("m:$key", null) ?: return null
        return try {
            Meta.fromJson(JSONObject(s))
        } catch (e: Exception) {
            null
        }
    }

    fun putMeta(key: String, m: Meta) {
        sp.edit().putString("m:$key", m.toJson().toString()).apply()
    }

    fun clearMeta() {
        val ed = sp.edit()
        sp.all.keys.filter { it.startsWith("m:") }.forEach { ed.remove(it) }
        ed.apply()
    }

    // ---- Watch progress ----
    fun getPos(url: String): Long = sp.getLong("p:$url", 0L)

    fun setPos(url: String, pos: Long) {
        val ed = sp.edit()
        if (pos <= 0) ed.remove("p:$url") else ed.putLong("p:$url", pos)
        ed.apply()
    }

    /** 0..1 progress of the last played file of a movie / show (used for the bar on poster cards). */
    fun getFrac(key: String): Float = sp.getFloat("f:$key", 0f)

    fun setFrac(key: String, f: Float) {
        val ed = sp.edit()
        if (f <= 0.01f || f >= 0.97f) ed.remove("f:$key") else ed.putFloat("f:$key", f)
        ed.apply()
    }

    fun clearHistory() {
        val ed = sp.edit()
        sp.all.keys.filter { it.startsWith("p:") || it.startsWith("f:") }.forEach { ed.remove(it) }
        ed.remove("recents")
        ed.apply()
    }

    // ---- Recently watched ----
    fun recents(): List<Movie> {
        val s = sp.getString("recents", null) ?: return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map { Movie.fromJson(a.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun pushRecent(m: Movie) {
        val list = (listOf(m) + recents().filter { it.url != m.url }).take(20)
        sp.edit().putString("recents", JSONArray(list.map { it.toJson() }).toString()).apply()
    }
}
