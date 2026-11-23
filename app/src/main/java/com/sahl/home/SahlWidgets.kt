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
import android.widget.ScrollView
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date

/**
 * Native Android Home Screen Widgets for the user's real phone launcher:
 *  1. ClockWidget (4x2): Live Digital TextClock + optional Hijri Date + Gregorian Date + Next Prayer countdown
 *  2. SanctuaryWidget (4x2): Customizable Hijri date, Surah reference, Daily Quranic Ayah, English translation, and Morning/Evening Adhkar
 *  3. UnionHubWidget (4x2): Customizable Live Clock, Hijri date, Next Prayer countdown (with exact time!) + 1-tap Search, Qibla, Tools & Sahl Apps Popup
 *  4. SahlBarWidget (4x1): Compact emerald & gold dock bar for Phone, Messages, Adhan, Camera & Sahl Folder Popup
 *  5. ThemedIconWidget (1x1): Badgeless Sahl Emerald & Gold App/Folder Icon with outer-edge gold border + optional app name label
 */
object SahlWidgets {
    const val ACTION_CYCLE_AYAH = "com.sahl.home.ACTION_CYCLE_AYAH"

    private fun isAr(c: Context) = c.resources.configuration.locales[0].language == "ar"

    fun opt(c: Context, key: String, default: Boolean = true): Boolean =
        Sahl.prefs(c).getBoolean(key, default)

    fun setOpt(c: Context, key: String, value: Boolean) {
        Sahl.prefs(c).edit().putBoolean(key, value).apply()
        updateAll(c)
    }

    private fun vis(show: Boolean) = if (show) View.VISIBLE else View.GONE

    private fun hijriText(c: Context): String = try {
        val loc = c.resources.configuration.locales[0]
        "۞  " + DateTimeFormatter.ofPattern("d MMMM yyyy", loc).format(HijrahDate.now())
    } catch (_: Exception) {
        "۞  Sahl"
    }

    private fun gregorianText(c: Context): String = try {
        val loc = c.resources.configuration.locales[0]
        SimpleDateFormat("EEEE, d MMMM yyyy", loc).format(Date())
    } catch (_: Exception) {
        ""
    }

    /**
     * Concise Next Prayer line that never cuts off the minutes unit (`د`) or prayer time (`(16:46)`):
     * e.g. `🕌 المغرب بعد 1 س 20 د · 16:46` or `🕌 Maghrib in 1h 20m · 16:46`
     */
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
                        return if (ar) "🕌  $pName بعد $dur  ·  $tFmt"
                        else "🕌  $pName in $dur  ·  $tFmt"
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

    /** Widget 0: Sahl Analog Clock (`ساعة سهل التناظرية` — 2×2) */
    fun buildAnalogClockViews(ctx: Context): RemoteViews {
        val c = Sahl.wrap(ctx)
        val v = RemoteViews(c.packageName, R.layout.widget_analog_clock)
        v.setTextViewText(R.id.w_aclock_hijri, hijriText(c).removePrefix("۞  "))
        v.setViewVisibility(R.id.w_aclock_hijri, vis(opt(c, "w_aclock_hijri", true)))
        v.setOnClickPendingIntent(R.id.w_aclock_root, launchPkgPendingIntent(c, 80, "com.sahl.adhan"))
        return v
    }

    /** Widget 1: Sahl Islamic Clock (`ساعة سهل الإسلامية` — 4×2) */
    fun buildClockViews(ctx: Context): RemoteViews {
        val c = Sahl.wrap(ctx)
        val v = RemoteViews(c.packageName, R.layout.widget_clock)
        v.setTextViewText(R.id.w_clock_hijri, hijriText(c))
        v.setViewVisibility(R.id.w_clock_hijri, vis(opt(c, "w_clock_hijri", true)))

        v.setTextViewText(R.id.w_clock_greg, gregorianText(c))
        v.setViewVisibility(R.id.w_clock_greg, vis(opt(c, "w_clock_greg", true)))

        v.setTextViewText(R.id.w_clock_prayer, queryNextPrayerSummary(c))
        v.setViewVisibility(R.id.w_clock_prayer, vis(opt(c, "w_clock_prayer", true)))

        v.setOnClickPendingIntent(R.id.w_clock_root, launchPkgPendingIntent(c, 81, "com.sahl.adhan"))
        return v
    }

