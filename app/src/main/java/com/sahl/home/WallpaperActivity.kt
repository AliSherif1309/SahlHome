package com.sahl.home

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Sahl Wallpapers Studio:
 * Live preview of all 4 Sahl procedural wallpapers, 1-tap apply to Home/Lock/Both,
 * 4K (2160x3840) PNG export to Gallery, and activation for the Sahl Prayer Sky Live Wallpaper.
 */
class WallpaperActivity : SahlActivity() {
    private var selectedStyle = WallpaperEngine.STYLE_GIRIH
    private lateinit var previewView: WallpaperPreviewView
    private val styleButtons = HashMap<String, LinearLayout>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedStyle = WallpaperEngine.currentStyle(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(col(R.color.bg))
        }
        root.addView(header(getString(R.string.wallpapers_title), true))

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(6), px(16), px(32))
        }
        col.addView(text(getString(R.string.wallpapers_sub), 14f, col(R.color.sub)).apply {
            setPadding(px(4), 0, px(4), px(12))
        })

        // Interactive phone aspect-ratio live canvas preview
        val previewFrame = FrameLayout(this).apply {
            background = round(col(R.color.card), 26f, col(R.color.gold))
            setPadding(px(4), px(4), px(4), px(4))
            clipToOutline = true
        }
        previewView = WallpaperPreviewView(this).apply {
            style = selectedStyle
        }
        previewFrame.addView(previewView, FrameLayout.LayoutParams(-1, px(340)))
        col.addView(previewFrame, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(14) })

        // Style selector cards
        for (st in WallpaperEngine.styles) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(16), px(12), px(16), px(12))
                setOnClickListener {
                    selectedStyle = st.id
                    previewView.style = st.id
                    previewView.invalidate()
                    updateSelectionBorders()
                }
            }.press(18f)
            item.addView(text(getString(st.titleRes), 17f, col(R.color.text), true))
            item.addView(text(getString(st.descRes), 13f, col(R.color.sub)).apply { setPadding(0, px(2), 0, 0) })
            styleButtons[st.id] = item
            col.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(10) })
        }
        updateSelectionBorders()

        // Action buttons: Apply Home, Lock, Both, or Save 4K PNG
        val actionsCard = card()
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(actionButton(getString(R.string.apply_home), true) {
            applyNow(WallpaperManager.FLAG_SYSTEM)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(6) })
        row1.addView(actionButton(getString(R.string.apply_lock), false) {
            applyNow(WallpaperManager.FLAG_LOCK)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(6) })
        actionsCard.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, px(10), 0, 0)
        }
        row2.addView(actionButton(getString(R.string.apply_both), true) {
            applyNow(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = px(6) })
        row2.addView(actionButton(getString(R.string.save_4k), false) {
            save4KNow()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(6) })
        actionsCard.addView(row2)
        col.addView(actionsCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(4); bottomMargin = px(14) })

        // Dynamic Live Wallpaper Card
        val liveCard = card()
        liveCard.addView(text(getString(R.string.live_wp_title), 17f, col(R.color.emerald), true))
        liveCard.addView(text(getString(R.string.live_wp_desc), 13f, col(R.color.sub)).apply {
            setPadding(0, px(4), 0, px(10))
        })
        liveCard.addView(actionButton(getString(R.string.enable_live_wp), true) {
            try {
                startActivity(
                    Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                        .putExtra(
                            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            ComponentName(this, SahlLiveWallpaperService::class.java)
                        )
                )
            } catch (_: Exception) {
                try { startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) } catch (_: Exception) {}
            }
        })
        col.addView(liveCard, LinearLayout.LayoutParams(-1, -2))

        root.addView(ScrollView(this).apply { addView(col) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun updateSelectionBorders() {
        for ((id, view) in styleButtons) {
            val active = id == selectedStyle
            view.background = round(
                col(if (active) R.color.tonal else R.color.card),
                18f,
                if (active) col(R.color.gold) else col(R.color.line)
            )
        }
    }

    private fun actionButton(label: String, primary: Boolean, onTap: () -> Unit): TextView =
        text(label, 14f, if (primary) 0xFFFFFFFF.toInt() else col(R.color.emerald), true).apply {
            gravity = Gravity.CENTER
            setPadding(px(12), px(12), px(12), px(12))
            background = round(if (primary) col(R.color.emerald) else col(R.color.tonal), 16f)
            setOnClickListener { onTap() }
        }.press(16f)

    private fun applyNow(which: Int) {
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
}

class WallpaperPreviewView(c: Context) : View(c) {
    var style: String = WallpaperEngine.STYLE_GIRIH
    var showLockClock: Boolean = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        WallpaperEngine.draw(canvas, w, h, style)
        if (showLockClock) {
            val light = WallpaperEngine.isLightStyle(style)
            val ink = if (light) 0xFF1B2A26.toInt() else 0xFFF5F2E9.toInt()
            val gold = if (light) 0xFF0B5D4B.toInt() else 0xFFF5C84C.toInt()
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = android.graphics.Paint.Align.CENTER
                color = ink
                textSize = h * 0.19f
                typeface = try {
                    android.graphics.Typeface.create(resources.getFont(R.font.thmanyah), android.graphics.Typeface.BOLD)
                } catch (_: Exception) {
                    android.graphics.Typeface.DEFAULT_BOLD
                }
            }
            val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            canvas.drawText(now, w * 0.5f, h * 0.24f, p)
            p.color = gold
            p.textSize = h * 0.055f
            val hijri = SahlHeliBoard.todayHijriString(context)
            canvas.drawText("۞  $hijri", w * 0.5f, h * 0.305f, p)
        }
    }
}
