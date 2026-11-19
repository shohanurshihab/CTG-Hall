package com.ctghall.app

import org.json.JSONObject

data class Root(val name: String, val url: String)

data class DirEntry(
    val name: String,
    val url: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
) {
    fun toMovie(): Movie {
        val p = Names.parse(name, isDir)
        return Movie(
            p.title,
            p.year,
            url,
            isDir,
            name,
            p.tmdbId,
            url.contains("/TV-Series/", ignoreCase = true),
        )
    }
}

/** Contents of one folder: video files, subtitle files (including Subs/ subfolders) and real subfolders. */
data class Folder(val videos: List<DirEntry>, val subs: List<DirEntry>, val dirs: List<DirEntry>)

/** A movie / show is either a folder (containing the video files) or a loose video file. */
data class Movie(
    val title: String,
    val year: String?,
    val url: String,
    val isFolder: Boolean,
    val rawName: String,
    val tmdbId: Long?,
    val tv: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("t", title)
        .put("y", year ?: JSONObject.NULL)
        .put("u", url)
        .put("f", isFolder)
        .put("r", rawName)
        .put("i", tmdbId ?: JSONObject.NULL)
        .put("tv", tv)

    companion object {
        fun fromJson(o: JSONObject) = Movie(
            o.getString("t"),
            if (o.isNull("y")) null else o.getString("y"),
            o.getString("u"),
            o.getBoolean("f"),
            o.getString("r"),
            if (o.isNull("i")) null else o.getLong("i"),
            o.optBoolean("tv", false),
        )
    }
}

/** Poster / description info from TMDB. [found] = false means "looked up, nothing found". */
data class Meta(
    val found: Boolean,
    val title: String?,
    val overview: String?,
    val rating: Double?,
    val poster: String?,
    val backdrop: String?,
    val genres: String?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("found", found)
        .put("title", title ?: JSONObject.NULL)
        .put("overview", overview ?: JSONObject.NULL)
        .put("rating", rating ?: JSONObject.NULL)
        .put("poster", poster ?: JSONObject.NULL)
        .put("backdrop", backdrop ?: JSONObject.NULL)
        .put("genres", genres ?: JSONObject.NULL)

    companion object {
        val NONE = Meta(false, null, null, null, null, null, null)

        private fun str(o: JSONObject, k: String): String? = if (o.isNull(k)) null else o.getString(k)

        fun fromJson(o: JSONObject) = Meta(
            o.optBoolean("found", false),
            str(o, "title"),
            str(o, "overview"),
            if (o.isNull("rating")) null else o.getDouble("rating"),
            str(o, "poster"),
            str(o, "backdrop"),
            str(o, "genres"),
        )
    }
}

object Names {
    val VIDEO = setOf("mkv", "mp4", "avi", "m4v", "mov", "webm", "ts", "wmv", "mpg", "mpeg")
    val SUBS = setOf("srt", "vtt", "ass", "ssa")
    private val SUB_DIRS = setOf("subs", "sub", "subtitles", "subtitle")

    fun ext(name: String) = name.substringAfterLast('.', "").lowercase()
    fun isVideo(name: String) = ext(name) in VIDEO
    fun isSub(name: String) = ext(name) in SUBS
    fun baseName(name: String) = name.substringBeforeLast('.', name)
    fun isHidden(name: String) = name.startsWith("_") || name.startsWith(".")
    fun isSubDir(name: String) = name.lowercase() in SUB_DIRS

    /** Sort key that orders "Episode 2" before "Episode 10". */
    fun natKey(s: String): String =
        s.lowercase().replace(Regex("\\d+")) { it.value.padStart(8, '0') }

    private val yearRe = Regex("(?<![0-9])((?:19[2-9]|20[0-3])[0-9])(?![0-9])")
    private val tmdbRe = Regex("(?i)tmdb-?(\\d+)")
    private val qualityRe = Regex(
        "(?i)\\b(2160p|1080p|720p|480p|4k|web-?rip|web-?dl|blu-?ray|brrip|bdrip|hdrip|hdtv|dvdrip|x264|x265|h264|hevc|aac)\\b"
    )

    data class Parsed(val title: String, val year: String?, val tmdbId: Long?)

    /**
     * "Allen (2026)"                      -> Allen, 2026
     * "6teen (2004) [tmdb-4355]"          -> 6teen, 2004, tmdb 4355
     * "9.Bullets.2022.1080p.WEBRip.x264"  -> 9 Bullets, 2022
     */
    fun parse(raw: String, isDir: Boolean): Parsed {
        val tmdbId = tmdbRe.find(raw)?.groupValues?.get(1)?.toLongOrNull()
        val s = if (!isDir && isVideo(raw)) baseName(raw) else raw
        val cleaned = s.replace('.', ' ').replace('_', ' ')
        val m = yearRe.findAll(cleaned).firstOrNull { it.range.first > 0 }
        val year = m?.value
        val cut = m?.range?.first ?: qualityRe.find(cleaned)?.range?.first ?: cleaned.length
        var title = cleaned.substring(0, cut)
            .replace(Regex("\\[[^\\]]*\\]"), " ")
            .replace(Regex("[()\\[\\]]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-')
        if (title.isBlank()) title = raw
        return Parsed(title, year, tmdbId)
    }

    /** True for names that look like one movie / show, false for category folders like "English" or "2026". */
    fun looksLikeTitle(raw: String): Boolean {
        if (tmdbRe.containsMatchIn(raw)) return true
        val cleaned = raw.replace('.', ' ').replace('_', ' ')
        return yearRe.findAll(cleaned).any { it.range.first > 0 }
    }

    /** Subtitle files that belong to [v]: same-name files first, English first, at most 10. */
    fun subsFor(f: Folder, v: DirEntry): List<DirEntry> {
        val base = baseName(v.name)
        val matched = f.subs.filter { it.name.startsWith(base, ignoreCase = true) }
        val pool = if (matched.isNotEmpty() || f.videos.size == 1) f.subs else emptyList()
        return pool
            .sortedWith(
                compareBy<DirEntry>(
                    { if (it.name.startsWith(base, ignoreCase = true)) 0 else 1 },
                    { if (it.name.contains("eng", ignoreCase = true)) 0 else 1 },
                )
            )
            .take(10)
    }
}

fun fmtTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun fmtSize(b: Long): String = when {
    b <= 0 -> ""
    b >= (1L shl 30) -> "%.1f GB".format(b / 1073741824.0)
    b >= (1L shl 20) -> "%d MB".format(b shr 20)
    else -> "%d KB".format(b shr 10)
}
