package com.mike.launcher

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class AppItem(val label: String, val pkg: String, val icon: Drawable)

// Effet « iPhone » : l'icône rétrécit quand on appuie, puis rebondit
fun View.pressEffect() {
    setOnTouchListener { v, e ->
        when (e.action) {
            MotionEvent.ACTION_DOWN ->
                v.animate().scaleX(0.86f).scaleY(0.86f).setDuration(110).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f)
                    .setInterpolator(OvershootInterpolator(3.5f)).setDuration(320).start()
        }
        false
    }
}

// Icône en verre avec bordure aux couleurs du thème
fun styleIcon(icon: ImageView) {
    icon.background = icon.context.glass(0x33FFFFFF, Themes.t().accent.withAlpha(0x66), 15)
}

class AppAdapter(
    private val onClick: (AppItem, View) -> Unit,
    private val onLong: (AppItem) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var items = listOf<AppItem>()

    fun submit(list: List<AppItem>) { items = list; notifyDataSetChanged() }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val label: TextView = v.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_app, p, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val app = items[pos]
        h.icon.setImageDrawable(app.icon)
        h.label.text = app.label
        styleIcon(h.icon)
        h.itemView.pressEffect()
        h.itemView.setOnClickListener { onClick(app, h.icon) }
        h.itemView.setOnLongClickListener { onLong(app); true }

        // Apparition en cascade, fluide
        h.itemView.alpha = 0f
        h.itemView.translationY = 40f
        h.itemView.animate().alpha(1f).translationY(0f)
            .setStartDelay((pos % 24) * 22L).setDuration(350).start()
    }
}
