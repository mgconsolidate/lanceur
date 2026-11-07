package com.mike.launcher

import android.app.ActivityOptions
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: AppAdapter
    private lateinit var root: View
    private lateinit var search: EditText
    private lateinit var dock: LinearLayout
    private lateinit var widgets: LinearLayout
    private lateinit var grid: RecyclerView
    private lateinit var date: TextView
    private lateinit var menuBtn: TextView
    private lateinit var signature: TextView
    private lateinit var host: AppWidgetHost
    private lateinit var awm: AppWidgetManager
    private var allApps = listOf<AppItem>()
    private var pendingId = -1
    private val REQ_PICK = 1
    private val REQ_CONFIG = 2
    private val prefs by lazy { getSharedPreferences("launcher", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        search = findViewById(R.id.search)
        dock = findViewById(R.id.dock)
        widgets = findViewById(R.id.widgets)
        grid = findViewById(R.id.grid)
        date = findViewById(R.id.date)
        menuBtn = findViewById(R.id.menu)
        signature = findViewById(R.id.signature)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, bars.bottom)
            insets
        }

        awm = AppWidgetManager.getInstance(this)
        host = AppWidgetHost(this, 1024)

        Themes.current = prefs.getInt("theme", 0)
        adapter = AppAdapter(
            onClick = { app, icon -> launch(app, icon) },
            onLong = { app -> toggleFavorite(app) }
        )
        grid.layoutManager = GridLayoutManager(this, prefs.getInt("cols", 4))
        grid.isNestedScrollingEnabled = false
        grid.adapter = adapter

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filter(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        menuBtn.setOnClickListener { openMenu() }
        root.setOnLongClickListener { openMenu(); true }
        applyTheme()
    }

    override fun onStart() { super.onStart(); host.startListening() }
    override fun onStop() { super.onStop(); host.stopListening() }

    override fun onResume() {
        super.onResume()
        loadApps()
        buildWidgets()
    }

    // ---------- Thème ----------
    private fun applyTheme() {
        val t = Themes.t()
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(t.top, t.bottom)
        )
        date.setTextColor(t.accent)
        signature.setTextColor(t.accent)
        search.background = glass(0x22FFFFFF, t.accent.withAlpha(0x77), 22)
        menuBtn.background = glass(0x22FFFFFF, t.accent.withAlpha(0x77), 23)
        dock.background = glass(0x26FFFFFF, t.accent.withAlpha(0x55), 30)
        adapter.notifyDataSetChanged()
        buildDock()
    }

    // ---------- Menu ----------
    private fun openMenu() {
        val cols = (grid.layoutManager as GridLayoutManager).spanCount
        val items = arrayOf(
            "🎨  Changer le thème",
            "🔲  Colonnes : $cols",
            "🧩  Ajouter un widget",
            "🗑  Retirer les widgets"
        )
        AlertDialog.Builder(this).setTitle("MG Launcher · MIKE MG").setItems(items) { _, which ->
            when (which) {
                0 -> chooseTheme()
                1 -> {
                    val n = if (cols >= 5) 3 else cols + 1
                    (grid.layoutManager as GridLayoutManager).spanCount = n
                    prefs.edit().putInt("cols", n).apply()
                    adapter.notifyDataSetChanged()
                }
                2 -> pickWidget()
                3 -> clearWidgets()
            }
        }.show()
    }

    private fun chooseTheme() {
        val names = Themes.all.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Thème").setItems(names) { _, i ->
            Themes.current = i
            prefs.edit().putInt("theme", i).apply()
            applyTheme()
        }.show()
    }

    // ---------- Widgets ----------
    private fun widgetIds(): List<Int> =
        (prefs.getString("widgets", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }

    private fun saveWidget(id: Int) {
        prefs.edit().putString("widgets", (widgetIds() + id).joinToString(",")).apply()
        buildWidgets()
    }

    private fun pickWidget() {
        pendingId = host.allocateAppWidgetId()
        val i = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
        startActivityForResult(i, REQ_PICK)
    }

    private fun clearWidgets() {
        widgetIds().forEach { host.deleteAppWidgetId(it) }
        prefs.edit().remove("widgets").apply()
        buildWidgets()
    }

    private fun buildWidgets() {
        widgets.removeAllViews()
        widgetIds().forEach { id ->
            val info = awm.getAppWidgetInfo(id) ?: return@forEach
            val hv = host.createView(this, id, info)
            hv.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) {
                    o.setRoundRect(0, 0, v.width, v.height, dp(22).toFloat())
                }
            }
            hv.clipToOutline = true
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(maxOf(info.minHeight, 90))
            )
            lp.topMargin = dp(8)
            widgets.addView(hv, lp)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK && requestCode != REQ_CONFIG) return
        if (resultCode != RESULT_OK) { host.deleteAppWidgetId(pendingId); return }
        if (requestCode == REQ_PICK) {
            val cfg = awm.getAppWidgetInfo(pendingId)?.configure
            if (cfg != null) {
                val i = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(cfg)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                startActivityForResult(i, REQ_CONFIG)
            } else saveWidget(pendingId)
        } else saveWidget(pendingId)
    }

    // ---------- Apps ----------
    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        allApps = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .sortedBy { it.label.lowercase() }
        filter(search.text.toString())
        buildDock()
    }

    private fun filter(q: String) {
        adapter.submit(
            if (q.isBlank()) allApps
            else allApps.filter { it.label.contains(q, ignoreCase = true) }
        )
    }

    private fun launch(app: AppItem, from: View) {
        val i = packageManager.getLaunchIntentForPackage(app.pkg) ?: return
        val opts = ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.width, from.height)
        startActivity(i, opts.toBundle())
    }

    // ---------- Dock ----------
    private fun favorites(): MutableList<String> =
        (prefs.getString("favs", "") ?: "").split(",").filter { it.isNotBlank() }.toMutableList()

    private fun toggleFavorite(app: AppItem) {
        val favs = favorites()
        if (favs.contains(app.pkg)) {
            favs.remove(app.pkg)
            Toast.makeText(this, "${app.label} retiré du dock", Toast.LENGTH_SHORT).show()
        } else if (favs.size < 4) {
            favs.add(app.pkg)
            Toast.makeText(this, "${app.label} ajouté au dock", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Dock plein (4 max)", Toast.LENGTH_SHORT).show()
        }
        prefs.edit().putString("favs", favs.joinToString(",")).apply()
        buildDock()
    }

    private fun buildDock() {
        dock.removeAllViews()
        favorites().mapNotNull { pkg -> allApps.find { it.pkg == pkg } }.forEach { app ->
            val v = LayoutInflater.from(this).inflate(R.layout.item_app, dock, false)
            v.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            val icon = v.findViewById<ImageView>(R.id.icon)
            v.findViewById<TextView>(R.id.label).visibility = View.GONE
            icon.setImageDrawable(app.icon)
            styleIcon(icon)
            v.pressEffect()
            v.setOnClickListener { launch(app, icon) }
            v.setOnLongClickListener { toggleFavorite(app); true }
            dock.addView(v)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        search.setText("")
        search.clearFocus()
    }
}
