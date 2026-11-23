package com.sahl.home

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Bundle

/**
 * Sahl Icon Styler & Badgeless 1×1 Home Screen Icon Pinning:
 *  - Because Android's ShortcutManager.requestPinShortcut ALWAYS stamps a small parent-app badge
 *    in the bottom-right corner on Android 8+, Sahl Theme uses a 1×1 AppWidget (ThemedIconWidget)
 *    to place clean, badgeless Sahl Emerald & Gold App Icons and Folder Icons onto the real home screen!
 */
object SahlThemeShortcuts {
    const val EXTRA_PIN_TYPE = "sahl_pin_type"   // "app" or "folder"
    const val EXTRA_PIN_PKG = "sahl_pin_pkg"
    const val EXTRA_PIN_ACT = "sahl_pin_act"
    const val EXTRA_PIN_LABEL = "sahl_pin_label"
    const val EXTRA_PIN_FOLDER_ID = "sahl_pin_folder_id"

    /** Renders a 192x192 Bitmap with the Sahl Emerald & Gold Seal background and the inner app/folder icon. */
    fun createSahlSealIconBitmap(inner: Drawable?, glyphText: String? = null): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 1. Deep Emerald Radial Background Squircle
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                size * 0.5f, size * 0.36f, size * 0.72f,
                0xFF136E56.toInt(), 0xFF07352A.toInt(), Shader.TileMode.CLAMP
            )
        }
        val outerRect = RectF(6f, 6f, size - 6f, size - 6f)
        canvas.drawRoundRect(outerRect, 44f, 44f, bgPaint)

        // 2. Signature Gold Seal Border + Top Crown Dot
        val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 5.5f
            shader = LinearGradient(
                20f, 20f, size - 20f, size - 20f,
                intArrayOf(0xFFF7D36A.toInt(), 0xFFD4AF37.toInt(), 0xFFB88922.toInt()),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val sealRect = RectF(20f, 20f, size - 20f, size - 20f)
        canvas.drawRoundRect(sealRect, 34f, 34f, goldPaint)

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.FILL
            color = 0xFFF7D36A.toInt()
        }
        canvas.drawCircle(size * 0.5f, 20f, 5f, dotPaint)

        // 3. Draw inner app icon or Islamic folder glyph
        if (inner != null) {
            val pad = 42
            inner.setBounds(pad, pad, size - pad, size - pad)
            inner.draw(canvas)
        } else if (!glyphText.isNullOrEmpty()) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFF5C84C.toInt()
                textSize = 72f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            val y = size * 0.5f - (textPaint.descent() + textPaint.ascent()) * 0.5f
            canvas.drawText(glyphText, size * 0.5f, y, textPaint)
        }
        return bmp
    }

    /** Saves a Pending configuration so the next added 1×1 ThemedIconWidget automatically adopts this app/folder. */
    fun savePendingIconConfig(c: Context, type: String, pkg: String, activity: String, label: String, folderId: String) {
        Sahl.prefs(c).edit()
            .putString("pending_icon_type", type)
            .putString("pending_icon_pkg", pkg)
            .putString("pending_icon_act", activity)
            .putString("pending_icon_label", label)
            .putString("pending_icon_folder", folderId)
            .putLong("pending_icon_time", System.currentTimeMillis())
            .commit()
    }

    /** Binds a specific appWidgetId to the given app or folder. */
    fun bindWidgetId(c: Context, appWidgetId: Int, type: String, pkg: String, activity: String, label: String, folderId: String) {
        Sahl.prefs(c).edit()
            .putString("ti_${appWidgetId}_type", type)
            .putString("ti_${appWidgetId}_pkg", pkg)
            .putString("ti_${appWidgetId}_act", activity)
            .putString("ti_${appWidgetId}_label", label)
            .putString("ti_${appWidgetId}_folder", folderId)
            .commit()
    }

    /**
     * Pins a BADGELESS 1×1 Sahl-themed emerald & gold app icon widget onto the user's real phone home screen!
     * Zero small home badge in the bottom-right corner.
     */
    fun pinThemedAppIconNoBadge(c: Context, pkg: String, activity: String, label: String): Boolean {
        savePendingIconConfig(c, "app", pkg, activity, label, "")
        return try {
            val awm = AppWidgetManager.getInstance(c)
            if (awm.isRequestPinAppWidgetSupported) {
                awm.requestPinAppWidget(ComponentName(c, ThemedIconWidget::class.java), Bundle(), null)
            } else false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Pins a BADGELESS 1×1 Sahl Emerald & Gold Folder popup icon widget onto the user's real phone home screen!
     */
    fun pinFolderIconNoBadge(c: Context, folderId: String, title: String): Boolean {
        savePendingIconConfig(c, "folder", "", "", title, folderId)
        return try {
            val awm = AppWidgetManager.getInstance(c)
            if (awm.isRequestPinAppWidgetSupported) {
                awm.requestPinAppWidget(ComponentName(c, ThemedIconWidget::class.java), Bundle(), null)
            } else false
        } catch (_: Exception) {
            false
        }
    }

    /** Requests Android to pin one of Sahl Home's widgets directly onto the user's real phone home screen. */
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
