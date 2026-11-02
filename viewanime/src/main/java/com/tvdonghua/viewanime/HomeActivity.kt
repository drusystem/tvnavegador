package com.tvdonghua.viewanime

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class HomeActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var clock: TextView
    private lateinit var chips: LinearLayout
    private lateinit var tabs: LinearLayout
    private lateinit var finder: LinearLayout
    private lateinit var query: EditText
    private lateinit var grid: LinearLayout
    private lateinit var status: TextView
    private lateinit var picked: TextView
    private lateinit var footer: LinearLayout
    private var sourceId = "sd"
    private var pageUrl: String? = null
    private var tab: String? = null
    private var section = "latest"
    private var directoryUrl: String? = null
    private var shelfMark = ""

    private val tick = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            ui.postDelayed(this, 30000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ink.bg)
        root.setPadding(dp(28), dp(18), dp(28), dp(12))

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val brand = label("View", 26f, Color.WHITE, true)
        val accent = label("Anime", 26f, Ink.cyan, true)
        header.addView(brand)
        header.addView(accent)
        clock = label("", 18f, Ink.mute, true)
        header.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        header.addView(clock)
        root.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        chips = row()
        root.addView(scroll(chips), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
        })

        tabs = row()
        root.addView(scroll(tabs), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        })

        finder = LinearLayout(this)
        finder.orientation = LinearLayout.HORIZONTAL
        finder.gravity = Gravity.CENTER_VERTICAL
        finder.visibility = View.GONE
        query = EditText(this)
        query.hint = "Nombre del anime"
        query.setTextColor(Color.WHITE)
        query.setHintTextColor(Ink.mute)
        query.textSize = 18f
        query.setSingleLine(true)
        query.setBackgroundColor(Ink.card)
        query.setPadding(dp(14), dp(10), dp(14), dp(10))
        query.imeOptions = EditorInfo.IME_ACTION_SEARCH
        query.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                runSearch()
                true
            } else {
                false
            }
        }
        finder.addView(query, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        finder.addView(chip("Buscar") { runSearch() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(10)
        })
        root.addView(finder, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        })

        status = label("Cargando capítulos...", 18f, Ink.mute)
        root.addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
            bottomMargin = dp(4)
        })
        picked = label(" ", 22f, Color.WHITE, true)
        picked.maxLines = 1
        picked.ellipsize = android.text.TextUtils.TruncateAt.END
        root.addView(picked, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        })

        grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        val scroller = ScrollView(this)
        scroller.isFocusable = false
        scroller.addView(grid)
        root.addView(scroller, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        footer = row()
        root.addView(footer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        })

        setContentView(root)
        ui.post(tick)
        buildChips()
        load()
    }

    override fun onResume() {
        super.onResume()
        val mark = Shelf.load(this).joinToString { it.id }
        if (mark != shelfMark) {
            shelfMark = mark
            if (!Library.all(this).any { it.id == sourceId }) sourceId = "sd"
            buildChips()
            load()
        }
    }

    private fun buildChips() {
        shelfMark = Shelf.load(this).joinToString { it.id }
        chips.removeAllViews()
        Library.all(this).forEach { source ->
            val button = chip(source.title) {
                sourceId = source.id
                pageUrl = null
                tab = null
                section = "latest"
                directoryUrl = null
                query.setText("")
                paintChips()
                load()
            }
            button.tag = source.id == sourceId
            chips.addView(button, gap())
        }
        chips.addView(chip("Navegador") {
            startActivity(Intent(this, BrowserActivity::class.java))
        }, gap())
        chips.addView(chip("Agregar lista") {
            startActivity(Intent(this, SourceActivity::class.java))
        }, gap())
        paintChips()
    }

    private fun paintChips() {
        for (i in 0 until chips.childCount) {
            val child = chips.getChildAt(i) as? TextView ?: continue
            val title = Library.all(this).getOrNull(i)?.id
            child.mark(title != null && title == sourceId)
        }
    }

    private fun load() {
        status.text = if (section == "directory") "Cargando el directorio..." else "Cargando capítulos..."
        grid.removeAllViews()
        footer.removeAllViews()
        tabs.removeAllViews()
        val id = sourceId
        val url = pageUrl
        val which = tab
        val mode = section
        io.execute {
            try {
                val page = when {
                    id == "sd" && mode == "directory" -> SeriesDonghua.directory(url)
                    id == "sd" && mode == "series" -> SeriesDonghua.load(url, null)
                    id == "jk" && mode == "directory" -> JkAnime.directory(url)
                    id == "jk" && mode == "series" -> JkAnime.episodes(url ?: "https://jkanime.net/")
                    else -> Library.open(this, id).load(url, which)
                }
                ui.post {
                    if (id != sourceId || mode != section) return@post
                    show(page)
                }
            } catch (e: Exception) {
                ui.post {
                    status.text = "No se pudo cargar la lista. Revisa la red e inténtalo de nuevo."
                }
            }
        }
    }

    private fun show(page: CatalogPage) {
        finder.visibility = if (section == "directory") View.VISIBLE else View.GONE
        picked.text = " "
        status.text = when {
            page.episodes.isEmpty() && section == "directory" -> "No encontré ese anime."
            page.episodes.isEmpty() -> "Esta lista no trajo capítulos."
            else -> page.label
        }
        tabs.removeAllViews()
        if (sourceId == "sd" || sourceId == "jk") {
            val latest = chip(if (sourceId == "jk") "Programación" else "Últimos") {
                section = "latest"
                pageUrl = null
                tab = null
                load()
            }
            latest.mark(section == "latest")
            tabs.addView(latest, gap())
            val directory = chip("Directorio") {
                section = "directory"
                pageUrl = null
                tab = null
                load()
            }
            directory.mark(section == "directory" || section == "series")
            tabs.addView(directory, gap())
        }
        page.tabs.forEach { (id, name) ->
            val button = chip(name) {
                tab = id
                load()
            }
            button.mark(id == page.activeTab)
            tabs.addView(button, gap())
        }
        grid.removeAllViews()
        var line: LinearLayout? = null
        page.episodes.forEachIndexed { index, card ->
            if (index % 5 == 0) {
                line = row()
                grid.addView(line)
            }
            line?.addView(poster(card, { open(card) }) {
                picked.text = card.title
            })
        }
        footer.removeAllViews()
        if (section == "series") {
            footer.addView(chip("Volver al directorio") {
                section = "directory"
                pageUrl = directoryUrl
                load()
            }, gap())
        }
        page.prevUrl?.let { prev ->
            footer.addView(chip("Anterior") {
                pageUrl = prev
                load()
            }, gap())
        }
        page.nextUrl?.let { next ->
            footer.addView(chip("Siguiente") {
                pageUrl = next
                load()
            }, gap())
        }
        val hint = label("OK abre el capítulo. Flechas mueven el foco.", 16f, 0xFFE7EDF5.toInt())
        hint.setPadding(dp(8), dp(8), 0, 0)
        footer.addView(hint)
        page.episodes.firstOrNull()?.let { picked.text = it.title }
        grid.post {
            val row = grid.getChildAt(0) as? LinearLayout
            val first = row?.getChildAt(0)
            if (first != null) first.requestFocus() else chips.getChildAt(0)?.requestFocus()
        }
    }

    private fun runSearch() {
        val text = query.text.toString().trim()
        section = "directory"
        pageUrl = if (text.isEmpty()) {
            null
        } else if (sourceId == "jk") {
            "https://jkanime.net/buscar/" + URLEncoder.encode(text, "UTF-8").replace("+", "%20")
        } else {
            "https://seriesdonghua.com/buscar.php?s=" + URLEncoder.encode(text, "UTF-8")
        }
        load()
    }

    private fun open(card: ShowCard) {
        if (section == "directory") {
            directoryUrl = pageUrl
            section = "series"
            pageUrl = card.href
            tab = null
            load()
            return
        }
        startActivity(Intent(this, EpisodeActivity::class.java).apply {
            putExtra("source", sourceId)
            putExtra("url", card.href)
            putExtra("title", card.title)
            putExtra("badge", card.badge)
        })
    }

    private fun row(): LinearLayout {
        val line = LinearLayout(this)
        line.orientation = LinearLayout.HORIZONTAL
        line.clipChildren = false
        line.clipToPadding = false
        return line
    }

    private fun scroll(content: View): HorizontalScrollView {
        val scroll = HorizontalScrollView(this)
        scroll.isFocusable = false
        scroll.isHorizontalScrollBarEnabled = false
        scroll.clipChildren = false
        scroll.addView(content)
        return scroll
    }

    private fun gap(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = dp(10)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            startActivity(Intent(this, BrowserActivity::class.java))
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        ui.removeCallbacks(tick)
        io.shutdownNow()
        super.onDestroy()
    }
}
