package com.tvdonghua.launcher

import android.service.dreams.DreamService
import android.widget.ImageView

class ForestDream : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val image = ImageView(this)
        image.setImageResource(R.drawable.bg_home)
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        setContentView(image)
    }
}
