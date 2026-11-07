package com.mike.launcher

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.Normalizer

data class AppItem(val label: String, val pkg: String, val icon: Drawable)

// Lettre de classement : accents retirés, tout le reste = '#'
fun letterOf(s: String): Char {
    val c = Normalizer.normalize(s.trim(), Normalizer.Form.NFD).firstOrNull()?.uppercaseChar()
    return if (c != null && c in 'A'..'Z') c else '#'
}

// Effet de pression qui rebondit
fun View.pressEffect() {
    setOnTouchListener { v, e ->
        when (e.action) {
            MotionEvent.ACTION_DOWN ->
                v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(110).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f)
                    .setInterpolator(OvershootInterpolator(3f)).setDuration(300).start()
        }
        false
    }
}

// Forme unifiée : on retire le masque du téléphone et on arrondit nous-mêmes
fun unify(d: Drawable): Drawable {
    if (d is AdaptiveIconDrawable) {
        val layers = listOfNotNull(d.background, d.foreground).toTypedArray()
        return InsetDrawable(LayerDrawable(layers), -0.17f)
    }
    return d
}

fun styleIcon(icon: ImageView, radiusDp: Int = 10) {
    icon.background = GradientDrawable().apply {
        setColor(0x33FFFFFF)
        cornerRadius = icon.context.dp(radiusDp).toFloat()
    }
}

class AppAdapter(
    private val onClick: (AppItem, View) -> Unit,
    private val onLong: (AppItem) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var items = listOf<AppItem>()
    var rightHand = true

    fun submit(list: List<AppItem>) { items = list; notifyDataSetChanged() }

    fun firstIndexFor(l: Char): Int {
        val i = items.indexOfFirst { letterOf(it.label) >= l }
        return if (i < 0) maxOf(items.size - 1, 0) else i
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val label: TextView = v.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_row, p, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val app = items[pos]
        val ctx = h.itemView.context
        h.icon.setImageDrawable(app.icon)
        styleIcon(h.icon)
        h.label.text = app.label
        val row = h.itemView as LinearLayout
        row.gravity = (if (rightHand) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        row.setPadding(
            if (rightHand) 0 else ctx.dp(40), ctx.dp(8),
            if (rightHand) ctx.dp(40) else 0, ctx.dp(8)
        )
        row.pressEffect()
        row.setOnClickListener { onClick(app, h.icon) }
        row.setOnLongClickListener { onLong(app); true }
    }
}
