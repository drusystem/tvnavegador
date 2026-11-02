package com.tvdonghua.viewanime

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

class SourceActivity : Activity() {
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ink.bg)
        root.setPadding(dp(36), dp(24), dp(36), dp(24))

        root.addView(label("Agregar lista", 26f, Color.WHITE, true))
        root.addView(label("Pega la página de capítulos y un capítulo de ejemplo. Si el sitio es SeriesDonghua o JKAnime, se usa su lectura.", 15f, Ink.mute).apply {
            setPadding(0, dp(8), 0, dp(16))
        })

        val listInput = field("https://sitio/capitulos")
        val sampleInput = field("https://sitio/serie/capitulo")
        root.addView(label("Lista de capítulos", 14f, Ink.cyan, true))
        root.addView(listInput, fieldGap())
        root.addView(label("Un capítulo", 14f, Ink.cyan, true).apply { setPadding(0, dp(12), 0, 0) })
        root.addView(sampleInput, fieldGap())
        root.addView(chip("Guardar") {
            val listUrl = normalize(listInput.text.toString())
            val sampleUrl = normalize(sampleInput.text.toString())
            if (listUrl == null || sampleUrl == null) return@chip
            Shelf.add(this, listUrl, sampleUrl)
            listInput.setText("")
            sampleInput.setText("")
            paint()
        }.apply { setPadding(0, dp(16), 0, 0) })

        root.addView(label("Listas guardadas", 16f, Color.WHITE, true).apply {
            setPadding(0, dp(22), 0, dp(8))
        })
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(this)
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        paint()
        listInput.requestFocus()
    }

    private fun paint() {
        list.removeAllViews()
        val saved = Shelf.load(this)
        if (saved.isEmpty()) {
            list.addView(label("Todavía no hay listas propias.", 14f, Ink.mute))
            return
        }
        saved.forEach { item ->
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = Gravity.CENTER_VERTICAL
            line.setPadding(0, dp(6), 0, dp(6))
            val name = label(item.name, 16f, Color.WHITE, true)
            line.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(chip("Quitar") {
                Shelf.remove(this, item.id)
                paint()
            })
            list.addView(line)
        }
    }

    private fun field(hint: String): EditText {
        val input = EditText(this)
        input.hint = hint
        input.setTextColor(Color.WHITE)
        input.setHintTextColor(Ink.mute)
        input.inputType = InputType.TYPE_TEXT_VARIATION_URI
        input.setSingleLine(true)
        input.setBackgroundColor(0xFF161A24.toInt())
        input.setPadding(dp(14), dp(12), dp(14), dp(12))
        return input
    }

    private fun fieldGap(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        }
    }

    private fun normalize(raw: String): String? {
        var url = raw.trim()
        if (url.isEmpty()) return null
        if (!url.contains("://")) url = "https://$url"
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        return url
    }
}
