package com.tvdonghua.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable

internal data class AppEntry(val label: String, val component: ComponentName, val icon: Drawable)

internal fun Context.loadLaunchableApps(): List<AppEntry> {
    val pm = packageManager
    val seen = LinkedHashMap<ComponentName, ResolveInfo>()
    fun collect(category: String) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        for (info in pm.queryIntentActivities(intent, 0)) {
            val activity = info.activityInfo ?: continue
            if (activity.packageName == packageName) continue
            seen.putIfAbsent(ComponentName(activity.packageName, activity.name), info)
        }
    }
    collect(Intent.CATEGORY_LEANBACK_LAUNCHER)
    collect(Intent.CATEGORY_LAUNCHER)
    return seen.mapNotNull { (component, info) ->
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

internal fun copyIcon(icon: Drawable): Drawable {
    return icon.constantState?.newDrawable()?.mutate() ?: icon
}
