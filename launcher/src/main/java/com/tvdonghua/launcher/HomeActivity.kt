package com.tvdonghua.launcher

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var look: Look
    private lateinit var root: FrameLayout
    private lateinit var livingBg: LivingBg
    private lateinit var clipPane: ClipPane
    private lateinit var clock: TextView
    private lateinit var settingsChip: ImageView
    private lateinit var favLabel: TextView
    private lateinit var appLabel: TextView
    private lateinit var favRow: SlideRow
    private lateinit var appRow: SlideRow
    private lateinit var menuScrim: FrameLayout
    private lateinit var menuCard: LinearLayout
    private lateinit var chromeSparks: ChromeSparks
    private var pickerScrim: FrameLayout? = null
    private var menuArmed = false
    private var menuReadyAt = 0L
    private var apps: List<AppEntry> = emptyList()
    private var rendered = ""
    private var returnView: View? = null

    private val tick = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            clock.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("spheres", MODE_PRIVATE)
        look = Look(this)
        window.decorView.setBackgroundColor(Color.parseColor("#1A1440"))
        root = FrameLayout(this)
        root.clipChildren = false

        livingBg = LivingBg(this)
        root.addView(livingBg, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        settingsChip = ImageView(this)
        settingsChip.setImageResource(R.drawable.ic_gear)
        settingsChip.scaleType = ImageView.ScaleType.FIT_CENTER
        settingsChip.setPadding(dp(10), dp(10), dp(10), dp(10))
        settingsChip.background = glass(22, "#26FFFFFF")
        settingsChip.isFocusable = true
        settingsChip.isClickable = true
        settingsChip.id = View.generateViewId()
        settingsChip.contentDescription = "Ajustes"
        settingsChip.setOnFocusChangeListener { view, has ->
            view.background = glass(22, if (has) "#59FFFFFF" else "#26FFFFFF")
        }
        wireOk(settingsChip, { openSettings("hora") }, null, null)
        settingsChip.setOnClickListener { openSettings("hora") }

        clock = TextView(this)
        clock.setTextColor(Color.WHITE)
        clock.textSize = 26f
        clock.typeface = Typeface.DEFAULT_BOLD
        clock.gravity = Gravity.CENTER
        clock.setPadding(dp(12), dp(6), dp(4), dp(6))
        clock.setShadowLayer(12f, 0f, 2f, Color.parseColor("#88000000"))
        clock.isFocusable = true
        clock.isClickable = true
        clock.id = View.generateViewId()
        clock.setOnFocusChangeListener { view, has ->
            view.background = if (has) glass(18, "#26FFFFFF") else null
        }
        wireOk(clock, { openSettings("hora") }, null, null)
        clock.setOnClickListener { openSettings("hora") }

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.addView(settingsChip, LinearLayout.LayoutParams(dp(44), dp(44)))
        val clockLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        clockLp.marginStart = dp(8)
        bar.addView(clock, clockLp)
        val barLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        )
        barLp.topMargin = dp(18)
        barLp.marginEnd = dp(28)
        root.addView(bar, barLp)
        settingsChip.nextFocusRightId = clock.id
        settingsChip.nextFocusLeftId = settingsChip.id
        clock.nextFocusLeftId = settingsChip.id
        clock.nextFocusRightId = clock.id
        chromeSparks = ChromeSparks(root, settingsChip, clock)

        val stack = LinearLayout(this)
        stack.orientation = LinearLayout.VERTICAL
        stack.clipChildren = true
        favLabel = channelLabel()
        favLabel.text = "Favoritos"
        appLabel = channelLabel()
        appLabel.text = "Aplicaciones"
        favRow = SlideRow(this)
        appRow = SlideRow(this)
        stack.addView(favLabel, labelLp(0))
        stack.addView(favRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        stack.addView(appLabel, labelLp(dp(6)))
        stack.addView(appRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val stackLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        stackLp.bottomMargin = dp(22)
        root.addView(stack, stackLp)

        clipPane = ClipPane(this)
        val clipLp = FrameLayout.LayoutParams(dp(248), dp(140), Gravity.BOTTOM or Gravity.END)
        clipLp.bottomMargin = dp(22)
        clipLp.marginEnd = dp(28)
        root.addView(clipPane, clipLp)

        menuScrim = FrameLayout(this)
        menuScrim.setBackgroundColor(Color.TRANSPARENT)
        menuScrim.visibility = View.GONE
        menuScrim.isClickable = true
        menuScrim.isFocusable = false
        menuCard = LinearLayout(this)
        menuCard.orientation = LinearLayout.VERTICAL
        menuCard.gravity = Gravity.CENTER_HORIZONTAL
        menuCard.setPadding(dp(22), dp(18), dp(22), dp(16))
        menuCard.background = glassArgb(28, look.panelArgb())
        menuScrim.addView(menuCard, FrameLayout.LayoutParams(
            dp(340),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        root.addView(menuScrim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)
        apps = loadLaunchableApps()
        applyChrome()
        publish(null)
    }

    private fun applyChrome() {
        look = Look(this)
        settingsChip.setColorFilter(look.textColor())
        clock.setTextColor(look.textColor())
        clock.textSize = look.sp(26f)
        clock.typeface = look.typeface(true)
        clock.setShadowLayer(8f, 0f, 1f, look.shadow())
        favLabel.setTextColor(look.textColor())
        favLabel.textSize = look.sp(14f)
        favLabel.typeface = look.typeface(true)
        favLabel.setShadowLayer(6f, 0f, 1f, look.shadow())
        appLabel.setTextColor(look.textColor())
        appLabel.textSize = look.sp(14f)
        appLabel.typeface = look.typeface(true)
        appLabel.setShadowLayer(6f, 0f, 1f, look.shadow())
        menuCard.background = glassArgb(28, look.panelArgb())
        if (::livingBg.isInitialized) livingBg.applyLook()
        if (::clipPane.isInitialized) clipPane.applyLook()
        if (::chromeSparks.isInitialized) chromeSparks.sync(look.bgSparksUi())
    }

    override fun onResume() {
        super.onResume()
        apps = loadLaunchableApps()
        applyChrome()
        if (::clipPane.isInitialized) {
            clipPane.reloadFromPrefs()
            clipPane.play()
        }
        if (::livingBg.isInitialized) livingBg.startMotion()
        if (menuScrim.visibility != View.VISIBLE && pickerScrim == null) {
            val sig = signature()
            if (sig != rendered) publish(currentFocus?.tag as? String)
        }
        tick.run()
    }

    override fun onPause() {
        clock.removeCallbacks(tick)
        if (::clipPane.isInitialized) clipPane.pause()
        if (::livingBg.isInitialized) livingBg.stopMotion()
        if (::chromeSparks.isInitialized) chromeSparks.stop()
        super.onPause()
    }

    override fun onDestroy() {
        if (::clipPane.isInitialized) clipPane.release()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        hideMenu()
        hidePicker()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            if (pickerScrim != null) hidePicker()
            else if (menuScrim.visibility == View.VISIBLE) hideMenu()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("El sistema lo marcó obsoleto")
    override fun onBackPressed() {
        if (pickerScrim != null) hidePicker()
        else if (menuScrim.visibility == View.VISIBLE) hideMenu()
    }

    private fun openSettings(page: String) {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_PAGE, page))
    }

    private fun channelLabel(): TextView {
        val label = TextView(this)
        label.setTextColor(look.textColor())
        label.textSize = look.sp(14f)
        label.typeface = look.typeface(true)
        return label
    }

    private fun labelLp(top: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.marginStart = dp(36)
        lp.topMargin = top
        return lp
    }

    private fun signature(): String {
        val fav = favorites().joinToString(",") { it.component.flattenToString() }
        val rest = others().joinToString(",") { it.component.flattenToString() }
        return "$fav#$rest@${look.signature()}"
    }

    private fun favorites(): List<AppEntry> {
        val byFlat = apps.associateBy { it.component.flattenToString() }
        val saved = loadOrder().filter { it in byFlat }
        return saved.map { byFlat.getValue(it) }
    }

    private fun others(): List<AppEntry> {
        val byFlat = apps.associateBy { it.component.flattenToString() }
        val pinned = loadOrder().toSet()
        return loadBottom().filter { it in byFlat && it !in pinned }.map { byFlat.getValue(it) }
    }

    private fun loadOrder(): MutableList<String> {
        if (!prefs.contains("order")) {
            val old = (0 until 8).mapNotNull { prefs.getString("sphere_$it", null) }
            prefs.edit().putString("order", old.joinToString("\n")).apply()
        }
        return prefs.getString("order", "").orEmpty().lineSequence().filter { it.isNotBlank() }.toMutableList()
    }

    private fun saveOrder(order: List<String>) {
        prefs.edit().putString("order", order.joinToString("\n")).apply()
    }

    private fun loadBottom(): MutableList<String> {
        if (!prefs.contains("order2")) {
            val pinned = loadOrder().toSet()
            val seeded = apps.map { it.component.flattenToString() }.filter { it !in pinned }
            prefs.edit().putString("order2", seeded.joinToString("\n")).apply()
        }
        return prefs.getString("order2", "").orEmpty().lineSequence().filter { it.isNotBlank() }.toMutableList()
    }

    private fun saveBottom(order: List<String>) {
        prefs.edit().putString("order2", order.joinToString("\n")).apply()
    }

    private fun publish(focusFlat: String?) {
        val favs = favorites()
        val rest = others()
        rendered = signature()
        favRow.strip.removeAllViews()
        appRow.strip.removeAllViews()
        val favCards = favs.mapIndexed { index, entry ->
            card(entry, true, index, favs.size).also { favRow.strip.addView(it) }
        }
        val appCards = rest.mapIndexed { index, entry ->
            card(entry, false, index, rest.size).also { appRow.strip.addView(it) }
        }
        val favPlus = plusCard(true).also { favRow.attachPlus(it) }
        val appPlus = plusCard(false).also { appRow.attachPlus(it) }
        val favChain = favCards + favPlus
        val appChain = appCards + appPlus
        favLabel.visibility = View.VISIBLE
        favRow.visibility = View.VISIBLE
        appLabel.visibility = View.VISIBLE
        appRow.visibility = View.VISIBLE
        wireRows(favChain, appChain)
        val target = (favChain + appChain).firstOrNull { it.tag == focusFlat } ?: favChain.first()
        target.post { target.requestFocus() }
    }

    private fun card(entry: AppEntry, favorite: Boolean, index: Int, count: Int): LinearLayout {
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.gravity = Gravity.CENTER_HORIZONTAL
        column.clipChildren = false
        column.isFocusable = true
        column.isClickable = true
        column.id = View.generateViewId()
        column.tag = entry.component.flattenToString()
        val pill = FrameLayout(this)
        pill.background = glassArgb(30, look.cardArgb(false))
        pill.isFocusable = false
        val icon = ImageView(this)
        icon.setImageDrawable(copyIcon(entry.icon))
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        val iconDp = dp(look.iconDp())
        pill.addView(icon, FrameLayout.LayoutParams(iconDp, iconDp, Gravity.CENTER))
        column.addView(pill, LinearLayout.LayoutParams(dp(look.cardWidthDp()), dp(look.cardHeightDp())))
        val name = TextView(this)
        name.text = entry.label
        name.gravity = Gravity.CENTER
        name.maxLines = 1
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        name.setTextColor(look.textColor())
        name.textSize = look.sp(12f)
        name.typeface = look.typeface(true)
        name.setShadowLayer(6f, 0f, 1f, look.shadow())
        name.visibility = View.INVISIBLE
        val nameLp = LinearLayout.LayoutParams(dp(look.cardWidthDp()), ViewGroup.LayoutParams.WRAP_CONTENT)
        nameLp.topMargin = dp(2)
        column.addView(name, nameLp)
        val row = if (favorite) favRow else appRow
        column.setOnFocusChangeListener { _, has ->
            paintCard(pill, has)
            name.visibility = if (has) View.VISIBLE else View.INVISIBLE
            name.setTextColor(look.textColor())
            if (has) row.park(column)
        }
        val flat = entry.component.flattenToString()
        wireOk(column, { launch(entry) }, { showMenu(column, entry, favorite, index, count) }) {
            armMenu()
        }
        column.setOnClickListener { launch(entry) }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.marginStart = dp(8)
        lp.marginEnd = dp(8)
        column.layoutParams = lp
        return column
    }

    private fun plusCard(top: Boolean): FrameLayout {
        val size = dp(60)
        val pill = FrameLayout(this)
        pill.background = glassArgb(30, look.cardArgb(false))
        pill.isFocusable = true
        pill.isClickable = true
        pill.id = View.generateViewId()
        pill.tag = if (top) "plus:top" else "plus:bottom"
        val mark = TextView(this)
        mark.text = "+"
        mark.setTextColor(look.textColor())
        mark.textSize = look.sp(26f)
        mark.typeface = look.typeface(true)
        mark.setShadowLayer(6f, 0f, 1f, look.shadow())
        mark.gravity = Gravity.CENTER
        pill.addView(mark, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        val row = if (top) favRow else appRow
        pill.setOnFocusChangeListener { view, has ->
            paintCard(view, has)
            if (has) row.park(view)
        }
        wireOk(pill, { openPicker(top) }, null, null)
        pill.setOnClickListener { openPicker(top) }
        val lp = LinearLayout.LayoutParams(size, size)
        lp.topMargin = (dp(look.cardHeightDp()) - size) / 2
        lp.marginStart = dp(4)
        lp.marginEnd = dp(8)
        pill.layoutParams = lp
        return pill
    }

    private fun paintCard(view: View, focused: Boolean) {
        view.background = glassArgb(30, look.cardArgb(focused))
        val scale = if (focused) 1.1f else 1f
        view.animate().scaleX(scale).scaleY(scale).setDuration(140L).start()
    }

    private fun wireRows(favCards: List<View>, appCards: List<View>) {
        fun link(cards: List<View>, up: List<View>?, down: List<View>?) {
            cards.forEachIndexed { i, card ->
                card.nextFocusLeftId = if (i == 0) card.id else cards[i - 1].id
                card.nextFocusRightId = if (i == cards.lastIndex) card.id else cards[i + 1].id
                card.nextFocusUpId = when {
                    up.isNullOrEmpty() -> settingsChip.id
                    else -> up[i.coerceAtMost(up.lastIndex)].id
                }
                card.nextFocusDownId = when {
                    down.isNullOrEmpty() -> card.id
                    else -> down[i.coerceAtMost(down.lastIndex)].id
                }
            }
        }
        link(favCards, null, appCards)
        link(appCards, favCards.ifEmpty { null }, null)
        val first = favCards.firstOrNull() ?: appCards.firstOrNull() ?: return
        settingsChip.nextFocusDownId = first.id
        clock.nextFocusDownId = first.id
        settingsChip.nextFocusUpId = settingsChip.id
        clock.nextFocusUpId = clock.id
        val lastFav = favCards.lastOrNull()
        val lastApp = appCards.lastOrNull()
        if (look.clipVisible()) {
            lastFav?.nextFocusRightId = clipPane.id
            lastApp?.nextFocusRightId = clipPane.id
            clipPane.nextFocusLeftId = (lastApp ?: lastFav)?.id ?: clipPane.id
            clipPane.nextFocusUpId = clock.id
            clipPane.nextFocusDownId = clipPane.id
            clipPane.nextFocusRightId = clipPane.id
        } else {
            lastFav?.nextFocusRightId = lastFav.id
            lastApp?.nextFocusRightId = lastApp.id
        }
    }

    private fun launch(entry: AppEntry) {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).setComponent(entry.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    private fun showMenu(anchor: View, entry: AppEntry, favorite: Boolean, index: Int, count: Int) {
        returnView = anchor
        menuCard.removeAllViews()
        menuCard.background = glassArgb(28, look.panelArgb())
        val title = TextView(this)
        title.text = entry.label
        title.setTextColor(look.textColor())
        title.textSize = look.sp(20f)
        title.typeface = look.typeface(true)
        title.gravity = Gravity.CENTER
        title.setShadowLayer(6f, 0f, 1f, look.shadow())
        val titleLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        titleLp.bottomMargin = dp(14)
        menuCard.addView(title, titleLp)
        val flat = entry.component.flattenToString()
        if (index > 0) menuCard.addView(menuButton("Mover a la izquierda") { move(favorite, flat, -1) })
        if (index < count - 1) menuCard.addView(menuButton("Mover a la derecha") { move(favorite, flat, 1) })
        menuCard.addView(menuButton("Quitar") { drop(favorite, flat) })
        if (entry.component.packageName != packageName) {
            menuCard.addView(menuButton("Detener") { stopApp(entry) })
        }
        menuCard.addView(menuButton("Cancelar") { hideMenu() })
        val lp = menuCard.layoutParams as FrameLayout.LayoutParams
        lp.width = dp(340)
        lp.leftMargin = 0
        lp.topMargin = 0
        lp.gravity = Gravity.CENTER
        menuCard.layoutParams = lp
        menuScrim.setBackgroundColor(Color.parseColor("#33060A14"))
        menuArmed = false
        menuScrim.visibility = View.VISIBLE
    }

    private fun armMenu() {
        if (menuScrim.visibility != View.VISIBLE) return
        setCardsFocusable(false)
        var first: View? = null
        for (i in 0 until menuCard.childCount) {
            val child = menuCard.getChildAt(i)
            if (child.isFocusable) {
                first = child
                break
            }
        }
        first?.let {
            it.requestFocus()
            paintMenuButton(it, true)
        }
        menuReadyAt = android.os.SystemClock.uptimeMillis() + 250
        menuCard.post { menuArmed = true }
    }

    private fun menuAccepts(): Boolean {
        return menuArmed && android.os.SystemClock.uptimeMillis() >= menuReadyAt
    }

    private fun paintMenuButton(view: View, has: Boolean) {
        view.background = glass(14, if (has) "#F4F0FA" else "#22FFFFFF")
        (view as TextView).setTextColor(if (has) Color.parseColor("#1A1230") else look.textColor())
        view.setShadowLayer(if (has) 0f else 5f, 0f, 1f, look.shadow())
        val scale = if (has) 1.08f else 1f
        view.scaleX = scale
        view.scaleY = scale
    }

    private fun menuButton(text: String, onPick: () -> Unit): TextView {
        val button = TextView(this)
        button.text = text
        button.setTextColor(look.textColor())
        button.textSize = look.sp(16f)
        button.typeface = look.typeface(true)
        button.setShadowLayer(5f, 0f, 1f, look.shadow())
        button.gravity = Gravity.CENTER
        button.setPadding(dp(12), dp(10), dp(12), dp(10))
        button.background = glass(14, "#22FFFFFF")
        button.isFocusable = true
        button.isFocusableInTouchMode = true
        button.isClickable = true
        if (android.os.Build.VERSION.SDK_INT >= 26) button.defaultFocusHighlightEnabled = false
        button.setOnFocusChangeListener { view, has -> paintMenuButton(view, has) }
        wireOk(button, { if (menuAccepts()) onPick() }, null, null)
        button.setOnClickListener { if (menuAccepts()) onPick() }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = dp(8)
        button.layoutParams = lp
        return button
    }

    private fun hideMenu() {
        if (menuScrim.visibility != View.VISIBLE) return
        menuScrim.visibility = View.GONE
        setCardsFocusable(true)
        val back = returnView
        if (back != null && back.isAttachedToWindow) back.requestFocus()
    }

    private fun setCardsFocusable(enabled: Boolean) {
        fun walk(row: SlideRow) {
            for (i in 0 until row.strip.childCount) row.strip.getChildAt(i).isFocusable = enabled
            row.endView?.isFocusable = enabled
        }
        walk(favRow)
        walk(appRow)
        settingsChip.isFocusable = enabled
        clock.isFocusable = enabled
        if (::clipPane.isInitialized) clipPane.isFocusable = enabled
    }

    private fun move(top: Boolean, flat: String, delta: Int) {
        hideMenu()
        val list = if (top) loadOrder() else loadBottom()
        val from = list.indexOf(flat)
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        list.removeAt(from)
        list.add(to, flat)
        if (top) saveOrder(list) else saveBottom(list)
        publish(flat)
    }

    private fun stopApp(entry: AppEntry) {
        hideMenu()
        val pkg = entry.component.packageName
        try {
            val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            manager.killBackgroundProcesses(pkg)
        } catch (_: Exception) {
        }
        Thread {
            try {
                Runtime.getRuntime().exec(arrayOf("su", "0", "am", "force-stop", pkg))
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun drop(top: Boolean, flat: String) {
        hideMenu()
        if (top) saveOrder(loadOrder().filter { it != flat })
        else saveBottom(loadBottom().filter { it != flat })
        publish(null)
    }

    private fun place(flat: String, top: Boolean) {
        hideMenu()
        hidePicker()
        val fav = loadOrder().filter { it != flat }.toMutableList()
        val bottom = loadBottom().filter { it != flat }.toMutableList()
        if (top) fav.add(flat) else bottom.add(flat)
        saveOrder(fav)
        saveBottom(bottom)
        publish(flat)
    }

    private fun openPicker(top: Boolean) {
        hideMenu()
        hidePicker()
        val taken = (if (top) loadOrder() else loadBottom()).toSet()
        val choices = apps.filter { it.component.flattenToString() !in taken }
        val scrim = FrameLayout(this)
        scrim.setBackgroundColor(Color.parseColor("#33060A14"))
        scrim.isClickable = true
        scrim.isFocusable = false
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(18), dp(16), dp(18), dp(14))
        box.background = glassArgb(28, look.panelArgb())
        val title = TextView(this)
        title.text = if (top) "Agregar a favoritos" else "Agregar a aplicaciones"
        title.setTextColor(look.textColor())
        title.textSize = look.sp(22f)
        title.typeface = look.typeface(true)
        title.setShadowLayer(8f, 0f, 1f, look.shadow())
        title.gravity = Gravity.CENTER
        box.addView(title)
        val grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        grid.gravity = Gravity.CENTER_HORIZONTAL
        var first: View? = null
        if (choices.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No hay más aplicaciones"
            empty.setTextColor(look.textColor())
            empty.textSize = look.sp(16f)
            empty.typeface = look.typeface(false)
            empty.setShadowLayer(6f, 0f, 1f, look.shadow())
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, dp(24), 0, dp(24))
            grid.addView(empty)
        } else {
            choices.chunked(4).forEach { rowEntries ->
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER
                rowEntries.forEach { entry ->
                    val cell = pickerCell(entry) { place(entry.component.flattenToString(), top) }
                    if (first == null) first = cell
                    row.addView(cell)
                }
                grid.addView(row)
            }
        }
        val scroll = ScrollView(this)
        scroll.isFocusable = false
        scroll.addView(grid)
        val scrollLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250))
        scrollLp.topMargin = dp(12)
        box.addView(scroll, scrollLp)
        val cancel = menuButton("Cancelar") { hidePicker() }
        menuArmed = true
        menuReadyAt = 0L
        val cancelLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        cancelLp.topMargin = dp(8)
        box.addView(cancel, cancelLp)
        scrim.addView(box, FrameLayout.LayoutParams(dp(620), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(scrim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        pickerScrim = scrim
        setCardsFocusable(false)
        (first ?: cancel).requestFocus()
    }

    private fun pickerCell(entry: AppEntry, onPick: () -> Unit): LinearLayout {
        val cell = LinearLayout(this)
        cell.orientation = LinearLayout.VERTICAL
        cell.gravity = Gravity.CENTER_HORIZONTAL
        cell.setPadding(dp(8), dp(8), dp(8), dp(8))
        cell.background = glassArgb(16, look.cardArgb(false))
        cell.isFocusable = true
        cell.isClickable = true
        cell.id = View.generateViewId()
        val icon = ImageView(this)
        icon.setImageDrawable(copyIcon(entry.icon))
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        val pickerIcon = dp(look.iconDp().coerceAtMost(48))
        cell.addView(icon, LinearLayout.LayoutParams(pickerIcon, pickerIcon))
        val label = TextView(this)
        label.text = entry.label
        label.setTextColor(look.textColor())
        label.textSize = look.sp(13f)
        label.typeface = look.typeface(false)
        label.setShadowLayer(5f, 0f, 1f, look.shadow())
        label.gravity = Gravity.CENTER
        label.maxLines = 2
        val labelLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        labelLp.topMargin = dp(4)
        cell.addView(label, labelLp)
        cell.setOnFocusChangeListener { view, has ->
            view.background = if (has) glass(16, "#F4F0FA") else glassArgb(16, look.cardArgb(false))
            label.setTextColor(if (has) Color.parseColor("#1A1230") else look.textColor())
            label.setShadowLayer(if (has) 0f else 5f, 0f, 1f, look.shadow())
        }
        wireOk(cell, onPick, null, null)
        cell.setOnClickListener { onPick() }
        val lp = LinearLayout.LayoutParams(dp(132), ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.marginStart = dp(4)
        lp.marginEnd = dp(4)
        lp.bottomMargin = dp(8)
        cell.layoutParams = lp
        return cell
    }

    private fun hidePicker() {
        val scrim = pickerScrim ?: return
        root.removeView(scrim)
        pickerScrim = null
        if (menuScrim.visibility != View.VISIBLE) setCardsFocusable(true)
    }

    private fun wireOk(
        view: View,
        onShort: () -> Unit,
        onHold: (() -> Unit)?,
        onReleaseHeld: (() -> Unit)?
    ) {
        var held = false
        val hold = Runnable {
            held = true
            onHold?.invoke()
        }
        view.setOnKeyListener { _, keyCode, event ->
            if (!isOk(keyCode)) return@setOnKeyListener false
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        held = false
                        view.removeCallbacks(hold)
                        if (onHold != null) view.postDelayed(hold, 500L)
                    }
                    true
                }
                KeyEvent.ACTION_UP -> {
                    view.removeCallbacks(hold)
                    if (!held) onShort() else onReleaseHeld?.invoke()
                    held = false
                    true
                }
                else -> false
            }
        }
    }

    private fun isOk(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            keyCode == KeyEvent.KEYCODE_BUTTON_A
    }
}
