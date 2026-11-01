package com.tvdonghua.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : Activity() {
    private data class AppEntry(val label: String, val component: ComponentName, val icon: Drawable)

    private lateinit var prefs: SharedPreferences
    private lateinit var root: FrameLayout
    private lateinit var clock: TextView
    private lateinit var chestImage: ImageView
    private lateinit var chest: FrameLayout
    private lateinit var modal: FrameLayout
    private lateinit var modalTitle: TextView
    private lateinit var grid: LinearLayout
    private lateinit var gridScroll: ScrollView
    private lateinit var cancel: TextView
    private lateinit var removeScrim: FrameLayout
    private lateinit var removeCard: LinearLayout
    private val spheres = ArrayList<FrameLayout>(7)
    private val sphereIcons = ArrayList<ImageView>(7)
    private var apps: List<AppEntry> = emptyList()
    private var returnView: View? = null
    private var removeIndex = -1
    private var placed = false

    private val spots = arrayOf(
        0.50f to 0.20f,
        0.355f to 0.33f,
        0.645f to 0.33f,
        0.255f to 0.49f,
        0.745f to 0.47f,
        0.385f to 0.63f,
        0.635f to 0.61f
    )

    private val tick = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            clock.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("spheres", MODE_PRIVATE)
        window.decorView.setBackgroundColor(Color.parseColor("#1A1440"))
        root = FrameLayout(this)
        root.clipChildren = false
        root.clipToPadding = false

        val wallpaper = ImageView(this)
        wallpaper.setImageResource(R.drawable.bg_home)
        wallpaper.scaleType = ImageView.ScaleType.CENTER_CROP
        wallpaper.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        root.addView(wallpaper, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        for (i in 0 until 7) {
            val orb = FrameLayout(this)
            orb.background = getDrawable(if (i % 2 == 0) R.drawable.bg_orb_cyan else R.drawable.bg_orb_amber)
            orb.visibility = View.INVISIBLE
            orb.isFocusable = true
            orb.isClickable = false
            val icon = ImageView(this)
            icon.scaleType = ImageView.ScaleType.CENTER_CROP
            icon.visibility = View.GONE
            icon.clipToOutline = true
            icon.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            val iconLp = FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER)
            orb.addView(icon, iconLp)
            attachVeil(orb, 54)
            val index = i
            wireOk(orb, onShort = { onSphereOk(index) }, onHold = { onSphereHold(index) })
            spheres.add(orb)
            sphereIcons.add(icon)
            root.addView(orb, FrameLayout.LayoutParams(dp(108), dp(108), Gravity.START or Gravity.TOP))
        }

        chest = FrameLayout(this)
        chest.visibility = View.INVISIBLE
        chest.isFocusable = true
        chest.isClickable = false
        chestImage = ImageView(this)
        chestImage.setImageResource(R.drawable.ic_chest)
        chestImage.scaleType = ImageView.ScaleType.FIT_CENTER
        chest.addView(chestImage, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        attachVeil(chest, 18)
        wireOk(chest, onShort = { openDrawer() }, onHold = null)
        root.addView(chest, FrameLayout.LayoutParams(dp(156), dp(116), Gravity.START or Gravity.TOP))

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
        root.addView(clock, clockLp)

        modal = buildModal()
        root.addView(modal, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        removeScrim = buildRemove()
        root.addView(removeScrim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (root.width == 0 || root.height == 0) return@addOnLayoutChangeListener
            placeDesk(root.width, root.height)
        }

        loadApps()
        refreshSpheres()
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        refreshSpheres()
        tick.run()
    }

    override fun onPause() {
        clock.removeCallbacks(tick)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (removeScrim.visibility == View.VISIBLE) hideRemove()
        if (modal.visibility == View.VISIBLE) hideModal()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && closeOverlay()) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        closeOverlay()
    }

    private fun closeOverlay(): Boolean {
        if (!::removeScrim.isInitialized) return true
        if (removeScrim.visibility == View.VISIBLE) {
            hideRemove()
            return true
        }
        if (modal.visibility == View.VISIBLE) {
            hideModal()
            return true
        }
        return true
    }

    private fun placeDesk(w: Int, h: Int) {
        val orb = dp(108)
        for (i in spots.indices) {
            val (fx, fy) = spots[i]
            move(spheres[i], (w * fx - orb / 2f).toInt(), (h * fy - orb / 2f).toInt(), orb, orb)
            spheres[i].visibility = View.VISIBLE
        }
        val cw = (w * 0.22f).toInt()
        val chh = (cw * 113f / 215f).toInt()
        move(chest, (w * 0.26f - cw / 2f).toInt(), (h * 0.90f - chh / 2f).toInt(), cw, chh)
        chest.visibility = View.VISIBLE
        if (!placed) {
            placed = true
            spheres[0].requestFocus()
        }
        spheres[5].nextFocusDownId = chest.id
        spheres[3].nextFocusDownId = chest.id
        chest.nextFocusUpId = spheres[5].id
    }

    private fun move(view: View, left: Int, top: Int, wv: Int, hv: Int) {
        if (view.id == View.NO_ID) view.id = View.generateViewId()
        val lp = view.layoutParams as FrameLayout.LayoutParams
        val x = left.coerceAtLeast(0)
        val y = top.coerceAtLeast(0)
        if (lp.leftMargin == x && lp.topMargin == y && lp.width == wv && lp.height == hv) return
        lp.width = wv
        lp.height = hv
        lp.gravity = Gravity.START or Gravity.TOP
        lp.leftMargin = x
        lp.topMargin = y
        view.layoutParams = lp
    }

    private fun onSphereOk(index: Int) {
        val entry = entryFor(index)
        if (entry == null) openAssign(index) else launch(entry)
    }

    private fun onSphereHold(index: Int): Boolean {
        if (entryFor(index) == null) return false
        showRemove(index)
        return true
    }

    private fun openAssign(index: Int) {
        showModal("Elegir aplicación", apps) { chosen ->
            assign(index, chosen)
            refreshSpheres()
        }
    }

    private fun openDrawer() {
        val pinned = (0 until 7).mapNotNull { prefs.getString(key(it), null) }.toSet()
        val rest = apps.filter { it.component.flattenToString() !in pinned }
        showModal("Aplicaciones", rest) { launch(it) }
    }

    private fun assign(index: Int, entry: AppEntry) {
        val flat = entry.component.flattenToString()
        val editor = prefs.edit()
        for (i in 0 until 7) {
            if (i != index && prefs.getString(key(i), null) == flat) editor.remove(key(i))
        }
        editor.putString(key(index), flat).apply()
    }

    private fun key(index: Int) = "sphere_$index"

    private fun entryFor(index: Int): AppEntry? {
        val flat = prefs.getString(key(index), null) ?: return null
        val found = apps.find { it.component.flattenToString() == flat }
        if (found == null) prefs.edit().remove(key(index)).apply()
        return found
    }

    private fun refreshSpheres() {
        if (spheres.isEmpty()) return
        for (i in spheres.indices) {
            val entry = entryFor(i)
            val icon = sphereIcons[i]
            if (entry == null) {
                icon.visibility = View.GONE
                icon.setImageDrawable(null)
            } else {
                icon.setImageDrawable(copyIcon(entry.icon))
                icon.visibility = View.VISIBLE
            }
        }
    }

    private fun launch(entry: AppEntry) {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).setComponent(entry.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    private fun loadApps() {
        val pm = packageManager
        val seen = LinkedHashMap<ComponentName, ResolveInfo>()
        fun collect(category: String) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(intent, 0)) {
                val activity = info.activityInfo ?: continue
                if (activity.packageName == packageName) continue
                val component = ComponentName(activity.packageName, activity.name)
                seen.putIfAbsent(component, info)
            }
        }
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER)
        collect(Intent.CATEGORY_LAUNCHER)
        apps = seen.mapNotNull { (component, info) ->
            try {
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                AppEntry(
                    if (label.isEmpty()) component.packageName else label,
                    component,
                    info.loadIcon(pm)
                )
            } catch (_: Exception) {
                null
            }
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun showModal(title: String, entries: List<AppEntry>, onPick: (AppEntry) -> Unit) {
        returnView = currentFocus ?: spheres[0]
        modalTitle.text = title
        grid.removeAllViews()
        var first: View? = null
        if (entries.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No hay más aplicaciones"
            empty.setTextColor(Color.parseColor("#1A1230"))
            empty.textSize = 16f
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, dp(24), 0, dp(24))
            grid.addView(empty)
        } else {
            val cellW = dp(148)
            entries.chunked(3).forEach { rowEntries ->
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_HORIZONTAL
                rowEntries.forEach { entry ->
                    val cell = appCell(entry, cellW) {
                        hideModal()
                        onPick(entry)
                    }
                    if (first == null) first = cell
                    row.addView(cell)
                }
                grid.addView(row)
            }
        }
        setDeskFocusable(false)
        modal.visibility = View.VISIBLE
        modal.post {
            if (grid.childCount > 0 && grid.getChildAt(0).height == 0) {
                modal.post { fitGrid(first) }
            } else {
                fitGrid(first)
            }
        }
    }

    private fun fitGrid(first: View?) {
        if (grid.childCount > 0 && grid.getChildAt(0).height > 0) {
            val rows = minOf(3, grid.childCount)
            var height = 0
            for (i in 0 until rows) height += grid.getChildAt(i).height
            val lp = gridScroll.layoutParams
            lp.height = height
            gridScroll.layoutParams = lp
        }
        (first ?: cancel).requestFocus()
    }

    private fun hideModal() {
        if (modal.visibility != View.VISIBLE) return
        modal.visibility = View.GONE
        setDeskFocusable(true)
        (returnView ?: spheres[0]).requestFocus()
    }

    private fun showRemove(index: Int) {
        removeIndex = index
        returnView = spheres[index]
        val anchor = spheres[index]
        val cardW = dp(168)
        val cardH = dp(116)
        var x = anchor.left + anchor.width + dp(12)
        var y = anchor.top
        if (x + cardW > root.width) x = anchor.left - cardW - dp(12)
        if (y + cardH > root.height) y = root.height - cardH - dp(12)
        val lp = removeCard.layoutParams as FrameLayout.LayoutParams
        lp.width = cardW
        lp.leftMargin = x.coerceAtLeast(dp(8))
        lp.topMargin = y.coerceAtLeast(dp(8))
        lp.gravity = Gravity.START or Gravity.TOP
        removeCard.layoutParams = lp
        removeScrim.visibility = View.VISIBLE
    }

    private fun focusRemove() {
        if (removeScrim.visibility != View.VISIBLE) return
        setDeskFocusable(false)
        removeCard.getChildAt(0).requestFocus()
    }

    private fun hideRemove() {
        if (removeScrim.visibility != View.VISIBLE) return
        removeScrim.visibility = View.GONE
        removeIndex = -1
        setDeskFocusable(true)
        (returnView ?: spheres[0]).requestFocus()
    }

    private fun setDeskFocusable(enabled: Boolean) {
        spheres.forEach { it.isFocusable = enabled }
        chest.isFocusable = enabled
    }

    private fun buildModal(): FrameLayout {
        val scrim = FrameLayout(this)
        scrim.setBackgroundColor(Color.parseColor("#66080A18"))
        scrim.visibility = View.GONE
        scrim.isClickable = true
        scrim.isFocusable = false

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(dp(22), dp(20), dp(22), dp(18))
        box.background = glass(28)
        box.isFocusable = false

        modalTitle = TextView(this)
        modalTitle.setTextColor(Color.WHITE)
        modalTitle.textSize = 22f
        modalTitle.gravity = Gravity.CENTER
        modalTitle.setShadowLayer(6f, 0f, 1f, Color.parseColor("#66000000"))
        box.addView(modalTitle)

        grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        grid.gravity = Gravity.CENTER_HORIZONTAL
        gridScroll = ScrollView(this)
        val scroll = gridScroll
        scroll.isFocusable = false
        scroll.isFillViewport = false
        scroll.addView(grid, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val scrollLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(280))
        scrollLp.topMargin = dp(14)
        box.addView(scroll, scrollLp)

        cancel = actionButton("Cancelar") { hideModal() }
        val cancelLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        cancelLp.topMargin = dp(8)
        box.addView(cancel, cancelLp)

        val boxLp = FrameLayout.LayoutParams(dp(520), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        scrim.addView(box, boxLp)
        return scrim
    }

    private fun buildRemove(): FrameLayout {
        val scrim = FrameLayout(this)
        scrim.setBackgroundColor(Color.TRANSPARENT)
        scrim.visibility = View.GONE
        scrim.isClickable = true
        scrim.isFocusable = false

        removeCard = LinearLayout(this)
        removeCard.orientation = LinearLayout.VERTICAL
        removeCard.gravity = Gravity.CENTER_HORIZONTAL
        removeCard.setPadding(dp(12), dp(12), dp(12), dp(12))
        removeCard.background = glass(18)
        removeCard.addView(actionButton("Retirar") {
            val index = removeIndex
            hideRemove()
            if (index >= 0) {
                prefs.edit().remove(key(index)).apply()
                refreshSpheres()
            }
        })
        val cancelLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        cancelLp.topMargin = dp(8)
        val cancelButton = actionButton("Cancelar") { hideRemove() }
        removeCard.addView(cancelButton, cancelLp)
        scrim.addView(removeCard, FrameLayout.LayoutParams(dp(168), ViewGroup.LayoutParams.WRAP_CONTENT))
        return scrim
    }

    private fun appCell(entry: AppEntry, cellW: Int, onPick: () -> Unit): View {
        val cell = LinearLayout(this)
        cell.orientation = LinearLayout.VERTICAL
        cell.gravity = Gravity.CENTER_HORIZONTAL
        cell.setPadding(dp(8), dp(8), dp(8), dp(8))
        cell.background = glass(16)
        cell.isFocusable = true
        cell.isClickable = true
        val icon = ImageView(this)
        icon.setImageDrawable(copyIcon(entry.icon))
        icon.scaleType = ImageView.ScaleType.FIT_CENTER
        cell.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
        val label = TextView(this)
        label.text = entry.label
        label.setTextColor(Color.parseColor("#1A1230"))
        label.textSize = 14f
        label.gravity = Gravity.CENTER
        label.maxLines = 2
        label.ellipsize = TextUtils.TruncateAt.END
        val labelLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        labelLp.topMargin = dp(6)
        cell.addView(label, labelLp)
        attachVeil(cell, 16, 150)
        cell.setOnClickListener { onPick() }
        val lp = LinearLayout.LayoutParams(cellW, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.marginStart = dp(4)
        lp.marginEnd = dp(4)
        lp.bottomMargin = dp(8)
        cell.layoutParams = lp
        return cell
    }

    private fun actionButton(label: String, onClick: () -> Unit): TextView {
        val button = TextView(this)
        button.text = label
        button.setTextColor(Color.parseColor("#1A1230"))
        button.textSize = 16f
        button.gravity = Gravity.CENTER
        button.setPadding(dp(22), dp(10), dp(22), dp(10))
        button.background = glass(22, "#F4F0FA")
        button.isFocusable = true
        button.isClickable = true
        attachVeil(button, 22, 150)
        button.setOnClickListener { onClick() }
        return button
    }

    private fun wireOk(view: View, onShort: () -> Unit, onHold: (() -> Boolean)?) {
        var held = false
        val hold = Runnable {
            if (onHold?.invoke() == true) held = true
        }
        view.setOnKeyListener { _, keyCode, event ->
            if (!isOk(keyCode)) return@setOnKeyListener false
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        held = false
                        view.removeCallbacks(hold)
                        if (onHold != null) view.postDelayed(hold, 600L)
                    }
                    true
                }
                KeyEvent.ACTION_UP -> {
                    view.removeCallbacks(hold)
                    val wasHeld = held
                    held = false
                    if (!wasHeld) onShort() else view.post { focusRemove() }
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

    private fun attachVeil(view: View, radiusDp: Int, focusedAlpha: Int = 87) {
        val veil = GradientDrawable()
        veil.cornerRadius = dp(radiusDp).toFloat()
        veil.setColor(Color.WHITE)
        veil.alpha = 0
        view.foreground = veil
        view.setOnFocusChangeListener { _, has ->
            veil.alpha = if (has) focusedAlpha else 0
            view.invalidate()
        }
    }

    private fun glass(radiusDp: Int, color: String = "#73E6D8F8"): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.cornerRadius = dp(radiusDp).toFloat()
        drawable.setColor(Color.parseColor(color))
        return drawable
    }

    private fun copyIcon(icon: Drawable): Drawable {
        return icon.constantState?.newDrawable()?.mutate() ?: icon
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
