package com.sahl.home

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle

/**
 * Sahl Icon Styler & Badgeless 1×1 Home Screen Icon Pinning:
 *  - Draws the gold border right on the outer edge of the squircle (matching normal Sahl adaptive icons in the dock!)
 *  - Offers 3 Icon Styles:
 *      0 = "fill"  (Full Squircle Masked to the Gold Border — no small square inside a square!)
 *      1 = "seal"  (Emerald Background + Gold Border + Centered App Icon)
 *      2 = "tint"  (Emerald Background + Gold-Tinted Monochromatic App Glyph)
 *  - Option to include or hide the app name label underneath!
 */
object SahlThemeShortcuts {

    const val ICON_STYLE_FILL = 0
    const val ICON_STYLE_SEAL = 1
    const val ICON_STYLE_GOLD_TINT = 2

    fun defaultShowLabel(c: Context): Boolean = Sahl.prefs(c).getBoolean("icon_show_label", true)
    fun setDefaultShowLabel(c: Context, show: Boolean) = Sahl.prefs(c).edit().putBoolean("icon_show_label", show).apply()

    fun defaultIconStyle(c: Context): Int = Sahl.prefs(c).getInt("icon_style_mode", ICON_STYLE_FILL)
    fun setDefaultIconStyle(c: Context, mode: Int) = Sahl.prefs(c).edit().putInt("icon_style_mode", mode).apply()

    fun defaultDockableMode(c: Context): Boolean = Sahl.prefs(c).getBoolean("icon_dockable_mode", false)
    fun setDefaultDockableMode(c: Context, dockable: Boolean) = Sahl.prefs(c).edit().putBoolean("icon_dockable_mode", dockable).apply()

    /**
     * Renders a 192x192 Bitmap where the gold border is on the exact outer edge of the squircle
     * (matching the Sahl adaptive launcher icons in the dock!).
     */
    fun createSahlSealIconBitmap(
        inner: Drawable?,
        glyphText: String? = null,
        styleMode: Int = ICON_STYLE_FILL
    ): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val margin = 4f
        val radius = 44f
        val squircleRect = RectF(margin, margin, size - margin, size - margin)
        val clipPath = Path().apply {
            addRoundRect(squircleRect, radius, radius, Path.Direction.CW)
        }

        // 1. Deep Emerald Radial Background filling the entire squircle
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                size * 0.5f, size * 0.35f, size * 0.78f,
                0xFF136E56.toInt(), 0xFF07352A.toInt(), Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(squircleRect, radius, radius, bgPaint)

