package com.sahl.home

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Islamic Essentials & Nova-Class Launcher State:
 *  - Daily Ayah & Morning/Evening Adhkar in Arabic & English
 *  - Customizable Desktop Pinned Apps, Dock Apps, Smart Folders, Hidden Apps, and Pinned AppWidget IDs
 *  - Full JSON Backup & Restore (compatible with Sahl Sync)
 */
object IslamicHomeData {
    data class Ayah(val ar: String, val en: String, val refAr: String, val refEn: String)
    data class Dhikr(val titleAr: String, val titleEn: String, val ar: String, val en: String)
    data class Folder(val id: String, var nameAr: String, var nameEn: String, val packages: MutableList<String>)

    val ayahs = listOf(
        Ayah(
            "أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ",
            "Verily, in the remembrance of Allah do hearts find rest.",
            "سورة الرعد · ٢٨",
            "Surah Ar-Ra'd · 13:28"
        ),
        Ayah(
            "فَإِنَّ مَعَ الْعُسْرِ يُسْرًا ۝ إِنَّ مَعَ الْعُسْرِ يُسْرًا",
            "For indeed, with hardship comes ease. Indeed, with hardship comes ease.",
            "سورة الشرح · ٥-٦",
            "Surah Ash-Sharh · 94:5-6"
        ),
        Ayah(
            "وَمَن يَتَّقِ اللَّهَ يَجْعَل لَّهُ مَخْرَجًا ۝ وَيَرْزُقْهُ مِنْ حَيْثُ لَا يَحْتَسِبُ",
            "Whoever is mindful of Allah, He will make a way out for them and provide from where they do not expect.",
            "سورة الطلاق · ٢-٣",
            "Surah At-Talaq · 65:2-3"
        ),
        Ayah(
            "رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الْآخِرَةِ حَسَنَةً",
            "Our Lord, grant us good in this world and good in the Hereafter.",
            "سورة البقرة · ٢٠١",
            "Surah Al-Baqarah · 2:201"
        ),
        Ayah(
            "وَقُل رَّبِّ زِدْنِي عِلْمًا",
            "And say: My Lord, increase me in knowledge.",
            "سورة طه · ١١٤",
            "Surah Ta-Ha · 20:114"
        ),
        Ayah(
            "إِنَّ اللَّهَ مَعَ الصَّابِرِينَ",
            "Indeed, Allah is with the patient.",
            "سورة البقرة · ١٥٣",
            "Surah Al-Baqarah · 2:153"
        ),
        Ayah(
            "وَهُوَ مَعَكُمْ أَيْنَ مَا كُنتُمْ",
            "And He is with you wherever you are.",
            "سورة الحديد · ٤",
            "Surah Al-Hadid · 57:4"
        )
    )

    fun todayAyah(): Ayah {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        return ayahs[day % ayahs.size]
    }

