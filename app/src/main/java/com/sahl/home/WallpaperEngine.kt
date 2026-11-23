package com.sahl.home

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Sahl Wallpaper Engine:
 * Renders procedural 4K emerald & gold wallpapers at exact screen or 2160x3840 resolution,
 * applies them to Home/Lock screen via WallpaperManager, exports 4K PNGs, and drives
 * the Sahl Prayer Sky Live Wallpaper Service.
 */
object WallpaperEngine {
    const val STYLE_GIRIH = "girih"
    const val STYLE_DUSK = "dusk"
    const val STYLE_CREAM = "cream"
    const val STYLE_QIBLA = "qibla"
    const val STYLE_MIHRAB_LOCK = "mihrab_lock"
    const val STYLE_NOOR_LOCK = "noor_lock"
    const val STYLE_PRAYER_SKY = "prayer_sky"

    data class StyleInfo(val id: String, val titleRes: Int, val descRes: Int, val isLight: Boolean)

    val styles = listOf(
        StyleInfo(STYLE_GIRIH, R.string.wp_girih, R.string.wp_girih_d, false),
        StyleInfo(STYLE_DUSK, R.string.wp_dusk, R.string.wp_dusk_d, false),
        StyleInfo(STYLE_CREAM, R.string.wp_cream, R.string.wp_cream_d, true),
        StyleInfo(STYLE_QIBLA, R.string.wp_qibla, R.string.wp_qibla_d, false),
        StyleInfo(STYLE_MIHRAB_LOCK, R.string.wp_mihrab_lock, R.string.wp_mihrab_lock_d, false),
        StyleInfo(STYLE_NOOR_LOCK, R.string.wp_noor_lock, R.string.wp_noor_lock_d, false)
    )

    fun currentStyle(c: Context): String = Sahl.prefs(c).getString("wp_style", STYLE_GIRIH) ?: STYLE_GIRIH
    fun setCurrentStyle(c: Context, styleId: String) = Sahl.prefs(c).edit().putString("wp_style", styleId).apply()

