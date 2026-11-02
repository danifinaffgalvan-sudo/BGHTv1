package com.bghtv.launcher

import android.app.Activity
import android.content.*
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

data class Tile(val label: String, val icon: Drawable?, val intent: Intent, val pkg: String? = null)

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var root: View
    private lateinit var clock: TextView
    private lateinit var pages: List<View>
    private lateinit var tabs: List<TextView>
    private lateinit var rowFavs: RecyclerView
    private lateinit var rowApps: RecyclerView
    private lateinit var gridApps: RecyclerView
    private lateinit var gridLib: RecyclerView
    private lateinit var favHint: TextView
    private lateinit var hero: View
    private lateinit var heroImg: ImageView
    private lateinit var heroGreeting: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private var allApps: List<Tile> = emptyList()
    private var heroTile: Tile? = null
    private var currentTab = 0

    private val themeNames = listOf("Azul BGH", "Noche", "Violeta", "Verde", "Rojo")
    private val themes = listOf(
        intArrayOf(0xFF0A1530.toInt(), 0xFF164A94.toInt()),
        intArrayOf(0xFF0E1116.toInt(), 0xFF252E3F.toInt()),
        intArrayOf(0xFF1A1033.toInt(), 0xFF4A2A86.toInt()),
        intArrayOf(0xFF07211C.toInt(), 0xFF14604E.toInt()),
        intArrayOf(0xFF2A0D12.toInt(), 0xFF7A2232.toInt())
    )

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = updateClock()
    }
    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { allApps = loadApps(); refresh() }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("bgh", MODE_PRIVATE)
        root = findViewById(R.id.root)
        clock = findViewById(R.id.clock)
        tabs = listOf(R.id.tabHome, R.id.tabApps, R.id.tabLib).map { findViewById<TextView>(it) }
        pages = listOf(R.id.pageHome, R.id.pageApps, R.id.pageLib).map { findViewById<View>(it) }
        tabs.forEachIndexed { i, t ->
            t.setOnFocusChangeListener { _, f -> if (f) selectTab(i) }
            t.setOnClickListener { selectTab(i) }
        }
        findViewById<View>(R.id.btnTheme).setOnClickListener { cycleTheme() }

        rowFavs = findViewById(R.id.rowFavs)
        rowFavs.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        rowApps = findViewById(R.id.rowApps)
        rowApps.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        gridApps = findViewById(R.id.gridApps)
        gridApps.layoutManager = GridLayoutManager(this, 5)
        gridLib = findViewById(R.id.gridLib)
        gridLib.layoutManager = GridLayoutManager(this, 5)
        favHint = findViewById(R.id.favHint)
        hero = findViewById(R.id.hero)
        heroImg = findViewById(R.id.heroImg)
        heroGreeting = findViewById(R.id.heroGreeting)
        heroTitle = findViewById(R.id.heroTitle)
        heroSub = findViewById(R.id.heroSub)
        hero.setOnClickListener {
            val t = heroTile
            if (t != null) launch(t) else {
                selectTab(1)
                gridApps.post { gridApps.getChildAt(0)?.requestFocus() }
            }
        }

        allApps = loadApps()
        applyTheme()
        refresh()
        selectTab(0)
        hero.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(tick, IntentFilter(Intent.ACTION_TIME_TICK))
        registerReceiver(pkgReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addDataScheme("package")
        })
        updateClock()
    }

    override fun onResume() {
        super.onResume()
        if (::prefs.isInitialized) updateHero()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(tick); unregisterReceiver(pkgReceiver)
    }

    // Atrás: vuelve a "Inicio" y nunca cierra el launcher
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentTab != 0) { selectTab(0); hero.post { hero.requestFocus() } }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun updateClock() {
        clock.text = SimpleDateFormat("EEE d MMM  HH:mm", Locale("es", "AR")).format(Date())
    }

    private fun selectTab(i: Int) {
        currentTab = i
        pages.forEachIndexed { idx, p -> p.visibility = if (idx == i) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { idx, t -> t.isSelected = idx == i }
        rowApps.adapter?.notifyDataSetChanged()
        gridApps.adapter?.notifyDataSetChanged()
    }

    private fun applyTheme() {
        val c = themes[prefs.getInt("theme", 0) % themes.size]
        root.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, c)
    }

    private fun cycleTheme() {
        val n = (prefs.getInt("theme", 0) + 1) % themes.size
        prefs.edit().putInt("theme", n).apply()
        applyTheme()
        Toast.makeText(this, "Fondo: " + themeNames[n], Toast.LENGTH_SHORT).show()
    }

    private fun favSet(): Set<String> = prefs.getStringSet("favs", emptySet()) ?: emptySet()

    private fun toggleFav(pkg: String, label: String) {
        val s = favSet().toMutableSet()
        val added = s.add(pkg)
        if (!added) s.remove(pkg)
        prefs.edit().putStringSet("favs", s).apply()
        Toast.makeText(this, if (added) "$label agregada a favoritos" else "$label quitada de favoritos", Toast.LENGTH_SHORT).show()
    }

    private fun refreshFavRow() {
        val favs = favSet()
        val list = allApps.filter { it.pkg != null && it.pkg in favs }
        rowFavs.adapter = TileAdapter(list, 200)
        rowFavs.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        favHint.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun refresh() {
        refreshFavRow()
        rowApps.adapter = TileAdapter(allApps, 200)
        gridApps.adapter = TileAdapter(allApps, 0)
        gridLib.adapter = TileAdapter(shortcuts(), 0)
        updateHero()
    }

    private fun updateHero() {
        val last = prefs.getString("last", null)
        heroTile = allApps.firstOrNull { it.pkg != null && it.pkg == last }
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        heroGreeting.text = when { h < 6 -> "Buenas noches"; h < 13 -> "Buen día"; h < 20 -> "Buenas tardes"; else -> "Buenas noches" }
        val t = heroTile
        if (t != null) {
            heroImg.setImageDrawable(t.icon)
            heroTitle.text = "Continuar con " + t.label
            heroSub.text = "Apretá OK para abrir"
        } else {
            heroImg.setImageResource(R.drawable.banner_bgh)
            heroTitle.text = "¿Qué querés ver hoy?"
            heroSub.text = "Apretá OK para ver todas tus apps"
        }
    }

    private fun launch(t: Tile) {
        t.pkg?.let { prefs.edit().putString("last", it).apply() }
        try {
            startActivity(Intent(t.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo abrir " + t.label, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadApps(): List<Tile> {
        val pm = packageManager
        fun query(cat: String) = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
        val list = (query(Intent.CATEGORY_LEANBACK_LAUNCHER) + query(Intent.CATEGORY_LAUNCHER))
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != packageName }
        return list.sortedBy { it.loadLabel(pm).toString().lowercase() }.map { ri: ResolveInfo ->
            val pkg = ri.activityInfo.packageName
            val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: Intent()
            Tile(ri.loadLabel(pm).toString(), ri.activityInfo.loadBanner(pm) ?: ri.loadIcon(pm), i, pkg)
        }
    }

    private fun icon(res: Int): Drawable? = getDrawable(res)?.mutate()?.also { it.setTint(Color.WHITE) }

    private fun shortcuts() = listOf(
        Tile("Ajustes", icon(android.R.drawable.ic_menu_manage), Intent(Settings.ACTION_SETTINGS)),
        Tile("Wi-Fi", icon(android.R.drawable.ic_menu_share), Intent(Settings.ACTION_WIFI_SETTINGS)),
        Tile("Bluetooth", icon(android.R.drawable.ic_menu_send), Intent(Settings.ACTION_BLUETOOTH_SETTINGS)),
        Tile("Pantalla", icon(android.R.drawable.ic_menu_gallery), Intent(Settings.ACTION_DISPLAY_SETTINGS)),
        Tile("Sonido", icon(android.R.drawable.ic_lock_silent_mode_off), Intent(Settings.ACTION_SOUND_SETTINGS)),
        Tile("Almacenamiento", icon(android.R.drawable.ic_menu_save), Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
        Tile("Aplicaciones", icon(android.R.drawable.ic_menu_agenda), Intent(Settings.ACTION_APPLICATION_SETTINGS)),
        Tile("Fecha y hora", icon(android.R.drawable.ic_menu_day), Intent(Settings.ACTION_DATE_SETTINGS))
    )

    inner class TileAdapter(private val items: List<Tile>, private val widthDp: Int) : RecyclerView.Adapter<TileAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.icon)
            val label: TextView = v.findViewById(R.id.label)
            val star: TextView = v.findViewById(R.id.star)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_app, p, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val t = items[pos]
            val pkg = t.pkg
            h.icon.setImageDrawable(t.icon)
            h.label.text = t.label
            h.star.visibility = if (pkg != null && pkg in favSet()) View.VISIBLE else View.GONE
            val lp = h.itemView.layoutParams as RecyclerView.LayoutParams
            lp.width = if (widthDp > 0) dp(widthDp) else ViewGroup.LayoutParams.MATCH_PARENT
            h.itemView.layoutParams = lp
            h.itemView.setOnFocusChangeListener { v, f ->
                v.animate().scaleX(if (f) 1.06f else 1f).scaleY(if (f) 1.06f else 1f).setDuration(120).start()
            }
            h.itemView.setOnClickListener { launch(t) }
            h.itemView.setOnLongClickListener {
                if (pkg != null) {
                    toggleFav(pkg, t.label)
                    h.star.visibility = if (pkg in favSet()) View.VISIBLE else View.GONE
                    refreshFavRow()
                }
                true
            }
        }
    }
}
