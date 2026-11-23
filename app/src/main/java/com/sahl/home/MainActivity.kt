package com.sahl.home

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Sahl Home Screen & Launcher:
 *  1. Live Sahl Union Dashboard (clock, Hijri & Gregorian date, Next Prayer countdown & 5 prayer pills from Sahl Adhan)
 *  2. Favorites row (from Sahl Contacts Union) & Quick Tools row (Torch, Sahl Search, Wallpapers, Sahl Tools)
 *  3. Sahl Family App Grid & Bottom Dock in unified emerald & gold squircle frames
 *  4. Searchable All-Apps Drawer with Arabic-smart normalization (أ/ا/إ, ة/ه, ى/ي)
 */
class MainActivity : SahlActivity() {
    data class PrayerSlot(val index: Int, val name: String, val timeMs: Long)
    data class FavContact(val id: Long, val name: String, val number: String, val photo: String?)
    data class AppEntry(val label: String, val normKey: String, val pkg: String, val activity: String, val icon: Drawable, val isSahl: Boolean)

    private val sahlPackages = listOf(
        "com.sahl.phone", "com.sahl.contacts", "com.sahl.messages", "com.sahl.adhan",
        "com.sahl.qibla", "com.aly.trueqibla", "com.sahl.files", "com.sahl.gallery",
        "com.sahl.camera", "com.sahl.tools", "com.aly.sahlsync", "com.sahl.pdf", "com.sahl.timer"
    )

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var bgBackdrop: WallpaperPreviewView
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var prayerCountdownText: TextView
    private lateinit var prayerPillsRow: LinearLayout
    private lateinit var favContainer: LinearLayout
    private lateinit var sahlAppsGrid: GridLayout
    private lateinit var allAppsGrid: GridLayout
    private lateinit var updateBar: TextView
    private lateinit var defaultHomeBanner: TextView
    private var searchInput: EditText? = null
    private var torchOn = false
    private val allApps = ArrayList<AppEntry>()

    private val clockTicker = object : Runnable {
        override fun run() {
            updateClockAndPrayers()
            handler.postDelayed(this, 30_000L)
        }
    }

