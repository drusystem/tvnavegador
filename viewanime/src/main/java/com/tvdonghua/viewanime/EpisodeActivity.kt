package com.tvdonghua.viewanime

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import java.util.concurrent.Executors

@UnstableApi
class EpisodeActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var source: CatalogSource
    private lateinit var servers: LinearLayout
    private lateinit var playerBox: FrameLayout
    private lateinit var hint: TextView
    private lateinit var heading: TextView
    private var page: EpisodePage? = null
    private var gecko: GeckoSession? = null
    private var geckoView: GeckoView? = null
    private var exo: ExoPlayer? = null
    private var playing = false
    private var full = false
    private var pageFull = false
    private var lastOk = 0L
    private var embedUrl: String? = null
    private var tapPosted = false
    private var surface: SurfaceView? = null
    private var filled = false

    private val singleOk = Runnable { togglePause() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = Library.open(this, intent.getStringExtra("source") ?: "sd")
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ink.bg)
        root.setPadding(dp(24), dp(16), dp(24), dp(12))

        val header = LinearLayout(this)
        header.gravity = Gravity.CENTER_VERTICAL
        val back = chip("Atrás") { finish() }
        header.addView(back)
        heading = label(intent.getStringExtra("title") ?: "Capítulo", 22f, Color.WHITE, true)
        heading.setPadding(dp(16), 0, dp(12), 0)
        header.addView(heading, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val badge = label(intent.getStringExtra("badge") ?: "", 14f, Ink.gold, true)
        header.addView(badge)
        root.addView(header)

        servers = LinearLayout(this)
        servers.orientation = LinearLayout.HORIZONTAL
        val scroller = HorizontalScrollView(this)
        scroller.isFocusable = false
        scroller.isHorizontalScrollBarEnabled = false
        scroller.addView(servers)
        root.addView(scroller, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
            bottomMargin = dp(10)
        })

        playerBox = FrameLayout(this)
        playerBox.setBackgroundColor(Color.BLACK)
        root.addView(playerBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        hint = label("Cargando reproductores...", 16f, 0xFFE7EDF5.toInt())
        hint.setPadding(0, dp(8), 0, 0)
        root.addView(hint)
        setContentView(root)

        val url = intent.getStringExtra("url") ?: return finish()
        io.execute {
            try {
                val loaded = source.episode(url)
                ui.post { if (!isDestroyed) showServers(loaded) }
            } catch (e: Exception) {
                ui.post { hint.text = "No se pudo leer el capítulo." }
            }
        }
    }

    private fun showServers(loaded: EpisodePage) {
        page = loaded
        heading.text = loaded.title
        servers.removeAllViews()
        if (loaded.servers.isEmpty()) {
            hint.text = "Este capítulo no publicó reproductores."
            return
        }
        loaded.servers.forEachIndexed { index, server ->
            val button = chip(server.name) { start(server) }
            button.mark(index == loaded.focus)
            val gap = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            gap.marginEnd = dp(10)
            servers.addView(button, gap)
        }
        hint.text = "OK pausa. Dos veces OK, pantalla completa. Atrás sale de ella."
        val chosen = loaded.focus.coerceIn(0, servers.childCount - 1)
        servers.getChildAt(chosen)?.requestFocus()
        start(loaded.servers[chosen])
    }

    private fun start(server: ServerSlot) {
        val loaded = page ?: return
        filled = false
        hint.text = "Preparando el capítulo..."
        playing = false
        tapPosted = false
        io.execute {
            try {
                val embed = source.embed(loaded, server)
                val stream = MediaFind.locate(embed, loaded.pageUrl)
                ui.post {
                    if (isDestroyed) return@post
                    embedUrl = embed
                    if (stream != null) playChip(stream, embed) else playPage(embed)
                }
            } catch (e: Exception) {
                ui.post {
                    val fallback = server.embed ?: loaded.pageUrl
                    embedUrl = fallback
                    playPage(fallback)
                }
            }
        }
    }

    private fun playChip(stream: Stream, embed: String) {
        releaseGecko()
        val player = exo ?: run {
            val selector = DefaultTrackSelector(this)
            selector.parameters = selector.buildUponParameters()
                .setMaxVideoSize(1280, 720)
                .setPreferredVideoMimeType(MimeTypes.VIDEO_H264)
                .build()
            ExoPlayer.Builder(this)
                .setTrackSelector(selector)
                .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory(stream.referer, stream.cookies)))
                .build()
        }
            .also { created ->
                created.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        ui.post {
                            val address = embedUrl
                            if (address != null && exo != null) playPage(address)
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (!isPlaying) return
                        ui.post {
                            hint.text = "Reproduciendo. OK pausa."
                            fillOnce()
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        val view = surface ?: return
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            view.holder.setFixedSize(videoSize.width, videoSize.height)
                        }
                    }
                })
                exo = created
            }
        val video = SurfaceView(this)
        surface = video
        mountVideo(video)
        player.setVideoSurfaceView(video)
        val item = MediaItem.Builder().setUri(stream.url).build()
        val sourceFactory = DefaultMediaSourceFactory(httpFactory(stream.referer, stream.cookies))
        player.setMediaSource(sourceFactory.createMediaSource(item))
        player.prepare()
        player.play()
        playing = true
        playerBox.requestFocus()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun httpFactory(referer: String, cookies: String): DefaultHttpDataSource.Factory {
        val headers = linkedMapOf("Referer" to referer)
        if (cookies.isNotBlank()) headers["Cookie"] = cookies
        return DefaultHttpDataSource.Factory()
            .setUserAgent(Net.AGENT)
            .setDefaultRequestProperties(headers)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(25000)
    }

    private fun playPage(url: String) {
        releaseExo()
        hint.text = "Abriendo el reproductor de la página..."
        Engine.onPlaying = { on ->
            playing = on
            if (on) {
                hint.text = "Reproduciendo en el visor."
                fillOnce()
            }
        }
        Engine.obtain(this) { runtime ->
            if (isDestroyed) return@obtain
            mount(runtime, url)
        }
    }

    private fun mount(runtime: GeckoRuntime, url: String) {
        releaseGecko()
        val session = Engine.session()
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                pageFull = fullScreen
                if (fullScreen) enterFull() else if (!full) exitFull()
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (!success || tapPosted) return
                tapPosted = true
                ui.postDelayed({
                    if (!playing) tapCenter()
                }, 1200)
            }
        }
        session.open(runtime)
        val view = GeckoView(this)
        view.setBackgroundColor(Color.BLACK)
        view.isFocusable = true
        mountVideo(view)
        view.setSession(session)
        session.load(GeckoSession.Loader().uri(url).referrer(page?.pageUrl ?: url))
        gecko = session
        geckoView = view
        view.requestFocus()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun tapCenter() {
        val view = geckoView ?: return
        if (view.width == 0 || view.height == 0) return
        val x = view.width / 2f
        val y = view.height / 2f
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0)
        view.dispatchTouchEvent(down)
        view.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun mountVideo(video: View) {
        playerBox.removeAllViews()
        playerBox.addView(video, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun togglePause() {
        val player = exo
        if (player != null) {
            if (player.isPlaying) player.pause() else player.play()
            return
        }
        tapCenter()
    }

    private fun fillOnce() {
        if (filled) return
        filled = true
        enterFull()
    }

    private fun enterFull() {
        full = true
        setChrome(false)
        (geckoView ?: playerBox).requestFocus()
    }

    private fun exitFull() {
        if (pageFull) {
            gecko?.exitFullScreen()
            pageFull = false
        }
        full = false
        setChrome(true)
    }

    private fun hold() {
        exo?.pause()
        gecko?.setActive(false)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setChrome(show: Boolean) {
        val mode = if (show) View.VISIBLE else View.GONE
        (playerBox.parent as LinearLayout).let { column ->
            for (i in 0 until column.childCount) {
                val child = column.getChildAt(i)
                if (child !== playerBox) child.visibility = mode
            }
        }
        val pad = if (show) dp(24) else 0
        (playerBox.parent as LinearLayout).setPadding(pad, if (show) dp(16) else 0, pad, if (show) dp(12) else 0)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val pointer = event.isFromSource(InputDevice.SOURCE_MOUSE) ||
            event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE) ||
            event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)
        val ok = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            event.keyCode == KeyEvent.KEYCODE_ENTER ||
            event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        if (!pointer && ok && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && (gecko != null || exo != null)) {
            ui.removeCallbacks(singleOk)
            val now = event.eventTime
            if (now - lastOk in 1..420) {
                lastOk = 0
                if (full || pageFull) exitFull() else enterFull()
                return true
            }
            lastOk = now
            ui.postDelayed(singleOk, 320)
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) {
                if (full || pageFull) exitFull() else finish()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun releaseExo() {
        exo?.release()
        exo = null
    }

    private fun releaseGecko() {
        gecko?.close()
        gecko = null
        geckoView = null
    }

    override fun onUserLeaveHint() {
        hold()
        super.onUserLeaveHint()
    }

    override fun onStop() {
        hold()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        if (gecko != null) gecko?.setActive(true)
    }

    override fun onDestroy() {
        ui.removeCallbacks(singleOk)
        Engine.onPlaying = null
        releaseExo()
        releaseGecko()
        io.shutdownNow()
        super.onDestroy()
    }
}
