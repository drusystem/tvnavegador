package com.tvdonghua.viewanime

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class BrowserActivity : Activity() {
    private lateinit var address: EditText
    private lateinit var geckoView: GeckoView
    private var session: GeckoSession? = null
    private var canGoBack = false
    private var pageFull = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ink.bg)

        val bar = LinearLayout(this)
        bar.setPadding(dp(12), dp(10), dp(12), dp(10))
        bar.orientation = LinearLayout.HORIZONTAL
        val back = chip("Atrás") { handleBack() }
        bar.addView(back)
        address = EditText(this)
        address.setTextColor(Color.WHITE)
        address.setHintTextColor(Ink.mute)
        address.hint = "https://"
        address.inputType = InputType.TYPE_TEXT_VARIATION_URI
        address.imeOptions = EditorInfo.IME_ACTION_GO
        address.setSingleLine(true)
        address.setBackgroundColor(0xFF161A24.toInt())
        address.setPadding(dp(14), dp(10), dp(14), dp(10))
        address.setOnEditorActionListener { _, action, event ->
            val go = action == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (go) {
                openTyped()
                true
            } else {
                false
            }
        }
        bar.addView(address, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(10)
            marginEnd = dp(10)
        })
        bar.addView(chip("Ir") { openTyped() })
        root.addView(bar)

        geckoView = GeckoView(this)
        geckoView.setBackgroundColor(Color.BLACK)
        geckoView.isFocusable = true
        root.addView(geckoView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        address.requestFocus()

        Engine.obtain(this) { runtime ->
            if (isDestroyed) return@obtain
            val opened = Engine.session()
            opened.navigationDelegate = object : GeckoSession.NavigationDelegate {
                override fun onCanGoBack(session: GeckoSession, can: Boolean) {
                    canGoBack = can
                }

                override fun onLoadRequest(
                    session: GeckoSession,
                    request: GeckoSession.NavigationDelegate.LoadRequest
                ): GeckoResult<AllowOrDeny> {
                    val scheme = try {
                        android.net.Uri.parse(request.uri).scheme?.lowercase()
                    } catch (e: Exception) {
                        null
                    }
                    val ok = scheme == null || scheme in listOf(
                        "http", "https", "about", "data", "blob", "file", "resource", "moz-extension"
                    )
                    return GeckoResult.fromValue(if (ok) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
                }

                override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                    runOnUiThread { session.loadUri(uri) }
                    return null
                }
            }
            opened.contentDelegate = object : GeckoSession.ContentDelegate {
                override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                    pageFull = fullScreen
                }
            }
            opened.open(runtime)
            geckoView.setSession(opened)
            session = opened
        }
    }

    private fun openTyped() {
        var url = address.text.toString().trim()
        if (url.isEmpty() || url == "https://") return
        if (!url.contains("://")) url = "https://$url"
        address.setText(url)
        session?.loadUri(url)
        geckoView.requestFocus()
    }

    private fun handleBack() {
        val current = session
        if (pageFull && current != null) {
            pageFull = false
            current.exitFullScreen()
            return
        }
        if (canGoBack && current != null) current.goBack() else finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            handleBack()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            address.requestFocus()
            return true
        }
        val ok = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER
        if (ok && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && geckoView.isFocused) {
            val now = event.eventTime
            if (now - lastOk in 1..450) {
                lastOk = 0
                Engine.fullscreen()
                return true
            }
            lastOk = now
        }
        return super.dispatchKeyEvent(event)
    }

    private var lastOk = 0L

    override fun onUserLeaveHint() {
        session?.setActive(false)
        super.onUserLeaveHint()
    }

    override fun onStop() {
        session?.setActive(false)
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        session?.setActive(true)
    }

    override fun onDestroy() {
        session?.close()
        super.onDestroy()
    }
}
