package com.mike.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

data class LTheme(val name: String, val top: Int, val bottom: Int, val accent: Int)

object Themes {
    private fun c(s: String) = Color.parseColor(s)
    val all = listOf(
        LTheme("Néon", c("#66150A3A"), c("#CC0A0F2E"), c("#00E5FF")),
        LTheme("Aurore", c("#663A0A3A"), c("#CC1A0A2E"), c("#FF5CC8")),
        LTheme("Minuit", c("#80050505"), c("#D90A1410"), c("#39FF88")),
        LTheme("Soleil", c("#66401A05"), c("#CC200A05"), c("#FFB347"))
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
