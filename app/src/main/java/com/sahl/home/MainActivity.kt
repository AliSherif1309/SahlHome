package com.sahl.home

import android.app.AlertDialog
import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
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
import kotlin.math.abs

/**
 * Sahl Home v1.5 — De-cluttered, Calm Islamic & Nova-Class Spatial Launcher:
 *  - No top app-actionbar clutter: the wallpaper's golden Mihrab arch breathes freely at the top.
 *  - Page 1:
 *      1. Live Sahl Union Mihrab Dashboard (Clock, Hijri date, Next Prayer countdown, golden progress bar,
 *         5 daily prayer pills, plus subtle Wallpapers & Settings icons tucked into the top corner).
 *      2. Slim, borderless Favorites & Quick Pill strip (Torch + starred contacts in a single airy row).
 *      3. Non-duplicated Pinned Apps Grid (automatically excludes apps already in the Bottom Dock so
 *         everything fits cleanly in 2 rows with zero vertical cutoff).
 *  - Page 2:
 *      1. Serene Islamic Sanctuary Card (Daily Quranic Ayah + 1-line translation + compact Tasbih; tap to cycle Ayah).
 *      2. 2×2 Smart Folders sitting directly on the wallpaper (no bulky outer wrapper card).
 *      3. Hosted Android Widgets (hidden when empty; add via long-press desktop menu or Settings).
 *  - Minimalist gold page dots (● ○) + clean 5-icon Bottom Dock.
 */
class MainActivity : SahlActivity() {
    data class PrayerSlot(val index: Int, val name: String, val timeMs: Long)
    data class FavContact(val id: Long, val name: String, val number: String, val photo: String?)
    data class AppEntry(
        val label: String,
        val normKey: String,
        val section: String,
        val pkg: String,
        val activity: String,
        val icon: Drawable,
        val isSahl: Boolean
    )

    private val sahlPackages = setOf(
        "com.sahl.phone", "com.sahl.contacts", "com.sahl.messages", "com.sahl.adhan",
        "com.sahl.qibla", "com.aly.trueqibla", "com.sahl.files", "com.sahl.gallery",
        "com.sahl.camera", "com.sahl.tools", "com.aly.sahlsync", "com.sahl.pdf", "com.sahl.timer"
    )

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var bgBackdrop: WallpaperPreviewView
    private lateinit var desktopPager: FrameLayout
    private lateinit var page1Container: LinearLayout
    private lateinit var page2Container: ScrollView
    private lateinit var dotPage1: View
    private lateinit var dotPage2: View
    private var currentPage = 0
    private var ayahOffset = 0

    // Page 1 views
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var prayerCountdownText: TextView
    private lateinit var prayerProgressFill: View
    private lateinit var prayerPillsRow: LinearLayout
    private lateinit var favAndToolsRow: LinearLayout
    private lateinit var desktopAppsGrid: GridLayout
    private lateinit var updateBar: TextView

    // Page 2 views (Islamic Sanctuary, Smart Folders & Android Widgets)
    private lateinit var ayahArabicText: TextView
    private lateinit var ayahTranslationText: TextView
    private lateinit var ayahRefText: TextView
    private lateinit var dhikrTitleText: TextView
    private lateinit var dhikrBodyText: TextView
    private lateinit var foldersGrid: GridLayout
    private lateinit var widgetsCard: LinearLayout
    private lateinit var widgetsContainer: LinearLayout

    // Bottom Dock & Drawer
    private lateinit var bottomDockRow: LinearLayout
    private lateinit var drawerOverlay: FrameLayout
    private lateinit var drawerAppsGrid: GridLayout
    private lateinit var drawerSectionBar: LinearLayout
    private var drawerSearchInput: EditText? = null
    private var drawerOpen = false
    private var showHiddenInDrawer = false

    // AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost
    private val HOST_ID = 1448
    private val REQ_PICK_WIDGET = 201
    private val REQ_BIND_WIDGET = 202

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
            when (i.action) {
                BadgeListenerService.ACTION_BADGES_CHANGED -> {
                    renderDesktopAppsGrid()
                    renderBottomDock()
                    if (drawerOpen) filterDrawerApps(drawerSearchInput?.text?.toString() ?: "")
                }
                else -> refreshUnionData()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, HOST_ID)

        val root = FrameLayout(this)

        // Procedural Sahl Wallpaper backdrop rendered behind the spatial launcher
        bgBackdrop = WallpaperPreviewView(this).apply {
            style = WallpaperEngine.currentStyle(this@MainActivity)
        }
        root.addView(bgBackdrop, FrameLayout.LayoutParams(-1, -1))