        // 2. Draw inner app icon or folder glyph
        if (inner != null) {
            when (styleMode) {
                ICON_STYLE_FILL -> {
                    // Clip the app icon directly to the outer squircle border so there's NO inner square box!
                    val save = canvas.save()
                    canvas.clipPath(clipPath)
                    if (inner is AdaptiveIconDrawable) {
                        // Draw adaptive icon layers expanded to fill the squircle cleanly
                        val expand = (size * 0.18f).toInt()
                        inner.background?.let { bg ->
                            bg.setBounds(-expand, -expand, size + expand, size + expand)
                            bg.draw(canvas)
                        }
                        inner.foreground?.let { fg ->
                            fg.setBounds(-expand, -expand, size + expand, size + expand)
                            fg.draw(canvas)
                        }
                    } else {
                        inner.setBounds(4, 4, size - 4, size - 4)
                        inner.draw(canvas)
                    }
                    canvas.restoreToCount(save)
                }
                ICON_STYLE_GOLD_TINT -> {
                    // Extract foreground if AdaptiveIconDrawable, or use full icon, tinted in warm Sahl Gold on Emerald
                    val layer = canvas.saveLayer(squircleRect, null)
                    if (inner is AdaptiveIconDrawable && inner.foreground != null) {
                        val fg = inner.foreground
                        val expand = (size * 0.12f).toInt()
                        fg.setBounds(-expand, -expand, size + expand, size + expand)
                        fg.draw(canvas)
                    } else {
                        val pad = 34
                        inner.setBounds(pad, pad, size - pad, size - pad)
                        inner.draw(canvas)
                    }
                    val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFF5C84C.toInt()
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                    }
                    canvas.drawRect(squircleRect, tintPaint)
                    canvas.restoreToCount(layer)
                }
                else -> {
                    // ICON_STYLE_SEAL: Emerald background + centered icon
                    val save = canvas.save()
                    val innerRect = RectF(30f, 30f, size - 30f, size - 30f)
                    val innerClip = Path().apply { addRoundRect(innerRect, 28f, 28f, Path.Direction.CW) }
                    canvas.clipPath(innerClip)
                    inner.setBounds(30, 30, size - 30, size - 30)
                    inner.draw(canvas)
                    canvas.restoreToCount(save)
                }
            }
        } else if (!glyphText.isNullOrEmpty()) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFF5C84C.toInt()
                textSize = 76f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            val y = size * 0.5f - (textPaint.descent() + textPaint.ascent()) * 0.5f
            canvas.drawText(glyphText, size * 0.5f, y, textPaint)
        }

        // 3. Signature Gold Border EXACTLY on the outer edge of the squircle (like normal Sahl dock icons!)
        val borderInset = 6.5f
        val borderRect = RectF(borderInset, borderInset, size - borderInset, size - borderInset)
        val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 6f
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                intArrayOf(0xFFF7D36A.toInt(), 0xFFD4AF37.toInt(), 0xFFB88922.toInt()),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(borderRect, 42f, 42f, goldPaint)

        // Top Gold Crown Dot on the outer gold border
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.FILL
            color = 0xFFF7D36A.toInt()
        }
        canvas.drawCircle(size * 0.5f, borderInset, 5.5f, dotPaint)

        return bmp
    }

    /** Saves a Pending configuration so the next added 1×1 ThemedIconWidget automatically adopts this app/folder. */
    fun savePendingIconConfig(
        c: Context,
        type: String,
        pkg: String,
        activity: String,
        label: String,
        folderId: String,
        showLabel: Boolean = defaultShowLabel(c),
        styleMode: Int = defaultIconStyle(c)
    ) {
        Sahl.prefs(c).edit()
            .putString("pending_icon_type", type)
            .putString("pending_icon_pkg", pkg)
            .putString("pending_icon_act", activity)
            .putString("pending_icon_label", label)
            .putString("pending_icon_folder", folderId)
            .putBoolean("pending_icon_show_label", showLabel)
            .putInt("pending_icon_style_mode", styleMode)
            .putLong("pending_icon_time", System.currentTimeMillis())
            .commit()
    }

    /** Binds a specific appWidgetId to the given app or folder and its display preferences. */
    fun bindWidgetId(
        c: Context,
        appWidgetId: Int,
        type: String,
        pkg: String,
        activity: String,
        label: String,
        folderId: String,
        showLabel: Boolean,
        styleMode: Int
    ) {
        Sahl.prefs(c).edit()
            .putString("ti_${appWidgetId}_type", type)
            .putString("ti_${appWidgetId}_pkg", pkg)
            .putString("ti_${appWidgetId}_act", activity)
            .putString("ti_${appWidgetId}_label", label)
            .putString("ti_${appWidgetId}_folder", folderId)
            .putBoolean("ti_${appWidgetId}_show_label", showLabel)
            .putInt("ti_${appWidgetId}_style_mode", styleMode)
            .commit()
    }

    fun pinThemedAppIconNoBadge(c: Context, pkg: String, activity: String, label: String): Boolean {
        if (defaultDockableMode(c)) {
            val sm = c.getSystemService(android.content.pm.ShortcutManager::class.java) ?: return false
            if (!sm.isRequestPinShortcutSupported) return false
            val appIcon = try { c.packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null }
            val bmp = createSahlSealIconBitmap(appIcon, if (appIcon == null) "۞" else null, defaultIconStyle(c))
            val launchIntent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(pkg, activity))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val info = android.content.pm.ShortcutInfo.Builder(c, "sahl_dock_${pkg}_${System.currentTimeMillis()}")
                .setShortLabel(if (defaultShowLabel(c)) label.take(18) else " ")
                .setLongLabel(label)
                .setIcon(android.graphics.drawable.Icon.createWithBitmap(bmp))
                .setIntent(launchIntent)
                .build()
            return sm.requestPinShortcut(info, null)
        }
        savePendingIconConfig(c, "app", pkg, activity, label, "", defaultShowLabel(c), defaultIconStyle(c))
        return try {
            val awm = AppWidgetManager.getInstance(c)
            if (awm.isRequestPinAppWidgetSupported) {
                awm.requestPinAppWidget(ComponentName(c, ThemedIconWidget::class.java), Bundle(), null)
            } else false
        } catch (_: Exception) {
            false
        }
    }

    fun pinFolderIconNoBadge(c: Context, folderId: String, title: String): Boolean {
        if (defaultDockableMode(c)) {
            val sm = c.getSystemService(android.content.pm.ShortcutManager::class.java) ?: return false
            if (!sm.isRequestPinShortcutSupported) return false
            val bmp = createSahlSealIconBitmap(null, "۞", defaultIconStyle(c))
            val popupIntent = Intent(c, SahlPopupActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("folder", folderId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val info = android.content.pm.ShortcutInfo.Builder(c, "sahl_dfolder_${folderId}_${System.currentTimeMillis()}")
                .setShortLabel(if (defaultShowLabel(c)) title.take(18) else " ")
                .setLongLabel(title)
                .setIcon(android.graphics.drawable.Icon.createWithBitmap(bmp))
                .setIntent(popupIntent)
                .build()
            return sm.requestPinShortcut(info, null)
        }
        savePendingIconConfig(c, "folder", "", "", title, folderId, defaultShowLabel(c), defaultIconStyle(c))
        return try {
            val awm = AppWidgetManager.getInstance(c)
            if (awm.isRequestPinAppWidgetSupported) {
                awm.requestPinAppWidget(ComponentName(c, ThemedIconWidget::class.java), Bundle(), null)
            } else false
        } catch (_: Exception) {
            false
        }
    }

    fun requestPinWidget(c: Context, providerClass: Class<*>): Boolean = try {
        val awm = AppWidgetManager.getInstance(c)
        if (awm.isRequestPinAppWidgetSupported) {
            awm.requestPinAppWidget(ComponentName(c, providerClass), null, null)
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }
}
