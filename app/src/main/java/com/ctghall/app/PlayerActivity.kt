package com.ctghall.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import org.json.JSONArray

/**
 * Plays a playlist (one movie, or all episodes of a season) and moves on to the next episode by itself.
 * Extras: urls, names (string lists), subs (JSON array of arrays), start (index), key (movie url).
 */
@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private var urls: List<String> = emptyList()
    private var names: List<String> = emptyList()
    private var subs: List<List<String>> = emptyList()
    private var start = 0
    private var key = ""
    private var lastId: String? = null
    private var toast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        urls = intent.getStringArrayListExtra("urls") ?: arrayListOf()
        names = intent.getStringArrayListExtra("names") ?: arrayListOf()
        start = intent.getIntExtra("start", 0)
        key = intent.getStringExtra("key") ?: ""
        val sj = try {
            JSONArray(intent.getStringExtra("subs") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
        subs = (0 until sj.length()).map { i ->
            val a = sj.optJSONArray(i)
            if (a == null) emptyList<String>() else (0 until a.length()).map { a.getString(it) }
        }

        if (urls.isEmpty()) {
            finish()
            return
        }

        view = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            controllerShowTimeoutMs = 4000
        }
        setContentView(view)
    }

    override fun onStart() {
        super.onStart()
        if (urls.isNotEmpty()) startPlayback()
    }

    private fun startPlayback() {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Net.UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)

        val p = ExoPlayer.Builder(this, DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        val subsOn = Store.subsDefault
        val items = urls.mapIndexed { i, u ->
            val sl = subs.getOrNull(i).orEmpty().mapIndexed { j, s ->
                val mime = when (Names.ext(s.substringBefore('?'))) {
                    "vtt" -> MimeTypes.TEXT_VTT
                    "ass", "ssa" -> MimeTypes.TEXT_SSA
                    else -> MimeTypes.APPLICATION_SUBRIP
                }
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(s))
                    .setMimeType(mime)
                    .setLabel(Uri.decode(s.substringAfterLast('/')))
                    .setSelectionFlags(if (j == 0 && subsOn) C.SELECTION_FLAG_DEFAULT else 0)
                    .build()
            }
            MediaItem.Builder()
                .setMediaId(u)
                .setUri(u)
                .setSubtitleConfigurations(sl)
                .build()
        }

        val startIdx = start.coerceIn(0, items.size - 1)
        p.setMediaItems(items, startIdx, Store.getPos(urls[startIdx]))
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                showError(error)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    lastId?.let { Store.setPos(it, 0) } // previous episode finished
                }
                lastId = mediaItem?.mediaId
                val idx = player?.currentMediaItemIndex ?: 0
                names.getOrNull(idx)?.let { if (it.isNotEmpty()) hint(it) }
            }
        })
        p.prepare()
        p.playWhenReady = true

        view.player = p
        player = p
    }

    private fun savePosition() {
        val p = player ?: return
        val id = p.currentMediaItem?.mediaId ?: return
        val dur = p.duration
        val pos = p.currentPosition
        val known = dur != C.TIME_UNSET && dur > 0
        if (known && pos > dur - 60_000) {
            Store.setPos(id, 0) // finished
            if (key.isNotEmpty()) Store.setFrac(key, 1f)
        } else if (pos > 15_000) {
            Store.setPos(id, pos)
            if (key.isNotEmpty() && known) Store.setFrac(key, pos.toFloat() / dur.toFloat())
        }
    }

    override fun onStop() {
        super.onStop()
        savePosition()
        player?.let { start = it.currentMediaItemIndex }
        if (::view.isInitialized) view.player = null
        player?.release()
        player = null
    }

    private fun showError(e: PlaybackException) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle("Can't play this file")
            .setMessage(
                "${e.errorCodeName}\n\nThis TV may not support the video or audio format. " +
                    "You can try an external player such as VLC or MX Player."
            )
            .setPositiveButton("Open external player") { _, _ -> openExternal() }
            .setNegativeButton("Close") { _, _ -> finish() }
            .show()
    }

    private fun openExternal() {
        val url = urls.getOrNull(player?.currentMediaItemIndex ?: start) ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*"))
        } catch (e: Exception) {
            Toast.makeText(this, "No external player installed", Toast.LENGTH_LONG).show()
        }
    }

    private fun hint(text: String) {
        toast?.cancel()
        toast = Toast.makeText(this, text, Toast.LENGTH_SHORT).also { it.show() }
    }

    /** With the controls hidden, left/right on the remote seek 10 seconds. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val p = player
        if (p != null && !view.isControllerFullyVisible && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    p.seekBack()
                    hint("<<  ${fmtTime(p.currentPosition)}")
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    p.seekForward()
                    hint(">>  ${fmtTime(p.currentPosition)}")
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
