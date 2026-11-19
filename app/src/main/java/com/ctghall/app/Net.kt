package com.ctghall.app

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object Net {
    const val UA =
        "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", UA).build())
        }
        .build()

    private val cache = ConcurrentHashMap<String, List<DirEntry>>()
    private val dateRe = Regex("(\\d{2}-[A-Za-z]{3}-\\d{4})\\s+(\\d{2}:\\d{2})")

    /** Lists one nginx "Index of /..." page. Results are cached in memory. */
    suspend fun listDir(url: String, force: Boolean = false): List<DirEntry> {
        val u = if (url.endsWith("/")) url else "$url/"
        if (!force) cache[u]?.let { return it }
        val result = withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(u).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                parse(resp.body?.string() ?: "", u)
            }
        }
        cache[u] = result
        return result
    }

    /** One movie / season folder: videos, subtitles (also from Subs/ folders) and real subfolders. */
    suspend fun folder(url: String): Folder {
        val all = listDir(url)
        val vids = all.filter { !it.isDir && Names.isVideo(it.name) }.sortedBy { Names.natKey(it.name) }
        var subs = all.filter { !it.isDir && Names.isSub(it.name) }
        for (d in all.filter { it.isDir && Names.isSubDir(it.name) }) {
            try {
                subs = subs + listDir(d.url).filter { !it.isDir && Names.isSub(it.name) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // a missing Subs folder is not a reason to fail the whole page
            }
        }
        val dirs = all.filter { it.isDir && !Names.isHidden(it.name) && !Names.isSubDir(it.name) }
        return Folder(vids, subs, dirs)
    }

    private fun parse(html: String, base: String): List<DirEntry> {
        val doc = Jsoup.parse(html, base)
        val df = SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.US)
        val out = ArrayList<DirEntry>()
        for (a in doc.select("a[href]")) {
            val href = a.attr("href")
            if (href.isEmpty() || href.startsWith("?") || href.startsWith("#")) continue
            if (href.startsWith("../") || href == "./" || href.startsWith("/")) continue
            val abs = a.absUrl("href")
            if (abs.isEmpty() || abs == base) continue
            // an absolute link must stay inside the current folder
            if (href.contains("://") && !abs.startsWith(base)) continue

            val path = href.substringBefore('?')
            val isDir = path.endsWith("/")
            val name = Uri.decode(path.trimEnd('/')).substringAfterLast('/')
            if (name.isEmpty()) continue

            // nginx prints "date time size" right after the link
            val tail = (a.nextSibling() as? TextNode)?.text()?.trim().orEmpty()
            val size = if (isDir) 0L else parseSize(tail.split(Regex("\\s+")).lastOrNull().orEmpty())
            val modified = dateRe.find(tail)?.let { mr ->
                runCatching { df.parse(mr.groupValues[1] + " " + mr.groupValues[2])?.time }.getOrNull()
            } ?: 0L
            out.add(DirEntry(name, abs, isDir, size, modified))
        }
        return out
    }

    private fun parseSize(s: String): Long {
        if (s.isEmpty() || s == "-") return 0L
        s.toLongOrNull()?.let { return it }
        val n = s.dropLast(1).toDoubleOrNull() ?: return 0L
        val mult = when (s.last().uppercaseChar()) {
            'K' -> 1024.0
            'M' -> 1024.0 * 1024
            'G' -> 1024.0 * 1024 * 1024
            else -> 0.0
        }
        return (n * mult).toLong()
    }
}