    /** Widget 2: Sahl Islamic Sanctuary (`واحة سهل الإسلامية` — 4×2) */
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
        v.setViewVisibility(R.id.w_sanctuary_hijri, vis(opt(c, "w_sanc_hijri", true)))

        v.setTextViewText(R.id.w_sanctuary_ref, if (ar) ayah.refAr else ayah.refEn)
        v.setViewVisibility(R.id.w_sanctuary_ref, vis(opt(c, "w_sanc_ref", true)))

        v.setTextViewText(R.id.w_sanctuary_ayah, ayah.ar)
        v.setTextViewText(R.id.w_sanctuary_trans, ayah.en)
        v.setViewVisibility(R.id.w_sanctuary_trans, vis(opt(c, "w_sanc_trans", !ar)))

        v.setTextViewText(R.id.w_sanctuary_dhikr, "📿  " + dhikr.ar)
        v.setViewVisibility(R.id.w_sanctuary_dhikr, vis(opt(c, "w_sanc_dhikr", true)))

        val cycleIntent = Intent(c, SanctuaryWidget::class.java).setAction(ACTION_CYCLE_AYAH)
        val pi = PendingIntent.getBroadcast(c, 90, cycleIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        v.setOnClickPendingIntent(R.id.w_sanctuary_root, pi)
        return v
    }

