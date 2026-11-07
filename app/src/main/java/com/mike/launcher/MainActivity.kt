package com.mike.launcher

import android.app.ActivityOptions
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: AppAdapter
    private lateinit var root: View
    private lateinit var search: EditText
    private lateinit var favs: LinearLayout
    private lateinit var widgets: LinearLayout
    private lateinit var grid: RecyclerView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var menuBtn: TextView
    private lateinit var signature: TextView
    private lateinit var drawer: View
    private lateinit var handle: TextView
    private lateinit var bar: AlphabetBar
    private lateinit var bubble: TextView
    private lateinit var host: AppWidgetHost
    private lateinit var awm: AppWidgetManager
    private var allApps = listOf<AppItem>()
    private var drawerOpen = false
    private var rightHand = true
    private var pendingId = -1
    private val REQ_PICK = 1
    private val REQ_CONFIG = 2
    private val prefs by lazy { getSharedPreferences("launcher", Context.MODE_PRIVATE) }

    // Gestes : glisser vers le haut = liste des apps, vers le bas = notifications
    private val gd by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (bar.touching) return false
                if (!drawerOpen && vy < -1200) openDrawer()
                else if (!drawerOpen && vy > 1500) expandShade()
                else if (drawerOpen && vy > 1800 && !grid.canScrollVertically(-1)) closeDrawer()
                return false
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        search = findViewById(R.id.search)
        favs = findViewById(R.id.favs)
        widgets = findViewById(R.id.widgets)
        grid = findViewById(R.id.grid)
        clock = findViewById(R.id.clock)
        date = findViewById(R.id.date)
        menuBtn = findViewById(R.id.menu)
        signature = findViewById(R.id.signature)
        drawer = findViewById(R.id.drawer)
        handle = findViewById(R.id.handle)
        bar = findViewById(R.id.bar)
        bubble = findViewById(R.id.bubble)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, bars.bottom)
            insets
        }

        awm = AppWidgetManager.getInstance(this)
        host = AppWidgetHost(this, 1024)
        Themes.current = prefs.getInt("theme", 0)
        rightHand = prefs.getBoolean("right", true)

        adapter = AppAdapter(
            onClick = { app, icon -> launch(app, icon) },
            onLong = { app -> appMenu(app) }
        )
        grid.layoutManager = LinearLayoutManager(this)
        grid.adapter = adapter

        // Barre alphabétique : glisse le doigt sur les lettres
        bar.onLetter = { l ->
            bubble.text = l.toString()
            bubble.visibility = View.VISIBLE
            (grid.layoutManager as LinearLayoutManager)
                .scrollToPositionWithOffset(adapter.firstIndexFor(l), 0)
        }
        bar.onRelease = { bubble.visibility = View.GONE }

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filter(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        menuBtn.setOnClickListener { openMenu() }
        findViewById<View>(R.id.home).setOnLongClickListener { openMenu(); true }
        handle.setOnClickListener { openDrawer() }
        applyHand()
        applyTheme()
    }

    override fun onStart() { super.onStart(); host.startListening() }
    override fun onStop() { super.onStop(); host.stopListening() }

    override fun onResume() {
        super.onResume()
        loadApps()
        buildWidgets()
    }

    // ---------- Main droite / gauche ----------
    private fun applyHand() {
        adapter.rightHand = rightHand
        val g = if (rightHand) Gravity.END else Gravity.START
        val og = if (rightHand) Gravity.START else Gravity.END
        clock.gravity = og
        date.gravity = og
        val lp = bar.layoutParams as FrameLayout.LayoutParams
        lp.gravity = g
        bar.layoutParams = lp
        adapter.notifyDataSetChanged()
        buildFavorites()
    }

    // ---------- Thème ----------
    private fun applyTheme() {
        val t = Themes.t()
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(t.top, t.bottom)
        )
        date.setTextColor(t.accent)
        signature.setTextColor(t.accent)
        handle.setTextColor(t.accent)
        bar.accent = t.accent
        bar.invalidate()
        bubble.setTextColor(t.accent)
        bubble.background = glass(0x55000000, t.accent.withAlpha(0xAA), 24)
        search.background = glass(0x22FFFFFF, t.accent.withAlpha(0x77), 22)
        menuBtn.background = glass(0x22FFFFFF, t.accent.withAlpha(0x77), 23)
        val r = dp(28).toFloat()
        drawer.background = GradientDrawable().apply {
            setColor(t.bottom.withAlpha(0xEE))
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
        adapter.notifyDataSetChanged()
        buildFavorites()
    }

    // ---------- Tiroir d'apps ----------
    private fun openDrawer() {
        drawerOpen = true
        grid.scrollToPosition(0)
        drawer.visibility = View.VISIBLE
        drawer.translationY = root.height.toFloat()
        drawer.animate().translationY(0f).setDuration(320)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun closeDrawer() {
        drawerOpen = false
        search.setText("")
        search.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(search.windowToken, 0)
        drawer.animate().translationY(root.height.toFloat()).setDuration(260)
            .withEndAction { if (!drawerOpen) drawer.visibility = View.GONE }.start()
    }

    // Volet de notifications (glisser vers le bas sur l'accueil)
    private fun expandShade() {
        try {
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(getSystemService("statusbar"))
        } catch (e: Exception) { }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gd.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (drawerOpen) closeDrawer()
    }

    // ---------- Menu ----------
    private fun openMenu() {
        val items = arrayOf(
            "🎨  Changer le thème",
            "🖐  Main : ${if (rightHand) "droite" else "gauche"}",
            "🧩  Ajouter un widget",
            "🗑  Retirer les widgets"
        )
        AlertDialog.Builder(this).setTitle("MG Launcher · MIKE MG").setItems(items) { _, which ->
            when (which) {
                0 -> chooseTheme()
                1 -> {
                    rightHand = !rightHand
                    prefs.edit().putBoolean("right", rightHand).apply()
                    applyHand()
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

    // Appui long sur une app
    private fun appMenu(app: AppItem) {
        val fav = favorites().contains(app.pkg)
        val items = arrayOf(
            if (fav) "☆  Retirer des favoris" else "★  Ajouter aux favoris",
            "ℹ️  Infos de l'app",
            "🗑  Désinstaller"
        )
        AlertDialog.Builder(this).setTitle(app.label).setItems(items) { _, i ->
            val uri = Uri.parse("package:${app.pkg}")
            when (i) {
                0 -> toggleFavorite(app)
                1 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                2 -> startActivity(Intent(Intent.ACTION_DELETE, uri))
            }
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
            .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, unify(it.loadIcon(pm))) }
            .sortedWith(compareBy({ letterOf(it.label) }, { it.label.lowercase() }))
        filter(search.text.toString())
        buildFavorites()
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
        if (drawerOpen) closeDrawer()
    }

    // ---------- Favoris sur l'accueil (7 max) ----------
    private fun favorites(): MutableList<String> =
        (prefs.getString("favs", "") ?: "").split(",").filter { it.isNotBlank() }.toMutableList()

    private fun toggleFavorite(app: AppItem) {
        val list = favorites()
        if (list.contains(app.pkg)) {
            list.remove(app.pkg)
            Toast.makeText(this, "${app.label} retiré des favoris", Toast.LENGTH_SHORT).show()
        } else if (list.size < 7) {
            list.add(app.pkg)
            Toast.makeText(this, "${app.label} ajouté aux favoris", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Favoris pleins (7 max)", Toast.LENGTH_SHORT).show()
        }
        prefs.edit().putString("favs", list.joinToString(",")).apply()
        buildFavorites()
    }

    private fun buildFavorites() {
        favs.removeAllViews()
        val list = favorites().mapNotNull { pkg -> allApps.find { it.pkg == pkg } }
        if (list.isEmpty()) {
            favs.addView(TextView(this).apply {
                text = "Ouvre la liste, puis appui long sur une app pour l'ajouter ici"
                setTextColor(0x99FFFFFF.toInt())
                textSize = 13f
                gravity = if (rightHand) Gravity.END else Gravity.START
            })
            return
        }
        list.forEach { app ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_row, favs, false) as LinearLayout
            val icon = row.findViewById<ImageView>(R.id.icon)
            val label = row.findViewById<TextView>(R.id.label)
            icon.setImageDrawable(app.icon)
            styleIcon(icon)
            label.text = app.label
            label.textSize = 28f
            row.gravity = (if (rightHand) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
            row.pressEffect()
            row.setOnClickListener { launch(app, icon) }
            row.setOnLongClickListener { appMenu(app); true }
            favs.addView(row)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerOpen) closeDrawer()
    }
}
