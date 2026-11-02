package com.tvdonghua.viewanime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.BitmapFactory
import android.util.LruCache
import java.util.concurrent.Executors

object Ink {
    const val bg = 0xFF07080D.toInt()
    const val card = 0xFF161A24.toInt()
    const val cardOn = 0xFF243044.toInt()
    const val line = 0xFF2A3142.toInt()
    const val text = 0xFFF4F7FB.toInt()
    const val mute = 0xFF93A0B5.toInt()
    const val cyan = 0xFF3EE0F0.toInt()
    const val gold = 0xFFE2B657.toInt()
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun plate(radius: Float, focused: Boolean, active: Boolean, stroke: Int = 2): GradientDrawable {
    val draw = GradientDrawable()
    draw.cornerRadius = radius
    draw.setColor(if (focused || active) Ink.cardOn else Ink.card)
    draw.setStroke(stroke.coerceAtLeast(1), if (focused) Ink.cyan else Ink.line)
    return draw
}

fun Context.label(text: String, size: Float, color: Int, bold: Boolean = false): TextView {
    val view = TextView(this)
    view.text = text
    view.setTextColor(color)
    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
    if (bold) view.typeface = Typeface.DEFAULT_BOLD
    return view
}

fun Context.chip(text: String, onClick: () -> Unit): TextView {
    val view = label(text, 16f, Ink.text, true)
    view.gravity = Gravity.CENTER
    view.setPadding(dp(18), dp(10), dp(18), dp(10))
    view.isFocusable = true
    view.isFocusableInTouchMode = true
    view.isClickable = true
    view.background = plate(dp(22).toFloat(), false, false, dp(2))
    view.setOnFocusChangeListener { _, focused ->
        view.background = plate(dp(22).toFloat(), focused, view.tag == true, if (focused) dp(5) else dp(2))
        view.setTextColor(if (focused || view.tag == true) Color.WHITE else Ink.mute)
        view.scaleX = if (focused) 1.08f else 1f
        view.scaleY = if (focused) 1.08f else 1f
    }
    view.setOnClickListener { onClick() }
    return view
}

fun TextView.mark(active: Boolean) {
    tag = active
    setTextColor(if (active || isFocused) Color.WHITE else Ink.mute)
    val density = resources.displayMetrics.density
    val radius = 22f * density
    background = plate(radius, isFocused, active, ((if (isFocused) 5f else 2f) * density).toInt())
}

object Posters {
    private val pool = Executors.newFixedThreadPool(2)
    private val cache = LruCache<String, android.graphics.Bitmap>(28)

    fun load(url: String?, into: ImageView) {
        if (url.isNullOrBlank()) return
        into.tag = url
        cache.get(url)?.let {
            into.setImageBitmap(it)
            return
        }
        pool.execute {
            try {
                val bytes = Net.bytes(url)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@execute
                cache.put(url, bitmap)
                into.post {
                    if (into.tag == url) into.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                // La ficha sigue siendo usable sin carátula.
            }
        }
    }
}

fun Context.poster(card: ShowCard, onClick: () -> Unit, onFocus: () -> Unit = {}): LinearLayout {
    val column = LinearLayout(this)
    column.orientation = LinearLayout.VERTICAL
    column.isFocusable = true
    column.isFocusableInTouchMode = true
    column.isClickable = true
    column.contentDescription = card.title
    column.setPadding(dp(6), dp(6), dp(6), dp(8))
    column.background = plate(dp(16).toFloat(), false, false, dp(2))
    val image = ImageView(this)
    image.scaleType = ImageView.ScaleType.CENTER_CROP
    image.setBackgroundColor(0xFF10141C.toInt())
    column.addView(image, LinearLayout.LayoutParams(dp(148), dp(148)))
    val badge = label(card.badge.ifBlank { " " }, 14f, Ink.gold, true)
    badge.setPadding(0, dp(6), 0, 0)
    column.addView(badge)
    val title = label(card.title, 16f, Ink.text, true)
    title.maxLines = 2
    title.ellipsize = android.text.TextUtils.TruncateAt.END
    title.setPadding(0, dp(2), 0, 0)
    column.addView(title)
    column.setOnFocusChangeListener { _, focused ->
        column.background = plate(dp(16).toFloat(), focused, false, if (focused) dp(5) else dp(2))
        column.scaleX = if (focused) 1.08f else 1f
        column.scaleY = if (focused) 1.08f else 1f
        if (focused) onFocus()
    }
    column.setOnClickListener { onClick() }
    Posters.load(card.image, image)
    val gap = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    gap.marginEnd = dp(12)
    gap.bottomMargin = dp(12)
    column.layoutParams = gap
    return column
}
