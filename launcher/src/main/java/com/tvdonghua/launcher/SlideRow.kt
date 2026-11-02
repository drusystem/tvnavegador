package com.tvdonghua.launcher

import android.content.Context
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout

internal class SlideRow(context: Context) : FrameLayout(context) {
    private val viewport = object : FrameLayout(context) {
        override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
            val save = canvas.save()
            canvas.clipRect(0, 0, width, height)
            val drawn = super.drawChild(canvas, child, drawingTime)
            canvas.restoreToCount(save)
            return drawn
        }
    }.apply {
        clipChildren = true
        clipToPadding = true
    }
    val strip: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.TOP
        clipChildren = false
    }
    var endView: View? = null
        private set
    private var parked: View? = null
    private var retries = 0

    private var plusInline = true

    init {
        clipChildren = false
        clipToPadding = false
        val pad = context.dp(8)
        setPadding(context.dp(28), pad, context.dp(16), pad)
        viewport.addView(strip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(viewport, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun attachPlus(view: View) {
        endView?.let { old ->
            (old.parent as? android.view.ViewGroup)?.removeView(old)
        }
        endView = view
        plusInline = true
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(context.dp(200))
        val plus = endView
        val plusSize = context.dp(60)
        val nameExtra = context.dp(22)
        val stripHeight = context.dp(cardH()) + nameExtra + paddingTop + paddingBottom
        val room = width - paddingLeft - paddingRight
        val gap = context.dp(8)
        strip.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(stripHeight, MeasureSpec.AT_MOST)
        )
        val plusInStrip = plus != null && plus.parent === strip
        val cardsW = if (plusInStrip) {
            (strip.measuredWidth - plus.measuredWidth - plusMargins(plus)).coerceAtLeast(0)
        } else {
            strip.measuredWidth
        }
        val inline = plus == null || cardsW + plusSize + gap <= room
        plusInline = inline
        if (plus != null && inline && plus.parent !== strip) {
            (plus.parent as? android.view.ViewGroup)?.removeView(plus)
            strip.addView(plus)
            strip.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(stripHeight, MeasureSpec.AT_MOST)
            )
        } else if (plus != null && !inline) {
            if (plus.parent === strip) {
                strip.removeView(plus)
                strip.measure(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(stripHeight, MeasureSpec.AT_MOST)
                )
            }
            if (plus.parent !== this) addView(plus)
            plus.measure(
                MeasureSpec.makeMeasureSpec(plusSize, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(plusSize, MeasureSpec.EXACTLY)
            )
        }
        val contentH = maxOf(strip.measuredHeight, plusSize) + paddingTop + paddingBottom
        setMeasuredDimension(width, contentH)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val plus = endView
        val plusSize = context.dp(60)
        val plusTop = paddingTop + (context.dp(cardH()) - plusSize) / 2
        if (plus != null && !plusInline) {
            val gap = context.dp(8)
            val limit = width - paddingLeft - paddingRight - plusSize - gap
            var used = 0
            for (i in 0 until strip.childCount) {
                val card = strip.getChildAt(i)
                val lp = card.layoutParams as? android.view.ViewGroup.MarginLayoutParams
                val w = (lp?.marginStart ?: 0) + card.measuredWidth + (lp?.marginEnd ?: 0)
                if (used + w > limit && used > 0) break
                used += w
            }
            val viewRight = paddingLeft + used.coerceAtLeast(plusSize)
            plus.layout(viewRight + gap, plusTop, viewRight + gap + plusSize, plusTop + plusSize)
            plus.bringToFront()
            viewport.layout(paddingLeft, 0, viewRight, height)
        } else {
            viewport.layout(paddingLeft, 0, width - paddingRight, height)
        }
        strip.layout(0, paddingTop, strip.measuredWidth, paddingTop + strip.measuredHeight)
    }

    private fun plusMargins(plus: View): Int {
        val lp = plus.layoutParams as? android.view.ViewGroup.MarginLayoutParams ?: return 0
        return lp.marginStart + lp.marginEnd
    }

    private fun cardW(): Int = Look(context).cardWidthDp()

    private fun cardH(): Int = Look(context).cardHeightDp()

    fun park(child: View) {
        parked = child
        if (!isLaidOut || (strip.width == 0 && child !== endView)) {
            if (retries++ < 8) post { if (parked === child) park(child) }
            return
        }
        retries = 0
        val pad = context.dp(8).toFloat()
        val limit = viewport.width.toFloat().coerceAtLeast(pad + 1f)
        val dest = if (strip.width <= limit - pad) {
            0f
        } else if (child === endView) {
            (limit - strip.width).coerceAtMost(0f)
        } else {
            var next = strip.translationX
            val left = child.left.toFloat()
            val right = child.right.toFloat()
            if (left + next < pad) next = pad - left
            if (right + next > limit - pad) next = limit - pad - right
            snap(next, pad, limit, child)
        }
        if (kotlin.math.abs(strip.translationX - dest) < 2f) return
        strip.animate().translationX(dest).setDuration(160L).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun snap(preferred: Float, pad: Float, limit: Float, focus: View): Float {
        val minTx = (limit - strip.width).coerceAtMost(0f)
        val maxTx = 0f
        var best = preferred.coerceIn(minTx, maxTx)
        var bestDist = Float.MAX_VALUE
        for (i in 0 until strip.childCount) {
            val card = strip.getChildAt(i)
            val candidate = (pad - card.left).coerceIn(minTx, maxTx)
            val focusLeft = focus.left + candidate
            val focusRight = focus.right + candidate
            if (focusLeft < pad - 2f || focusRight > limit - pad + 2f) continue
            val dist = kotlin.math.abs(candidate - preferred)
            if (dist < bestDist) {
                bestDist = dist
                best = candidate
            }
        }
        return best
    }
}
