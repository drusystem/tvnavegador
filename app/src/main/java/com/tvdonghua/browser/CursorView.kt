package com.tvdonghua.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Cursor tipo flecha que se dibuja encima del navegador y se mueve con el D-pad. */
class CursorView(context: Context) : View(context) {

    var cx = 200f
    var cy = 200f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val path = Path()

    init {
        isFocusable = false
        isClickable = false
    }

    fun moveBy(dx: Float, dy: Float) {
        cx = (cx + dx).coerceIn(0f, (width - 1).toFloat().coerceAtLeast(0f))
        cy = (cy + dy).coerceIn(0f, (height - 1).toFloat().coerceAtLeast(0f))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val s = resources.displayMetrics.density * 1.4f
        path.reset()
        path.moveTo(cx, cy)
        path.lineTo(cx, cy + 22f * s)
        path.lineTo(cx + 6f * s, cy + 17f * s)
        path.lineTo(cx + 11f * s, cy + 27f * s)
        path.lineTo(cx + 15f * s, cy + 25f * s)
        path.lineTo(cx + 10f * s, cy + 15f * s)
        path.lineTo(cx + 18f * s, cy + 15f * s)
        path.close()
        canvas.drawPath(path, fill)
        canvas.drawPath(path, stroke)
    }
}
