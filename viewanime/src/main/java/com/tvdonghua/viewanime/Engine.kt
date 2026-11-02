package com.tvdonghua.viewanime

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebExtension
import java.io.File

object Engine {
    private var runtime: GeckoRuntime? = null
    private var ready = false
    private var starting = false
    private val waiters = ArrayList<(GeckoRuntime) -> Unit>()
    private val ui = Handler(Looper.getMainLooper())
    var port: WebExtension.Port? = null
    var onPlaying: ((Boolean) -> Unit)? = null

    fun obtain(context: Context, done: (GeckoRuntime) -> Unit) {
        synchronized(this) {
            val have = runtime
            if (ready && have != null) {
                ui.post { done(have) }
                return
            }
            waiters.add(done)
            if (starting) return
            starting = true
        }
        val app = context.applicationContext
        val settings = GeckoRuntimeSettings.Builder()
            .configFilePath(writeConfig(app))
            .fissionEnabled(false)
            .build()
        val created = GeckoRuntime.create(app, settings)
        created.webExtensionController
            .ensureBuiltIn("resource://android/assets/guard/", "viewanime-guard@tvdonghua.viewanime")
            .accept({ ext ->
                ext?.setMessageDelegate(messages, "view")
                finish(created)
            }, { _ ->
                finish(created)
            })
    }

    fun session(): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
            .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP)
            .build()
        val session = GeckoSession(settings)
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
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
                ui.post { session.loadUri(uri) }
                return null
            }
        }
        return session
    }

    fun fullscreen() {
        val current = port ?: return
        try {
            current.postMessage(JSONObject().put("action", "fullscreen"))
        } catch (e: Exception) {
            port = null
        }
    }

    private fun finish(created: GeckoRuntime) {
        val pending = synchronized(this) {
            runtime = created
            ready = true
            starting = false
            waiters.toList().also { waiters.clear() }
        }
        ui.post { pending.forEach { it(created) } }
    }

    private val messages = object : WebExtension.MessageDelegate {
        override fun onConnect(connected: WebExtension.Port) {
            port = connected
            connected.setDelegate(object : WebExtension.PortDelegate {
                override fun onDisconnect(disconnected: WebExtension.Port) {
                    if (port == disconnected) port = null
                }

                override fun onPortMessage(message: Any, target: WebExtension.Port) {
                    val json = message as? JSONObject ?: return
                    if (json.optString("action") == "playing") {
                        ui.post { onPlaying?.invoke(json.optBoolean("on")) }
                    }
                }
            })
        }
    }

    private fun writeConfig(context: Context): String {
        val file = File(context.filesDir, "geckoview-config.yaml")
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
}
