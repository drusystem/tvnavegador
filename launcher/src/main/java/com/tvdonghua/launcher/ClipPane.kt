package com.tvdonghua.launcher

import android.content.Context
import android.graphics.Outline
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource

/** Mini reproductor local en bucle, con marco glass del launcher. */
internal class ClipPane(context: Context) : FrameLayout(context) {
    private val shell = FrameLayout(context)
    private val surface = TextureView(context)
    private val hint = TextView(context)
    private val main = Handler(Looper.getMainLooper())
    private var look = Look(context)
    private var media: MediaPlayer? = null
    private var audio: MediaPlayer? = null
    private var exo: ExoPlayer? = null
    private var playing = true
    private var wantPlay = true
    private var status = "Cargando…"
    private var pending: ClipStore.ClipFiles? = null
    private var surfaceReady = false

    init {
        clipChildren = false
        isFocusable = true
        isClickable = true
        id = View.generateViewId()
        contentDescription = "Video del inicio"
        clipChildren = true
        shell.background = context.glassArgb(22, look.cardArgb(false))
        shell.clipChildren = true
        shell.clipToOutline = true
        roundOutline(shell, 22)
        val pad = context.dp(8)
        shell.setPadding(pad, pad, pad, pad)
        addView(shell, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val videoHost = RoundHost(context)
        videoHost.setCornerRadiusDp(16)
        shell.addView(videoHost, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        videoHost.addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        surface.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, w: Int, h: Int) {
                surfaceReady = true
                pending?.let {
                    pending = null
                    bind(it)
                }
            }

            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, w: Int, h: Int) = Unit

            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                surfaceReady = false
                releasePlayers()
                return true
            }

            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
        }

        hint.setTextColor(look.textColor())
        hint.textSize = look.sp(12f)
        hint.typeface = look.typeface(true)
        hint.gravity = Gravity.CENTER
        hint.setShadowLayer(6f, 0f, 1f, look.shadow())
        hint.setPadding(context.dp(8), context.dp(4), context.dp(8), context.dp(4))
        hint.background = context.glass(12, "#66000000")
        hint.visibility = View.GONE
        val hintLp = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        hintLp.bottomMargin = context.dp(10)
        addView(hint, hintLp)

        setOnFocusChangeListener { _, has -> paintFocus(has) }
        setOnKeyListener { _, keyCode, event ->
            if (!isOk(keyCode)) return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) toggle()
            true
        }
        setOnClickListener { toggle() }
        ensureLocal()
    }

    fun applyLook() {
        look = Look(context)
        paintFocus(isFocused)
        hint.setTextColor(look.textColor())
        hint.textSize = look.sp(12f)
        hint.typeface = look.typeface(true)
        hint.setShadowLayer(6f, 0f, 1f, look.shadow())
        applyVisibility()
    }

    fun applyVisibility() {
        val show = Look(context).clipVisible()
        visibility = if (show) View.VISIBLE else View.GONE
        isFocusable = show
        if (!show) pause()
        else if (wantPlay && (media != null || exo != null)) play()
    }

    fun reloadFromPrefs() {
        ensureLocal()
    }

    fun play() {
        wantPlay = true
        playing = true
        try {
            media?.start()
            audio?.start()
            exo?.play()
        } catch (_: Exception) {
        }
        paintHint()
    }

    fun pause() {
        wantPlay = false
        playing = false
        try {
            if (media?.isPlaying == true) media?.pause()
            if (audio?.isPlaying == true) audio?.pause()
            exo?.pause()
        } catch (_: Exception) {
        }
        paintHint()
    }

    fun toggle() {
        if (media == null && exo == null) {
            ensureLocal()
            return
        }
        if (playing) pause() else play()
    }

    fun release() {
        releasePlayers()
    }

    private fun ensureLocal() {
        status = "Cargando…"
        paintHint()
        ClipStore.ensure(
            context,
            onReady = { files ->
                main.post {
                    if (!surfaceReady) pending = files else bind(files)
                }
            },
            onStatus = { message ->
                main.post {
                    status = message
                    paintHint()
                }
            }
        )
    }

    private fun bind(files: ClipStore.ClipFiles) {
        if (!surfaceReady) {
            pending = files
            return
        }
        releasePlayers()
        if (files.audio == null) {
            bindMediaPlayer(files)
        } else {
            bindExo(files)
        }
    }

    private fun bindMediaPlayer(files: ClipStore.ClipFiles) {
        val texture = surface.surfaceTexture ?: run {
            pending = files
            return
        }
        try {
            val player = MediaPlayer()
            player.setDataSource(context, Uri.fromFile(files.video))
            player.setSurface(Surface(texture))
            player.isLooping = true
            player.setOnPreparedListener { prepared ->
                prepared.isLooping = true
                status = "Listo"
                if (wantPlay) {
                    try {
                        prepared.start()
                        playing = true
                    } catch (_: Exception) {
                    }
                }
                paintHint()
            }
            player.setOnCompletionListener { ended ->
                try {
                    ended.seekTo(0)
                    ended.isLooping = true
                    if (wantPlay) ended.start()
                } catch (_: Exception) {
                }
            }
            player.setOnErrorListener { _, _, _ ->
                status = "Error de video"
                paintHint()
                true
            }
            player.prepareAsync()
            media = player
        } catch (_: Exception) {
            status = "Error de video"
            paintHint()
        }
    }

    private fun bindExo(files: ClipStore.ClipFiles) {
        val player = ExoPlayer.Builder(context).build()
        exo = player
        player.setVideoTextureView(surface)
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                paintHint()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) status = "Listo"
            }
        })
        val factory = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))
        val videoSource = factory.createMediaSource(MediaItem.fromUri(Uri.fromFile(files.video)))
        val audioSource = factory.createMediaSource(
            MediaItem.Builder()
                .setUri(Uri.fromFile(files.audio))
                .setMimeType(MimeTypes.AUDIO_AAC)
                .build()
        )
        player.setMediaSource(MergingMediaSource(videoSource, audioSource))
        player.prepare()
        if (wantPlay) player.play()
        paintHint()
    }

    private fun releasePlayers() {
        try {
            media?.release()
        } catch (_: Exception) {
        }
        try {
            audio?.release()
        } catch (_: Exception) {
        }
        try {
            exo?.release()
        } catch (_: Exception) {
        }
        media = null
        audio = null
        exo = null
    }

    private fun paintFocus(has: Boolean) {
        shell.background = context.glassArgb(22, look.cardArgb(has))
        val scale = if (has) 1.04f else 1f
        animate().scaleX(scale).scaleY(scale).setDuration(140L).start()
        paintHint()
        hint.visibility = if (has) View.VISIBLE else View.GONE
    }

    private fun paintHint() {
        hint.text = when {
            media == null && exo == null -> status
            playing -> "OK · pausa"
            else -> "OK · play"
        }
    }

    private fun isOk(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            keyCode == KeyEvent.KEYCODE_BUTTON_A
    }

    private fun roundOutline(view: View, radiusDp: Int) {
        val radius = context.dp(radiusDp).toFloat()
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, radius)
            }
        }
        view.clipToOutline = true
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> v.invalidateOutline() }
    }

    companion object {
        fun url(context: Context): String = ClipStore.url(context)
        fun saveUrl(context: Context, raw: String): Boolean = ClipStore.saveUrl(context, raw)
    }
}
