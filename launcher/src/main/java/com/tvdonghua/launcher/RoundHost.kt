package com.tvdonghua.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.widget.FrameLayout

/** Recorta hijos con esquinas redondeadas (sirve para TextureView). */
internal class RoundHost(context: Context) : FrameLayout(context) {
    private val path = Path()
    private val rect = RectF()
    private var radiusPx = 0f

    fun setCornerRadiusDp(radiusDp: Int) {
        radiusPx = context.dp(radiusDp).toFloat()
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        path.reset()
        path.addRoundRect(rect, radiusPx, radiusPx, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(path)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }
}
