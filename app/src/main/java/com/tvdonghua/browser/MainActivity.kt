package com.tvdonghua.browser

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoPreferenceController
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
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
    private lateinit var home: LinearLayout
    private lateinit var status: TextView
    private lateinit var firstTile: View

    private val ui = Handler(Looper.getMainLooper())
    private var canGoBack = false
    private var currentHost: String? = null
    private var hasPage = false
    private var backDownTime = 0L
    private var backLongHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // User-agent de escritorio para que el sitio sirva el reproductor grande,
        // pero la pagina se maqueta a 1280x720: el H313 no aguanta un viewport de PC.
        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .screenSizeOverride(1280, 720)
            .displayDensityOverride(1f)
            .build()
        val runtime = sRuntime ?: GeckoRuntime.create(applicationContext, runtimeSettings).also {
            sRuntime = it
        }
        // El decodificador de hardware del Allwinner deja el video en negro.
        GeckoPreferenceController.setGeckoPref(
            "media.hardware-video-decoding.enabled",
            false,
            GeckoPreferenceController.PREF_BRANCH_USER
        ).accept({ _ -> }, { _ -> })
        installQualityHelper(runtime)

        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .build()
        session = GeckoSession(settings)
        setupDelegates()
        session.open(runtime)

        geckoView = GeckoView(this)
        geckoView.setSession(session)
        geckoView.isFocusable = true
        geckoView.isFocusableInTouchMode = true

        home = buildHome()

        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        root.addView(geckoView, FrameLayout.LayoutParams(match, match))
        root.addView(home, FrameLayout.LayoutParams(match, match))
        setContentView(root)

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
        hint.text = "Flechas y OK: elegir  |  En la web: cursor del control\nAtras: volver  |  Atras (mantener): inicio  |  Canal +/-: desplazar"
        hint.setTextColor(Color.LTGRAY)
        hint.textSize = 15f
        hint.gravity = Gravity.CENTER
        hint.setPadding(0, 12, 0, 28)
        l.addView(hint)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        firstTile = addTile(row, "SeriesDonghua", R.drawable.ic_seriesdonghua) {
            openUrl("https://seriesdonghua.com/")
        }
        addTile(row, "JKAnime", R.drawable.ic_jkanime) {
            openUrl("https://jkanime.net/")
        }
        addTile(row, "Navegador", R.drawable.ic_navegador) {
            askUrl()
        }
        l.addView(row)

        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.gravity = Gravity.CENTER
        addHomeButton(actions, "Seguir navegando", 420) { if (hasPage) showHome(false) }
        addHomeButton(actions, "Salir", 280) { finish() }
        val actionsLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        actionsLp.topMargin = 22
        l.addView(actions, actionsLp)

        status = TextView(this)
        status.setTextColor(Color.GRAY)
        status.textSize = 14f
        status.gravity = Gravity.CENTER
        status.setPadding(0, 28, 0, 0)
        l.addView(status)

        firstTile.requestFocus()
        return l
    }

    private fun addTile(parent: LinearLayout, label: String, imageRes: Int, onClick: () -> Unit): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.setPadding(16, 16, 16, 14)
        card.isFocusable = true
        card.isClickable = true
        card.setBackgroundColor(Color.parseColor("#333344"))
        card.setOnFocusChangeListener { v, has ->
            v.setBackgroundColor(Color.parseColor(if (has) "#E65100" else "#333344"))
        }
        card.setOnClickListener { onClick() }

        val size = (resources.displayMetrics.density * 210).toInt()
        val image = ImageView(this)
        image.setImageResource(imageRes)
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        image.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        card.addView(image, LinearLayout.LayoutParams(size, size))

        val text = TextView(this)
        text.text = label
        text.setTextColor(Color.WHITE)
        text.textSize = 20f
        text.gravity = Gravity.CENTER
        text.setPadding(0, 12, 0, 0)
        card.addView(text, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.marginStart = 16
        lp.marginEnd = 16
        parent.addView(card, lp)
        return card
    }

    private fun addHomeButton(parent: LinearLayout, text: String, width: Int = 900, onClick: () -> Unit): Button {
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
        val lp = LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 14
        lp.marginStart = 12
        lp.marginEnd = 12
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
        if (show) {
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

    /** Si la pagina ofrece 1080p y tambien 720p, elige 720p. Si no hay 720p, elige 480p. */
    private fun installQualityHelper(runtime: GeckoRuntime) {
        runtime.webExtensionController
            .installBuiltIn("resource://android/assets/quality/")
            .accept({ _ -> }, { _ -> })
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
