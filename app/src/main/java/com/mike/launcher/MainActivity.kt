package com.mike.launcher

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: AppAdapter
    private lateinit var search: EditText
    private lateinit var dock: LinearLayout
    private var allApps = listOf<AppItem>()
    private val prefs by lazy { getSharedPreferences("launcher", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        // Marges pour la barre d'état et de navigation
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, bars.bottom)
            insets
        }

        search = findViewById(R.id.search)
        dock = findViewById(R.id.dock)

        adapter = AppAdapter(
            onClick = { app, icon -> launch(app, icon) },
            onLong = { app -> toggleFavorite(app) }
        )
        findViewById<RecyclerView>(R.id.grid).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 4)
            adapter = this@MainActivity.adapter
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filter(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

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
        // Animation de zoom depuis l'icône, comme sur iPhone
        val opts = ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.width, from.height)
        startActivity(i, opts.toBundle())
    }

    // ----- Dock (favoris) : appui long sur une app pour l'ajouter / l'enlever -----
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
            v.pressEffect()
            v.setOnClickListener { launch(app, icon) }
            v.setOnLongClickListener { toggleFavorite(app); true }
            dock.addView(v)
        }
    }

    // Le bouton retour ne quitte pas le launcher : il efface juste la recherche
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        search.setText("")
        search.clearFocus()
    }
}