    /** Widget 3: Sahl Union Hub (`محور اتحاد سهل` — 4×2) */
    fun buildUnionHubViews(ctx: Context): RemoteViews {
        val c = Sahl.wrap(ctx)
        val ar = isAr(c)
        val v = RemoteViews(c.packageName, R.layout.widget_union_hub)
        v.setViewVisibility(R.id.w_hub_clock, vis(opt(c, "w_hub_clock", true)))
        v.setTextViewText(R.id.w_hub_title, if (ar) "۞  اتحاد سهل" else "۞  Sahl Union")
        v.setTextViewText(R.id.w_hub_hijri, hijriText(c).removePrefix("۞  "))
        v.setViewVisibility(R.id.w_hub_hijri, vis(opt(c, "w_hub_hijri", true)))

        v.setTextViewText(R.id.w_hub_prayer, queryNextPrayerSummary(c))
        v.setViewVisibility(R.id.w_hub_prayer, vis(opt(c, "w_hub_prayer", true)))

        v.setViewVisibility(R.id.w_hub_actions_row, vis(opt(c, "w_hub_buttons", true)))
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

    /** Widget 4: Sahl Apps Dock Bar (`شريط تطبيقات سهل` — 4×1) */
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

    /**
     * Widget 5: BADGELESS 1×1 Sahl Emerald & Gold Icon Widget (with outer-edge gold border + optional app name label)
     */
    fun buildThemedIconViews(ctx: Context, appWidgetId: Int): RemoteViews {
        val c = Sahl.wrap(ctx)
        val p = Sahl.prefs(c)

        if (!p.contains("ti_${appWidgetId}_type")) {
            val pendingType = p.getString("pending_icon_type", null)
            if (pendingType != null) {
                SahlThemeShortcuts.bindWidgetId(
                    c,
                    appWidgetId,
                    pendingType,
                    p.getString("pending_icon_pkg", "") ?: "",
                    p.getString("pending_icon_act", "") ?: "",
                    p.getString("pending_icon_label", "Sahl") ?: "Sahl",
                    p.getString("pending_icon_folder", "all_sahl") ?: "all_sahl",
                    p.getBoolean("pending_icon_show_label", SahlThemeShortcuts.defaultShowLabel(c)),
                    p.getInt("pending_icon_style_mode", SahlThemeShortcuts.defaultIconStyle(c))
                )
            }
        }

        val type = p.getString("ti_${appWidgetId}_type", "folder") ?: "folder"
        val pkg = p.getString("ti_${appWidgetId}_pkg", "") ?: ""
        val act = p.getString("ti_${appWidgetId}_act", "") ?: ""
        val label = p.getString("ti_${appWidgetId}_label", "Sahl") ?: "Sahl"
        val folderId = p.getString("ti_${appWidgetId}_folder", "all_sahl") ?: "all_sahl"
        val showLabel = p.getBoolean("ti_${appWidgetId}_show_label", SahlThemeShortcuts.defaultShowLabel(c))
        val styleMode = p.getInt("ti_${appWidgetId}_style_mode", SahlThemeShortcuts.defaultIconStyle(c))

        val v = RemoteViews(c.packageName, R.layout.widget_themed_icon)
        v.setTextViewText(R.id.w_icon_label, label)
        v.setViewVisibility(R.id.w_icon_label, if (showLabel) View.VISIBLE else View.GONE)

        if (type == "app" && pkg.isNotEmpty()) {
            val appIcon = try { c.packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null }
            val bmp = SahlThemeShortcuts.createSahlSealIconBitmap(appIcon, if (appIcon == null) "۞" else null, styleMode)
            v.setImageViewBitmap(R.id.w_icon_img, bmp)
            val count = BadgeListenerService.countFor(pkg)
            if (count > 0) {
                v.setTextViewText(R.id.w_icon_badge, if (count > 99) "99+" else count.toString())
                v.setViewVisibility(R.id.w_icon_badge, View.VISIBLE)
            } else {
                v.setViewVisibility(R.id.w_icon_badge, View.GONE)
            }
            val launchIntent = if (act.isNotEmpty()) {
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(ComponentName(pkg, act)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                c.packageManager.getLaunchIntentForPackage(pkg) ?: Intent(c, MainActivity::class.java)
            }
            val pi = PendingIntent.getActivity(
                c, 3000 + appWidgetId, launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            v.setOnClickPendingIntent(R.id.w_icon_root, pi)
        } else {
            val bmp = SahlThemeShortcuts.createSahlSealIconBitmap(null, "۞", styleMode)
            v.setImageViewBitmap(R.id.w_icon_img, bmp)
            v.setViewVisibility(R.id.w_icon_badge, View.GONE)
            v.setOnClickPendingIntent(R.id.w_icon_root, popupPendingIntent(c, 4000 + appWidgetId, folderId))
        }
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
        m.getAppWidgetIds(ComponentName(ctx, AnalogClockWidget::class.java))
            .forEach { m.updateAppWidget(it, buildAnalogClockViews(ctx)) }
        m.getAppWidgetIds(ComponentName(ctx, ClockWidget::class.java))
            .forEach { m.updateAppWidget(it, buildClockViews(ctx)) }
        m.getAppWidgetIds(ComponentName(ctx, SanctuaryWidget::class.java))
            .forEach { m.updateAppWidget(it, buildSanctuaryViews(ctx)) }
        m.getAppWidgetIds(ComponentName(ctx, UnionHubWidget::class.java))
            .forEach { m.updateAppWidget(it, buildUnionHubViews(ctx)) }
        m.getAppWidgetIds(ComponentName(ctx, SahlBarWidget::class.java))
            .forEach { m.updateAppWidget(it, buildSahlBarViews(ctx)) }
        m.getAppWidgetIds(ComponentName(ctx, ThemedIconWidget::class.java))
            .forEach { m.updateAppWidget(it, buildThemedIconViews(ctx, it)) }
    }
}

class AnalogClockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, SahlWidgets.buildAnalogClockViews(context)) }
    }
}

class ClockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, SahlWidgets.buildClockViews(context)) }
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

class ThemedIconWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, SahlWidgets.buildThemedIconViews(context, it)) }
    }
}

