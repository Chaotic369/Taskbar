package com.quicklauncher.app

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import kotlin.math.max

data class FloatWin(val pkg: String, val label: String)

/** Floating (freeform) app windows: which ones are open, where they go and how big they are. */
object Floating {
    val open = ArrayList<FloatWin>()
    var onChange: () -> Unit = {}

    val PRESETS = linkedMapOf(
        "small" to "Small", "medium" to "Medium", "large" to "Large",
        "full" to "Full", "left" to "Left half", "right" to "Right half",
    )

    fun add(pkg: String, label: String) { if (open.none { it.pkg == pkg }) open.add(FloatWin(pkg, label)); onChange() }
    fun remove(pkg: String) { open.removeAll { it.pkg == pkg }; onChange() }

    /** Window rectangle for [pkg]: its own saved preset, else the default size. Windows cascade so they don't stack exactly. */
    fun bounds(scr: Rect, s: Settings, pkg: String, density: Float): Rect {
        var wp = s.floatW; var hp = s.floatH; var side = ""
        when (val key = s.appSizes[pkg]) {
            "small" -> { wp = 40f; hp = 40f }
            "medium" -> { wp = 60f; hp = 55f }
            "large" -> { wp = 80f; hp = 75f }
            "full" -> { wp = 100f; hp = 92f }
            "left", "right" -> { wp = 50f; hp = 100f; side = key ?: "" }
        }
        val sw = scr.width(); val sh = scr.height()
        val w = (sw * wp / 100f).toInt(); val h = (sh * hp / 100f).toInt()
        if (side == "left") return Rect(0, 0, w, h)
        if (side == "right") return Rect(sw - w, 0, sw, h)
        val idx = max(0, open.indexOfFirst { it.pkg == pkg }.let { if (it < 0) open.size else it })
        val off = ((idx % 6) - 2) * (28 * density).toInt()
        val l = ((sw - w) / 2 + off).coerceIn(0, max(0, sw - w))
        val t = ((sh - h) / 2 + off).coerceIn(0, max(0, sh - h))
        return Rect(l, t, l + w, t + h)
    }

    fun start(ctx: Context, pkg: String, bounds: Rect): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) { ctx.toast("App not installed"); return false }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val o = ActivityOptions.makeBasic()
        o.setLaunchBounds(bounds)
        // 5 = WINDOWING_MODE_FREEFORM (hidden API; ignored when the device doesn't allow it)
        runCatching { ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(o, 5) }
        return try { ctx.startActivity(i, o.toBundle()); true }
        catch (e: Exception) { ctx.toast("Couldn't open floating window"); false }
    }
}
