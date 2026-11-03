package com.tvdonghua.launcher

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView

/** Destellos en tuerca y hora, a tiempo con la energía del fondo Levitar. */
internal class ChromeSparks(
    private val root: FrameLayout,
    private val gear: View,
    private val clock: View
) {
    private val loop = object : Runnable {
        override fun run() {
            burst()
            root.postDelayed(this, 15000L)
        }
    }
    private var running = false

    fun sync(enabled: Boolean) {
        if (enabled && !running) {
            running = true
            root.removeCallbacks(loop)
            // El haz del video suele ir a mitad del loop de 15 s.
            root.postDelayed(loop, 7500L)
        } else if (!enabled && running) {
            running = false
            root.removeCallbacks(loop)
            gear.animate().cancel()
            clock.animate().cancel()
            gear.scaleX = 1f
            gear.scaleY = 1f
            clock.scaleX = 1f
            clock.scaleY = 1f
        }
    }

    fun stop() {
        sync(false)
    }

    private fun burst() {
        pulse(gear, 220L)
        root.postDelayed({ pulse(clock, 260L) }, 180L)
        spawnOrb(gear, R.drawable.bg_orb_cyan)
        root.postDelayed({ spawnOrb(clock, R.drawable.bg_orb_amber) }, 160L)
        root.postDelayed({ spawnOrb(gear, R.drawable.bg_orb_amber) }, 320L)
        root.postDelayed({ spawnOrb(clock, R.drawable.bg_orb_cyan) }, 420L)
    }

    private fun pulse(view: View, duration: Long) {
        val up = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.18f).setDuration(duration)
        val upY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.18f).setDuration(duration)
        val down = ObjectAnimator.ofFloat(view, View.SCALE_X, 1.18f, 1f).setDuration(duration)
        val downY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1.18f, 1f).setDuration(duration)
        val glow = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.72f, 1f).setDuration(duration * 2)
        AnimatorSet().apply {
            play(up).with(upY).with(glow)
            play(down).with(downY).after(up)
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        val oldBg = view.background
        view.background = view.context.glass(22, "#88E7F7FF")
        view.postDelayed({
            if (view === gear) {
                view.background = view.context.glass(22, if (view.isFocused) "#59FFFFFF" else "#26FFFFFF")
            } else {
                view.background = if (view.isFocused) view.context.glass(18, "#26FFFFFF") else oldBg
            }
        }, duration * 2)
    }

    private fun spawnOrb(anchor: View, drawable: Int) {
        val loc = IntArray(2)
        val rootLoc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        root.getLocationOnScreen(rootLoc)
        val size = root.context.dp(52)
        val orb = ImageView(root.context)
        orb.setImageResource(drawable)
        orb.alpha = 0f
        orb.setColorFilter(Color.WHITE)
        val lp = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.START)
        lp.leftMargin = (loc[0] - rootLoc[0] + anchor.width / 2 - size / 2)
        lp.topMargin = (loc[1] - rootLoc[1] + anchor.height / 2 - size / 2)
        root.addView(orb, lp)
        orb.animate()
            .alpha(0.9f)
            .scaleX(1.35f)
            .scaleY(1.35f)
            .translationY(-root.context.dp(18).toFloat())
            .setDuration(420L)
            .withEndAction {
                orb.animate()
                    .alpha(0f)
                    .scaleX(0.6f)
                    .scaleY(0.6f)
                    .translationY(-root.context.dp(36).toFloat())
                    .setDuration(380L)
                    .withEndAction {
                        (orb.parent as? ViewGroup)?.removeView(orb)
                    }
                    .start()
            }
            .start()
    }
}
