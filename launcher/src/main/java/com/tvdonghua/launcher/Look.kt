package com.tvdonghua.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface

internal class Look(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun textColor(): Int = prefs.getInt(TEXT, Color.WHITE)
    fun textScale(): Int = prefs.getInt(TEXT_SCALE, 100).coerceIn(70, 160)
    fun fontKey(): String = prefs.getString(FONT, "default") ?: "default"
    fun cardScale(): Int = prefs.getInt(CARD_SCALE, 100).coerceIn(60, 160)
    fun iconScale(): Int = prefs.getInt(ICON_SCALE, 100).coerceIn(50, 160)
    fun cardRgb(): Int = opaque(prefs.getInt(CARD, Color.WHITE))
    fun cardOpacity(): Int = prefs.getInt(CARD_OPACITY, 22).coerceIn(4, 90)
    fun cardFocusOpacity(): Int = prefs.getInt(CARD_FOCUS, 38).coerceIn(8, 95)
    fun panelRgb(): Int = opaque(prefs.getInt(PANEL, Color.parseColor("#F4F0FA")))
    fun panelOpacity(): Int = prefs.getInt(PANEL_OPACITY, 22).coerceIn(4, 92)
    fun bgKey(): String = prefs.getString(BG, BG_LEVITATE) ?: BG_LEVITATE
    fun clipVisible(): Boolean = prefs.getBoolean(CLIP_VISIBLE, true)
    fun bgSparksUi(): Boolean = bgKey() == BG_LEVITATE

    fun setTextColor(index: Int) = put(TEXT, palette[wrap(index, palette.size)].second)
    fun setTextScale(value: Int) = put(TEXT_SCALE, value.coerceIn(70, 160))
    fun setFont(index: Int) = prefs.edit().putString(FONT, fonts[wrap(index, fonts.size)].first).apply()
    fun setCardScale(value: Int) = put(CARD_SCALE, value.coerceIn(60, 160))
    fun setIconScale(value: Int) = put(ICON_SCALE, value.coerceIn(50, 160))
    fun setCardColor(index: Int) = put(CARD, palette[wrap(index, palette.size)].second)
    fun setCardOpacity(value: Int) = put(CARD_OPACITY, value.coerceIn(4, 90))
    fun setCardFocusOpacity(value: Int) = put(CARD_FOCUS, value.coerceIn(8, 95))
    fun setPanelColor(index: Int) = put(PANEL, palette[wrap(index, palette.size)].second)
    fun setPanelOpacity(value: Int) = put(PANEL_OPACITY, value.coerceIn(4, 92))
    fun setBg(index: Int) = prefs.edit().putString(BG, backgrounds[wrap(index, backgrounds.size)].first).apply()
    fun setClipVisible(visible: Boolean) = prefs.edit().putBoolean(CLIP_VISIBLE, visible).apply()

    fun stepTextColor(delta: Int) = setTextColor(colorIndex(textColor()) + delta)
    fun stepFont(delta: Int) = setFont(fontIndex() + delta)
    fun stepCardColor(delta: Int) = setCardColor(colorIndex(cardRgb()) + delta)
    fun stepPanelColor(delta: Int) = setPanelColor(colorIndex(panelRgb()) + delta)
    fun stepBg(delta: Int) = setBg(bgIndex() + delta)
    fun stepClipVisible(@Suppress("UNUSED_PARAMETER") delta: Int) = setClipVisible(!clipVisible())

    fun textName(): String = colorName(textColor())
    fun cardName(): String = colorName(cardRgb())
    fun panelName(): String = colorName(panelRgb())
    fun fontName(): String = fonts[fontIndex()].second
    fun bgName(): String = backgrounds[bgIndex()].second
    fun clipVisibleName(): String = if (clipVisible()) "Visible" else "Oculto"

    fun bgRawId(): Int = when (bgKey()) {
        BG_DANCE -> R.raw.bg_home_dance
        BG_LEVITATE -> R.raw.bg_home_levitate
        else -> R.raw.bg_home_breeze
    }

    fun sp(base: Float): Float = base * textScale() / 100f

    fun cardWidthDp(): Int = (104 * cardScale() / 100f).toInt().coerceIn(72, 170)
    fun cardHeightDp(): Int = (84 * cardScale() / 100f).toInt().coerceIn(58, 140)
    fun iconDp(): Int = (52 * iconScale() / 100f).toInt().coerceIn(28, cardHeightDp() - 16)

    fun typeface(bold: Boolean): Typeface {
        val base = when (fontKey()) {
            "serif" -> Typeface.SERIF
            "mono" -> Typeface.MONOSPACE
            "casual" -> Typeface.create("casual", Typeface.NORMAL)
            "condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            "sans" -> Typeface.SANS_SERIF
            else -> Typeface.DEFAULT
        }
        return if (bold) Typeface.create(base, Typeface.BOLD) else base
    }

    fun panelArgb(): Int = withAlpha(panelRgb(), panelOpacity())
    fun cardArgb(focused: Boolean): Int {
        val opacity = if (focused) cardFocusOpacity() else cardOpacity()
        return withAlpha(cardRgb(), opacity)
    }

    fun shadow(): Int {
        val color = textColor()
        val light = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000 > 150
        return if (light) Color.parseColor("#B4000000") else Color.parseColor("#66FFFFFF")
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    fun signature(): String {
        return listOf(
            textColor(), textScale(), fontKey(), cardScale(), iconScale(),
            cardRgb(), cardOpacity(), cardFocusOpacity(), panelRgb(), panelOpacity(),
            bgKey(), clipVisible()
        ).joinToString(",")
    }

    private fun fontIndex(): Int {
        val key = fontKey()
        val index = fonts.indexOfFirst { it.first == key }
        return if (index < 0) 0 else index
    }

    private fun bgIndex(): Int {
        val key = bgKey()
        val index = backgrounds.indexOfFirst { it.first == key }
        return if (index < 0) 0 else index
    }

    private fun colorIndex(color: Int): Int {
        val rgb = color and 0xFFFFFF
        val index = palette.indexOfFirst { (it.second and 0xFFFFFF) == rgb }
        return if (index < 0) 0 else index
    }

    private fun colorName(color: Int): String {
        val rgb = color and 0xFFFFFF
        return palette.firstOrNull { (it.second and 0xFFFFFF) == rgb }?.first ?: "Color"
    }

    private fun put(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    private fun opaque(color: Int): Int = color or 0xFF000000.toInt()

    private fun withAlpha(color: Int, percent: Int): Int {
        val alpha = (percent.coerceIn(0, 100) * 255 / 100)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun wrap(index: Int, size: Int): Int = Math.floorMod(index, size)

    companion object {
        private const val PREFS = "look"
        private const val TEXT = "text"
        private const val TEXT_SCALE = "textScale"
        private const val FONT = "font"
        private const val CARD_SCALE = "cardScale"
        private const val ICON_SCALE = "iconScale"
        private const val CARD = "card"
        private const val CARD_OPACITY = "cardOpacity"
        private const val CARD_FOCUS = "cardFocus"
        private const val PANEL = "panel"
        private const val PANEL_OPACITY = "panelOpacity"
        private const val BG = "bg"
        private const val CLIP_VISIBLE = "clipVisible"
        const val BG_BREEZE = "breeze"
        const val BG_DANCE = "dance"
        const val BG_LEVITATE = "levitate"

        val backgrounds = listOf(
            BG_BREEZE to "Brisa",
            BG_DANCE to "Danza",
            BG_LEVITATE to "Levitar"
        )

        val palette = listOf(
            "Blanco" to Color.WHITE,
            "Lavanda" to Color.parseColor("#F4F0FA"),
            "Crema" to Color.parseColor("#FFF4E0"),
            "Amarillo" to Color.parseColor("#FFE08A"),
            "Naranja" to Color.parseColor("#FFB15A"),
            "Rosa" to Color.parseColor("#FF8AD4"),
            "Rojo" to Color.parseColor("#FF6B6B"),
            "Cian" to Color.parseColor("#7EE7FF"),
            "Azul" to Color.parseColor("#8AB4FF"),
            "Violeta" to Color.parseColor("#C9A6FF"),
            "Verde" to Color.parseColor("#9DFFB0"),
            "Negro" to Color.parseColor("#1A1230")
        )

        val fonts = listOf(
            "default" to "Normal",
            "sans" to "Sans",
            "serif" to "Serif",
            "mono" to "Mono",
            "casual" to "Casual",
            "condensed" to "Condensada"
        )
    }
}