        // Main spatial desktop column — top breathing room lets the Mihrab arch crown the screen
        val desktopShell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(14), px(22), px(14), px(10))
        }

        // Update notification banner (only visible when an update exists)
        updateBar = text("", 12f, 0xFF072A22.toInt(), true).apply {
            visibility = View.GONE
            background = round(0xFFF5C84C.toInt(), 14f)
            setPadding(px(12), px(8), px(12), px(8))
            gravity = Gravity.CENTER
        }
        desktopShell.addView(updateBar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(8) })

        // 2-Page Desktop Stage
        desktopPager = FrameLayout(this)
        page1Container = buildPage1Desktop()
        page2Container = buildPage2SanctuaryAndFolders()
        page2Container.visibility = View.GONE
        desktopPager.addView(page1Container, FrameLayout.LayoutParams(-1, -1))
        desktopPager.addView(page2Container, FrameLayout.LayoutParams(-1, -1))
        attachGestureEngine(desktopPager)
        desktopShell.addView(desktopPager, LinearLayout.LayoutParams(-1, 0, 1f))

        // Minimalist Gold Page Dots (● ○)
        desktopShell.addView(buildMinimalPageDots(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = px(6)
            bottomMargin = px(8)
        })

        // Bottom Dock
        bottomDockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = round(if (night) 0xD90E2922.toInt() else 0xE6FFFFFF.toInt(), 26f, 0x88D4AF37.toInt())
            setPadding(px(10), px(8), px(10), px(8))
        }
        desktopShell.addView(bottomDockRow, LinearLayout.LayoutParams(-1, -2))

        root.addView(desktopShell, FrameLayout.LayoutParams(-1, -1))

        // Full-Screen Slide-Up All Apps Drawer Overlay
        drawerOverlay = buildAllAppsDrawerOverlay()
        drawerOverlay.visibility = View.GONE
        root.addView(drawerOverlay, FrameLayout.LayoutParams(-1, -1))

        setContentView(root)

        Updater.scheduleDaily(this)
        checkUpdate(false)
    }

    private fun glassCard(strokeAlphaColor: Int = 0x66D4AF37): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = round(if (night) 0xD414221E.toInt() else 0xEAFFFFFF.toInt(), 24f, strokeAlphaColor)
        setPadding(px(16), px(14), px(16), px(14))
    }

    /**
     * PAGE 1 (Calm & Airy):
     *  1. Live Sahl Union Mihrab Dashboard (with subtle Wallpaper & Settings icons in its top corner)
     *  2. Slim, borderless Favorites & Torch pill strip (no heavy box!)
     *  3. 2 clean rows of non-dock Sahl Apps centered in the remaining space (zero cutoff!)
     */
    private fun buildPage1Desktop(): LinearLayout {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 1. Live Sahl Union Dashboard Card
        val dash = glassCard(0x88D4AF37.toInt())

        // Top mini header inside the card: Sahl Union · Doha + subtle Wallpaper & Settings icons
        val metaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        metaRow.addView(text("۞  " + getString(R.string.union_title), 12f, 0xFFD4AF37.toInt(), true),
            LinearLayout.LayoutParams(0, -2, 1f))
        dateText = text("", 12f, col(R.color.sub), true).apply {
            setPadding(px(6), 0, px(8), 0)
        }
        metaRow.addView(dateText)
        metaRow.addView(iconButton(R.drawable.ic_wallpaper, 0, 0xFFD4AF37.toInt(), 30, 16) {
            startActivity(Intent(this, WallpaperActivity::class.java))
        })
        metaRow.addView(iconButton(R.drawable.ic_tune, 0, 0xFFD4AF37.toInt(), 30, 16) {
            showSettingsSheet()
        })
        dash.addView(metaRow)

        // Clock + Next Prayer row
        val clockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(2), 0, px(2))
        }
        clockText = text("00:00", 36f, col(R.color.text), true)
        clockRow.addView(clockText)
        prayerCountdownText = text("", 13f, if (night) 0xFFF5C84C.toInt() else col(R.color.emerald), true).apply {
            gravity = Gravity.END
            setPadding(px(10), 0, 0, 0)
        }
        clockRow.addView(prayerCountdownText, LinearLayout.LayoutParams(0, -2, 1f))
        dash.addView(clockRow)

        // Golden progress bar between previous and next prayer
        val progressTrack = FrameLayout(this).apply {
            background = round(col(R.color.line), 4f)
        }
        prayerProgressFill = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF0B5D4B.toInt(), 0xFFD4AF37.toInt(), 0xFFF5C84C.toInt())
            ).apply { cornerRadius = 4f * dp }
        }
        progressTrack.addView(prayerProgressFill, FrameLayout.LayoutParams(px(180), px(4)))
        dash.addView(progressTrack, LinearLayout.LayoutParams(-1, px(4)).apply {
            topMargin = px(8)
            bottomMargin = px(10)
        })

        // 5 Daily Prayer Pills
        prayerPillsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        dash.addView(prayerPillsRow, LinearLayout.LayoutParams(-1, -2))
        dash.setOnClickListener { launchPackageOrToast("com.sahl.adhan") }
        col.addView(dash, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(12) })

        // 2. Slim, borderless Favorites & Quick Action Pill Strip (single horizontal line, no bulky card box)
        favAndToolsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        col.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(favAndToolsRow)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(12) })

        // Flexible spacer pushes the 2-row app grid comfortably toward the lower-middle desktop
        col.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))

        // 3. Clean Pinned Apps Grid (excludes apps already in Bottom Dock -> fits cleanly in 2 rows!)
        desktopAppsGrid = GridLayout(this).apply {
            columnCount = IslamicHomeData.gridColumns(this@MainActivity)
        }
        col.addView(desktopAppsGrid, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(4) })

        col.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))
        col.setOnTouchListener { _, ev -> gestureDetector.onTouchEvent(ev); true }
        return col
    }

    /**
     * PAGE 2 (Serene Islamic Sanctuary & Smart Folders):
     *  1. Compact Daily Quranic Ayah & Adhkar Card (tap to cycle Ayahs)
     *  2. 2×2 Smart Folders sitting directly on the wallpaper (no heavy outer box)
     *  3. Hosted Android Widgets (only visible when at least 1 widget is added)
     */
    private fun buildPage2SanctuaryAndFolders(): ScrollView {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 1. Daily Quranic Ayah & Morning/Evening Adhkar Sanctuary Card
        val ayahCard = glassCard(0x88D4AF37.toInt())
        val ar = resources.configuration.locales[0].language == "ar"
        val topMeta = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topMeta.addView(text(if (ar) "۞  آية اليوم" else "۞  Daily Quranic Ayah", 12f, 0xFFD4AF37.toInt(), true),
            LinearLayout.LayoutParams(0, -2, 1f))
        ayahRefText = text("", 11f, col(R.color.sub), true)
        topMeta.addView(ayahRefText)
        ayahCard.addView(topMeta)

        ayahArabicText = text("", 20f, if (night) 0xFFF5C84C.toInt() else col(R.color.emerald), true).apply {
            setPadding(0, px(8), 0, px(4))
        }
        ayahCard.addView(ayahArabicText)

        ayahTranslationText = text("", 12f, col(R.color.text), false).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, px(2))
        }
        ayahCard.addView(ayahTranslationText)

        // Subtle divider
        ayahCard.addView(View(this).apply { setBackgroundColor(col(R.color.line)) },
            LinearLayout.LayoutParams(-1, 1).apply { topMargin = px(10); bottomMargin = px(8) })

        val dhikrRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        dhikrTitleText = text("", 11f, 0xFFD4AF37.toInt(), true)
        dhikrRow.addView(dhikrTitleText)
        dhikrBodyText = text("", 14f, col(R.color.text), true).apply {
            setPadding(0, px(3), 0, 0)
        }
        dhikrRow.addView(dhikrBodyText)
        ayahCard.addView(dhikrRow)

        // Tap card to cycle through tranquil Quranic Ayahs
        ayahCard.setOnClickListener {
            ayahOffset = (ayahOffset + 1) % IslamicHomeData.ayahs.size
            refreshIslamicSanctuary()
        }
        col.addView(ayahCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(14) })

        // 2. Smart Folders sitting directly on the wallpaper (no outer box!)
        val folderHdr = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(4), 0, px(4), px(6))
        }
        folderHdr.addView(text(getString(R.string.folders_title), 12f, 0xFFF5C84C.toInt(), true),
            LinearLayout.LayoutParams(0, -2, 1f))
        folderHdr.addView(text(getString(R.string.new_folder), 12f, 0xFFF5C84C.toInt(), true).apply {
            setPadding(px(8), px(2), px(8), px(2))
            setOnClickListener { promptCreateFolder() }
        }.press(12f))
        col.addView(folderHdr, LinearLayout.LayoutParams(-1, -2))

        foldersGrid = GridLayout(this).apply { columnCount = 2 }
        col.addView(foldersGrid, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(12) })

        // 3. Native Android AppWidget Host Card (auto-hides when empty so it never adds clutter)
        widgetsCard = glassCard().apply { visibility = View.GONE }
        widgetsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        widgetsCard.addView(widgetsContainer, LinearLayout.LayoutParams(-1, -2))
        col.addView(widgetsCard, LinearLayout.LayoutParams(-1, -2))

        return ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(col)
            setOnTouchListener { _, ev -> gestureDetector.onTouchEvent(ev); false }
        }
    }

    private fun buildMinimalPageDots(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        dotPage1 = View(this).apply {
            background = round(0xFFF5C84C.toInt(), 6f)
            setOnClickListener { switchPage(0) }
        }
        dotPage2 = View(this).apply {
            background = round(0x55EDEBE4, 6f)
            setOnClickListener { switchPage(1) }
        }
        row.addView(dotPage1, LinearLayout.LayoutParams(px(22), px(6)).apply { marginEnd = px(6) })
        row.addView(dotPage2, LinearLayout.LayoutParams(px(8), px(6)))
        return row
    }

    private fun switchPage(page: Int) {
        currentPage = page.coerceIn(0, 1)
        page1Container.visibility = if (currentPage == 0) View.VISIBLE else View.GONE
        page2Container.visibility = if (currentPage == 1) View.VISIBLE else View.GONE

        dotPage1.layoutParams = LinearLayout.LayoutParams(px(if (currentPage == 0) 22 else 8), px(6)).apply { marginEnd = px(6) }
        dotPage1.background = round(if (currentPage == 0) 0xFFF5C84C.toInt() else 0x55EDEBE4, 6f)
        dotPage2.layoutParams = LinearLayout.LayoutParams(px(if (currentPage == 1) 22 else 8), px(6))
        dotPage2.background = round(if (currentPage == 1) 0xFFF5C84C.toInt() else 0x55EDEBE4, 6f)
        dotPage1.requestLayout()

        if (currentPage == 1) {
            refreshIslamicSanctuary()
            renderFoldersGrid()
        }
    }

    /**
     * Full-Screen Slide-Up All Apps Drawer with Arabic-smart search & A–Z / أ–ي Fast Side Index.
     */
    private fun buildAllAppsDrawerOverlay(): FrameLayout {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(if (night) 0xF4081512.toInt() else 0xF7F7F5EE.toInt())
            isClickable = true
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(14), px(18), px(14), px(14))
        }

        // Top search bar + Close drawer button
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val (sBox, sEt) = searchBox(getString(R.string.search_apps)) { q -> filterDrawerApps(q) }
        drawerSearchInput = sEt
        topBar.addView(sBox, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(8) })
        topBar.addView(iconButton(R.drawable.ic_close, col(R.color.tonal), col(R.color.emerald), 42, 20) {
            closeDrawer()
        })
        col.addView(topBar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(8) })

        // Fast A-Z / أ-ي Horizontal Section Chips
        drawerSectionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, px(2), 0, px(8))
        }
        col.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(drawerSectionBar)
        }, LinearLayout.LayoutParams(-1, -2))

        // Scrollable App Grid
        drawerAppsGrid = GridLayout(this).apply {
            columnCount = IslamicHomeData.gridColumns(this@MainActivity)
        }
        col.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(drawerAppsGrid)
        }, LinearLayout.LayoutParams(-1, 0, 1f))

        overlay.addView(col, FrameLayout.LayoutParams(-1, -1))
        return overlay
    }

    private fun openDrawer() {
        drawerOpen = true
        drawerOverlay.visibility = View.VISIBLE
        drawerOverlay.translationY = 120f * dp
        drawerOverlay.alpha = 0f
        drawerOverlay.animate().translationY(0f).alpha(1f).setDuration(180).start()
        filterDrawerApps(drawerSearchInput?.text?.toString() ?: "")
    }

    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        drawerSearchInput?.setText("")
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(drawerOverlay.windowToken, 0)
        drawerOverlay.visibility = View.GONE
    }

    private lateinit var gestureDetector: GestureDetector

    private fun attachGestureEngine(target: View) {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                when (IslamicHomeData.doubleTapAction(this@MainActivity)) {
                    "wallpapers" -> startActivity(Intent(this@MainActivity, WallpaperActivity::class.java))
                    "dim" -> launchPackageOrToast("com.sahl.tools")
                    else -> openSahlSearch()
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                target.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showSettingsSheet()
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.rawX - e1.rawX
                val dy = e2.rawY - e1.rawY
                if (abs(dx) > abs(dy) * 1.25f && abs(dx) > 65 * dp) {
                    if (dx < 0 && currentPage == 0) { switchPage(1); return true }
                    if (dx > 0 && currentPage == 1) { switchPage(0); return true }
                } else if (abs(dy) > abs(dx) * 1.35f && abs(dy) > 85 * dp) {
                    if (dy < 0 && !drawerOpen) {
                        openDrawer()
                        return true
                    } else if (dy > 0 && !drawerOpen && e1.rawY < resources.displayMetrics.heightPixels * 0.48f) {
                        expandStatusBarNotifications()
                        return true
                    }
                }
                return false
            }
        })
        target.setOnTouchListener { _, ev -> gestureDetector.onTouchEvent(ev); true }
    }

    private fun expandStatusBarNotifications() {
        try {
            val sbm = getSystemService("statusbar") ?: return
            val cls = Class.forName("android.app.StatusBarManager")
            cls.getMethod("expandNotificationsPanel").invoke(sbm)
        } catch (_: Exception) {}
    }

    /**
     * Renders the Bottom Dock cleanly without double-wrapping Sahl icons.
     */
    private fun renderBottomDock() {
        bottomDockRow.removeAllViews()
        val dockPkgs = IslamicHomeData.dockPackages(this)
        for (pkg in dockPkgs) {
            val entry = allApps.firstOrNull { it.pkg == pkg }
            val cell = FrameLayout(this).apply {
                val appIcon = entry?.icon ?: try { packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null }
                val isSahl = pkg in sahlPackages || pkg.startsWith("com.sahl.")
                if (appIcon != null) {
                    if (!isSahl && IslamicHomeData.frameThirdParty(this@MainActivity)) {
                        background = round(0xFF0B5D4B.toInt(), 16f, 0xFFD4AF37.toInt())
                        addView(ImageView(this@MainActivity).apply { setImageDrawable(appIcon) },
                            FrameLayout.LayoutParams(px(36), px(36), Gravity.CENTER))
                    } else {
                        addView(ImageView(this@MainActivity).apply { setImageDrawable(appIcon) },
                            FrameLayout.LayoutParams(px(48), px(48), Gravity.CENTER))
                    }
                } else {
                    background = round(0xFF0B5D4B.toInt(), 16f, 0xFFD4AF37.toInt())
                    addView(icon(R.drawable.ic_star, 0xFFF5C84C.toInt(), 24),
                        FrameLayout.LayoutParams(px(24), px(24), Gravity.CENTER))
                }

                val badgeCount = BadgeListenerService.countFor(pkg)
                if (badgeCount > 0) {
                    addView(buildGoldBadgeView(badgeCount),
                        FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))
                }

                setOnClickListener { launchPackageOrToast(pkg) }
                setOnLongClickListener {
                    if (entry != null) showAppContextMenu(entry)
                    true
                }
            }.press(16f)
            bottomDockRow.addView(cell, LinearLayout.LayoutParams(px(52), px(52)).apply {
                setMargins(px(8), 0, px(8), 0)
            })
        }

        // All Apps Drawer button in the dock
        val allAppsBtn = FrameLayout(this).apply {
            background = round(0xFF0B5D4B.toInt(), 16f, 0xFFD4AF37.toInt())
            addView(icon(R.drawable.ic_apps, 0xFFF5C84C.toInt(), 22),
                FrameLayout.LayoutParams(px(22), px(22), Gravity.CENTER))
            setOnClickListener { if (drawerOpen) closeDrawer() else openDrawer() }
        }.press(16f)
        bottomDockRow.addView(allAppsBtn, LinearLayout.LayoutParams(px(48), px(48)).apply {
            setMargins(px(8), 0, px(8), 0)
        })
    }

    private fun buildGoldBadgeView(count: Int): TextView =
        text(if (count > 99) "99+" else count.toString(), 9f, 0xFF072A22.toInt(), true).apply {
            background = round(0xFFF5C84C.toInt(), 10f, 0xFF072A22.toInt())
            setPadding(px(5), px(1), px(5), px(1))
            gravity = Gravity.CENTER
        }

    private fun buildAppCell(app: AppEntry, cols: Int, showLabel: Boolean): View {
        val dm = resources.displayMetrics
        val cellW = (dm.widthPixels - px(32)) / cols
        val frame3p = IslamicHomeData.frameThirdParty(this)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(px(2), px(8), px(2), px(8))
            layoutParams = GridLayout.LayoutParams().apply { width = cellW }

            val iconStage = FrameLayout(this@MainActivity)
            if (app.isSahl || !frame3p) {
                iconStage.addView(ImageView(this@MainActivity).apply {
                    setImageDrawable(app.icon)
                }, FrameLayout.LayoutParams(px(54), px(54), Gravity.CENTER))
            } else {
                val seal = FrameLayout(this@MainActivity).apply {
                    background = round(0xFF0B5D4B.toInt(), 16f, 0xFFD4AF37.toInt())
                    setPadding(px(6), px(6), px(6), px(6))
                    addView(ImageView(this@MainActivity).apply { setImageDrawable(app.icon) },
                        FrameLayout.LayoutParams(px(38), px(38), Gravity.CENTER))
                }
                iconStage.addView(seal, FrameLayout.LayoutParams(px(54), px(54), Gravity.CENTER))
            }

            val badgeCount = BadgeListenerService.countFor(app.pkg)
            if (badgeCount > 0) {
                iconStage.addView(buildGoldBadgeView(badgeCount),
                    FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))
            }

            addView(iconStage, LinearLayout.LayoutParams(px(58), px(56)))

            if (showLabel) {
                val cleanLabel = app.label.removePrefix("Sahl ").removePrefix("سهل ")
                addView(text(
                    cleanLabel,
                    11.5f,
                    0xFFF5F3EB.toInt(),
                    app.isSahl
                ).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    setShadowLayer(4f, 0f, 1f, 0xCC000000.toInt())
                    setPadding(0, px(4), 0, 0)
                })
            }

            setOnClickListener {
                closeDrawer()
                try {
                    startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LAUNCHER)
                            .setComponent(ComponentName(app.pkg, app.activity))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
            }
            setOnLongClickListener {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showAppContextMenu(app)
                true
            }
        }.press(14f)
    }

    private fun showAppContextMenu(app: AppEntry) {
        val items = ArrayList<Triple<Int, String, () -> Unit>>()

        try {
            val la = getSystemService(LauncherApps::class.java)
            if (la != null && la.hasShortcutHostPermission()) {
                val q = LauncherApps.ShortcutQuery().apply {
                    setPackage(app.pkg)
                    setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                    )
                }
                val shortcuts: List<ShortcutInfo> = la.getShortcuts(q, Process.myUserHandle()) ?: emptyList()
                for (sc in shortcuts.take(4)) {
                    val lbl = sc.shortLabel?.toString() ?: sc.longLabel?.toString() ?: continue
                    items.add(Triple(R.drawable.ic_star, "⚡  $lbl") {
                        try { la.startShortcut(sc, null, null) } catch (_: Exception) {}
                    })
                }
            }
        } catch (_: Exception) {}

        val pinned = IslamicHomeData.pinnedDesktopPackages(this)
        if (app.pkg in pinned) {
            items.add(Triple(R.drawable.ic_close, getString(R.string.unpin_home)) {
                pinned.remove(app.pkg)
                IslamicHomeData.savePinnedDesktopPackages(this, pinned)
                renderDesktopAppsGrid()
            })
        } else {
            items.add(Triple(R.drawable.ic_add, getString(R.string.pin_home)) {
                pinned.add(app.pkg)
                IslamicHomeData.savePinnedDesktopPackages(this, pinned)
                renderDesktopAppsGrid()
            })
        }

        val dock = IslamicHomeData.dockPackages(this)
        if (app.pkg in dock) {
            items.add(Triple(R.drawable.ic_close, getString(R.string.unpin_dock)) {
                dock.remove(app.pkg)
                IslamicHomeData.saveDockPackages(this, dock)
                renderBottomDock()
                renderDesktopAppsGrid()
            })
        } else if (dock.size < 5) {
            items.add(Triple(R.drawable.ic_add, getString(R.string.pin_dock)) {
                dock.add(app.pkg)
                IslamicHomeData.saveDockPackages(this, dock)
                renderBottomDock()
                renderDesktopAppsGrid()
            })
        }

        items.add(Triple(R.drawable.ic_apps, getString(R.string.add_to_folder)) {
            promptAddAppToFolder(app)
        })

        val hidden = IslamicHomeData.hiddenPackages(this)
        if (app.pkg in hidden) {
            items.add(Triple(R.drawable.ic_check, getString(R.string.unhide_app)) {
                hidden.remove(app.pkg)
                IslamicHomeData.saveHiddenPackages(this, hidden)
                filterDrawerApps(drawerSearchInput?.text?.toString() ?: "")
            })
        } else {
            items.add(Triple(R.drawable.ic_close, getString(R.string.hide_app)) {
                hidden.add(app.pkg)
                IslamicHomeData.saveHiddenPackages(this, hidden)
                filterDrawerApps(drawerSearchInput?.text?.toString() ?: "")
            })
        }

        items.add(Triple(R.drawable.ic_tune, getString(R.string.app_info)) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
            } catch (_: Exception) {}
        })

        items.add(Triple(R.drawable.ic_debloat, getString(R.string.uninstall_app)) {
            try {
                startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")))
            } catch (_: Exception) {}
        })

        sheet(app.label, items)
    }

    private fun promptAddAppToFolder(app: AppEntry) {
        val ar = resources.configuration.locales[0].language == "ar"
        val folders = IslamicHomeData.folders(this)
        val items = folders.map { f ->
            Triple(R.drawable.ic_apps, if (ar) f.nameAr else f.nameEn) {
                if (app.pkg !in f.packages) f.packages.add(app.pkg)
                IslamicHomeData.saveFolders(this, folders)
                renderFoldersGrid()
            }
        }
        sheet(getString(R.string.add_to_folder), items)
    }

    private fun promptCreateFolder() {
        val et = EditText(this).apply {
            hint = getString(R.string.folder_name_hint)
            typeface = font
            setTextColor(col(R.color.text))
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.new_folder))
            .setView(et)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = et.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    val list = IslamicHomeData.folders(this)
                    list.add(IslamicHomeData.Folder("f_${System.currentTimeMillis()}", name, name, mutableListOf()))
                    IslamicHomeData.saveFolders(this, list)
                    renderFoldersGrid()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun renderFoldersGrid() {
        foldersGrid.removeAllViews()
        val ar = resources.configuration.locales[0].language == "ar"
        val list = IslamicHomeData.folders(this)
        val dm = resources.displayMetrics
        val cellW = (dm.widthPixels - px(44)) / 2
        for (f in list) {
            val matchedApps = allApps.distinctBy { it.pkg }.filter { it.pkg in f.packages }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = round(if (night) 0xD414221E.toInt() else 0xEAFFFFFF.toInt(), 20f, 0x66D4AF37)
                setPadding(px(14), px(12), px(14), px(12))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cellW
                    setMargins(px(4), px(4), px(4), px(4))
                }
                setOnClickListener { openFolderSheet(f, matchedApps) }
            }.press(20f)

            box.addView(text("۞  " + (if (ar) f.nameAr else f.nameEn), 13f, col(R.color.text), true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            val previewRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, px(8), 0, 0)
            }
            for (app in matchedApps.take(4)) {
                previewRow.addView(ImageView(this).apply {
                    setImageDrawable(app.icon)
                }, LinearLayout.LayoutParams(px(30), px(30)).apply { marginEnd = px(6) })
            }
            if (matchedApps.isEmpty()) {
                previewRow.addView(text("—", 12f, col(R.color.sub)))
            }
            box.addView(previewRow)
            foldersGrid.addView(box)
        }
    }

    private fun openFolderSheet(f: IslamicHomeData.Folder, apps: List<AppEntry>) {
        val ar = resources.configuration.locales[0].language == "ar"
        val items = apps.map { app ->
            Triple(R.drawable.ic_star, app.label) {
                try {
                    startActivity(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LAUNCHER)
                            .setComponent(ComponentName(app.pkg, app.activity))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
            }
        }
        sheet(if (ar) f.nameAr else f.nameEn, items)
    }

    private fun refreshIslamicSanctuary() {
        val ar = resources.configuration.locales[0].language == "ar"
        val baseIdx = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        val ayah = IslamicHomeData.ayahs[(baseIdx + ayahOffset) % IslamicHomeData.ayahs.size]
        ayahArabicText.text = ayah.ar
        ayahTranslationText.text = ayah.en
        ayahTranslationText.visibility = if (ar) View.GONE else View.VISIBLE
        ayahRefText.text = if (ar) ayah.refAr else ayah.refEn

        val dhikr = IslamicHomeData.currentDhikr()
        dhikrTitleText.text = "📿  " + (if (ar) dhikr.titleAr else dhikr.titleEn)
        // Keep Tasbih serene & concise in Arabic script without a duplicate 2-line paragraph
        dhikrBodyText.text = dhikr.ar
    }

    // ─────────────────── Native Android AppWidgetHost ───────────────────

    private fun pickNewAppWidget() {
        try {
            val id = appWidgetHost.allocateAppWidgetId()
            val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            }
            startActivityForResult(pickIntent, REQ_PICK_WIDGET)
        } catch (_: Exception) {}
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        val id = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (id == -1) return
        when (requestCode) {
            REQ_PICK_WIDGET -> {
                val info = appWidgetManager.getAppWidgetInfo(id)
                if (info?.configure != null) {
                    val cfg = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                        component = info.configure
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    }
                    startActivityForResult(cfg, REQ_BIND_WIDGET)
                } else {
                    attachHostedWidget(id)
                }
            }
            REQ_BIND_WIDGET -> attachHostedWidget(id)
        }
    }

    private fun attachHostedWidget(id: Int) {
        val list = IslamicHomeData.widgetIds(this)
        if (id !in list) {
            list.add(id)
            IslamicHomeData.saveWidgetIds(this, list)
        }
        renderHostedWidgets()
        switchPage(1)
    }

    private fun renderHostedWidgets() {
        widgetsContainer.removeAllViews()
        val ids = IslamicHomeData.widgetIds(this)
        var added = 0
        for (id in ids) {
            val info = appWidgetManager.getAppWidgetInfo(id) ?: continue
            try {
                val hv = appWidgetHost.createView(applicationContext, id, info)
                hv.setAppWidget(id, info)
                val wrap = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, px(4), 0, px(8))
                }
                wrap.addView(hv, LinearLayout.LayoutParams(-1, info.minHeight.coerceAtLeast(px(110))))
                wrap.addView(text(getString(R.string.remove_widget), 11f, col(R.color.danger), true).apply {
                    gravity = Gravity.END
                    setPadding(px(4), px(4), px(4), px(4))
                    setOnClickListener {
                        try { appWidgetHost.deleteAppWidgetId(id) } catch (_: Exception) {}
                        val cur = IslamicHomeData.widgetIds(this@MainActivity)
                        cur.remove(id)
                        IslamicHomeData.saveWidgetIds(this@MainActivity, cur)
                        renderHostedWidgets()
                    }
                })
                widgetsContainer.addView(wrap)
                added++
            } catch (_: Exception) {}
        }
        widgetsCard.visibility = if (added > 0) View.VISIBLE else View.GONE
    }

    override fun onStart() {
        super.onStart()
        try { appWidgetHost.startListening() } catch (_: Exception) {}
    }

    override fun onStop() {
        try { appWidgetHost.stopListening() } catch (_: Exception) {}
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        bgBackdrop.style = WallpaperEngine.currentStyle(this)
        bgBackdrop.invalidate()
        handler.removeCallbacks(clockTicker)
        clockTicker.run()
        refreshUnionData()
        refreshIslamicSanctuary()
        loadInstalledApps()
        renderHostedWidgets()
        val f = IntentFilter(Sahl.ACTION_EVENT).apply {
            addAction(BadgeListenerService.ACTION_BADGES_CHANGED)
        }
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
        when {
            drawerOpen -> closeDrawer()
            currentPage != 0 -> switchPage(0)
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
        val ar = resources.configuration.locales[0].language == "ar"
        val now = System.currentTimeMillis()
        val nextIdx = slots.indexOfFirst { it.timeMs > now }.let { if (it == -1) 0 else it }
        val next = slots[nextIdx]
        val prevTimeMs = if (nextIdx > 0) slots[nextIdx - 1].timeMs else (next.timeMs - 6 * 3600_000L)
        val totalWindow = (next.timeMs - prevTimeMs).coerceAtLeast(60_000L)
        val elapsed = (now - prevTimeMs).coerceIn(0L, totalWindow)
        val ratio = (elapsed.toFloat() / totalWindow.toFloat()).coerceIn(0.08f, 1f)

        prayerProgressFill.post {
            val parentW = (prayerProgressFill.parent as? View)?.width ?: px(260)
            prayerProgressFill.layoutParams = FrameLayout.LayoutParams((parentW * ratio).toInt(), px(4))
        }

        val diffMin = ((next.timeMs - now).coerceAtLeast(0L) / 60_000L).toInt()
        val durStr = if (ar) {
            if (diffMin >= 60) "${diffMin / 60} س ${diffMin % 60} د" else "$diffMin د"
        } else {
            if (diffMin >= 60) "${diffMin / 60}h ${diffMin % 60}m" else "${diffMin}m"
        }
        val loc = resources.configuration.locales[0]
        val fmt = DateTimeFormatter.ofPattern("HH:mm", loc)
        val timeFormatted = Instant.ofEpochMilli(next.timeMs).atZone(ZoneId.systemDefault()).format(fmt)
        val localizedNextName = IslamicHomeData.localizedPrayerName(next.name, ar)
        prayerCountdownText.text = "$localizedNextName · $durStr ($timeFormatted)"

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
            val pName = IslamicHomeData.localizedPrayerName(s.name, ar)
            pill.addView(text(pName, 11f, fg, true).apply { gravity = Gravity.CENTER })
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
            runOnUiThread { renderSlimFavAndToolsRow(favs) }
        }.start()
    }

    /**
     * Slim, single-line floating pill strip:
     * Torch pill + Set Default Home pill (if not default yet) + Starred Favorite Contact pills.
     * No bulky outer card box!
     */
    private fun renderSlimFavAndToolsRow(favs: List<FavContact>) {
        favAndToolsRow.removeAllViews()

        // 1. Torch quick pill
        val torchPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = round(if (torchOn) 0xFFD4AF37.toInt() else (if (night) 0xCC14221E.toInt() else 0xE6FFFFFF.toInt()), 20f, 0x77D4AF37)
            setPadding(px(12), px(7), px(12), px(7))
            val fg = if (torchOn) 0xFF072A22.toInt() else 0xFFF5C84C.toInt()
            addView(icon(R.drawable.ic_flash, fg, 16))
            addView(text(getString(R.string.tool_torch), 11.5f, if (torchOn) 0xFF072A22.toInt() else col(R.color.text), true).apply {
                setPadding(px(6), 0, 0, 0)
            })
            setOnClickListener {
                toggleTorch()
                renderSlimFavAndToolsRow(favs)
            }
        }.press(20f)
        favAndToolsRow.addView(torchPill, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(8) })

        // 2. Starred Favorite Contact pills
        for (f in favs.take(10)) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = round(if (night) 0xCC14221E.toInt() else 0xE6FFFFFF.toInt(), 20f, 0x77D4AF37)
                setPadding(px(6), px(4), px(12), px(4))
                setOnClickListener {
                    if (f.number.isNotBlank()) {
                        try {
                            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(f.number)}")))
                        } catch (_: Exception) {}
                    } else {
                        launchPackageOrToast("com.sahl.contacts")
                    }
                }
            }.press(20f)
            val (avFrame, iv, letter) = avatar(26)
            avFrame.background = oval(0xFF0B5D4B.toInt())
            letter.text = f.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "★"
            Thumbs.load(this, f.photo, iv, letter)
            item.addView(avFrame)
            item.addView(text(f.name, 11.5f, col(R.color.text), true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                maxWidth = px(100)
                setPadding(px(7), 0, 0, 0)
            })
            favAndToolsRow.addView(item, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(8) })
        }

        // 3. Subtle "Set as Default Home" pill only if not yet default
        if (!isDefaultHome()) {
            val defPill = text("🏠  " + getString(R.string.set_default_home), 11.5f, 0xFF072A22.toInt(), true).apply {
                background = round(0xFFF5C84C.toInt(), 20f)
                setPadding(px(12), px(7), px(12), px(7))
                setOnClickListener { askDefaultHomeRole() }
            }.press(20f)
            favAndToolsRow.addView(defPill, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(8) })
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
                val cleanName = lbl.removePrefix("Sahl ").removePrefix("سهل ")
                AppEntry(lbl, Norm.text(lbl), Norm.section(cleanName), ai.packageName, ai.name, icon, isSahl)
            }.sortedWith(compareByDescending<AppEntry> { it.isSahl }.thenBy { it.normKey })

            runOnUiThread {
                allApps.clear()
                allApps.addAll(entries)
                renderDesktopAppsGrid()
                renderBottomDock()
                renderFoldersGrid()
                if (drawerOpen) filterDrawerApps(drawerSearchInput?.text?.toString() ?: "")
            }
        }.start()
    }

    /**
     * Renders up to 2 clean rows of apps on Page 1, excluding apps already pinned in the Bottom Dock
     * so Phone, Messages, Adhan, and Camera are never duplicated above the dock!
     */
    private fun renderDesktopAppsGrid() {
        val cols = IslamicHomeData.gridColumns(this)
        val showLbl = IslamicHomeData.showLabels(this)
        val dockPkgs = IslamicHomeData.dockPackages(this).toSet()
        val pinnedPkgs = IslamicHomeData.pinnedDesktopPackages(this)

        desktopAppsGrid.removeAllViews()
        desktopAppsGrid.columnCount = cols

        val pinnedApps = allApps.filter { app ->
            app.pkg !in dockPkgs && (app.pkg in pinnedPkgs || app.isSahl)
        }
        // Cap at 2 rows (e.g. 8 apps in 4-col mode, or 10 apps in 5-col mode) so it NEVER overflows or cuts off
        for (app in pinnedApps.take(cols * 2)) {
            desktopAppsGrid.addView(buildAppCell(app, cols, showLbl))
        }
    }

    private fun filterDrawerApps(q: String) {
        val cols = IslamicHomeData.gridColumns(this)
        drawerAppsGrid.removeAllViews()
        drawerAppsGrid.columnCount = cols
        val hidden = IslamicHomeData.hiddenPackages(this)
        val nq = Norm.text(q.trim())
        val shown = allApps.filter { app ->
            (showHiddenInDrawer || app.pkg !in hidden) &&
                (nq.isEmpty() || app.normKey.contains(nq) || app.pkg.contains(nq))
        }

        drawerSectionBar.removeAllViews()
        val sections = shown.map { it.section }.distinct()
        for (sec in sections) {
            val chip = text(sec, 11f, 0xFFF5C84C.toInt(), true).apply {
                background = round(0x440B5D4B, 10f, 0x66D4AF37)
                setPadding(px(9), px(3), px(9), px(3))
                setOnClickListener {
                    drawerSearchInput?.setText(sec)
                }
            }.press(10f)
            drawerSectionBar.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(5) })
        }

        for (app in shown.take(120)) {
            drawerAppsGrid.addView(buildAppCell(app, cols, true))
        }
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
            openDrawer()
            drawerSearchInput?.requestFocus()
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
        val cols = IslamicHomeData.gridColumns(this)
        val showLbl = IslamicHomeData.showLabels(this)
        val frame3p = IslamicHomeData.frameThirdParty(this)
        val dtAct = IslamicHomeData.doubleTapAction(this)
        val hiddenCount = IslamicHomeData.hiddenPackages(this).size
        val badgesOn = BadgeListenerService.isEnabled(this)

        sheet(
            getString(R.string.settings_title),
            listOf(
                Triple(R.drawable.ic_wallpaper, getString(R.string.wallpapers_title)) {
                    startActivity(Intent(this, WallpaperActivity::class.java))
                },
                Triple(R.drawable.ic_add, getString(R.string.add_widget)) {
                    pickNewAppWidget()
                },
                Triple(R.drawable.ic_home, getString(R.string.set_default_home)) {
                    askDefaultHomeRole()
                },
                Triple(R.drawable.ic_apps, "${getString(R.string.grid_cols_label)}: ${cols}×2") {
                    IslamicHomeData.setGridColumns(this, if (cols == 4) 5 else 4)
                    renderDesktopAppsGrid()
                },
                Triple(R.drawable.ic_note, "${getString(R.string.show_labels_label)}: ${if (showLbl) "✓" else "✕"}") {
                    IslamicHomeData.setShowLabels(this, !showLbl)
                    renderDesktopAppsGrid()
                },
                Triple(R.drawable.ic_shield, "${getString(R.string.frame_3p_label)}: ${if (frame3p) "✓" else "✕"}") {
                    IslamicHomeData.setFrameThirdParty(this, !frame3p)
                    renderDesktopAppsGrid()
                    renderBottomDock()
                },
                Triple(R.drawable.ic_touch, "${getString(R.string.double_tap_label)}: $dtAct") {
                    val next = when (dtAct) { "search" -> "wallpapers"; "wallpapers" -> "dim"; else -> "search" }
                    IslamicHomeData.setDoubleTapAction(this, next)
                },
                Triple(
                    R.drawable.ic_star,
                    if (badgesOn) getString(R.string.badges_enabled) else getString(R.string.enable_badges)
                ) {
                    try {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    } catch (_: Exception) {}
                },
                Triple(R.drawable.ic_freeze, getString(R.string.show_hidden_apps, hiddenCount)) {
                    showHiddenInDrawer = !showHiddenInDrawer
                    openDrawer()
                },
                Triple(R.drawable.ic_save, getString(R.string.backup_export)) {
                    val json = IslamicHomeData.exportBackupJson(this)
                    getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("sahl_home_backup", json))
                    Toast.makeText(this, R.string.backup_saved, Toast.LENGTH_SHORT).show()
                },
                Triple(R.drawable.ic_history, getString(R.string.backup_import)) {
                    val clip = getSystemService(ClipboardManager::class.java)
                        ?.primaryClip?.getItemAt(0)?.text?.toString()
                    if (!clip.isNullOrBlank() && IslamicHomeData.importBackupJson(this, clip)) {
                        Toast.makeText(this, R.string.backup_restored, Toast.LENGTH_SHORT).show()
                        recreate()
                    }
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
