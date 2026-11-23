package com.sahl.home

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Sahl Theme, Widgets & 4K Wallpaper Studio (for your phone's real Home Screen!):
 *  1. Sahl 4K Procedural Wallpaper Engine + Live Prayer Sky Wallpaper (1-tap apply to Home/Lock/Both or Save 4K PNG)
 *  2. Sahl Islamic Home Screen Widgets (1-tap Pin to your phone's real Home Screen):
 *     - Sahl Islamic Sanctuary (4x2): Hijri date, Daily Quranic Ayah & Morning/Evening Adhkar
 *     - Sahl Union Hub (4x2): Next Prayer countdown + Search, Qibla, Tools & Sahl Apps Popup
 *     - Sahl Apps Dock Bar (4x1): Compact emerald & gold dock bar for your real home screen
 *  3. Sahl Floating Folders (Pin an emerald & gold folder shortcut to your real home screen that opens a floating popup)
 *  4. Sahl Emerald & Gold Icon Styler (Pin a Sahl-sealed emerald & gold icon for ANY installed app onto your real home screen)
 */
class MainActivity : SahlActivity() {
    data class AppItem(val label: String, val normKey: String, val pkg: String, val activity: String, val icon: Drawable)

    private var selectedStyle = WallpaperEngine.STYLE_GIRIH
    private lateinit var previewView: WallpaperPreviewView
    private val styleCards = HashMap<String, LinearLayout>()
    private lateinit var stylerGrid: GridLayout
    private lateinit var updateBar: TextView
    private val installedApps = ArrayList<AppItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedStyle = WallpaperEngine.currentStyle(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(col(R.color.bg))
        }

        // Top Header with Settings button
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

        // Update Banner
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
        previewFrame.addView(previewView, FrameLayout.LayoutParams(-1, px(260)))
        col.addView(previewFrame, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) })

        // 2x2 Wallpaper Style Selector Grid
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

        // Apply Home / Lock / Both / Save 4K buttons + Live Prayer Sky button
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

        // ────────────────── SECTION 2: SAHL ISLAMIC HOME SCREEN WIDGETS ──────────────────
        col.addView(sectionHeader(getString(R.string.sec_widgets), getString(R.string.sec_widgets_sub)))

        col.addView(
            buildWidgetGalleryCard(
                "۞  " + getString(R.string.w_sanctuary_title),
                getString(R.string.w_sanctuary_desc),
                SanctuaryWidget::class.java
            ),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) }
        )
        col.addView(
            buildWidgetGalleryCard(
                "🕌  " + getString(R.string.w_hub_title),
                getString(R.string.w_hub_desc),
                UnionHubWidget::class.java
            ),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) }
        )
        col.addView(
            buildWidgetGalleryCard(
                "📞  " + getString(R.string.w_bar_title),
                getString(R.string.w_bar_desc),
                SahlBarWidget::class.java
            ),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(18) }
        )

        // ────────────────── SECTION 3: SAHL FLOATING FOLDERS FOR YOUR HOME SCREEN ──────────────────
        col.addView(sectionHeader(getString(R.string.sec_folders), getString(R.string.sec_folders_sub)))
        val foldersCard = card()
        val ar = resources.configuration.locales[0].language == "ar"

        // All Sahl Apps Master Popup Folder
        foldersCard.addView(
            buildFolderPinRow(
                "all_sahl",
                if (ar) "۞  كل تطبيقات سهل" else "۞  All Sahl Apps"
            )
        )

        for (f in IslamicHomeData.folders(this)) {
            foldersCard.addView(View(this).apply { setBackgroundColor(col(R.color.line)) },
                LinearLayout.LayoutParams(-1, 1).apply { topMargin = px(8); bottomMargin = px(8) })
            foldersCard.addView(buildFolderPinRow(f.id, "۞  " + (if (ar) f.nameAr else f.nameEn)))
        }
        col.addView(foldersCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(18) })

        // ────────────────── SECTION 4: SAHL EMERALD & GOLD ICON STYLER ──────────────────
        col.addView(sectionHeader(getString(R.string.sec_icon_styler), getString(R.string.sec_icon_styler_sub)))
        val stylerCard = card()
        val (sBox, _) = searchBox(getString(R.string.search_apps)) { q -> renderStylerApps(q) }
        stylerCard.addView(sBox, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) })
        stylerGrid = GridLayout(this).apply { columnCount = 4 }
        stylerCard.addView(stylerGrid, LinearLayout.LayoutParams(-1, -2))
        col.addView(stylerCard, LinearLayout.LayoutParams(-1, -2))

        root.addView(ScrollView(this).apply { addView(col) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        loadAppsForStyler()
        SahlWidgets.updateAll(this)
        Updater.scheduleDaily(this)
        checkUpdate(false)
    }

    private fun sectionHeader(title: String, subtitle: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(4), px(4), px(4), px(10))
        addView(text(title, 18f, col(R.color.emerald), true))
        addView(text(subtitle, 12.5f, col(R.color.sub)).apply { setPadding(0, px(2), 0, 0) })
    }

    private fun buildWidgetGalleryCard(title: String, desc: String, cls: Class<*>): View {
        val c = card()
        c.addView(text(title, 16f, col(R.color.text), true))
        c.addView(text(desc, 12.5f, col(R.color.sub)).apply { setPadding(0, px(4), 0, px(10)) })
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
            val cleanTitle = title.removePrefix("۞  ")
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
                renderStylerApps("")
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
        for (app in shown.take(48)) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(px(4), px(8), px(4), px(8))
                layoutParams = GridLayout.LayoutParams().apply { width = cellW }

                // Show preview of the app inside the Sahl Emerald & Gold Seal frame
                val sealPreview = FrameLayout(this@MainActivity).apply {
                    background = round(0xFF0B5D4B.toInt(), 16f, 0xFFD4AF37.toInt())
                    setPadding(px(7), px(7), px(7), px(7))
                    addView(ImageView(this@MainActivity).apply {
                        setImageDrawable(app.icon)
                    }, FrameLayout.LayoutParams(px(36), px(36), Gravity.CENTER))
                }
                addView(sealPreview, LinearLayout.LayoutParams(px(50), px(50)))
                addView(text(app.label, 11f, col(R.color.text), true).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    setPadding(0, px(4), 0, 0)
                })
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
}
