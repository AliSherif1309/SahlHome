package com.sahl.home

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RemoteViews
import java.time.Instant
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

/**
 * Native Android Home Screen Widgets for the user's real phone launcher:
 *  1. SanctuaryWidget (4x2): Hijri date, Daily Quranic Ayah, and Morning/Evening Adhkar (tap to cycle Ayahs)
 *  2. UnionHubWidget (4x2): Next Prayer countdown from Sahl Adhan Union + 1-tap Search, Qibla, Tools & Sahl Apps Popup
 *  3. SahlBarWidget (4x1): Compact emerald & gold dock bar for Phone, Messages, Adhan, Camera & Sahl Folder Popup
 */
object SahlWidgets {
    const val ACTION_CYCLE_AYAH = "com.sahl.home.ACTION_CYCLE_AYAH"

    private fun isAr(c: Context) = c.resources.configuration.locales[0].language == "ar"

    private fun hijriText(c: Context): String = try {
        val loc = c.resources.configuration.locales[0]
        "۞  " + DateTimeFormatter.ofPattern("d MMMM yyyy", loc).format(HijrahDate.now())
    } catch (_: Exception) {
        "۞  Sahl"
    }

    private fun queryNextPrayerSummary(c: Context): String {
        val ar = isAr(c)
        try {
            val now = System.currentTimeMillis()
            c.contentResolver.query(Uri.parse("${Sahl.ADHAN_UNION}/prayers"), null, null, null, null)?.use { cur ->
                while (cur.moveToNext()) {
                    val name = cur.getString(1) ?: ""
                    val tMs = cur.getLong(2)
                    if (tMs > now) {
                        val diffMin = ((tMs - now) / 60_000L).toInt()
                        val dur = if (ar) {
                            if (diffMin >= 60) "${diffMin / 60} س ${diffMin % 60} د" else "$diffMin د"
                        } else {
                            if (diffMin >= 60) "${diffMin / 60}h ${diffMin % 60}m" else "${diffMin}m"
                        }
                        val loc = c.resources.configuration.locales[0]
                        val tFmt = Instant.ofEpochMilli(tMs).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("HH:mm", loc))
                        val pName = IslamicHomeData.localizedPrayerName(name, ar)
                        return if (ar) "🕌  الصلاة القادمة: $pName بعد $dur ($tFmt)"
                        else "🕌  Next prayer: $pName in $dur ($tFmt)"
                    }
                }
            }
        } catch (_: Exception) {}
        return if (ar) "🕌  اضغط لفتح سهل للأذان" else "🕌  Tap to open Sahl Adhan"
    }

    private fun launchPkgPendingIntent(c: Context, reqCode: Int, pkg: String, fallbackCls: Class<*> = MainActivity::class.java): PendingIntent {
        val launch = c.packageManager.getLaunchIntentForPackage(pkg)
            ?: Intent(c, fallbackCls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(c, reqCode, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun popupPendingIntent(c: Context, reqCode: Int, folderId: String): PendingIntent {
        val i = Intent(c, SahlPopupActivity::class.java).apply {
            putExtra("folder", folderId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(c, reqCode, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun buildSanctuaryViews(ctx: Context): RemoteViews {
        val c = Sahl.wrap(ctx)
        val ar = isAr(c)
        val p = Sahl.prefs(c)
        val offset = p.getInt("widget_ayah_offset", 0)
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val ayah = IslamicHomeData.ayahs[(day + offset) % IslamicHomeData.ayahs.size]
        val dhikr = IslamicHomeData.currentDhikr()

        val v = RemoteViews(c.packageName, R.layout.widget_sanctuary)
        v.setTextViewText(R.id.w_sanctuary_hijri, hijriText(c))
        v.setTextViewText(R.id.w_sanctuary_ref, if (ar) ayah.refAr else ayah.refEn)
        v.setTextViewText(R.id.w_sanctuary_ayah, ayah.ar)
        v.setTextViewText(R.id.w_sanctuary_trans, ayah.en)
        v.setViewVisibility(R.id.w_sanctuary_trans, if (ar) View.GONE else View.VISIBLE)
        v.setTextViewText(R.id.w_sanctuary_dhikr, "📿  " + dhikr.ar)

        val cycleIntent = Intent(c, SanctuaryWidget::class.java).setAction(ACTION_CYCLE_AYAH)
        val pi = PendingIntent.getBroadcast(c, 90, cycleIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        v.setOnClickPendingIntent(R.id.w_sanctuary_root, pi)
        return v
    }

    fun buildUnionHubViews(ctx: Context): RemoteViews {
        val c = Sahl.wrap(ctx)
        val ar = isAr(c)
        val v = RemoteViews(c.packageName, R.layout.widget_union_hub)
        v.setTextViewText(R.id.w_hub_title, if (ar) "۞  اتحاد سهل" else "۞  Sahl Union")
        v.setTextViewText(R.id.w_hub_hijri, hijriText(c).removePrefix("۞  "))
        v.setTextViewText(R.id.w_hub_prayer, queryNextPrayerSummary(c))

        v.setTextViewText(R.id.w_hub_btn_search, if (ar) "🔍 بحث" else "🔍 Search")
        v.setTextViewText(R.id.w_hub_btn_qibla, if (ar) "🧭 القبلة" else "🧭 Qibla")
        v.setTextViewText(R.id.w_hub_btn_tools, if (ar) "🛠 الأدوات" else "🛠 Tools")
        v.setTextViewText(R.id.w_hub_btn_apps, if (ar) "۞ تطبيقات" else "۞ Sahl Apps")

        v.setOnClickPendingIntent(R.id.w_hub_prayer, launchPkgPendingIntent(c, 101, "com.sahl.adhan"))
        val searchIntent = try {
            Intent().setComponent(ComponentName("com.sahl.tools", "com.sahl.tools.SearchActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } catch (_: Exception) {
            Intent(c, MainActivity::class.java)
        }
        v.setOnClickPendingIntent(
            R.id.w_hub_btn_search,
            PendingIntent.getActivity(c, 102, searchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        )
        val qiblaPkg = if (isInstalled(c, "com.sahl.qibla")) "com.sahl.qibla" else "com.aly.trueqibla"
        v.setOnClickPendingIntent(R.id.w_hub_btn_qibla, launchPkgPendingIntent(c, 103, qiblaPkg))
        v.setOnClickPendingIntent(R.id.w_hub_btn_tools, launchPkgPendingIntent(c, 104, "com.sahl.tools"))
        v.setOnClickPendingIntent(R.id.w_hub_btn_apps, popupPendingIntent(c, 105, "all_sahl"))
        return v
    }

    fun buildSahlBarViews(ctx: Context): RemoteViews {
        val c = Sahl.wrap(ctx)
        val ar = isAr(c)
        val v = RemoteViews(c.packageName, R.layout.widget_sahl_bar)
        v.setTextViewText(R.id.w_bar_phone, if (ar) "📞 الهاتف" else "📞 Phone")
        v.setTextViewText(R.id.w_bar_messages, if (ar) "💬 الرسائل" else "💬 SMS")
        v.setTextViewText(R.id.w_bar_adhan, if (ar) "🕌 الأذان" else "🕌 Adhan")
        v.setTextViewText(R.id.w_bar_camera, if (ar) "📷 كاميرا" else "📷 Camera")
        v.setTextViewText(R.id.w_bar_more, if (ar) "۞ سهل" else "۞ Sahl")

        v.setOnClickPendingIntent(R.id.w_bar_phone, launchPkgPendingIntent(c, 201, "com.sahl.phone"))
        v.setOnClickPendingIntent(R.id.w_bar_messages, launchPkgPendingIntent(c, 202, "com.sahl.messages"))
        v.setOnClickPendingIntent(R.id.w_bar_adhan, launchPkgPendingIntent(c, 203, "com.sahl.adhan"))
        v.setOnClickPendingIntent(R.id.w_bar_camera, launchPkgPendingIntent(c, 204, "com.sahl.camera"))
        v.setOnClickPendingIntent(R.id.w_bar_more, popupPendingIntent(c, 205, "all_sahl"))
        return v
    }

    private fun isInstalled(c: Context, pkg: String): Boolean = try {
        c.packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (_: Exception) {
        false
    }

    fun updateAll(ctx: Context) {
        val m = AppWidgetManager.getInstance(ctx)
        m.getAppWidgetIds(ComponentName(ctx, SanctuaryWidget::class.java))
            .forEach { m.updateAppWidget(it, buildSanctuaryViews(ctx)) }
        m.getAppWidgetIds(ComponentName(ctx, UnionHubWidget::class.java))
            .forEach { m.updateAppWidget(it, buildUnionHubViews(ctx)) }
        m.getAppWidgetIds(ComponentName(ctx, SahlBarWidget::class.java))
            .forEach { m.updateAppWidget(it, buildSahlBarViews(ctx)) }
    }
}

class SanctuaryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, SahlWidgets.buildSanctuaryViews(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == SahlWidgets.ACTION_CYCLE_AYAH) {
            val p = Sahl.prefs(context)
            p.edit().putInt("widget_ayah_offset", (p.getInt("widget_ayah_offset", 0) + 1) % IslamicHomeData.ayahs.size).apply()
            SahlWidgets.updateAll(context)
        }
    }
}

class UnionHubWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, SahlWidgets.buildUnionHubViews(context)) }
    }
}

class SahlBarWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, SahlWidgets.buildSahlBarViews(context)) }
    }
}

/**
 * Floating Emerald & Gold Popup Folder Activity:
 * Opens directly over the user's real phone home screen when they tap a Sahl Folder shortcut or the "۞ Sahl Apps" widget button!
 */
class SahlPopupActivity : SahlActivity() {
    private val allSahlPkgs = listOf(
        "com.sahl.phone", "com.sahl.contacts", "com.sahl.messages", "com.sahl.adhan",
        "com.sahl.qibla", "com.aly.trueqibla", "com.sahl.files", "com.sahl.gallery",
        "com.sahl.camera", "com.sahl.tools", "com.aly.sahlsync", "com.sahl.pdf", "com.sahl.timer",
        "com.sahl.home"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val folderId = intent.getStringExtra("folder") ?: "all_sahl"
        val ar = resources.configuration.locales[0].language == "ar"
        val folder = IslamicHomeData.folders(this).firstOrNull { it.id == folderId }
        val titleStr = when {
            folder != null -> if (ar) folder.nameAr else folder.nameEn
            ar -> "۞  تطبيقات سهل"
            else -> "۞  Sahl Apps"
        }
        val targetPkgs = folder?.packages ?: allSahlPkgs

        val outer = FrameLayout(this).apply {
            setBackgroundColor(0x88000000.toInt())
            setOnClickListener { finish() }
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(if (night) 0xF212241F.toInt() else 0xF5F7F5EE.toInt(), 26f, 0xFFD4AF37.toInt())
            setPadding(px(18), px(16), px(18), px(18))
            isClickable = true
        }

        val hdr = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(4), 0, px(4), px(12))
        }
        hdr.addView(text(titleStr, 18f, 0xFFF5C84C.toInt(), true), LinearLayout.LayoutParams(0, -2, 1f))
        hdr.addView(iconButton(R.drawable.ic_close, 0, col(R.color.sub), 34, 18) { finish() })
        card.addView(hdr)

        val grid = GridLayout(this).apply { columnCount = 4 }
        val pm = packageManager
        val cellW = (resources.displayMetrics.widthPixels - px(88)) / 4
        for (pkg in targetPkgs.distinct()) {
            val info = try { pm.getApplicationInfo(pkg, 0) } catch (_: Exception) { null } ?: continue
            val icon = pm.getApplicationIcon(info)
            val label = pm.getApplicationLabel(info).toString().removePrefix("Sahl ").removePrefix("سهل ")
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(px(4), px(8), px(4), px(8))
                layoutParams = GridLayout.LayoutParams().apply { width = cellW }
                addView(ImageView(this@SahlPopupActivity).apply {
                    setImageDrawable(icon)
                }, LinearLayout.LayoutParams(px(52), px(52)))
                addView(text(label, 11.5f, col(R.color.text), true).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    setPadding(0, px(4), 0, 0)
                })
                setOnClickListener {
                    finish()
                    try { pm.getLaunchIntentForPackage(pkg)?.let { startActivity(it) } } catch (_: Exception) {}
                }
            }.press(14f)
            grid.addView(item)
        }
        card.addView(grid)

        outer.addView(card, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply {
            setMargins(px(20), px(20), px(20), px(20))
        })

        setContentView(outer)
    }
}