    fun currentDhikr(): Dhikr {
        val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hr in 4..11 -> Dhikr(
                "أذكار الصباح",
                "Morning Remembrance",
                "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ",
                "We have entered the morning and the whole kingdom belongs to Allah, and all praise is due to Allah."
            )
            hr in 15..20 -> Dhikr(
                "أذكار المساء",
                "Evening Remembrance",
                "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ",
                "We have entered the evening and the whole kingdom belongs to Allah, and all praise is due to Allah."
            )
            else -> Dhikr(
                "ذكر وتسبيح",
                "Daily Tasbih",
                "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحَانَ اللَّهِ الْعَظِيمِ",
                "Glory and praise be to Allah; Glory be to Allah, the Supreme."
            )
        }
    }

    /** Translates any prayer name (Arabic or English) to match Sahl Home's active language. */
    fun localizedPrayerName(rawName: String, isArabic: Boolean): String {
        val n = rawName.trim()
        val pairs = listOf(
            "Fajr" to "الفجر",
            "Sunrise" to "الشروق",
            "Dhuhr" to "الظهر",
            "Asr" to "العصر",
            "Maghrib" to "المغرب",
            "Isha" to "العشاء",
            "Jumuah" to "الجمعة"
        )
        for ((en, ar) in pairs) {
            if (n.equals(en, true) || n == ar || n.contains(ar)) {
                return if (isArabic) ar else en
            }
        }
        return n
    }

    val defaultDesktopApps = listOf(
        "com.sahl.phone", "com.sahl.contacts", "com.sahl.messages", "com.sahl.adhan",
        "com.sahl.qibla", "com.aly.trueqibla", "com.sahl.files", "com.sahl.gallery",
        "com.sahl.camera", "com.sahl.tools", "com.aly.sahlsync", "com.aly.sahlpdf", "com.sahl.pdf", "com.sahl.timer"
    )

    val defaultDockApps = listOf(
        "com.sahl.phone", "com.sahl.messages", "com.sahl.adhan", "com.sahl.camera"
    )

    fun gridColumns(c: Context): Int = Sahl.prefs(c).getInt("grid_cols", 4).coerceIn(4, 5)
    fun setGridColumns(c: Context, cols: Int) = Sahl.prefs(c).edit().putInt("grid_cols", cols.coerceIn(4, 5)).apply()

    fun showLabels(c: Context): Boolean = Sahl.prefs(c).getBoolean("show_labels", true)
    fun setShowLabels(c: Context, show: Boolean) = Sahl.prefs(c).edit().putBoolean("show_labels", show).apply()

    fun frameThirdParty(c: Context): Boolean = Sahl.prefs(c).getBoolean("frame_3p", true)
    fun setFrameThirdParty(c: Context, frame: Boolean) = Sahl.prefs(c).edit().putBoolean("frame_3p", frame).apply()

    fun doubleTapAction(c: Context): String = Sahl.prefs(c).getString("dt_action", "search") ?: "search"
    fun setDoubleTapAction(c: Context, act: String) = Sahl.prefs(c).edit().putString("dt_action", act).apply()

    fun pinnedDesktopPackages(c: Context): MutableList<String> {
        val raw = Sahl.prefs(c).getString("pinned_desktop", null)
            ?: return defaultDesktopApps.toMutableList()
        val arr = try { JSONArray(raw) } catch (_: Exception) { return defaultDesktopApps.toMutableList() }
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) out.add(arr.getString(i))
        return out
    }

    fun savePinnedDesktopPackages(c: Context, pkgs: List<String>) {
        val arr = JSONArray()
        pkgs.distinct().forEach { arr.put(it) }
        Sahl.prefs(c).edit().putString("pinned_desktop", arr.toString()).apply()
    }

    fun dockPackages(c: Context): MutableList<String> {
        val raw = Sahl.prefs(c).getString("dock_pkgs", null)
            ?: return defaultDockApps.toMutableList()
        val arr = try { JSONArray(raw) } catch (_: Exception) { return defaultDockApps.toMutableList() }
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) out.add(arr.getString(i))
        return out
    }

    fun saveDockPackages(c: Context, pkgs: List<String>) {
        val arr = JSONArray()
        pkgs.distinct().take(5).forEach { arr.put(it) }
        Sahl.prefs(c).edit().putString("dock_pkgs", arr.toString()).apply()
    }

    fun hiddenPackages(c: Context): MutableSet<String> {
        val raw = Sahl.prefs(c).getString("hidden_pkgs", "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        val out = LinkedHashSet<String>()
        for (i in 0 until arr.length()) out.add(arr.getString(i))
        return out
    }

    fun saveHiddenPackages(c: Context, pkgs: Set<String>) {
        val arr = JSONArray()
        pkgs.forEach { arr.put(it) }
        Sahl.prefs(c).edit().putString("hidden_pkgs", arr.toString()).apply()
    }

    fun folders(c: Context): MutableList<Folder> {
        val raw = Sahl.prefs(c).getString("folders_json", null)
        if (raw == null) {
            return mutableListOf(
                Folder(
                    "islamic", "إسلاميات وصلاة", "Islamic & Prayer",
                    mutableListOf("com.sahl.adhan", "com.sahl.qibla", "com.aly.trueqibla", "com.aly.sahlpdf", "com.sahl.pdf")
                ),
                Folder(
                    "sahl_comm", "تواصل سهل", "Sahl Communication",
                    mutableListOf("com.sahl.phone", "com.sahl.contacts", "com.sahl.messages")
                ),
                Folder(
                    "sahl_media", "ملفات ومعرض", "Files & Media",
                    mutableListOf("com.sahl.files", "com.aly.sahlpdf", "com.sahl.gallery", "com.sahl.camera", "com.aly.sahlsync")
                ),
                Folder(
                    "sahl_utils", "أدوات ومؤقت", "Tools & Timer",
                    mutableListOf("com.sahl.tools", "com.sahl.timer")
                )
            )
        }
        val arr = try { JSONArray(raw) } catch (_: Exception) { return mutableListOf() }
        val list = ArrayList<Folder>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val pArr = o.optJSONArray("pkgs") ?: JSONArray()
            val pkgs = ArrayList<String>()
            for (j in 0 until pArr.length()) pkgs.add(pArr.getString(j))
            list.add(Folder(o.optString("id", "f$i"), o.optString("ar", "مجلد"), o.optString("en", "Folder"), pkgs))
        }
        return list
    }

    fun saveFolders(c: Context, list: List<Folder>) {
        val arr = JSONArray()
        for (f in list) {
            val o = JSONObject()
            o.put("id", f.id)
            o.put("ar", f.nameAr)
            o.put("en", f.nameEn)
            val pArr = JSONArray()
            f.packages.distinct().forEach { pArr.put(it) }
            o.put("pkgs", pArr)
            arr.put(o)
        }
        Sahl.prefs(c).edit().putString("folders_json", arr.toString()).apply()
    }

    fun widgetIds(c: Context): MutableList<Int> {
        val raw = Sahl.prefs(c).getString("appwidget_ids", "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        val out = ArrayList<Int>()
        for (i in 0 until arr.length()) out.add(arr.getInt(i))
        return out
    }

    fun saveWidgetIds(c: Context, ids: List<Int>) {
        val arr = JSONArray()
        ids.distinct().forEach { arr.put(it) }
        Sahl.prefs(c).edit().putString("appwidget_ids", arr.toString()).apply()
    }

    fun exportBackupJson(c: Context): String {
        val root = JSONObject()
        root.put("app", "com.sahl.home")
        root.put("version", 2)
        root.put("wp_style", WallpaperEngine.currentStyle(c))
        root.put("grid_cols", gridColumns(c))
        root.put("show_labels", showLabels(c))
        root.put("frame_3p", frameThirdParty(c))
        root.put("dt_action", doubleTapAction(c))
        root.put("pinned_desktop", JSONArray(pinnedDesktopPackages(c)))
        root.put("dock_pkgs", JSONArray(dockPackages(c)))
        root.put("hidden_pkgs", JSONArray(hiddenPackages(c)))
        val fArr = JSONArray(Sahl.prefs(c).getString("folders_json", "[]"))
        root.put("folders", fArr)
        return root.toString(2)
    }

    fun importBackupJson(c: Context, jsonStr: String): Boolean = try {
        val root = JSONObject(jsonStr)
        WallpaperEngine.setCurrentStyle(c, root.optString("wp_style", WallpaperEngine.STYLE_GIRIH))
        setGridColumns(c, root.optInt("grid_cols", 4))
        setShowLabels(c, root.optBoolean("show_labels", true))
        setFrameThirdParty(c, root.optBoolean("frame_3p", true))
        setDoubleTapAction(c, root.optString("dt_action", "search"))
        root.optJSONArray("pinned_desktop")?.let {
            val l = ArrayList<String>()
            for (i in 0 until it.length()) l.add(it.getString(i))
            savePinnedDesktopPackages(c, l)
        }
        root.optJSONArray("dock_pkgs")?.let {
            val l = ArrayList<String>()
            for (i in 0 until it.length()) l.add(it.getString(i))
            saveDockPackages(c, l)
        }
        root.optJSONArray("hidden_pkgs")?.let {
            val s = LinkedHashSet<String>()
            for (i in 0 until it.length()) s.add(it.getString(i))
            saveHiddenPackages(c, s)
        }
        root.optJSONArray("folders")?.let {
            if (it.length() > 0) Sahl.prefs(c).edit().putString("folders_json", it.toString()).apply()
        }
        true
    } catch (_: Exception) {
        false
    }
}
