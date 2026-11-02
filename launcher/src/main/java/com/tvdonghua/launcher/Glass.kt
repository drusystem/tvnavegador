package com.tvdonghua.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal fun Context.glass(radiusDp: Int, color: String, strokeDp: Int = 0): GradientDrawable {
    return glassArgb(radiusDp, Color.parseColor(color), strokeDp)
}

internal fun Context.glassArgb(radiusDp: Int, color: Int, strokeDp: Int = 0): GradientDrawable {
    val drawable = GradientDrawable()
    drawable.cornerRadius = dp(radiusDp).toFloat()
    drawable.setColor(color)
    if (strokeDp > 0) drawable.setStroke(dp(strokeDp), Color.WHITE)
    return drawable
}
