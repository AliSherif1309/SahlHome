package com.sahl.home

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon

/**
 * Sahl Icon Styler & Home Screen Shortcut Pinner:
 *  - Wraps any app icon or Sahl Islamic Folder in the signature Sahl Emerald & Gold Seal Frame
 *    and pins it directly onto the user's real phone home screen via ShortcutManager.requestPinShortcut.
 *  - Requests 1-tap pinning of Sahl Home Screen Widgets onto the user's real phone home screen.
 */
object SahlThemeShortcuts {

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
        val outerRect = RectF(8f, 8f, size - 8f, size - 8f)
        canvas.drawRoundRect(outerRect, 44f, 44f, bgPaint)

        // 2. Signature Gold Seal Border + Top Crown Dot
        val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 5f
            shader = LinearGradient(
                24f, 24f, size - 24f, size - 24f,
                intArrayOf(0xFFF7D36A.toInt(), 0xFFD4AF37.toInt(), 0xFFB88922.toInt()),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val sealRect = RectF(22f, 22f, size - 22f, size - 22f)
        canvas.drawRoundRect(sealRect, 34f, 34f, goldPaint)

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.FILL
            color = 0xFFF7D36A.toInt()
        }
        canvas.drawCircle(size * 0.5f, 22f, 5f, dotPaint)

        // 3. Draw inner app icon or Islamic folder glyph
        if (inner != null) {
            val pad = 44
            inner.setBounds(pad, pad, size - pad, size - pad)
            inner.draw(canvas)
        } else if (!glyphText.isNullOrEmpty()) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFF5C84C.toInt()
                textSize = 68f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            val y = size * 0.5f - (textPaint.descent() + textPaint.ascent()) * 0.5f
            canvas.drawText(glyphText, size * 0.5f, y, textPaint)
        }
        return bmp
    }

    /** Pins a Sahl-themed emerald & gold app icon shortcut onto the user's real phone home screen. */
    fun pinThemedAppShortcut(c: Context, pkg: String, activity: String, label: String, icon: Drawable): Boolean {
        val sm = c.getSystemService(ShortcutManager::class.java) ?: return false
        if (!sm.isRequestPinShortcutSupported) return false
        val bmp = createSahlSealIconBitmap(icon)
        val launchIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(pkg, activity))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val info = ShortcutInfo.Builder(c, "sahl_icon_${pkg}_${System.currentTimeMillis()}")
            .setShortLabel(label.take(18))
            .setLongLabel(label)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(launchIntent)
            .build()
        return sm.requestPinShortcut(info, null)
    }

    /** Pins a Sahl Emerald & Gold Folder popup shortcut onto the user's real phone home screen. */
    fun pinFolderPopupShortcut(c: Context, folderId: String, title: String): Boolean {
        val sm = c.getSystemService(ShortcutManager::class.java) ?: return false
        if (!sm.isRequestPinShortcutSupported) return false
        val bmp = createSahlSealIconBitmap(null, "۞")
        val popupIntent = Intent(c, SahlPopupActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("folder", folderId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val info = ShortcutInfo.Builder(c, "sahl_folder_${folderId}_${System.currentTimeMillis()}")
            .setShortLabel(title.take(18))
            .setLongLabel(title)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(popupIntent)
            .build()
        return sm.requestPinShortcut(info, null)
    }

    /** Requests Android to pin one of Sahl Home's 3 widgets directly onto the user's real phone home screen. */
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
