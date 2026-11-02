package com.tvdonghua.launcher

import android.service.dreams.DreamService
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ForestDream : DreamService() {
    private lateinit var clock: TextView

    private val tick = object : Runnable {
        override fun run() {
            if (!::clock.isInitialized) return
            clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            clock.postDelayed(this, 1000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val root = FrameLayout(this)
        val image = ImageView(this)
        image.setImageResource(R.drawable.bg_home)
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        root.addView(image, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        val look = Look(this)
        clock = TextView(this)
        clock.setTextColor(look.textColor())
        clock.textSize = look.sp(42f)
        clock.typeface = look.typeface(true)
        clock.setShadowLayer(10f, 0f, 2f, look.shadow())
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        )
        lp.topMargin = dp(28)
        lp.marginEnd = dp(36)
        root.addView(clock, lp)
        setContentView(root)
        tick.run()
    }

    override fun onDetachedFromWindow() {
        if (::clock.isInitialized) clock.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }
}