class ThemedIconConfigureActivity : SahlActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val p = Sahl.prefs(this)
        val pendingTime = p.getLong("pending_icon_time", 0L)
        if (System.currentTimeMillis() - pendingTime < 15_000L) {
            val type = p.getString("pending_icon_type", "folder") ?: "folder"
            val pkg = p.getString("pending_icon_pkg", "") ?: ""
            val act = p.getString("pending_icon_act", "") ?: ""
            val lbl = p.getString("pending_icon_label", "Sahl") ?: "Sahl"
            val fid = p.getString("pending_icon_folder", "all_sahl") ?: "all_sahl"
            val showLbl = p.getBoolean("pending_icon_show_label", SahlThemeShortcuts.defaultShowLabel(this))
            val styleMode = p.getInt("pending_icon_style_mode", SahlThemeShortcuts.defaultIconStyle(this))
            p.edit().putLong("pending_icon_time", 0L).commit()
            completeConfig(type, pkg, act, lbl, fid, showLbl, styleMode)
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(col(R.color.bg))
        }
        root.addView(header(getString(R.string.sec_icon_styler), true))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(8), px(16), px(28))
        }

        val ar = resources.configuration.locales[0].language == "ar"
        val foldersCard = card()
        val allFolderTitle = if (ar) "تطبيقات سهل" else "Sahl Apps"
        foldersCard.addView(text("۞  $allFolderTitle", 16f, col(R.color.emerald), true).apply {
            setPadding(px(8), px(10), px(8), px(10))
            setOnClickListener {
                completeConfig("folder", "", "", allFolderTitle, "all_sahl",
                    SahlThemeShortcuts.defaultShowLabel(this@ThemedIconConfigureActivity),
                    SahlThemeShortcuts.defaultIconStyle(this@ThemedIconConfigureActivity))
            }
        }.press(12f))

        for (f in IslamicHomeData.folders(this)) {
            val fTitle = if (ar) f.nameAr else f.nameEn
            foldersCard.addView(text("۞  $fTitle", 15f, col(R.color.text), true).apply {
                setPadding(px(8), px(10), px(8), px(10))
                setOnClickListener {
                    completeConfig("folder", "", "", fTitle, f.id,
                        SahlThemeShortcuts.defaultShowLabel(this@ThemedIconConfigureActivity),
                        SahlThemeShortcuts.defaultIconStyle(this@ThemedIconConfigureActivity))
                }
            }.press(12f))
        }
        col.addView(foldersCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(14) })

        val appsGrid = GridLayout(this).apply { columnCount = 4 }
        col.addView(appsGrid, LinearLayout.LayoutParams(-1, -2))
        root.addView(ScrollView(this).apply { addView(col) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        Thread {
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(mainIntent, 0).mapNotNull { ri ->
                val ai = ri.activityInfo ?: return@mapNotNull null
                if (ai.packageName == packageName) return@mapNotNull null
                val lbl = ri.loadLabel(pm).toString()
                MainActivity.AppItem(lbl, Norm.text(lbl), ai.packageName, ai.name, ri.loadIcon(pm))
            }.sortedBy { it.normKey }
            runOnUiThread {
                val cellW = (resources.displayMetrics.widthPixels - px(48)) / 4
                val curStyle = SahlThemeShortcuts.defaultIconStyle(this@ThemedIconConfigureActivity)
                val curShowLbl = SahlThemeShortcuts.defaultShowLabel(this@ThemedIconConfigureActivity)
                for (app in apps.take(80)) {
                    val item = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(px(4), px(8), px(4), px(8))
                        layoutParams = GridLayout.LayoutParams().apply { width = cellW }
                        val bmp = SahlThemeShortcuts.createSahlSealIconBitmap(app.icon, null, curStyle)
                        addView(ImageView(this@ThemedIconConfigureActivity).apply {
                            setImageBitmap(bmp)
                        }, LinearLayout.LayoutParams(px(54), px(54)))
                        if (curShowLbl) {
                            addView(text(app.label, 11f, col(R.color.text), true).apply {
                                maxLines = 1
                                ellipsize = TextUtils.TruncateAt.END
                                gravity = Gravity.CENTER
                                setPadding(0, px(4), 0, 0)
                            })
                        }
                        setOnClickListener {
                            completeConfig("app", app.pkg, app.activity, app.label, "", curShowLbl, curStyle)
                        }
                    }.press(12f)
                    appsGrid.addView(item)
                }
            }
        }.start()
    }

    private fun completeConfig(
        type: String,
        pkg: String,
        act: String,
        label: String,
        folderId: String,
        showLabel: Boolean,
        styleMode: Int
    ) {
        SahlThemeShortcuts.bindWidgetId(this, appWidgetId, type, pkg, act, label, folderId, showLabel, styleMode)
        val awm = AppWidgetManager.getInstance(this)
        awm.updateAppWidget(appWidgetId, SahlWidgets.buildThemedIconViews(this, appWidgetId))
        val res = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, res)
        finish()
    }
}

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
            folder != null -> "۞  " + (if (ar) folder.nameAr else folder.nameEn)
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
