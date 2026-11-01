package com.tvdonghua.browser

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.ScreenLength
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

class MainActivity : Activity() {

    companion object {
        private var sRuntime: GeckoRuntime? = null
        private const val UBLOCK_URL =
            "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"
        private const val PREFS = "tvnav"
        private const val KEY_UBLOCK = "ublock_ok"
    }

    private lateinit var root: FrameLayout
    private lateinit var geckoView: GeckoView
    private lateinit var session: GeckoSession
    private lateinit var cursor: CursorView
    private lateinit var home: LinearLayout
    private lateinit var status: TextView

    private val ui = Handler(Looper.getMainLooper())
    private var canGoBack = false
    private var currentHost: String? = null
    private var hasPage = false
    private var backDownTime = 0L
    private var backLongHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val runtime = sRuntime ?: GeckoRuntime.create(applicationContext).also { sRuntime = it }

        // Modo "escritorio": reproductores mas grandes y compatibles en pantalla de TV
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP)
            .build()
        session = GeckoSession(settings)
        setupDelegates()
        session.open(runtime)

        geckoView = GeckoView(this)
        geckoView.setSession(session)

        cursor = CursorView(this)
        home = buildHome()

        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        root.addView(geckoView, FrameLayout.LayoutParams(match, match))
        root.addView(cursor, FrameLayout.LayoutParams(match, match))
        root.addView(home, FrameLayout.LayoutParams(match, match))
        setContentView(root)

        cursor.post {
            cursor.cx = cursor.width / 2f
            cursor.cy = cursor.height / 2f
            cursor.invalidate()
        }

        showHome(true)
        installUBlock(runtime)
    }

    // ------------------------------------------------------------------ UI de inicio

    private fun buildHome(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.gravity = Gravity.CENTER
        l.setBackgroundColor(Color.parseColor("#E6101018"))
        l.setPadding(80, 40, 80, 40)

        val title = TextView(this)
        title.text = "TV Navegador"
        title.setTextColor(Color.WHITE)
        title.textSize = 34f
        title.gravity = Gravity.CENTER
        l.addView(title)

        val hint = TextView(this)
        hint.text = "Flechas: mover cursor  |  OK: clic  |  Atras: volver  |  Atras (mantener): inicio"
        hint.setTextColor(Color.LTGRAY)
        hint.textSize = 15f
        hint.gravity = Gravity.CENTER
        hint.setPadding(0, 12, 0, 36)
        l.addView(hint)

        val first = addHomeButton(l, "SeriesDonghua") { openUrl("https://seriesdonghua.com/") }
        addHomeButton(l, "JKAnime") { openUrl("https://jkanime.net/") }
        addHomeButton(l, "Escribir otra direccion...") { askUrl() }
        addHomeButton(l, "Seguir navegando") { if (hasPage) showHome(false) }
        addHomeButton(l, "Salir") { finish() }

        status = TextView(this)
        status.setTextColor(Color.GRAY)
        status.textSize = 14f
        status.gravity = Gravity.CENTER
        status.setPadding(0, 28, 0, 0)
        l.addView(status)

        first.requestFocus()
        return l
    }

    private fun addHomeButton(parent: LinearLayout, text: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.textSize = 20f
        b.isAllCaps = false
        b.setTextColor(Color.WHITE)
        b.setBackgroundColor(Color.parseColor("#333344"))
        b.isFocusable = true
        b.setOnFocusChangeListener { v, has ->
            v.setBackgroundColor(Color.parseColor(if (has) "#E65100" else "#333344"))
        }
        b.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(900, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 14
        parent.addView(b, lp)
        return b
    }

    private fun askUrl() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_TEXT_VARIATION_URI
        input.setText("https://")
        AlertDialog.Builder(this)
            .setTitle("Direccion web")
            .setView(input)
            .setPositiveButton("Ir") { _, _ ->
                var u = input.text.toString().trim()
                if (u.isNotEmpty()) {
                    if (!u.contains("://")) u = "https://$u"
                    openUrl(u)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openUrl(url: String) {
        hasPage = true
        session.loadUri(url)
        showHome(false)
    }

    private fun showHome(show: Boolean) {
        home.visibility = if (show) View.VISIBLE else View.GONE
        cursor.visibility = if (show) View.GONE else View.VISIBLE
        if (show) {
            home.getChildAt(2)?.requestFocus()
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

    /** Instala uBlock Origin una sola vez (necesita internet). */
    private fun installUBlock(runtime: GeckoRuntime) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val ctrl = runtime.webExtensionController

        ctrl.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>
            ): GeckoResult<WebExtension.PermissionPromptResponse>? {
                return GeckoResult.fromValue(
                    WebExtension.PermissionPromptResponse(true, false, false)
                )
            }
        })

        if (prefs.getBoolean(KEY_UBLOCK, false)) {
            status.text = "Bloqueador de anuncios (uBlock Origin): activo"
            return
        }
        status.text = "Instalando bloqueador de anuncios (uBlock Origin)..."
        ctrl.install(UBLOCK_URL).accept(
            { _ ->
                prefs.edit().putBoolean(KEY_UBLOCK, true).apply()
                ui.post { status.text = "Bloqueador de anuncios (uBlock Origin): instalado" }
            },
            { err ->
                ui.post {
                    status.text = "No se pudo instalar uBlock Origin (se reintentara): ${err?.message ?: ""}"
                }
            }
        )
    }

    // ------------------------------------------------------------------ Control remoto

    private fun clickAtCursor() {
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cursor.cx, cursor.cy, 0)
        down.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        geckoView.dispatchTouchEvent(down)
        down.recycle()
        ui.postDelayed({
            val t2 = SystemClock.uptimeMillis()
            val up = MotionEvent.obtain(t2 - 60, t2, MotionEvent.ACTION_UP, cursor.cx, cursor.cy, 0)
            up.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            geckoView.dispatchTouchEvent(up)
            up.recycle()
        }, 60)
    }

    private fun scrollPage(dy: Double) {
        session.panZoomController.scrollBy(
            ScreenLength.fromPixels(0.0),
            ScreenLength.fromPixels(dy)
        )
    }

    private fun moveCursor(dx: Int, dy: Int, repeat: Int) {
        val speed = (18f + repeat * 5f).coerceAtMost(90f)
        val margin = 6f
        // Si el cursor ya esta en el borde superior/inferior, se hace scroll de la pagina
        if (dy > 0 && cursor.cy >= cursor.height - margin) {
            scrollPage(speed * 3.0)
        } else if (dy < 0 && cursor.cy <= margin) {
            scrollPage(-speed * 3.0)
        }
        cursor.moveBy(dx * speed, dy * speed)
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

        // En la pantalla de inicio se usa la navegacion normal por foco
        if (home.visibility == View.VISIBLE) return super.dispatchKeyEvent(event)

        // Modo navegador: D-pad mueve el cursor
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> { if (down) moveCursor(0, -1, event.repeatCount); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (down) moveCursor(0, 1, event.repeatCount); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (down) moveCursor(-1, 0, event.repeatCount); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (down) moveCursor(1, 0, event.repeatCount); return true }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_A -> {
                if (down && event.repeatCount == 0) clickAtCursor()
                return true
            }
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (down) scrollPage(500.0); return true
            }
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (down) scrollPage(-500.0); return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleBack() {
        if (home.visibility == View.VISIBLE) {
            if (hasPage) showHome(false) else finish()
            return
        }
        if (canGoBack) session.goBack() else showHome(true)
    }

    override fun onDestroy() {
        session.close()
        super.onDestroy()
    }
}
