package com.sahl.home

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.media.RingtoneManager
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Sahl Theme, Widgets & 4K Wallpaper Studio v1.8:
 *  1. Sahl 4K Procedural Wallpaper Engine + Live Prayer Sky Wallpaper
 *  2. Sahl Islamic Home Screen Widgets (with live toggle chips for every widget element!):
 *     - Sahl Islamic Clock (4x2): Live Digital Clock + optional Hijri, Gregorian, Next Prayer
 *     - Sahl Islamic Sanctuary (4x2): Daily Quranic Ayah + optional Hijri, Surah name, Translation, Adhkar
 *     - Sahl Union Hub (4x2): Next Prayer countdown + optional Live Clock, Hijri, Quick Action Buttons
 *     - Sahl Apps Dock Bar (4x1): Compact emerald & gold dock bar
 *  3. Sahl Floating Folders (No Corner Badge — with optional folder name label)
 *  4. Sahl Emerald & Gold Icon Styler (Outer-edge gold border matching dock icons, 3 border styles, and toggle to include or hide the app name!)
 */
class MainActivity : SahlActivity() {
    data class AppItem(val label: String, val normKey: String, val pkg: String, val activity: String, val icon: Drawable)

    private var selectedStyle = WallpaperEngine.STYLE_GIRIH
    private lateinit var previewView: WallpaperPreviewView
    private val styleCards = HashMap<String, LinearLayout>()
    private lateinit var stylerGrid: GridLayout
    private lateinit var updateBar: TextView
    private var currentSearchQuery = ""
    private val installedApps = ArrayList<AppItem>()
    private val iconStyleButtons = HashMap<Int, TextView>()
    private val soundPlayButtons = HashMap<String, TextView>()
    private val soundChips = ArrayList<Triple<String, Int, TextView>>()
    private var restoreBox: LinearLayout? = null
    private val restoreChips = ArrayList<Pair<Int, TextView>>()
    private var pendingSoundAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedStyle = WallpaperEngine.currentStyle(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(col(R.color.bg))
        }

