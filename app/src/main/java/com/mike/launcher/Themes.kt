package com.mike.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

data class LTheme(val name: String, val top: Int, val bottom: Int, val accent: Int)

object Themes {
    private fun c(s: String) = Color.parseColor(s)
    val all = listOf(
        LTheme("Néon", c("#E6150A3A"), c("#E60A0F2E"), c("#00E5FF")),
        LTheme("Aurore", c("#E63A0A3A"), c("#E61A0A2E"), c("#FF5CC8")),
        LTheme("Minuit", c("#F2050505"), c("#F20A1410"), c("#39FF88")),
        LTheme("Soleil", c("#E63A1408"), c("#E61E0A0A"), c("#FFB347"))
    )
    var current = 0
    fun t() = all[current]
}

fun Int.withAlpha(a: Int) = (a shl 24) or (this and 0x00FFFFFF)

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.glass(fill: Int, stroke: Int, radiusDp: Int) = GradientDrawable().apply {
    setColor(fill)
    setStroke(dp(1), stroke)
    cornerRadius = dp(radiusDp).toFloat()
}
