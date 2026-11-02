package com.tvdonghua.browser

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.ScreenLength
import org.mozilla.geckoview.WebExtension
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : Activity() {

    companion object {
        private var sRuntime: GeckoRuntime? = null
    }

    private lateinit var root: FrameLayout
    private lateinit var geckoView: GeckoView
    private lateinit var session: GeckoSession
    private lateinit var home: FrameLayout
    private lateinit var clock: TextView
    private lateinit var status: TextView
    private lateinit var firstTile: View
    private lateinit var seguirTile: View
    private lateinit var urlPanel: View
    private lateinit var urlInput: EditText
    private val homePills = ArrayList<View>()

    private val ui = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            ui.postDelayed(this, 1000)
        }
    }
    private var canGoBack = false
    private var currentHost: String? = null
    private var hasPage = false
    private var backDownTime = 0L
    private var backLongHandled = false
    private var browserReady = false
    private var pendingUrl: String? = null
    private var pageFullScreen = false
    private var fsPort: WebExtension.Port? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // El pref de video solo cuenta si Gecko lo lee al arrancar.
        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .configFilePath(writeGeckoConfig())
            .fissionEnabled(false)
            .build()
        val runtime = sRuntime ?: GeckoRuntime.create(applicationContext, runtimeSettings).also {
            sRuntime = it
        }

        geckoView = GeckoView(this)
        geckoView.setBackgroundColor(Color.parseColor("#101018"))
        geckoView.isFocusable = true
        geckoView.isFocusableInTouchMode = true

        home = buildHome()
        ui.post(clockTick)

        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        root.addView(geckoView, FrameLayout.LayoutParams(match, match))
        root.addView(home, FrameLayout.LayoutParams(match, match))
        setContentView(root)

        showHome(true)
        prepareBrowser(runtime)
    }

    /** Quita uBlock (en este chip congela la pagina) y recien ahi abre el motor. */
    private fun prepareBrowser(runtime: GeckoRuntime) {
        runtime.webExtensionController.list().accept({ extensions ->
            val heavy = extensions.orEmpty().filter { ext ->
                val id = ext.id.lowercase()
                id.contains("ublock") || id.contains("raymondhill")
            }
            if (heavy.isEmpty()) {
                installQualityHelper(runtime)
            } else {
                val left = AtomicInteger(heavy.size)
                heavy.forEach { ext ->
                    runtime.webExtensionController.uninstall(ext).accept(
                        { _ -> if (left.decrementAndGet() == 0) installQualityHelper(runtime) },
                        { _ -> if (left.decrementAndGet() == 0) installQualityHelper(runtime) }
                    )
                }
            }
        }, { _ ->
            installQualityHelper(runtime)
        })
    }

    private fun ensureBrowser(runtime: GeckoRuntime) {
        if (browserReady) return
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP)
            .build()
        session = GeckoSession(settings)
        setupDelegates()
        session.open(runtime)
        geckoView.setSession(session)
        browserReady = true
        pendingUrl?.let { url ->
            pendingUrl = null
            openUrl(url)
        }
    }

    private fun writeGeckoConfig(): String {
        val file = java.io.File(filesDir, "geckoview-config.yaml")
        file.writeText(
            "prefs:\n" +
                "  media.hardware-video-decoding.enabled: false\n" +
                "  media.rdd-process.enabled: false\n" +
                "  media.cache_size: 131072\n" +
                "  media.memory_cache_max_size: 65536\n" +
                "  media.cache_readahead_limit: 90\n" +
                "  media.cache_readahead_limit.cellular: 90\n" +
                "  media.cache_resume_threshold: 15\n" +
                "  media.cache_resume_threshold.cellular: 15\n" +
                "  media.decoder.skip-to-next-key-frame.enabled: false\n" +
                "  media.decoder.skip_when_video_too_slow_ms: 2500\n" +
                "  media.av1.enabled: false\n" +
                "  media.hevc.enabled: false\n" +
                "  media.mediasource.vp9.enabled: false\n" +
                "  dom.ipc.processCount: 1\n" +
                "  dom.ipc.processCount.webIsolated: 1\n" +
                "  dom.ipc.processPrelaunch.enabled: false\n" +
                "  fission.autostart: false\n" +
                "  browser.sessionhistory.max_total_viewers: 1\n" +
                "  browser.cache.disk.enable: true\n" +
                "  browser.cache.disk.capacity: 102400\n" +
                "  browser.cache.memory.capacity: 32768\n" +
                "  image.mem.surfacecache.max_size_kb: 65536\n" +
                "  image.multithreaded_decoding.limit: 2\n" +
                "  image.animation_mode: \"none\"\n" +
                "  gfx.downloadable_fonts.enabled: false\n" +
                "  nglayout.initialpaint.delay: 0\n" +
                "  accessibility.force_disabled: 1\n" +
                "  general.smoothScroll: false\n" +
                "  network.dns.disableIPv6: true\n" +
                "  network.dnsCacheExpiration: 3600\n" +
                "  network.dnsCacheEntries: 400\n" +
                "  network.predictor.enabled: false\n" +
                "  network.prefetch-next: false\n" +
                "  full-screen-api.allow-trusted-requests-only: false\n"
        )
        return file.absolutePath
    }

    // ------------------------------------------------------------------ UI de inicio

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun pillBackground(): GradientDrawable {
        val d = GradientDrawable()
        d.cornerRadius = dp(22).toFloat()
        d.setColor(Color.parseColor("#73E6D8F8"))
        return d
    }

    private fun buildHome(): FrameLayout {
        val screen = FrameLayout(this)

        val wallpaper = ImageView(this)
        wallpaper.setImageResource(R.drawable.bg_home)
        wallpaper.scaleType = ImageView.ScaleType.CENTER_CROP
        wallpaper.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        screen.addView(wallpaper, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        clock = TextView(this)
        clock.setTextColor(Color.WHITE)
        clock.textSize = 22f
        clock.setShadowLayer(10f, 0f, 2f, Color.parseColor("#88000000"))
        val clockLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        )
        clockLp.topMargin = dp(22)
        clockLp.marginEnd = dp(28)
        screen.addView(clock, clockLp)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        firstTile = addPill(row, "SeriesDonghua", R.drawable.ic_seriesdonghua, 68) {
            openUrl("https://seriesdonghua.com/")
        }
        addPill(row, "JKAnime", R.drawable.ic_jkanime, 44) {
            openUrl("https://jkanime.net/")
        }
        addPill(row, "Navegador", R.drawable.ic_navegador, 68) {
            askUrl()
        }
        seguirTile = addPill(row, "Seguir", null) { if (hasPage) showHome(false) }
        seguirTile.visibility = View.GONE
        val rowLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        rowLp.bottomMargin = dp(28)
        screen.addView(row, rowLp)

        status = TextView(this)
        status.setTextColor(Color.WHITE)
        status.textSize = 14f
        status.visibility = View.GONE
        status.setShadowLayer(8f, 0f, 1f, Color.parseColor("#88000000"))
        val statusLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        statusLp.bottomMargin = dp(118)
        screen.addView(status, statusLp)

        addPill(null, "Salir", null) { finish() }.also { salir ->
            homePills.add(salir)
            val salirLp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
            salirLp.topMargin = dp(62)
            salirLp.marginEnd = dp(28)
            screen.addView(salir, salirLp)
        }

        urlPanel = buildUrlPanel()
        screen.addView(urlPanel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        firstTile.requestFocus()
        return screen
    }

    /** La tarjeta no cambia de tamaño ni de icono. El foco solo pone una sombra blanca encima. */
    private fun addPill(parent: LinearLayout?, label: String, imageRes: Int?, iconDp: Int = 56, onClick: () -> Unit): View {
        val card = FrameLayout(this)
        card.isFocusable = true
        card.isClickable = true
        card.background = pillBackground()
        card.setOnClickListener { onClick() }

        val content = LinearLayout(this)
        content.orientation = LinearLayout.HORIZONTAL
        content.gravity = Gravity.CENTER_VERTICAL
        content.setPadding(dp(16), dp(10), dp(18), dp(10))
        content.isDuplicateParentStateEnabled = false

        if (imageRes != null) {
            val image = ImageView(this)
            image.setImageResource(imageRes)
            image.adjustViewBounds = true
            image.maxWidth = dp(108)
            image.maxHeight = dp(iconDp)
            image.scaleType = ImageView.ScaleType.FIT_CENTER
            image.setBackgroundColor(Color.TRANSPARENT)
            image.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            val imageLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(iconDp)
            )
            imageLp.marginEnd = dp(12)
            content.addView(image, imageLp)
        }

        val text = TextView(this)
        text.text = label
        text.setTextColor(Color.WHITE)
        text.typeface = android.graphics.Typeface.DEFAULT_BOLD
        text.textSize = 20f
        text.setShadowLayer(6f, 0f, 1f, Color.parseColor("#66000000"))
        content.addView(text)

        card.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // La sombra va encima de la tarjeta, sin un hijo a pantalla completa:
        // ese hijo estiraba la primera opción y tapaba el bosque.
        val veil = GradientDrawable()
        veil.cornerRadius = dp(22).toFloat()
        veil.setColor(Color.WHITE)
        veil.alpha = 0
        card.foreground = veil
        card.setOnFocusChangeListener { _, has ->
            veil.alpha = if (has) 87 else 0
            card.invalidate()
        }

        if (parent != null) {
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.marginStart = dp(8)
            lp.marginEnd = dp(8)
            parent.addView(card, lp)
            homePills.add(card)
        }
        return card
    }

    private fun buildUrlPanel(): View {
        val scrim = FrameLayout(this)
        scrim.setBackgroundColor(Color.parseColor("#66080A18"))
        scrim.visibility = View.GONE
        scrim.isClickable = true

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(28), dp(24), dp(28), dp(22))
        box.background = pillBackground()

        val title = TextView(this)
        title.text = "Dirección web"
        title.setTextColor(Color.WHITE)
        title.textSize = 22f
        title.gravity = Gravity.CENTER
        box.addView(title)

        urlInput = EditText(this)
        urlInput.inputType = InputType.TYPE_TEXT_VARIATION_URI
        urlInput.setText("https://")
        urlInput.setTextColor(Color.parseColor("#1A1230"))
        urlInput.textSize = 18f
        urlInput.setPadding(dp(16), dp(12), dp(16), dp(12))
        urlInput.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.parseColor("#F4F0FA"))
        }
        val inputLp = LinearLayout.LayoutParams(dp(460), ViewGroup.LayoutParams.WRAP_CONTENT)
        inputLp.topMargin = dp(18)
        box.addView(urlInput, inputLp)

        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.gravity = Gravity.CENTER
        val actionsLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        actionsLp.topMargin = dp(16)
        box.addView(actions, actionsLp)

        actions.addView(urlButton("Cancelar") { closeUrlPanel() })
        actions.addView(urlButton("Ir") { submitUrl() })

        val boxLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        boxLp.gravity = Gravity.CENTER
        scrim.addView(box, boxLp)
        return scrim
    }

    private fun urlButton(label: String, onClick: () -> Unit): View {
        val b = addPill(null, label, null, onClick = onClick)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.marginStart = dp(8)
        lp.marginEnd = dp(8)
        b.layoutParams = lp
        return b
    }

    private fun askUrl() {
        urlInput.setText("https://")
        urlInput.setSelection(urlInput.text.length)
        setHomePillsFocusable(false)
        urlPanel.visibility = View.VISIBLE
        urlInput.requestFocus()
    }

    private fun closeUrlPanel() {
        urlPanel.visibility = View.GONE
        setHomePillsFocusable(true)
        firstTile.requestFocus()
    }

    private fun setHomePillsFocusable(enabled: Boolean) {
        homePills.forEach { it.isFocusable = enabled }
    }

    private fun submitUrl() {
        var u = urlInput.text.toString().trim()
        if (u.isEmpty() || u == "https://" || u == "http://") return
        if (!u.contains("://")) u = "https://$u"
        urlPanel.visibility = View.GONE
        setHomePillsFocusable(true)
        openUrl(u)
    }

    private fun setStatus(text: String) {
        status.text = text
        status.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openUrl(url: String) {
        if (!browserReady) {
            pendingUrl = url
            setStatus("Abriendo...")
            return
        }
        hasPage = true
        setStatus("")
        session.loadUri(url)
        showHome(false)
    }

    private fun showHome(show: Boolean) {
        if (show && pageFullScreen && ::session.isInitialized) {
            pageFullScreen = false
            session.exitFullScreen()
        }
        home.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            urlPanel.visibility = View.GONE
            setHomePillsFocusable(true)
            seguirTile.visibility = if (hasPage) View.VISIBLE else View.GONE
            firstTile.requestFocus()
        } else {
            geckoView.requestFocus()
        }
    }

    // ------------------------------------------------------------------ Gecko

    private fun setupDelegates() {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                this@MainActivity.canGoBack = canGoBack
            }

            // Bloquea esquemas raros (intent://, market://, etc.)
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                val scheme = try {
                    android.net.Uri.parse(request.uri).scheme?.lowercase()
                } catch (e: Exception) {
                    null
                }
                val ok = scheme == null || scheme in
                    listOf("http", "https", "about", "data", "blob", "file", "resource", "moz-extension")
                return GeckoResult.fromValue(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
            }

            // Anti pop-ups: se bloquean ventanas nuevas; si son del mismo sitio se abren aqui mismo
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val host = try {
                    android.net.Uri.parse(uri).host
                } catch (e: Exception) {
                    null
                }
                val cur = currentHost
                if (host != null && cur != null && sameSite(host, cur)) {
                    ui.post { session.loadUri(uri) }
                }
                return null
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                pageFullScreen = fullScreen
                if (fullScreen) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                currentHost = try {
                    android.net.Uri.parse(url).host
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private fun sameSite(a: String, b: String): Boolean {
        fun base(h: String): String {
            val p = h.lowercase().split(".")
            return if (p.size >= 2) p.takeLast(2).joinToString(".") else h.lowercase()
        }
        return base(a) == base(b)
    }

    /** Actualiza el complemento aunque ya estuviera instalado en el perfil. */
    private fun installQualityHelper(runtime: GeckoRuntime) {
        runtime.webExtensionController
            .ensureBuiltIn(
                "resource://android/assets/quality/",
                "tvnav-quality@tvdonghua.browser"
            )
            .accept(
                { ext ->
                    ext?.setMessageDelegate(fsMessages, "tvnav")
                    ui.post { ensureBrowser(runtime) }
                },
                { _ -> ui.post { ensureBrowser(runtime) } }
            )
    }

    // ------------------------------------------------------------------ Control remoto
    // El stick ya trae puntero (boton de cursor del mando) y el OK hace clic.
    // No se dibuja otro cursor ni se fabrican toques: el puntero llega a la pagina.

    private fun scrollPage(dy: Double) {
        session.panZoomController.scrollBy(
            ScreenLength.fromPixels(0.0),
            ScreenLength.fromPixels(dy)
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN

        // BACK: corto = atras en la pagina, largo = pantalla de inicio
        if (code == KeyEvent.KEYCODE_BACK) {
            if (down) {
                if (event.repeatCount == 0) {
                    backDownTime = event.eventTime
                    backLongHandled = false
                } else if (!backLongHandled && event.eventTime - backDownTime > 700) {
                    backLongHandled = true
                    showHome(true)
                }
            } else {
                if (!backLongHandled) handleBack()
                backLongHandled = false
            }
            return true
        }

        // Teclas para abrir/cerrar el inicio
        if (down && event.repeatCount == 0 &&
            (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_INFO ||
                    code == KeyEvent.KEYCODE_GUIDE || code == KeyEvent.KEYCODE_TV ||
                    code == KeyEvent.KEYCODE_PROG_RED)
        ) {
            showHome(home.visibility != View.VISIBLE)
            return true
        }

        // En el inicio, flechas y OK mueven el foco entre los botones.
        if (home.visibility == View.VISIBLE) return super.dispatchKeyEvent(event)

        // pv del control (tercera tecla de apps, despues de YT y NF).
        if (down && event.repeatCount == 0 && isFullscreenKey(code)) {
            if (::session.isInitialized) {
                if (pageFullScreen) {
                    pageFullScreen = false
                    session.exitFullScreen()
                } else {
                    requestPageFullscreen()
                }
            }
            return true
        }

        // En la pagina, flechas y OK no se interceptan: los usa el cursor del mando.
        when (code) {
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (down) scrollPage(500.0); return true
            }
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (down) scrollPage(-500.0); return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isFullscreenKey(code: Int): Boolean {
        return code == KeyEvent.KEYCODE_F3 ||
            code == KeyEvent.KEYCODE_PROG_BLUE ||
            code == KeyEvent.KEYCODE_PROG_YELLOW
    }

    private val fsMessages = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            fsPort = port
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onDisconnect(port: WebExtension.Port) {
                    if (fsPort == port) fsPort = null
                }
            })
        }
    }

    private fun requestPageFullscreen() {
        val port = fsPort ?: return
        try {
            port.postMessage(org.json.JSONObject().put("action", "fullscreen"))
        } catch (e: Exception) {
            fsPort = null
        }
    }

    private fun handleBack() {
        if (home.visibility == View.VISIBLE) {
            if (urlPanel.visibility == View.VISIBLE) {
                closeUrlPanel()
                return
            }
            if (hasPage) showHome(false) else finish()
            return
        }
        if (pageFullScreen) {
            pageFullScreen = false
            session.exitFullScreen()
            return
        }
        if (canGoBack) session.goBack() else showHome(true)
    }

    override fun onDestroy() {
        ui.removeCallbacks(clockTick)
        if (::session.isInitialized) session.close()
        super.onDestroy()
    }
}