    private val unionReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            refreshUnionData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)

        // Procedural Sahl Wallpaper backdrop rendered behind the home screen
        bgBackdrop = WallpaperPreviewView(this).apply {
            style = WallpaperEngine.currentStyle(this@MainActivity)
        }
        root.addView(bgBackdrop, FrameLayout.LayoutParams(-1, -1))

        val pageCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(14), px(16), px(14), px(20))
        }

        // Top bar: Sahl Home title + Wallpapers & Settings quick icons
        val topBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(4), px(4), px(4), px(10))
        }
        topBar.addView(text(getString(R.string.app_name), 22f, 0xFFF5C84C.toInt(), true), LinearLayout.LayoutParams(0, -2, 1f))
        topBar.addView(iconButton(R.drawable.ic_wallpaper, 0x33000000, 0xFFF5C84C.toInt(), 40, 20) {
            startActivity(Intent(this, WallpaperActivity::class.java))
        }.apply { (layoutParams as LinearLayout.LayoutParams).marginEnd = px(8) })
        topBar.addView(iconButton(R.drawable.ic_tune, 0x33000000, 0xFFF5C84C.toInt(), 40, 20) {
            showSettingsSheet()
        })
        scrollContent.addView(topBar)

        // Update notification banner
        updateBar = text("", 13f, 0xFF072A22.toInt(), true).apply {
            visibility = View.GONE
            background = round(0xFFF5C84C.toInt(), 14f)
            setPadding(px(14), px(10), px(14), px(10))
            gravity = Gravity.CENTER
        }
        scrollContent.addView(updateBar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) })

        // Optional banner to set Sahl Home as default Android Home launcher
        defaultHomeBanner = text(getString(R.string.set_default_home), 13f, 0xFFF5C84C.toInt(), true).apply {
            background = round(0xCC12463A.toInt(), 16f, 0x88D4AF37.toInt())
            setPadding(px(14), px(10), px(14), px(10))
            gravity = Gravity.CENTER
            setOnClickListener { askDefaultHomeRole() }
        }.press(16f)
        scrollContent.addView(defaultHomeBanner, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(12) })

        // 1. Live Sahl Union Dashboard Card
        scrollContent.addView(buildUnionDashboardCard(), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(12) })

        // 2. Favorites & Quick Tools Card
        scrollContent.addView(buildFavoritesAndToolsCard(), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(12) })

        // 3. Sahl Family App Grid Card
        val sahlCard = glassCard()
        sahlCard.addView(text(getString(R.string.sahl_family), 13f, 0xFFF5C84C.toInt(), true).apply {
            setPadding(px(4), 0, px(4), px(8))
        })
        sahlAppsGrid = GridLayout(this).apply { columnCount = 4 }
        sahlCard.addView(sahlAppsGrid, LinearLayout.LayoutParams(-1, -2))
        scrollContent.addView(sahlCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(12) })

        // 4. All Apps Drawer with Arabic-smart search
        val drawerCard = glassCard()
        drawerCard.addView(text(getString(R.string.all_apps), 13f, 0xFFF5C84C.toInt(), true).apply {
            setPadding(px(4), 0, px(4), px(6))
        })
        val (sBox, sEt) = searchBox(getString(R.string.search_apps)) { q -> filterAllApps(q) }
        searchInput = sEt
        drawerCard.addView(sBox, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) })
        allAppsGrid = GridLayout(this).apply { columnCount = 4 }
        drawerCard.addView(allAppsGrid, LinearLayout.LayoutParams(-1, -2))
        scrollContent.addView(drawerCard, LinearLayout.LayoutParams(-1, -2))

        pageCol.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(scrollContent)
        }, LinearLayout.LayoutParams(-1, 0, 1f))

        // Bottom Dock
        pageCol.addView(buildBottomDock(), LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(px(14), px(4), px(14), px(12))
        })

        root.addView(pageCol, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        Updater.scheduleDaily(this)
        checkUpdate(false)
    }

    private fun glassCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = round(if (night) 0xD918221F.toInt() else 0xE6FFFFFF.toInt(), 22f, 0x66D4AF37)
        setPadding(px(14), px(12), px(14), px(12))
    }

    private fun buildUnionDashboardCard(): View {
        val c = glassCard()
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val leftCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        leftCol.addView(text(getString(R.string.union_title), 12f, 0xFFD4AF37.toInt(), true))
        clockText = text("00:00", 34f, col(R.color.text), true)
        leftCol.addView(clockText)
        topRow.addView(leftCol, LinearLayout.LayoutParams(0, -2, 1f))

        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        dateText = text("", 13f, col(R.color.text), true)
        rightCol.addView(dateText)
        prayerCountdownText = text("", 12f, col(R.color.emerald), true).apply {
            setPadding(0, px(2), 0, 0)
        }
        rightCol.addView(prayerCountdownText)
        topRow.addView(rightCol)
        c.addView(topRow)

        prayerPillsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, px(10), 0, 0)
        }
        c.addView(prayerPillsRow, LinearLayout.LayoutParams(-1, -2))
        c.setOnClickListener {
            launchPackageOrToast("com.sahl.adhan")
        }
        return c
    }

    private fun buildFavoritesAndToolsCard(): View {
        val c = glassCard()
        c.addView(text(getString(R.string.favorites_title), 12f, 0xFFD4AF37.toInt(), true).apply {
            setPadding(px(2), 0, px(2), px(6))
        })
        favContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        c.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(favContainer)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) })

        c.addView(text(getString(R.string.quick_tools), 12f, 0xFFD4AF37.toInt(), true).apply {
            setPadding(px(2), 0, px(2), px(6))
        })
        val toolsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        toolsRow.addView(quickToolPill(R.drawable.ic_flash, getString(R.string.tool_torch)) {
            toggleTorch()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(4) })
        toolsRow.addView(quickToolPill(R.drawable.ic_search, getString(R.string.tool_search)) {
            openSahlSearch()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(4) })
        toolsRow.addView(quickToolPill(R.drawable.ic_wallpaper, getString(R.string.tool_wallpapers)) {
            startActivity(Intent(this, WallpaperActivity::class.java))
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(4) })
        toolsRow.addView(quickToolPill(R.drawable.ic_tune, getString(R.string.tool_tools)) {
            launchPackageOrToast("com.sahl.tools")
        }, LinearLayout.LayoutParams(0, -2, 1f))
        c.addView(toolsRow)
        return c
    }

    private fun quickToolPill(iconRes: Int, title: String, onTap: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = round(col(R.color.tonal), 14f, 0x44D4AF37)
            setPadding(px(6), px(8), px(6), px(8))
            addView(icon(iconRes, col(R.color.emerald), 18))
            addView(text(title, 11f, col(R.color.text), true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setPadding(0, px(4), 0, 0)
            })
            setOnClickListener { onTap() }
        }.press(14f)

    private fun buildBottomDock(): View {
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = round(if (night) 0xE612463A.toInt() else 0xE6FFFFFF.toInt(), 26f, 0x88D4AF37.toInt())
            setPadding(px(12), px(10), px(12), px(10))
        }
        val dockItems = listOf(
            Triple("com.sahl.phone", R.drawable.ic_call, "Phone"),
            Triple("com.sahl.messages", R.drawable.ic_chat, "Messages"),
            Triple("com.sahl.adhan", R.drawable.ic_star, "Adhan"),
            Triple("com.sahl.camera", R.drawable.ic_photo, "Camera")
        )
        for ((pkg, fallbackIcon, _) in dockItems) {
            val cell = FrameLayout(this).apply {
                background = round(0xFF0B5D4B.toInt(), 16f, 0xFFD4AF37.toInt())
                val appIcon = try { packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null }
                if (appIcon != null) {
                    addView(ImageView(this@MainActivity).apply { setImageDrawable(appIcon) },
                        FrameLayout.LayoutParams(px(34), px(34), Gravity.CENTER))
                } else {
                    addView(icon(fallbackIcon, 0xFFF5C84C.toInt(), 24),
                        FrameLayout.LayoutParams(px(24), px(24), Gravity.CENTER))
                }
                setOnClickListener { launchPackageOrToast(pkg) }
            }.press(16f)
            dock.addView(cell, LinearLayout.LayoutParams(px(50), px(50)).apply {
                setMargins(px(10), 0, px(10), 0)
            })
        }
        // 5th dock button: Wallpaper Studio
        val wpBtn = FrameLayout(this).apply {
            background = round(0xFF0B5D4B.toInt(), 16f, 0xFFD4AF37.toInt())
            addView(icon(R.drawable.ic_wallpaper, 0xFFF5C84C.toInt(), 24),
                FrameLayout.LayoutParams(px(24), px(24), Gravity.CENTER))
            setOnClickListener { startActivity(Intent(this@MainActivity, WallpaperActivity::class.java)) }
        }.press(16f)
        dock.addView(wpBtn, LinearLayout.LayoutParams(px(50), px(50)).apply {
            setMargins(px(10), 0, px(10), 0)
        })
        return dock
    }

    override fun onResume() {
        super.onResume()
        bgBackdrop.style = WallpaperEngine.currentStyle(this)
        bgBackdrop.invalidate()
        defaultHomeBanner.visibility = if (isDefaultHome()) View.GONE else View.VISIBLE
        handler.removeCallbacks(clockTicker)
        clockTicker.run()
        refreshUnionData()
        loadInstalledApps()
        val f = IntentFilter(Sahl.ACTION_EVENT)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(unionReceiver, f, Context.RECEIVER_EXPORTED)
        else registerReceiver(unionReceiver, f)
    }

    override fun onPause() {
        handler.removeCallbacks(clockTicker)
        try { unregisterReceiver(unionReceiver) } catch (_: Exception) {}
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // On a home screen launcher, pressing Back clears search instead of exiting
        if (!searchInput?.text.isNullOrEmpty()) {
            searchInput?.setText("")
        }
    }

    private fun isDefaultHome(): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleHeld(RoleManager.ROLE_HOME)) return true
        }
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        return res?.activityInfo?.packageName == packageName
    }

    private fun askDefaultHomeRole() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 10)
                return
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    private fun updateClockAndPrayers() {
        val loc = resources.configuration.locales[0]
        clockText.text = SimpleDateFormat("HH:mm", loc).format(Date())
        val hijri = try {
            val hd = HijrahDate.now()
            DateTimeFormatter.ofPattern("d MMMM yyyy", loc).format(hd)
        } catch (_: Exception) {
            SimpleDateFormat("EEE, d MMM", loc).format(Date())
        }
        dateText.text = hijri
        loadPrayersFromUnion()
    }

    private fun refreshUnionData() {
        loadPrayersFromUnion()
        loadFavoritesFromUnion()
    }

    private fun loadPrayersFromUnion() {
        Thread {
            val slots = ArrayList<PrayerSlot>()
            try {
                contentResolver.query(Uri.parse("${Sahl.ADHAN_UNION}/prayers"), null, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        slots.add(PrayerSlot(c.getInt(0), c.getString(1) ?: "", c.getLong(2)))
                    }
                }
            } catch (_: Exception) {}
            runOnUiThread { renderPrayerSlots(slots) }
        }.start()
    }

    private fun renderPrayerSlots(slots: List<PrayerSlot>) {
        prayerPillsRow.removeAllViews()
        if (slots.isEmpty()) {
            prayerCountdownText.text = getString(R.string.adhan_not_installed)
            return
        }
        val now = System.currentTimeMillis()
        val next = slots.firstOrNull { it.timeMs > now } ?: slots.first()
        val diffMin = ((next.timeMs - now).coerceAtLeast(0L) / 60_000L).toInt()
        val durStr = if (diffMin >= 60) "${diffMin / 60}h ${diffMin % 60}m" else "${diffMin}m"
        val loc = resources.configuration.locales[0]
        val fmt = DateTimeFormatter.ofPattern("HH:mm", loc)
        val timeFormatted = Instant.ofEpochMilli(next.timeMs).atZone(ZoneId.systemDefault()).format(fmt)
        prayerCountdownText.text = getString(R.string.next_prayer, next.name, durStr, timeFormatted)

        val todaySlots = slots.take(5)
        for (s in todaySlots) {
            val isNext = s.timeMs == next.timeMs
            val pill = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = round(
                    if (isNext) 0xFFD4AF37.toInt() else col(R.color.tonal),
                    12f
                )
                setPadding(px(4), px(6), px(4), px(6))
            }
            val fg = if (isNext) 0xFF072A22.toInt() else col(R.color.text)
            val tStr = Instant.ofEpochMilli(s.timeMs).atZone(ZoneId.systemDefault()).format(fmt)
            pill.addView(text(s.name, 11f, fg, true).apply { gravity = Gravity.CENTER })
            pill.addView(text(tStr, 10f, fg, false).apply { gravity = Gravity.CENTER })
            prayerPillsRow.addView(pill, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = px(4)
            })
        }
    }

    private fun loadFavoritesFromUnion() {
        Thread {
            val favs = ArrayList<FavContact>()
            try {
                contentResolver.query(Uri.parse("${Sahl.CONTACTS_UNION}/favorites"), null, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        favs.add(FavContact(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3)))
                    }
                }
            } catch (_: Exception) {}
            runOnUiThread { renderFavorites(favs) }
        }.start()
    }

    private fun renderFavorites(favs: List<FavContact>) {
        favContainer.removeAllViews()
        if (favs.isEmpty()) {
            favContainer.addView(text(getString(R.string.no_favorites), 12f, col(R.color.sub)).apply {
                setOnClickListener { launchPackageOrToast("com.sahl.contacts") }
            })
            return
        }
        for (f in favs.take(10)) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(px(6), px(2), px(10), px(2))
                setOnClickListener {
                    if (f.number.isNotBlank()) {
                        try {
                            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(f.number)}")))
                        } catch (_: Exception) {}
                    } else {
                        launchPackageOrToast("com.sahl.contacts")
                    }
                }
            }
            val (avFrame, iv, letter) = avatar(46)
            avFrame.background = oval(0xFF0B5D4B.toInt())
            letter.text = f.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "★"
            Thumbs.load(this, f.photo, iv, letter)
            item.addView(avFrame)
            item.addView(text(f.name, 11f, col(R.color.text), true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                maxWidth = px(68)
                gravity = Gravity.CENTER
                setPadding(0, px(4), 0, 0)
            })
            favContainer.addView(item)
        }
    }

    private fun loadInstalledApps() {
        Thread {
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = pm.queryIntentActivities(mainIntent, 0)
            val entries = resolved.mapNotNull { ri ->
                val ai = ri.activityInfo ?: return@mapNotNull null
                if (ai.packageName == packageName) return@mapNotNull null
                val lbl = ri.loadLabel(pm).toString()
                val icon = ri.loadIcon(pm)
                val isSahl = ai.packageName in sahlPackages || ai.packageName.startsWith("com.sahl.")
                AppEntry(lbl, Norm.text(lbl), ai.packageName, ai.name, icon, isSahl)
            }.sortedWith(compareByDescending<AppEntry> { it.isSahl }.thenBy { it.normKey })

            runOnUiThread {
                allApps.clear()
                allApps.addAll(entries)
                renderSahlAppsGrid()
                filterAllApps(searchInput?.text?.toString() ?: "")
            }
        }.start()
    }

    private fun renderSahlAppsGrid() {
        sahlAppsGrid.removeAllViews()
        val sahlInstalled = allApps.filter { it.isSahl }
        for (app in sahlInstalled) {
            sahlAppsGrid.addView(buildAppCell(app, true))
        }
    }

    private fun filterAllApps(q: String) {
        allAppsGrid.removeAllViews()
        val nq = Norm.text(q.trim())
        val shown = if (nq.isEmpty()) allApps else allApps.filter { it.normKey.contains(nq) || it.pkg.contains(nq) }
        for (app in shown.take(80)) {
            allAppsGrid.addView(buildAppCell(app, app.isSahl))
        }
    }

    private fun buildAppCell(app: AppEntry, goldBorder: Boolean): View {
        val dm = resources.displayMetrics
        val cellW = (dm.widthPixels - px(64)) / 4
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(px(4), px(8), px(4), px(8))
            layoutParams = GridLayout.LayoutParams().apply { width = cellW }
            val iconWrap = FrameLayout(this@MainActivity).apply {
                background = round(
                    if (goldBorder) 0xFF0B5D4B.toInt() else col(R.color.tonal),
                    16f,
                    if (goldBorder) 0xFFD4AF37.toInt() else 0
                )
                setPadding(px(6), px(6), px(6), px(6))
                addView(ImageView(this@MainActivity).apply { setImageDrawable(app.icon) },
                    FrameLayout.LayoutParams(px(40), px(40), Gravity.CENTER))
            }
            addView(iconWrap, LinearLayout.LayoutParams(px(52), px(52)))
            addView(text(
                app.label.removePrefix("Sahl ").removePrefix("سهل "),
                11f,
                col(R.color.text),
                goldBorder
            ).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setPadding(0, px(4), 0, 0)
            })
            setOnClickListener {
                try {
                    startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LAUNCHER)
                            .setComponent(ComponentName(app.pkg, app.activity))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
            }
        }.press(14f)
    }

    private fun toggleTorch() {
        try {
            val cm = getSystemService(CameraManager::class.java) ?: return
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
        } catch (_: Exception) {}
    }

    private fun openSahlSearch() {
        try {
            startActivity(
                Intent().setComponent(ComponentName("com.sahl.tools", "com.sahl.tools.SearchActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            searchInput?.requestFocus()
        }
    }

    private fun launchPackageOrToast(pkg: String) {
        try {
            val i = packageManager.getLaunchIntentForPackage(pkg)
            if (i != null) startActivity(i)
            else Toast.makeText(this, pkg, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun showSettingsSheet() {
        val p = Sahl.prefs(this)
        val curLang = p.getString("lang", "system") ?: "system"
        val curTheme = p.getString("theme", "system") ?: "system"
        sheet(
            getString(R.string.settings_title),
            listOf(
                Triple(R.drawable.ic_wallpaper, getString(R.string.wallpapers_title)) {
                    startActivity(Intent(this, WallpaperActivity::class.java))
                },
                Triple(R.drawable.ic_home, getString(R.string.set_default_home)) {
                    askDefaultHomeRole()
                },
                Triple(R.drawable.ic_tune, "${getString(R.string.lang_label)}: $curLang") {
                    val next = when (curLang) { "en" -> "ar"; "ar" -> "system"; else -> "en" }
                    p.edit().putString("lang", next).apply()
                    recreate()
                },
                Triple(R.drawable.ic_light, "${getString(R.string.theme_label)}: $curTheme") {
                    val next = when (curTheme) { "dark" -> "light"; "light" -> "system"; else -> "dark" }
                    p.edit().putString("theme", next).apply()
                    recreate()
                },
                Triple(R.drawable.ic_check, getString(R.string.check_updates) + " · v" + BuildConfig.VERSION_NAME) {
                    checkUpdate(true)
                }
            )
        )
    }

    private fun checkUpdate(manual: Boolean) {
        if (manual) {
            updateBar.text = Updater.checkingText(this)
            updateBar.visibility = View.VISIBLE
        }
        Thread {
            val r = Updater.fetchNewer()
            runOnUiThread {
                if (r == null) {
                    if (manual) {
                        updateBar.visibility = View.GONE
                        Toast.makeText(this, Updater.upToDateText(this), Toast.LENGTH_SHORT).show()
                    }
                    return@runOnUiThread
                }
                updateBar.text = Updater.availableText(this, r) + "  ·  " + Updater.updateNowText(this)
                updateBar.visibility = View.VISIBLE
                updateBar.setOnClickListener {
                    Updater.downloadAndInstall(this, r)
                    Toast.makeText(this, Updater.downloadingText(this), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
