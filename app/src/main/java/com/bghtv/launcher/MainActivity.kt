package com.bghtv.launcher

import android.app.Activity
import android.content.*
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

data class Tile(val label: String, val icon: Drawable?, val intent: Intent)

class MainActivity : Activity() {
    private lateinit var clock: TextView
    private lateinit var apps: RecyclerView
    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = updateClock()
    }
    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { apps.adapter = TileAdapter(loadApps()) }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)
        clock = findViewById(R.id.clock)
        apps = findViewById(R.id.apps)
        apps.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        apps.adapter = TileAdapter(loadApps())
        val sc = findViewById<RecyclerView>(R.id.shortcuts)
        sc.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        sc.adapter = TileAdapter(shortcuts())
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        findViewById<TextView>(R.id.greeting).text =
            when { h < 6 -> "Buenas noches"; h < 13 -> "Buen día"; h < 20 -> "Buenas tardes"; else -> "Buenas noches" }
        apps.post { apps.getChildAt(0)?.requestFocus() }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(tick, IntentFilter(Intent.ACTION_TIME_TICK))
        registerReceiver(pkgReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addDataScheme("package")
        })
        updateClock()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(tick); unregisterReceiver(pkgReceiver)
    }

    // Un launcher no debe cerrarse con "Atrás"
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {}

    private fun updateClock() {
        clock.text = SimpleDateFormat("EEE d MMM  HH:mm", Locale("es", "AR")).format(Date())
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
            Tile(ri.loadLabel(pm).toString(), ri.activityInfo.loadBanner(pm) ?: ri.loadIcon(pm), i)
        }
    }

    private fun shortcuts() = listOf(
        Tile("Ajustes", getDrawable(android.R.drawable.ic_menu_manage), Intent(Settings.ACTION_SETTINGS)),
        Tile("Wi-Fi", getDrawable(android.R.drawable.ic_menu_share), Intent(Settings.ACTION_WIFI_SETTINGS)),
        Tile("Bluetooth", getDrawable(android.R.drawable.stat_sys_data_bluetooth), Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    )

    inner class TileAdapter(private val items: List<Tile>) : RecyclerView.Adapter<TileAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.icon)
            val label: TextView = v.findViewById(R.id.label)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_app, p, false))
        override fun getItemCount() = items.size
        override fun onBindViewHolder(h: VH, pos: Int) {
            val t = items[pos]
            h.icon.setImageDrawable(t.icon); h.label.text = t.label
            h.itemView.setOnFocusChangeListener { v, f ->
                v.animate().scaleX(if (f) 1.1f else 1f).scaleY(if (f) 1.1f else 1f).setDuration(120).start()
            }
            h.itemView.setOnClickListener {
                try { startActivity(t.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
            }
        }
    }
}