        root.addView(
            header(
                "۞  " + getString(R.string.app_name),
                false,
                listOf(R.drawable.ic_tune to { showSettingsSheet() })
            )
        )

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(4), px(16), px(36))
        }

        col.addView(text(getString(R.string.tagline), 14f, col(R.color.sub)).apply {
            setPadding(px(4), 0, px(4), px(12))
        })

        updateBar = text("", 13f, 0xFF072A22.toInt(), true).apply {
            visibility = View.GONE
            background = round(0xFFF5C84C.toInt(), 14f)
            setPadding(px(14), px(10), px(14), px(10))
            gravity = Gravity.CENTER
        }
        col.addView(updateBar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(12) })

        // ────────────────── SECTION 1: 4K WALLPAPER STUDIO & LIVE PRAYER SKY ──────────────────
        col.addView(sectionHeader(getString(R.string.wallpapers_title), getString(R.string.wallpapers_sub)))

        val previewFrame = FrameLayout(this).apply {
            background = round(col(R.color.card), 24f, col(R.color.gold))
            setPadding(px(4), px(4), px(4), px(4))
            clipToOutline = true
        }
        previewView = WallpaperPreviewView(this).apply { style = selectedStyle }
        previewFrame.addView(previewView, FrameLayout.LayoutParams(-1, px(240)))
        col.addView(previewFrame, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) })

        val wpGrid = GridLayout(this).apply { columnCount = 2 }
        val wpCellW = (resources.displayMetrics.widthPixels - px(44)) / 2
        for (st in WallpaperEngine.styles) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(12), px(10), px(12), px(10))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = wpCellW
                    setMargins(px(3), px(3), px(3), px(3))
                }
                setOnClickListener {
                    selectedStyle = st.id
                    previewView.style = st.id
                    previewView.invalidate()
                    updateWallpaperSelectionBorders()
                }
            }.press(16f)
            item.addView(text(getString(st.titleRes), 15f, col(R.color.text), true))
            item.addView(text(getString(st.descRes), 11.5f, col(R.color.sub)).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, px(2), 0, 0)
            })
            styleCards[st.id] = item
            wpGrid.addView(item)
        }
        updateWallpaperSelectionBorders()
        col.addView(wpGrid, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) })

        val wpActionsCard = card()
        val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r1.addView(pillButton(getString(R.string.apply_home), true) {
            applyWallpaperNow(WallpaperManager.FLAG_SYSTEM)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(5) })
        r1.addView(pillButton(getString(R.string.apply_lock), false) {
            applyWallpaperNow(WallpaperManager.FLAG_LOCK)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(5) })
        wpActionsCard.addView(r1)

        val r2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, px(8), 0, px(8))
        }
        r2.addView(pillButton(getString(R.string.apply_both), true) {
            applyWallpaperNow(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(5) })
        r2.addView(pillButton(getString(R.string.save_4k), false) {
            save4KNow()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(5) })
        wpActionsCard.addView(r2)

        wpActionsCard.addView(pillButton("🌙  " + getString(R.string.enable_live_wp), false) {
            try {
                startActivity(
                    Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this, SahlLiveWallpaperService::class.java)
                    )
                )
            } catch (_: Exception) {
                try { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) } catch (_: Exception) {}
            }
        })
        col.addView(wpActionsCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(18) })

        // ────────────────── SECTION 2: SAHL ISLAMIC HOME SCREEN WIDGETS (WITH ELEMENT TOGGLES!) ──────────────────
        col.addView(sectionHeader(getString(R.string.sec_widgets), getString(R.string.sec_widgets_sub)))

        val ar = resources.configuration.locales[0].language == "ar"

        // Widget 0: Sahl Analog Clock (2x2)
        col.addView(
            buildWidgetGalleryCard(
                "🕰️  " + getString(R.string.w_aclock_title),
                getString(R.string.w_aclock_desc),
                AnalogClockWidget::class.java,
                listOf(
                    Triple("w_aclock_digital", getString(R.string.opt_clock) + " (24h)", true),
                    Triple("w_aclock_hijri", getString(R.string.opt_hijri), true)
                )
            ),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) }
        )

        // Widget 1: Sahl Islamic Clock (4x2)
        col.addView(
            buildWidgetGalleryCard(
                "🕰️  " + getString(R.string.w_clock_title),
                getString(R.string.w_clock_desc),
                ClockWidget::class.java,
                listOf(
                    Triple("w_clock_hijri", getString(R.string.opt_hijri), true),
                    Triple("w_clock_greg", getString(R.string.opt_greg), true),
                    Triple("w_clock_prayer", getString(R.string.opt_prayer), true)
                )
            ),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) }
        )

        // Widget 2: Sahl Islamic Sanctuary (4x2)
        col.addView(
            buildWidgetGalleryCard(
                "۞  " + getString(R.string.w_sanctuary_title),
                getString(R.string.w_sanctuary_desc),
                SanctuaryWidget::class.java,
                listOf(
                    Triple("w_sanc_hijri", getString(R.string.opt_hijri), true),
                    Triple("w_sanc_ref", getString(R.string.opt_ref), true),
                    Triple("w_sanc_trans", getString(R.string.opt_trans), !ar),
                    Triple("w_sanc_dhikr", getString(R.string.opt_dhikr), true)
                )
            ),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) }
        )

        // Widget 3: Sahl Union Hub (4x2)
        col.addView(
            buildWidgetGalleryCard(
                "🕌  " + getString(R.string.w_hub_title),
                getString(R.string.w_hub_desc),
                UnionHubWidget::class.java,
                listOf(
                    Triple("w_hub_clock", getString(R.string.opt_clock), true),
                    Triple("w_hub_hijri", getString(R.string.opt_hijri), true),
                    Triple("w_hub_prayer", getString(R.string.opt_prayer), true),
                    Triple("w_hub_buttons", getString(R.string.opt_buttons), true)
                )
            ),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) }
        )

        // Widget 4: Sahl Apps Dock Bar (4x1)
        col.addView(
            buildWidgetGalleryCard(
                "📞  " + getString(R.string.w_bar_title),
                getString(R.string.w_bar_desc),
                SahlBarWidget::class.java,
                emptyList()
            ),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) }
        )

        // Widget 4b: Sahl Ambient Glance Pill (Home & Lock Screen 4x1)
        col.addView(
            buildWidgetGalleryCard(
                "🌙  " + getString(R.string.w_amb_title),
                getString(R.string.w_amb_desc),
                AmbientPillWidget::class.java,
                listOf(
                    Triple("w_amb_prayer", getString(R.string.opt_prayer), true),
                    Triple("w_amb_dhikr", getString(R.string.opt_dhikr), true),
                    Triple("w_amb_hijri", getString(R.string.opt_hijri), true)
                )
            ),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(18) }
        )

        // ────────────────── SECTION 3: SAHL FLOATING FOLDERS FOR YOUR HOME SCREEN ──────────────────
        col.addView(sectionHeader(getString(R.string.sec_folders), getString(R.string.sec_folders_sub)))
        val foldersCard = card()

        foldersCard.addView(
            buildFolderPinRow(
                "all_sahl",
                if (ar) "۞   تطبيقات سهل" else "۞  Sahl Apps"
            )
        )

        for (f in IslamicHomeData.folders(this)) {
            foldersCard.addView(View(this).apply { setBackgroundColor(col(R.color.line)) },
                LinearLayout.LayoutParams(-1, 1).apply { topMargin = px(8); bottomMargin = px(8) })
            foldersCard.addView(buildFolderPinRow(f.id, "۞  " + (if (ar) f.nameAr else f.nameEn)))
        }
        col.addView(foldersCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(18) })

        // ────────────────── SECTION 5: SAHL SYSTEM SOUND PACK & RINGTONE MANAGER ──────────────────
        col.addView(sectionHeader(getString(R.string.sec_sounds), getString(R.string.sec_sounds_sub)))
        col.addView(buildSoundsSection(), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(18) })

        // ────────────────── SECTION 4: SAHL EMERALD & GOLD ICON STYLER ──────────────────
        col.addView(sectionHeader(getString(R.string.sec_icon_styler), getString(R.string.sec_icon_styler_sub)))
        val stylerCard = card()

        // Toggle 1: Include App Name under icon (Switch)
        val labelToggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(4), px(2), px(4), px(10))
        }
        labelToggleRow.addView(text(getString(R.string.icon_label_toggle), 14.5f, col(R.color.text), true),
            LinearLayout.LayoutParams(0, -2, 1f))
        val labelSwitch = Switch(this).apply {
            isChecked = SahlThemeShortcuts.defaultShowLabel(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                SahlThemeShortcuts.setDefaultShowLabel(this@MainActivity, checked)
                // Also update any already-pinned 1x1 icon widgets if desired
                val p = Sahl.prefs(this@MainActivity)
                val ed = p.edit()
                for (k in p.all.keys) {
                    if (k.startsWith("ti_") && k.endsWith("_show_label")) ed.putBoolean(k, checked)
                }
                ed.apply()
                SahlWidgets.updateAll(this@MainActivity)
                renderStylerApps(currentSearchQuery)
            }
        }
        labelToggleRow.addView(labelSwitch)
        stylerCard.addView(labelToggleRow)

        // Toggle 1b: Dockable Shortcut mode (so user can place in Bottom Dock if desired)
        val dockableRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(4), 0, px(4), px(10))
        }
        dockableRow.addView(text(getString(R.string.icon_dockable_toggle), 13.5f, col(R.color.sub), true),
            LinearLayout.LayoutParams(0, -2, 1f))
        val dockableSwitch = Switch(this).apply {
            isChecked = SahlThemeShortcuts.defaultDockableMode(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                SahlThemeShortcuts.setDefaultDockableMode(this@MainActivity, checked)
            }
        }
        dockableRow.addView(dockableSwitch)
        stylerCard.addView(dockableRow)

        // Toggle 1c: Live Notification Badges (for WhatsApp & 1x1 themed app widgets)
        val badgeBtn = pillButton(
            if (BadgeListenerService.isEnabled(this)) getString(R.string.badges_enabled) else getString(R.string.enable_badges),
            !BadgeListenerService.isEnabled(this)
        ) {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (_: Exception) {}
        }
        stylerCard.addView(badgeBtn, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) })

        // Toggle 2: 3 Icon Border Styles (Full Gold Border / Gold Glyph on Emerald / Emerald Seal)
        val styleModesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, px(12))
        }
        val modeOptions = listOf(
            SahlThemeShortcuts.ICON_STYLE_FILL to getString(R.string.style_fill),
            SahlThemeShortcuts.ICON_STYLE_GOLD_TINT to getString(R.string.style_tint),
            SahlThemeShortcuts.ICON_STYLE_SEAL to getString(R.string.style_seal)
        )
        for ((modeId, modeTitle) in modeOptions) {
            val btn = text(modeTitle, 11.5f, col(R.color.text), true).apply {
                gravity = Gravity.CENTER
                setPadding(px(8), px(8), px(8), px(8))
                setOnClickListener {
                    SahlThemeShortcuts.setDefaultIconStyle(this@MainActivity, modeId)
                    val p = Sahl.prefs(this@MainActivity)
                    val ed = p.edit()
                    for (k in p.all.keys) {
                        if (k.startsWith("ti_") && k.endsWith("_style_mode")) ed.putInt(k, modeId)
                    }
                    ed.apply()
                    SahlWidgets.updateAll(this@MainActivity)
                    updateIconStyleButtons()
                    renderStylerApps(currentSearchQuery)
                }
            }.press(12f)
            iconStyleButtons[modeId] = btn
            styleModesRow.addView(btn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(4) })
        }
        updateIconStyleButtons()
        stylerCard.addView(styleModesRow)

        val (sBox, _) = searchBox(getString(R.string.search_apps)) { q ->
            currentSearchQuery = q
            renderStylerApps(q)
        }
        stylerCard.addView(sBox, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) })
        stylerGrid = GridLayout(this).apply { columnCount = 4 }
        stylerCard.addView(stylerGrid, LinearLayout.LayoutParams(-1, -2))
        col.addView(stylerCard, LinearLayout.LayoutParams(-1, -2))

        root.addView(ScrollView(this).apply { addView(col) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        loadAppsForStyler()
        BadgeListenerService.syncIfConnected(this)
        SahlWidgets.updateAll(this)
        Updater.scheduleDaily(this)
        checkUpdate(false)
    }

    private fun updateIconStyleButtons() {
        val cur = SahlThemeShortcuts.defaultIconStyle(this)
        for ((id, btn) in iconStyleButtons) {
            val active = id == cur
            btn.setTextColor(if (active) 0xFF072A22.toInt() else col(R.color.text))
            btn.background = round(
                if (active) 0xFFF5C84C.toInt() else col(R.color.tonal),
                12f,
                if (active) col(R.color.gold) else col(R.color.line)
            )
        }
    }

    private fun sectionHeader(title: String, subtitle: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(4), px(4), px(4), px(10))
        addView(text(title, 18f, col(R.color.emerald), true))
        addView(text(subtitle, 12.5f, col(R.color.sub)).apply { setPadding(0, px(2), 0, 0) })
    }

    private fun buildWidgetGalleryCard(
        title: String,
        desc: String,
        cls: Class<*>,
        toggles: List<Triple<String, String, Boolean>>
    ): View {
        val c = card()
        c.addView(text(title, 16f, col(R.color.text), true))
        c.addView(text(desc, 12.5f, col(R.color.sub)).apply { setPadding(0, px(4), 0, px(8)) })

        if (toggles.isNotEmpty()) {
            val chipsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, px(10))
            }
            for ((prefKey, labelText, defVal) in toggles) {
                val chip = text("", 11.5f, col(R.color.text), true).apply {
                    setPadding(px(10), px(6), px(10), px(6))
                }.press(12f)

                fun refreshChip() {
                    val on = SahlWidgets.opt(this, prefKey, defVal)
                    chip.text = (if (on) "✓  " else "✕  ") + labelText
                    chip.setTextColor(if (on) 0xFF072A22.toInt() else col(R.color.sub))
                    chip.background = round(
                        if (on) 0xFFF5C84C.toInt() else col(R.color.tonal),
                        12f,
                        if (on) col(R.color.gold) else col(R.color.line)
                    )
                }
                refreshChip()
                chip.setOnClickListener {
                    val next = !SahlWidgets.opt(this, prefKey, defVal)
                    SahlWidgets.setOpt(this, prefKey, next)
                    refreshChip()
                }
                chipsRow.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(6) })
            }
            c.addView(HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(chipsRow)
            })
        }

        c.addView(pillButton(getString(R.string.pin_widget_btn), true) {
            if (!SahlThemeShortcuts.requestPinWidget(this, cls)) {
                Toast.makeText(this, R.string.widget_pin_guide, Toast.LENGTH_LONG).show()
            }
        })
        return c
    }

    private fun buildFolderPinRow(folderId: String, title: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(text(title, 15f, col(R.color.text), true), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(pillButton(getString(R.string.preview_folder_btn), false) {
            startActivity(Intent(this, SahlPopupActivity::class.java).putExtra("folder", folderId))
        }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(6) })
        row.addView(pillButton(getString(R.string.pin_folder_btn), true) {
            val cleanTitle = title.removePrefix("۞  ").trim()
            if (SahlThemeShortcuts.pinFolderIconNoBadge(this, folderId, cleanTitle)) {
                Toast.makeText(this, R.string.shortcut_pinned, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.widget_pin_guide, Toast.LENGTH_LONG).show()
            }
        })
        return row
    }

    private fun loadAppsForStyler() {
        Thread {
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(mainIntent, 0).mapNotNull { ri ->
                val ai = ri.activityInfo ?: return@mapNotNull null
                if (ai.packageName == packageName) return@mapNotNull null
                val lbl = ri.loadLabel(pm).toString()
                AppItem(lbl, Norm.text(lbl), ai.packageName, ai.name, ri.loadIcon(pm))
            }.sortedBy { it.normKey }
            runOnUiThread {
                installedApps.clear()
                installedApps.addAll(list)
                renderStylerApps(currentSearchQuery)
            }
        }.start()
    }

    private fun renderStylerApps(q: String) {
        stylerGrid.removeAllViews()
        val nq = Norm.text(q.trim())
        val shown = if (nq.isEmpty()) installedApps else installedApps.filter {
            it.normKey.contains(nq) || it.pkg.contains(nq)
        }
        val cellW = (resources.displayMetrics.widthPixels - px(68)) / 4
        val curStyle = SahlThemeShortcuts.defaultIconStyle(this)
        val showLbl = SahlThemeShortcuts.defaultShowLabel(this)

        for (app in shown.take(48)) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(px(4), px(8), px(4), px(8))
                layoutParams = GridLayout.LayoutParams().apply { width = cellW }

                // Live preview using the exact same Bitmap renderer as the 1x1 widget!
                val bmp = SahlThemeShortcuts.createSahlSealIconBitmap(app.icon, null, curStyle)
                addView(ImageView(this@MainActivity).apply {
                    setImageBitmap(bmp)
                }, LinearLayout.LayoutParams(px(54), px(54)))

                if (showLbl) {
                    addView(text(app.label, 11f, col(R.color.text), true).apply {
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        gravity = Gravity.CENTER
                        setPadding(0, px(4), 0, 0)
                    })
                }
                setOnClickListener {
                    if (SahlThemeShortcuts.pinThemedAppIconNoBadge(this@MainActivity, app.pkg, app.activity, app.label)) {
                        Toast.makeText(this@MainActivity, R.string.shortcut_pinned, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, R.string.widget_pin_guide, Toast.LENGTH_LONG).show()
                    }
                }
            }.press(14f)
            stylerGrid.addView(item)
        }
    }

    private fun updateWallpaperSelectionBorders() {
        for ((id, view) in styleCards) {
            val active = id == selectedStyle
            view.background = round(
                col(if (active) R.color.tonal else R.color.card),
                16f,
                if (active) col(R.color.gold) else col(R.color.line)
            )
        }
    }

    private fun pillButton(label: String, primary: Boolean, onTap: () -> Unit): TextView =
        text(label, 12.5f, if (primary) 0xFFFFFFFF.toInt() else col(R.color.emerald), true).apply {
            gravity = Gravity.CENTER
            setPadding(px(12), px(10), px(12), px(10))
            background = round(if (primary) col(R.color.emerald) else col(R.color.tonal), 14f)
            setOnClickListener { onTap() }
        }.press(14f)

    private fun applyWallpaperNow(which: Int) {
        val style = selectedStyle
        Thread {
            val ok = WallpaperEngine.applyWallpaper(this, style, which)
            runOnUiThread {
                if (ok) Toast.makeText(this, R.string.wp_applied, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun save4KNow() {
        val style = selectedStyle
        Thread {
            val ok = WallpaperEngine.save4KToGallery(this, style)
            runOnUiThread {
                if (ok) Toast.makeText(this, R.string.wp_saved, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun showSettingsSheet() {
        val p = Sahl.prefs(this)
        val curLang = p.getString("lang", "system") ?: "system"
        val curTheme = p.getString("theme", "system") ?: "system"
        sheet(
            getString(R.string.settings_title),
            listOf(
                Triple(R.drawable.ic_tune, "${getString(R.string.lang_label)}: $curLang") {
                    val next = when (curLang) { "en" -> "ar"; "ar" -> "system"; else -> "en" }
                    p.edit().putString("lang", next).apply()
                    SahlWidgets.updateAll(this)
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

    private fun buildSoundsSection(): View {
        val c = card()
        c.addView(pillButton("🔔  " + getString(R.string.apply_sound_pack), true) {
            runWithWriteSettings {
                if (SahlSounds.applyFullPack(this)) {
                    refreshSoundUi()
                    Toast.makeText(this, R.string.sound_pack_applied, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, R.string.sound_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(8) })

        val exportRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        exportRow.addView(pillButton("💾  " + getString(R.string.export_sound_pack), false) {
            val n = SahlSounds.exportAllToDevice(this)
            Toast.makeText(this, if (n > 0) getString(R.string.sound_pack_exported) else getString(R.string.sound_failed), Toast.LENGTH_LONG).show()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(5) })
        exportRow.addView(pillButton("📤  " + getString(R.string.share_sound_pack), false) {
            if (!SahlSounds.sharePackZip(this)) {
                Toast.makeText(this, R.string.sound_failed, Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(5) })
        c.addView(exportRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) })

        val typeOptions = listOf(
            RingtoneManager.TYPE_RINGTONE to R.string.set_ringtone,
            RingtoneManager.TYPE_NOTIFICATION to R.string.set_notification,
            RingtoneManager.TYPE_ALARM to R.string.set_alarm
        )

        val rBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(col(R.color.tonal), 14f, col(R.color.line))
            setPadding(px(12), px(10), px(12), px(10))
        }
        rBox.addView(text(getString(R.string.restore_prev_title), 12f, col(R.color.sub), true).apply {
            setPadding(0, 0, 0, px(6))
        })
        val rRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((rType, labelRes) in typeOptions) {
            val chip = text("↩  " + getString(labelRes), 11.5f, col(R.color.text), true).apply {
                gravity = Gravity.CENTER
                background = round(col(R.color.card), 10f, col(R.color.gold))
                setPadding(px(10), px(6), px(10), px(6))
                setOnClickListener {
                    runWithWriteSettings {
                        if (SahlSounds.restorePrevious(this@MainActivity, rType)) {
                            refreshSoundUi()
                            Toast.makeText(this@MainActivity, R.string.sound_restored, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, R.string.sound_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.press(10f)
            restoreChips.add(rType to chip)
            rRow.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(6) })
        }
        rBox.addView(rRow)
        restoreBox = rBox
        c.addView(rBox, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(12) })

        SahlSounds.items.forEachIndexed { idx, item ->
            if (idx > 0) {
                c.addView(
                    View(this).apply { setBackgroundColor(col(R.color.line)) },
                    LinearLayout.LayoutParams(-1, 1).apply { topMargin = px(10); bottomMargin = px(10) }
                )
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val playBtn = text("▶", 14f, 0xFF072A22.toInt(), true).apply {
                gravity = Gravity.CENTER
                background = round(0xFFF5C84C.toInt(), 18f, col(R.color.gold))
                setOnClickListener {
                    SahlSounds.playPreview(this@MainActivity, item) {
                        runOnUiThread { refreshSoundUi() }
                    }
                    refreshSoundUi()
                }
            }.press(18f)
            soundPlayButtons[item.id] = playBtn
            row.addView(playBtn, LinearLayout.LayoutParams(px(36), px(36)).apply { marginEnd = px(10) })

            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            info.addView(text(getString(item.titleRes), 14.5f, col(R.color.text), true))
            info.addView(text(getString(item.descRes), 11.5f, col(R.color.sub)).apply {
                setPadding(0, px(2), 0, 0)
            })
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            c.addView(row)

            val chips = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(px(46), px(8), 0, 0)
            }
            for ((rType, labelRes) in typeOptions) {
                val chip = text(getString(labelRes), 11f, col(R.color.text), true).apply {
                    setPadding(px(10), px(5), px(10), px(5))
                    setOnClickListener {
                        runWithWriteSettings {
                            if (SahlSounds.installAndSetDefault(this@MainActivity, item, rType)) {
                                refreshSoundUi()
                                Toast.makeText(this@MainActivity, R.string.sound_applied, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@MainActivity, R.string.sound_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }.press(10f)
                soundChips.add(Triple(item.id, rType, chip))
                chips.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(6) })
            }
            c.addView(chips)
        }

        refreshSoundUi()
        return c
    }

    private fun refreshSoundUi() {
        val playing = SahlSounds.playingId
        for ((id, btn) in soundPlayButtons) {
            val isPlaying = id == playing
            btn.text = if (isPlaying) "■" else "▶"
            btn.setTextColor(if (isPlaying) 0xFFFFFFFF.toInt() else 0xFF072A22.toInt())
            btn.background = round(
                if (isPlaying) col(R.color.emerald) else 0xFFF5C84C.toInt(),
                18f,
                col(R.color.gold)
            )
        }

        val activeRing = SahlSounds.activeId(this, RingtoneManager.TYPE_RINGTONE)
        val activeNotif = SahlSounds.activeId(this, RingtoneManager.TYPE_NOTIFICATION)
        val activeAlarm = SahlSounds.activeId(this, RingtoneManager.TYPE_ALARM)

        for ((id, rType, chip) in soundChips) {
            val active = when (rType) {
                RingtoneManager.TYPE_RINGTONE -> id == activeRing
                RingtoneManager.TYPE_ALARM -> id == activeAlarm
                else -> id == activeNotif
            }
            val labelRes = when (rType) {
                RingtoneManager.TYPE_RINGTONE -> R.string.set_ringtone
                RingtoneManager.TYPE_ALARM -> R.string.set_alarm
                else -> R.string.set_notification
            }
            chip.text = (if (active) "✓  " else "") + getString(labelRes)
            chip.setTextColor(if (active) 0xFF072A22.toInt() else col(R.color.text))
            chip.background = round(
                if (active) 0xFFF5C84C.toInt() else col(R.color.tonal),
                10f,
                if (active) col(R.color.gold) else col(R.color.line)
            )
        }

        var anyRestore = false
        for ((rType, chip) in restoreChips) {
            val hasPrev = SahlSounds.hasPrevious(this, rType)
            chip.visibility = if (hasPrev) View.VISIBLE else View.GONE
            if (hasPrev) anyRestore = true
        }
        restoreBox?.visibility = if (anyRestore) View.VISIBLE else View.GONE
    }

    private fun runWithWriteSettings(action: () -> Unit) {
        if (SahlSounds.canWriteSettings(this)) {
            action()
        } else {
            pendingSoundAction = action
            Toast.makeText(this, R.string.sound_perm_prompt, Toast.LENGTH_LONG).show()
            SahlSounds.openWriteSettings(this)
        }
    }

    override fun onResume() {
        super.onResume()
        val pending = pendingSoundAction
        if (pending != null && SahlSounds.canWriteSettings(this)) {
            pendingSoundAction = null
            pending()
        }
    }

    override fun onPause() {
        super.onPause()
        SahlSounds.stopPreview()
        refreshSoundUi()
    }

    override fun onDestroy() {
        SahlSounds.stopPreview()
        super.onDestroy()
    }
}
