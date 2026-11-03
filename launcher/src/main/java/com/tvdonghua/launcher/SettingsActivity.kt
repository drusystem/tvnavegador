package com.tvdonghua.launcher

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.Inet4Address
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : Activity() {
    private lateinit var detail: FrameLayout
    private lateinit var railBox: FrameLayout
    private lateinit var railViews: List<TextView>
    private lateinit var look: Look
    private var currentPage = "hora"
    private var clockView: TextView? = null
    private var blockPage = false
    private var editingKey: String? = null
    private val rowPaint = HashMap<String, (Boolean, Boolean) -> Unit>()
    private val inkDark = Color.parseColor("#1A1230")

    private val tick = object : Runnable {
        override fun run() {
            val view = clockView ?: return
            view.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            view.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        look = Look(this)
        currentPage = intent.getStringExtra(EXTRA_PAGE) ?: "hora"
        window.decorView.setBackgroundColor(Color.parseColor("#1A1440"))
        val root = FrameLayout(this)
        root.addView(LivingBg(this), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val board = LinearLayout(this)
        board.orientation = LinearLayout.HORIZONTAL
        board.gravity = Gravity.CENTER

        val rail = LinearLayout(this)
        rail.orientation = LinearLayout.VERTICAL
        rail.setPadding(dp(14), dp(16), dp(14), dp(16))
        val pages = listOf(
            "hora" to "Hora y fecha",
            "red" to "Red",
            "bluetooth" to "Bluetooth",
            "pantalla" to "Pantalla",
            "sonido" to "Sonido",
            "aplicaciones" to "Aplicaciones",
            "sistema" to "Sistema",
            "apariencia" to "Apariencia"
        )
        val built = ArrayList<TextView>(pages.size)
        pages.forEach { (id, title) ->
            val item = TextView(this)
            item.text = title
            item.tag = id
            item.setTextColor(look.textColor())
            item.textSize = look.sp(16f)
            item.typeface = look.typeface(true)
            item.gravity = Gravity.CENTER_VERTICAL
            item.setPadding(dp(16), dp(10), dp(16), dp(10))
            item.background = glass(16, "#14FFFFFF")
            item.isFocusable = true
            item.id = View.generateViewId()
            item.setShadowLayer(6f, 0f, 1f, look.shadow())
            item.setTag(R.id.look_style, Ink(16f, true))
            item.setOnFocusChangeListener { view, has ->
                view.background = glass(16, if (has) "#F4F0FA" else "#14FFFFFF")
                (view as TextView).setTextColor(if (has) inkDark else look.textColor())
                view.setShadowLayer(if (has) 0f else 6f, 0f, 1f, look.shadow())
                if (has && !blockPage) showPage(id)
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(8)
            rail.addView(item, lp)
            built.add(item)
        }
        railViews = built
        built.forEachIndexed { index, item ->
            item.nextFocusUpId = if (index == 0) item.id else built[index - 1].id
            item.nextFocusDownId = if (index == built.lastIndex) item.id else built[index + 1].id
            item.nextFocusLeftId = item.id
        }

        val railScroll = ScrollView(this)
        railScroll.isFocusable = false
        railScroll.isVerticalScrollBarEnabled = false
        railScroll.addView(rail, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        railBox = FrameLayout(this)
        railBox.addView(railScroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        detail = FrameLayout(this)
        detail.setPadding(dp(22), dp(20), dp(22), dp(20))
        chrome()
        val railLp = LinearLayout.LayoutParams(dp(250), dp(460))
        board.addView(railBox, railLp)
        val detailLp = LinearLayout.LayoutParams(dp(560), dp(460))
        detailLp.marginStart = dp(16)
        board.addView(detail, detailLp)
        root.addView(board, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        setContentView(root)
        val start = built.firstOrNull { it.tag == currentPage } ?: built.first()
        start.post { start.requestFocus() }
    }

    override fun onPause() {
        clockView?.removeCallbacks(tick)
        clockView = null
        super.onPause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            val editing = editingKey
            if (editing != null) {
                editingKey = null
                rowPaint[editing]?.invoke(true, false)
                return true
            }
            val focused = currentFocus
            if (focused != null && isInside(focused, detail)) {
                railViews.firstOrNull { it.tag == currentPage }?.requestFocus()
            } else {
                finish()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("El sistema lo marcó obsoleto")
    override fun onBackPressed() {
        finish()
    }

    private fun isInside(view: View, parent: View): Boolean {
        var node: View? = view
        while (node != null) {
            if (node === parent) return true
            val next = node.parent
            node = next as? View
        }
        return false
    }

    private fun showPage(page: String) {
        currentPage = page
        clockView?.removeCallbacks(tick)
        clockView = null
        detail.removeAllViews()
        val pageView = when (page) {
            "hora" -> pageHora()
            "red" -> pageRed()
            "bluetooth" -> pageBluetooth()
            "pantalla" -> pagePantalla()
            "sonido" -> pageSonido()
            "aplicaciones" -> pageApps()
            "apariencia" -> pageApariencia()
            else -> pageSistema()
        }
        detail.addView(pageView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        val rail = railViews.firstOrNull { it.tag == page } ?: return
        val first = firstFocusable(pageView)
        if (first != null) {
            rail.nextFocusRightId = first.id
            markLeft(pageView, rail.id)
        } else {
            rail.nextFocusRightId = rail.id
        }
    }

    private fun firstFocusable(root: View): View? {
        if (root.isFocusable) return root
        if (root !is ViewGroup) return null
        for (i in 0 until root.childCount) {
            val found = firstFocusable(root.getChildAt(i))
            if (found != null) return found
        }
        return null
    }

    private fun markLeft(root: View, leftId: Int) {
        if (root.isFocusable && root.id != View.NO_ID) root.nextFocusLeftId = leftId
        if (root !is ViewGroup) return
        for (i in 0 until root.childCount) markLeft(root.getChildAt(i), leftId)
    }

    private fun chrome() {
        railBox.background = glassArgb(28, look.panelArgb())
        detail.background = glassArgb(28, look.panelArgb())
    }

    private fun commitLook(keep: String) {
        editingKey = null
        blockPage = true
        chrome()
        showPage("apariencia")
        val target = findTagged(detail, keep)
        if (target == null) {
            blockPage = false
        } else {
            target.post {
                target.requestFocus()
                blockPage = false
            }
        }
    }

    private fun findTagged(root: View, key: String): View? {
        if (root.tag == key) return root
        if (root !is ViewGroup) return null
        for (i in 0 until root.childCount) {
            val found = findTagged(root.getChildAt(i), key)
            if (found != null) return found
        }
        return null
    }

    private fun pageApariencia(): View {
        rowPaint.clear()
        editingKey = null
        val box = column()
        box.addView(heading("Apariencia"))
        val hint = TextView(this)
        hint.text = "OK modifica. Izquierda y derecha cambian. Atrás suelta."
        hint.setTextColor(look.textColor())
        hint.textSize = look.sp(14f)
        hint.typeface = look.typeface(false)
        hint.gravity = Gravity.CENTER
        hint.setShadowLayer(5f, 0f, 1f, look.shadow())
        hint.setTag(R.id.look_style, Ink(14f, false))
        box.addView(hint, gap())
        box.addView(tuner("letras", "Color de letras", { look.textName() }) { look.stepTextColor(it) }, gap())
        box.addView(tuner("letras-tam", "Tamaño de letras", { "${look.textScale()}%" }) {
            look.setTextScale(look.textScale() + it * 5)
        }, gap())
        box.addView(tuner("fuente", "Fuente", { look.fontName() }) { look.stepFont(it) }, gap())
        box.addView(tuner("tarjetas", "Tamaño de tarjetas", { "${look.cardScale()}%" }) {
            look.setCardScale(look.cardScale() + it * 5)
        }, gap())
        box.addView(tuner("iconos", "Tamaño de iconos", { "${look.iconScale()}%" }) {
            look.setIconScale(look.iconScale() + it * 5)
        }, gap())
        box.addView(tuner("tarjeta-color", "Color de tarjetas", { look.cardName() }) { look.stepCardColor(it) }, gap())
        box.addView(tuner("tarjeta-op", "Opacidad de tarjetas", { "${look.cardOpacity()}%" }) {
            look.setCardOpacity(look.cardOpacity() + it * 4)
        }, gap())
        box.addView(tuner("tarjeta-foco", "Opacidad al enfocar", { "${look.cardFocusOpacity()}%" }) {
            look.setCardFocusOpacity(look.cardFocusOpacity() + it * 4)
        }, gap())
        box.addView(tuner("modal-color", "Color de modales", { look.panelName() }) { look.stepPanelColor(it) }, gap())
        box.addView(tuner("modal-op", "Opacidad de modales", { "${look.panelOpacity()}%" }) {
            look.setPanelOpacity(look.panelOpacity() + it * 4)
        }, gap())
        box.addView(tuner("fondo-anim", "Fondo animado", { look.bgName() }) { look.stepBg(it) }, gap())
        box.addView(tuner("clip-visible", "Reproductor del inicio", { look.clipVisibleName() }) {
            look.stepClipVisible(it)
        }, gap())
        val clipUrl = ClipPane.url(this)
        val clipLabel = if (clipUrl.length > 42) clipUrl.take(39) + "…" else clipUrl
        val clipBtn = action("Video del inicio\n$clipLabel") { openClipEditor() }
        clipBtn.tag = "clip-url"
        box.addView(clipBtn, gap())
        val reset = action("Volver a valores por defecto") {
            look.reset()
            commitLook("reset")
        }
        reset.tag = "reset"
        box.addView(reset, gap())
        val scroll = ScrollView(this)
        scroll.isFocusable = false
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(box)
        return scroll
    }

    private fun tuner(key: String, title: String, value: () -> String, onDelta: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.tag = key
        row.setPadding(dp(14), dp(10), dp(14), dp(10))
        row.background = glass(16, "#1AFFFFFF")
        row.isFocusable = true
        row.id = View.generateViewId()
        val label = TextView(this)
        label.text = title
        label.setTextColor(look.textColor())
        label.textSize = look.sp(16f)
        label.typeface = look.typeface(true)
        label.setShadowLayer(5f, 0f, 1f, look.shadow())
        label.setTag(R.id.look_style, Ink(16f, true))
        row.addView(label, LinearLayout.LayoutParams(dp(230), ViewGroup.LayoutParams.WRAP_CONTENT))
        val read = TextView(this)
        read.text = value()
        read.setTextColor(look.textColor())
        read.textSize = look.sp(16f)
        read.typeface = look.typeface(true)
        read.gravity = Gravity.END
        read.setShadowLayer(5f, 0f, 1f, look.shadow())
        read.setTag(R.id.look_style, Ink(16f, true))
        row.addView(read, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        fun paint(focused: Boolean, editing: Boolean) {
            row.background = glass(16, when {
                editing -> "#7EE7FF"
                focused -> "#F4F0FA"
                else -> "#1AFFFFFF"
            })
            val ink = if (focused || editing) inkDark else look.textColor()
            val shadow = if (focused || editing) 0f else 5f
            label.setTextColor(ink)
            read.setTextColor(ink)
            label.textSize = look.sp(16f)
            read.textSize = look.sp(16f)
            label.typeface = look.typeface(true)
            read.typeface = look.typeface(true)
            label.setShadowLayer(shadow, 0f, 1f, look.shadow())
            read.setShadowLayer(shadow, 0f, 1f, look.shadow())
            read.text = if (editing) "‹ ${value()} ›" else value()
        }
        rowPaint[key] = { focused, editing -> paint(focused, editing) }
        row.setOnFocusChangeListener { _, has ->
            if (!has && editingKey == key) editingKey = null
            paint(has, has && editingKey == key)
        }
        row.setOnKeyListener { _, keyCode, event ->
            val editing = editingKey == key
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (editing && event.action == KeyEvent.ACTION_DOWN) {
                    onDelta(1)
                    applyLive()
                    paint(true, true)
                }
                return@setOnKeyListener true
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (!editing) return@setOnKeyListener false
                if (event.action == KeyEvent.ACTION_DOWN) {
                    onDelta(-1)
                    applyLive()
                    paint(true, true)
                }
                return@setOnKeyListener true
            }
            if (!isOk(keyCode)) return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && !editing) {
                editingKey = key
                paint(true, true)
            }
            true
        }
        return row
    }

    private fun applyLive() {
        look = Look(this)
        chrome()
        restyle(railBox)
        restyle(detail)
    }

    private fun restyle(root: View) {
        if (root is TextView) {
            val parent = root.parent as? View
            val hot = root.isFocused || parent?.isFocused == true || parent?.tag == editingKey
            if (!hot) {
                root.setTextColor(look.textColor())
                root.setShadowLayer(5f, 0f, 1f, look.shadow())
                val ink = root.getTag(R.id.look_style) as? Ink
                if (ink != null) {
                    root.textSize = look.sp(ink.base)
                    root.typeface = look.typeface(ink.bold)
                }
            }
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) restyle(root.getChildAt(i))
        }
    }

    private fun isOk(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            keyCode == KeyEvent.KEYCODE_BUTTON_A
    }

    private fun pageHora(): LinearLayout {
        val box = column()
        val time = TextView(this)
        time.setTextColor(look.textColor())
        time.textSize = look.sp(64f)
        time.typeface = look.typeface(true)
        time.gravity = Gravity.CENTER
        time.setShadowLayer(12f, 0f, 2f, look.shadow())
        clockView = time
        tick.run()
        box.addView(time, wrap())
        val date = TextView(this)
        date.text = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es")).format(Date())
            .replaceFirstChar { it.titlecase(Locale("es")) }
        date.setTextColor(look.textColor())
        date.textSize = look.sp(22f)
        date.typeface = look.typeface(false)
        date.setShadowLayer(6f, 0f, 1f, look.shadow())
        date.gravity = Gravity.CENTER
        val dateLp = wrap()
        dateLp.topMargin = dp(8)
        box.addView(date, dateLp)
        box.addView(infoLine("Zona", java.util.TimeZone.getDefault().id.replace('_', ' ')), gap())
        box.addView(action("Cambiar hora") { openSystem(Settings.ACTION_DATE_SETTINGS) }, gap())
        return box
    }

    private fun pageRed(): LinearLayout {
        val box = column()
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        val link = cm.getLinkProperties(network)
        val kind = when {
            caps == null -> "Sin conexión"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Otra"
        }
        val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val raw = wifi.connectionInfo?.ssid?.trim('"').orEmpty()
        val ssid = if (raw.isBlank() || raw == "<unknown ssid>") "No disponible" else raw
        val ip = link?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress ?: "—"
        box.addView(heading(kind))
        box.addView(infoLine("Red", if (kind == "Wi-Fi") ssid else kind), gap())
        box.addView(infoLine("Dirección", ip), gap())
        box.addView(action("Abrir ajustes de red") { openSystem(Settings.ACTION_WIFI_SETTINGS) }, gap())
        return box
    }

    private fun pageBluetooth(): LinearLayout {
        val box = column()
        val adapter = try {
            BluetoothAdapter.getDefaultAdapter()
        } catch (_: Exception) {
            null
        }
        if (adapter == null) {
            box.addView(heading("Bluetooth no disponible"))
            return box
        }
        box.addView(heading(if (adapter.isEnabled) "Encendido" else "Apagado"))
        val name = adapter.name?.takeIf { it.isNotBlank() } ?: "Este equipo"
        box.addView(infoLine("Nombre", name), gap())
        val paired = try {
            adapter.bondedDevices?.mapNotNull { it.name }?.filter { it.isNotBlank() }.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        box.addView(infoLine("Emparejados", if (paired.isEmpty()) "Ninguno" else paired.joinToString(", ")), gap())
        box.addView(action("Abrir ajustes de Bluetooth") { openSystem(Settings.ACTION_BLUETOOTH_SETTINGS) }, gap())
        return box
    }

    private fun pagePantalla(): LinearLayout {
        val box = column()
        val metrics = resources.displayMetrics
        box.addView(heading("Pantalla"))
        box.addView(infoLine("Resolución", "${metrics.widthPixels} × ${metrics.heightPixels}"), gap())
        box.addView(infoLine("Densidad", "${metrics.densityDpi} dpi"), gap())
        val timeout = try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        } catch (_: Exception) {
            -1
        }
        val timeoutLabel = when {
            timeout < 0 -> "—"
            timeout >= 86400000 -> "Nunca"
            else -> "${timeout / 60000} min"
        }
        box.addView(infoLine("Apagado", timeoutLabel), gap())
        box.addView(action("Abrir ajustes de pantalla") { openSystem(Settings.ACTION_DISPLAY_SETTINGS) }, gap())
        return box
    }

    private fun pageSonido(): LinearLayout {
        val box = column()
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        box.addView(heading("Volumen"))
        box.addView(volumeRow(audio, AudioManager.STREAM_MUSIC, "Multimedia"), gap())
        box.addView(volumeRow(audio, AudioManager.STREAM_NOTIFICATION, "Avisos"), gap())
        box.addView(action("Abrir ajustes de sonido") { openSystem(Settings.ACTION_SOUND_SETTINGS) }, gap())
        return box
    }

    private fun volumeRow(audio: AudioManager, stream: Int, title: String): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(14), dp(12), dp(14), dp(12))
        row.background = glass(16, "#1AFFFFFF")
        row.isFocusable = true
        row.id = View.generateViewId()
        val label = TextView(this)
        label.text = title
        label.setTextColor(look.textColor())
        label.textSize = look.sp(18f)
        label.typeface = look.typeface(true)
        label.setShadowLayer(5f, 0f, 1f, look.shadow())
        row.addView(label, LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT))
        val value = TextView(this)
        value.setTextColor(look.textColor())
        value.textSize = look.sp(18f)
        value.typeface = look.typeface(true)
        value.setShadowLayer(5f, 0f, 1f, look.shadow())
        value.gravity = Gravity.END
        fun paint() {
            val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
            val now = audio.getStreamVolume(stream)
            value.text = "$now / $max"
        }
        paint()
        row.addView(value, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.setOnFocusChangeListener { view, has ->
            view.background = glass(16, if (has) "#F4F0FA" else "#1AFFFFFF")
            val ink = if (has) inkDark else look.textColor()
            label.setTextColor(ink)
            value.setTextColor(ink)
        }
        row.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            val delta = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> -1
                KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                else -> return@setOnKeyListener false
            }
            val max = audio.getStreamMaxVolume(stream)
            val now = audio.getStreamVolume(stream)
            audio.setStreamVolume(stream, (now + delta).coerceIn(0, max), 0)
            paint()
            true
        }
        return row
    }

    private fun pageApps(): View {
        val box = column()
        box.addView(heading("Aplicaciones"))
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        loadLaunchableApps().forEach { entry ->
            val row = TextView(this)
            row.text = entry.label
            row.setTextColor(look.textColor())
            row.textSize = look.sp(18f)
            row.typeface = look.typeface(false)
            row.setShadowLayer(5f, 0f, 1f, look.shadow())
            row.setPadding(dp(14), dp(10), dp(14), dp(10))
            row.background = glass(14, "#1AFFFFFF")
            row.isFocusable = true
            row.id = View.generateViewId()
            row.setOnFocusChangeListener { view, has ->
                view.background = glass(14, if (has) "#F4F0FA" else "#1AFFFFFF")
                (view as TextView).setTextColor(if (has) inkDark else look.textColor())
                view.setShadowLayer(if (has) 0f else 5f, 0f, 1f, look.shadow())
            }
            val pkg = entry.component.packageName
            row.setOnClickListener { openApp(pkg) }
            row.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_UP) return@setOnKeyListener false
                if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER) return@setOnKeyListener false
                openApp(pkg)
                true
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(6)
            list.addView(row, lp)
        }
        val scroll = ScrollView(this)
        scroll.isFocusable = false
        scroll.addView(list)
        val scrollLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        scrollLp.topMargin = dp(12)
        box.addView(scroll, scrollLp)
        return box
    }

    private fun pageSistema(): LinearLayout {
        val box = column()
        box.addView(heading("Sistema"))
        box.addView(infoLine("Modelo", android.os.Build.MODEL ?: "—"), gap())
        box.addView(infoLine("Android", android.os.Build.VERSION.RELEASE ?: "—"), gap())
        val stat = StatFs(Environment.getDataDirectory().path)
        val free = stat.availableBytes / (1024 * 1024 * 1024)
        val total = stat.totalBytes / (1024 * 1024 * 1024)
        box.addView(infoLine("Almacenamiento", "$free GB libres de $total GB"), gap())
        box.addView(action("Información del equipo") { openSystem(Settings.ACTION_DEVICE_INFO_SETTINGS) }, gap())
        return box
    }

    private fun column(): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        return box
    }

    private fun heading(text: String): TextView {
        val view = TextView(this)
        view.text = text
        view.setTextColor(look.textColor())
        view.textSize = look.sp(28f)
        view.typeface = look.typeface(true)
        view.gravity = Gravity.CENTER
        view.setShadowLayer(8f, 0f, 1f, look.shadow())
        view.setTag(R.id.look_style, Ink(28f, true))
        return view
    }

    private fun infoLine(title: String, value: String): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val name = TextView(this)
        name.text = title
        name.setTextColor(look.textColor())
        name.textSize = look.sp(16f)
        name.typeface = look.typeface(false)
        name.setShadowLayer(5f, 0f, 1f, look.shadow())
        row.addView(name, LinearLayout.LayoutParams(dp(160), ViewGroup.LayoutParams.WRAP_CONTENT))
        val data = TextView(this)
        data.text = value
        data.setTextColor(look.textColor())
        data.textSize = look.sp(18f)
        data.typeface = look.typeface(true)
        data.setShadowLayer(5f, 0f, 1f, look.shadow())
        data.maxLines = 2
        row.addView(data, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun action(text: String, onPick: () -> Unit): TextView {
        val button = TextView(this)
        button.text = text
        button.setTextColor(look.textColor())
        button.textSize = look.sp(18f)
        button.typeface = look.typeface(true)
        button.setShadowLayer(5f, 0f, 1f, look.shadow())
        button.gravity = Gravity.CENTER
        button.setPadding(dp(18), dp(12), dp(18), dp(12))
        button.background = glass(18, "#1AFFFFFF")
        button.isFocusable = true
        button.isClickable = true
        button.id = View.generateViewId()
        button.setOnFocusChangeListener { view, has ->
            view.background = glass(18, if (has) "#F4F0FA" else "#1AFFFFFF")
            (view as TextView).setTextColor(if (has) inkDark else look.textColor())
            view.setShadowLayer(if (has) 0f else 5f, 0f, 1f, look.shadow())
        }
        button.setOnClickListener { onPick() }
        button.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_UP) return@setOnKeyListener false
            if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER &&
                keyCode != KeyEvent.KEYCODE_BUTTON_A
            ) return@setOnKeyListener false
            onPick()
            true
        }
        return button
    }

    private fun wrap(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun gap(): LinearLayout.LayoutParams {
        val lp = wrap()
        lp.topMargin = dp(14)
        return lp
    }

    private fun openClipEditor() {
        val root = window.decorView as? ViewGroup ?: return
        val scrim = FrameLayout(this)
        scrim.setBackgroundColor(Color.parseColor("#66060A14"))
        scrim.isClickable = true
        scrim.isFocusable = false
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(22), dp(18), dp(22), dp(16))
        box.background = glassArgb(28, look.panelArgb())
        val title = TextView(this)
        title.text = "Enlace de YouTube"
        title.setTextColor(look.textColor())
        title.textSize = look.sp(20f)
        title.typeface = look.typeface(true)
        title.gravity = Gravity.CENTER
        title.setShadowLayer(6f, 0f, 1f, look.shadow())
        box.addView(title)
        val field = EditText(this)
        field.setText(ClipPane.url(this))
        field.setSelection(field.text.length)
        field.setTextColor(Color.parseColor("#1A1230"))
        field.setHintTextColor(Color.parseColor("#661A1230"))
        field.hint = "https://www.youtube.com/watch?v=…"
        field.textSize = look.sp(15f)
        field.typeface = look.typeface(false)
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        field.setSingleLine(true)
        field.background = glass(14, "#F4F0FA")
        field.setPadding(dp(14), dp(12), dp(14), dp(12))
        field.isFocusable = true
        field.isFocusableInTouchMode = true
        field.id = View.generateViewId()
        val fieldLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        fieldLp.topMargin = dp(14)
        box.addView(field, fieldLp)
        fun close() {
            root.removeView(scrim)
        }
        val save = action("Guardar") {
            val raw = field.text?.toString().orEmpty()
            if (!ClipPane.saveUrl(this, raw)) {
                Toast.makeText(this, "Enlace de YouTube no válido", Toast.LENGTH_SHORT).show()
                return@action
            }
            close()
            Toast.makeText(this, "Descargando video…", Toast.LENGTH_SHORT).show()
            ClipStore.replaceAsync(
                this,
                onReady = {
                    Toast.makeText(this, "Video listo para el inicio", Toast.LENGTH_SHORT).show()
                    commitLook("clip-url")
                },
                onStatus = { message ->
                    if (message != "Video listo" && message != "Descargando video…") {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        val cancel = action("Cancelar") { close() }
        val saveLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        saveLp.topMargin = dp(14)
        box.addView(save, saveLp)
        val cancelLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        cancelLp.topMargin = dp(8)
        box.addView(cancel, cancelLp)
        scrim.addView(box, FrameLayout.LayoutParams(dp(520), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(scrim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        field.requestFocus()
    }

    private fun openSystem(action: String) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    private fun openApp(packageName: String) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    companion object {
        const val EXTRA_PAGE = "page"
    }

    private data class Ink(val base: Float, val bold: Boolean)
}
