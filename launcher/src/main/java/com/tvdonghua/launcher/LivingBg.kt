package com.tvdonghua.launcher

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView

/** Escena Frieren animada en loop infinito (brisa o danza). */
internal class LivingBg(context: Context) : FrameLayout(context) {
    private val still = ImageView(context)
    private val surface = TextureView(context)
    private var player: MediaPlayer? = null
    private var wantPlay = true
    private var surfaceReady = false
    private var rawId = Look(context).bgRawId()

    init {
        clipChildren = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        still.setImageResource(R.drawable.bg_home)
        still.scaleType = ImageView.ScaleType.CENTER_CROP
        still.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(still, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        surface.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(surface, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
        surface.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, w: Int, h: Int) {
                surfaceReady = true
                bindPlayer()
            }

            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, w: Int, h: Int) {
                fitCrop()
            }

            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                surfaceReady = false
                releasePlayer(keepStill = true)
                return true
            }

            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitCrop()
    }

    fun applyLook() {
        val next = Look(context).bgRawId()
        if (next == rawId && player != null) return
        rawId = next
        still.alpha = 1f
        still.visibility = VISIBLE
        releasePlayer(keepStill = true)
        if (surfaceReady && wantPlay) bindPlayer()
    }

    fun startMotion() {
        wantPlay = true
        applyLook()
        val media = player
        if (media == null) {
            if (surfaceReady) bindPlayer()
            return
        }
        try {
            if (!media.isPlaying) media.start()
        } catch (_: Exception) {
            releasePlayer(keepStill = false)
            if (surfaceReady) bindPlayer()
        }
    }

    fun stopMotion() {
        wantPlay = false
        try {
            if (player?.isPlaying == true) player?.pause()
        } catch (_: Exception) {
        }
    }

    private fun bindPlayer() {
        if (!surfaceReady || player != null) return
        val texture = surface.surfaceTexture ?: return
        try {
            val media = MediaPlayer()
            media.setDataSource(context, Uri.parse("android.resource://${context.packageName}/$rawId"))
            media.setSurface(Surface(texture))
            media.isLooping = true
            media.setVolume(0f, 0f)
            media.setOnPreparedListener { prepared ->
                prepared.isLooping = true
                fitCrop()
                still.animate().alpha(0f).setDuration(500L).withEndAction {
                    still.visibility = GONE
                }.start()
                if (wantPlay) {
                    try {
                        prepared.start()
                    } catch (_: Exception) {
                    }
                }
            }
            media.setOnCompletionListener { ended ->
                try {
                    ended.seekTo(0)
                    ended.isLooping = true
                    if (wantPlay) ended.start()
                } catch (_: Exception) {
                    releasePlayer(keepStill = false)
                    if (surfaceReady && wantPlay) bindPlayer()
                }
            }
            media.setOnErrorListener { _, _, _ ->
                still.alpha = 1f
                still.visibility = VISIBLE
                releasePlayer(keepStill = true)
                postDelayed({
                    if (wantPlay && surfaceReady && player == null) bindPlayer()
                }, 1500L)
                true
            }
            media.prepareAsync()
            player = media
        } catch (_: Exception) {
            still.alpha = 1f
            still.visibility = VISIBLE
        }
    }

    private fun fitCrop() {
        val vw = width.takeIf { it > 0 } ?: return
        val vh = height.takeIf { it > 0 } ?: return
        val videoW = (player?.videoWidth ?: 0).takeIf { it > 0 } ?: 1280
        val videoH = (player?.videoHeight ?: 0).takeIf { it > 0 } ?: 720
        val scale = maxOf(vw.toFloat() / videoW, vh.toFloat() / videoH)
        surface.scaleX = (videoW * scale) / vw
        surface.scaleY = (videoH * scale) / vh
    }

    private fun releasePlayer(keepStill: Boolean) {
        try {
            player?.setOnCompletionListener(null)
            player?.setOnErrorListener(null)
            player?.setOnPreparedListener(null)
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        if (keepStill) {
            still.alpha = 1f
            still.visibility = VISIBLE
        }
    }

    override fun onDetachedFromWindow() {
        releasePlayer(keepStill = true)
        super.onDetachedFromWindow()
    }

    companion object {
        fun attach(parent: ViewGroup, index: Int = 0): LivingBg {
            val living = LivingBg(parent.context)
            parent.addView(
                living,
                index,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            return living
        }
    }
}