    /** Determines current prayer phase (0 Fajr, 1 Dhuhr, 2 Asr, 3 Maghrib, 4 Isha) by hour of day. */
    fun currentPrayerPhase(): Int {
        val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hr in 4..6 -> 0    // Fajr dawn teal
            hr in 7..14 -> 1   // Dhuhr daylight cream
            hr in 15..16 -> 2  // Asr warm sand
            hr in 17..18 -> 3  // Maghrib emerald-gold dusk
            else -> 4          // Isha deep emerald night
        }
    }

    fun isLightStyle(styleId: String, phase: Int = currentPrayerPhase()): Boolean = when (styleId) {
        STYLE_CREAM -> true
        STYLE_PRAYER_SKY -> phase == 1 || phase == 2
        else -> false
    }

    /** Renders the given Sahl wallpaper onto any Canvas of size (w x h). */
    fun draw(canvas: Canvas, w: Float, h: Float, styleId: String, prayerPhase: Int = currentPrayerPhase()) {
        val light = isLightStyle(styleId, prayerPhase)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Background gradient
        when (styleId) {
            STYLE_PRAYER_SKY -> {
                val colors = when (prayerPhase) {
                    0 -> intArrayOf(0xFF0B1D24.toInt(), 0xFF164A41.toInt(), 0xFF061411.toInt())
                    1 -> intArrayOf(0xFFFAF8F2.toInt(), 0xFFE4EEE9.toInt(), 0xFFD8E6DF.toInt())
                    2 -> intArrayOf(0xFFEFECE1.toInt(), 0xFFD5E5DC.toInt(), 0xFFC5DBD0.toInt())
                    3 -> intArrayOf(0xFF091D18.toInt(), 0xFF12463A.toInt(), 0xFF061411.toInt())
                    else -> intArrayOf(0xFF071210.toInt(), 0xFF0C2B24.toInt(), 0xFF050F0C.toInt())
                }
                bgPaint.shader = LinearGradient(0f, 0f, 0f, h, colors, floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
            }
            STYLE_GIRIH -> {
                bgPaint.shader = RadialGradient(
                    w * 0.5f, h * 0.42f, max(w, h) * 0.75f,
                    intArrayOf(0xFF144E40.toInt(), 0xFF0B352B.toInt(), 0xFF061A15.toInt()),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
            }
            STYLE_DUSK -> {
                bgPaint.shader = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(0xFF071412.toInt(), 0xFF0D2F27.toInt(), 0xFF15493D.toInt(), 0xFF081B16.toInt()),
                    floatArrayOf(0f, 0.45f, 0.75f, 1f), Shader.TileMode.CLAMP
                )
            }
            STYLE_CREAM -> {
                bgPaint.shader = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(0xFFFAF8F2.toInt(), 0xFFF4F1E6.toInt(), 0xFFE4EEE9.toInt()),
                    floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
                )
            }
            STYLE_QIBLA -> {
                bgPaint.shader = RadialGradient(
                    w * 0.5f, h * 0.65f, max(w, h) * 0.75f,
                    intArrayOf(0xFF1B5E4D.toInt(), 0xFF0C2D25.toInt(), 0xFF071310.toInt()),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                )
            }
            STYLE_MIHRAB_LOCK -> {
                bgPaint.shader = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(0xFF04100D.toInt(), 0xFF08241D.toInt(), 0xFF114639.toInt(), 0xFF071814.toInt()),
                    floatArrayOf(0f, 0.34f, 0.68f, 1f), Shader.TileMode.CLAMP
                )
            }
            STYLE_NOOR_LOCK -> {
                bgPaint.shader = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(0xFF040D11.toInt(), 0xFF082026.toInt(), 0xFF0E3934.toInt(), 0xFF061613.toInt()),
                    floatArrayOf(0f, 0.36f, 0.72f, 1f), Shader.TileMode.CLAMP
                )
            }
        }
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val strokeGold = if (light) 0x290B5D4B else 0x38D4AF37
        val accentGold = if (light) 0x52B88922 else 0x62F5C84C

        // 2. Islamic 12-fold Girih Rosette
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            color = strokeGold
            strokeWidth = max(1.5f, w / 360f)
        }
        val cx = w * 0.5f
        val cy = when (styleId) {
            STYLE_DUSK -> h * 0.30f
            STYLE_MIHRAB_LOCK -> h * 0.63f
            else -> h * 0.44f
        }
        val baseR = if (styleId == STYLE_MIHRAB_LOCK) w * 0.34f else w * 0.42f
        val folds = 12

        if (styleId != STYLE_NOOR_LOCK) {
            for (ring in 1..3) {
                val r = baseR * (ring * 0.33f)
                canvas.drawCircle(cx, cy, r, linePaint)
                val path = Path()
                for (i in 0 until folds) {
                    val a1 = (i * 2.0 * Math.PI) / folds
                    val a2 = ((i + 2) * 2.0 * Math.PI) / folds
                    val a3 = ((i + 0.5) * 2.0 * Math.PI) / folds
                    val x1 = cx + (cos(a1) * r * 0.65f).toFloat()
                    val y1 = cy + (sin(a1) * r * 0.65f).toFloat()
                    val x3 = cx + (cos(a3) * r).toFloat()
                    val y3 = cy + (sin(a3) * r).toFloat()
                    val x2 = cx + (cos(a2) * r * 0.65f).toFloat()
                    val y2 = cy + (sin(a2) * r * 0.65f).toFloat()
                    path.moveTo(x1, y1)
                    path.lineTo(x3, y3)
                    path.lineTo(x2, y2)
                }
                canvas.drawPath(path, linePaint)
            }
        }

        // 3. Desert Dusk Dunes & Crescent Moon
        if (styleId == STYLE_DUSK || styleId == STYLE_PRAYER_SKY) {
            val moonLayer = canvas.saveLayer(0f, 0f, w, h, null)
            val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.FILL
                color = if (light) 0xFFD4AF37.toInt() else 0xFFF5C84C.toInt()
            }
            val mx = w * 0.5f
            val my = h * 0.28f
            val mr = w * 0.055f
            canvas.drawCircle(mx, my, mr, moonPaint)
            moonPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            canvas.drawCircle(mx - mr * 0.38f, my - mr * 0.25f, mr * 0.88f, moonPaint)
            moonPaint.xfermode = null
            canvas.restoreToCount(moonLayer)

            val duneY = floatArrayOf(h * 0.68f, h * 0.77f, h * 0.86f)
            val duneAmp = floatArrayOf(h * 0.04f, h * 0.05f, h * 0.035f)
            val duneCol = if (light) {
                intArrayOf(0xFFD5E5DC.toInt(), 0xFFC2DBD0.toInt(), 0xFF0B5D4B.toInt())
            } else {
                intArrayOf(0xFF0E392F.toInt(), 0xFF0A2922.toInt(), 0xFF061814.toInt())
            }
            val duneFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.FILL }
            val duneEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                color = accentGold
                strokeWidth = max(2f, w / 280f)
            }
            for (i in 0..2) {
                val y = duneY[i]
                val amp = duneAmp[i]
                val p = Path().apply {
                    moveTo(0f, y)
                    cubicTo(w * 0.32f, y - amp, w * 0.68f, y + amp, w, y - amp * 0.5f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                duneFill.color = duneCol[i]
                canvas.drawPath(p, duneFill)

                val edge = Path().apply {
                    moveTo(0f, y)
                    cubicTo(w * 0.32f, y - amp, w * 0.68f, y + amp, w, y - amp * 0.5f)
                }
                canvas.drawPath(edge, duneEdge)
            }
        }

        // 4. Mihrab Arch Frame (skipped on lock-screen depth styles so top clock zone stays clean)
        if (styleId != STYLE_MIHRAB_LOCK && styleId != STYLE_NOOR_LOCK) {
            val archPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                color = accentGold
                strokeWidth = max(2f, w / 250f)
            }
            val archW = w * 0.78f
            val archLeft = (w - archW) * 0.5f
            val archRight = archLeft + archW
            val archTop = h * 0.075f
            val archShoulder = h * 0.21f
            val archPath = Path().apply {
                moveTo(archLeft, h * 0.92f)
                lineTo(archLeft, archShoulder)
                quadTo(archLeft, archTop + (archShoulder - archTop) * 0.38f, w * 0.5f, archTop)
                quadTo(archRight, archTop + (archShoulder - archTop) * 0.38f, archRight, archShoulder)
                lineTo(archRight, h * 0.92f)
            }
            canvas.drawPath(archPath, archPaint)
        }

        // 5. Qibla Horizon Compass Motif
        if (styleId == STYLE_QIBLA) {
            val qx = w * 0.5f
            val qy = h * 0.65f
            val qr = w * 0.24f
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                color = 0x99F5C84C.toInt()
                strokeWidth = max(2f, w / 240f)
            }
            canvas.drawCircle(qx, qy, qr, ringPaint)

            canvas.save()
            canvas.translate(qx, qy)
            canvas.rotate(-27.5f) // Doha Qibla bearing angle visual
            val arrow = Path().apply {
                moveTo(0f, -qr * 0.92f)
                lineTo(w * 0.035f, -qr * 0.50f)
                lineTo(0f, -qr * 0.62f)
                lineTo(-w * 0.035f, -qr * 0.50f)
                close()
            }
            val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.FILL
                color = 0xFFF5C84C.toInt()
            }
            canvas.drawPath(arrow, arrowPaint)
            canvas.restore()
        }

        // 6. Mihrab Depth Lock (Clean upper 34% for lock clock + glowing lower Mihrab arch & fanous lantern)
        if (styleId == STYLE_MIHRAB_LOCK) {
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w * 0.5f, h * 0.54f, w * 0.48f,
                    intArrayOf(0x38F5C84C, 0x12D4AF37, 0x00000000),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(w * 0.5f, h * 0.54f, w * 0.48f, glowPaint)

            val outerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                color = 0x99F5C84C.toInt()
                strokeWidth = max(2.5f, w / 210f)
            }
            val innerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                color = 0x48D4AF37
                strokeWidth = max(1.5f, w / 340f)
            }
            for ((scale, p) in listOf(0.76f to outerStroke, 0.66f to innerStroke)) {
                val aw = w * scale
                val al = (w - aw) * 0.5f
                val ar = al + aw
                val aTop = if (scale > 0.7f) h * 0.35f else h * 0.385f
                val aShoulder = aTop + h * 0.12f
                val path = Path().apply {
                    moveTo(al, h * 0.94f)
                    lineTo(al, aShoulder)
                    quadTo(al, aTop + (aShoulder - aTop) * 0.36f, w * 0.5f, aTop)
                    quadTo(ar, aTop + (aShoulder - aTop) * 0.36f, ar, aShoulder)
                    lineTo(ar, h * 0.94f)
                }
                canvas.drawPath(path, p)
            }

            // Hanging Fanous lantern from apex
            val lx = w * 0.5f
            val lTop = h * 0.35f
            val lBodyTop = h * 0.44f
            val lBodyBot = h * 0.505f
            canvas.drawLine(lx, lTop, lx, lBodyTop, outerStroke)
            val lantern = Path().apply {
                moveTo(lx, lBodyTop)
                lineTo(lx + w * 0.036f, lBodyTop + h * 0.02f)
                lineTo(lx + w * 0.028f, lBodyBot)
                lineTo(lx - w * 0.028f, lBodyBot)
                lineTo(lx - w * 0.036f, lBodyTop + h * 0.02f)
                close()
            }
            val lanternFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.FILL
                color = 0xCCF5C84C.toInt()
            }
            canvas.drawPath(lantern, lanternFill)
        }

        // 7. Noor Crescent Lock (Clean upper 32% for lock clock + starfield, crescent & dunes)
        if (styleId == STYLE_NOOR_LOCK) {
            val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.FILL
                color = 0x77F5C84C
            }
            val stars = arrayOf(
                0.15f to 0.12f, 0.84f to 0.14f, 0.24f to 0.38f, 0.77f to 0.36f,
                0.12f to 0.54f, 0.88f to 0.52f, 0.34f to 0.46f, 0.68f to 0.44f,
                0.20f to 0.65f, 0.80f to 0.63f
            )
            for ((sx, sy) in stars) {
                canvas.drawCircle(w * sx, h * sy, max(1.5f, w / 280f), starPaint)
            }

            val moonLayer = canvas.saveLayer(0f, 0f, w, h, null)
            val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.FILL
                color = 0xFFF5C84C.toInt()
            }
            val mx = w * 0.5f
            val my = h * 0.50f
            val mr = w * 0.11f
            canvas.drawCircle(mx, my, mr, moonPaint)
            moonPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            canvas.drawCircle(mx - mr * 0.36f, my - mr * 0.24f, mr * 0.88f, moonPaint)
            moonPaint.xfermode = null
            canvas.restoreToCount(moonLayer)

            val duneY = floatArrayOf(h * 0.74f, h * 0.82f, h * 0.90f)
            val duneAmp = floatArrayOf(h * 0.035f, h * 0.045f, h * 0.03f)
            val duneCol = intArrayOf(0xFF0C342C.toInt(), 0xFF08251F.toInt(), 0xFF051512.toInt())
            val duneFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.FILL }
            val duneEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                color = 0x66F5C84C
                strokeWidth = max(2f, w / 260f)
            }
            for (i in 0..2) {
                val y = duneY[i]
                val amp = duneAmp[i]
                val p = Path().apply {
                    moveTo(0f, y)
                    cubicTo(w * 0.30f, y - amp, w * 0.70f, y + amp, w, y - amp * 0.4f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                duneFill.color = duneCol[i]
                canvas.drawPath(p, duneFill)
                val edge = Path().apply {
                    moveTo(0f, y)
                    cubicTo(w * 0.30f, y - amp, w * 0.70f, y + amp, w, y - amp * 0.4f)
                }
                canvas.drawPath(edge, duneEdge)
            }
        }
    }

    /** Creates a Bitmap of the requested Sahl wallpaper style. */
    fun renderBitmap(width: Int, height: Int, styleId: String): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        draw(canvas, width.toFloat(), height.toFloat(), styleId)
        return bmp
    }

    /** Applies the Sahl wallpaper to Home Screen, Lock Screen, or Both. */
    fun applyWallpaper(c: Context, styleId: String, which: Int): Boolean = try {
        val dm = c.resources.displayMetrics
        val w = max(1080, dm.widthPixels)
        val h = max(2400, dm.heightPixels)
        val bmp = renderBitmap(w, h, styleId)
        val wm = WallpaperManager.getInstance(c)
        wm.setBitmap(bmp, null, true, which)
        setCurrentStyle(c, styleId)
        true
    } catch (e: Exception) {
        false
    }

    /** Exports a 2160x3840 4K PNG wallpaper into Pictures/SahlWallpapers. */
    fun save4KToGallery(c: Context, styleId: String): Boolean {
        return try {
            val bmp = renderBitmap(2160, 3840, styleId)
            val name = "Sahl_Wallpaper_${styleId}_4K_${System.currentTimeMillis()}.png"
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SahlWallpapers")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = c.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return false
            c.contentResolver.openOutputStream(uri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= 29) {
                cv.clear()
                cv.put(MediaStore.Images.Media.IS_PENDING, 0)
                c.contentResolver.update(uri, cv, null, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Android Live Wallpaper Service:
 * Automatically shifts the phone wallpaper palette across the 5 daily prayer windows.
 */
class SahlLiveWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = PrayerSkyEngine()

    private inner class PrayerSkyEngine : Engine() {
        private var visible = false

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) drawFrame()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            drawFrame()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            drawFrame()
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    WallpaperEngine.draw(
                        canvas,
                        canvas.width.toFloat(),
                        canvas.height.toFloat(),
                        WallpaperEngine.STYLE_PRAYER_SKY,
                        WallpaperEngine.currentPrayerPhase()
                    )
                }
            } catch (_: Exception) {
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
                }
            }
        }
    }
}
