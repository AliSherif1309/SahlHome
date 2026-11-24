package com.sahl.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Sahl HeliBoard Integration:
 * Generates HeliBoard-compatible custom color theme JSONs (Emerald Night & Daylight Cream),
 * a custom Arabic Honorifics & Tashkeel row layout JSON, and provides 1-tap clipboard/export helpers.
 */
object SahlHeliBoard {

    private fun colorEntry(argb: Int): JSONObject = JSONObject().apply {
        put("first", argb)
        put("second", false)
    }

    fun darkThemeJson(): String = JSONObject().apply {
        put("name", "Sahl Emerald Night")
        put("moreColors", 0)
        put("colors", JSONObject().apply {
            put("background", colorEntry(0xFF071A15.toInt()))
            put("keys", colorEntry(0xFF0D2F27.toInt()))
            put("functional_keys", colorEntry(0xFF0A241E.toInt()))
            put("spacebar", colorEntry(0xFF0B5D4B.toInt()))
            put("text", colorEntry(0xFFF5F2E9.toInt()))
            put("hint_text", colorEntry(0xFFD4AF37.toInt()))
            put("accent", colorEntry(0xFFF5C84C.toInt()))
        })
    }.toString()

    fun lightThemeJson(): String = JSONObject().apply {
        put("name", "Sahl Daylight Cream")
        put("moreColors", 0)
        put("colors", JSONObject().apply {
            put("background", colorEntry(0xFFF4F1E6.toInt()))
            put("keys", colorEntry(0xFFFFFFFF.toInt()))
            put("functional_keys", colorEntry(0xFFE4EEE9.toInt()))
            put("spacebar", colorEntry(0xFF0B5D4B.toInt()))
            put("text", colorEntry(0xFF1B2A26.toInt()))
            put("hint_text", colorEntry(0xFFB88922.toInt()))
            put("accent", colorEntry(0xFF0B5D4B.toInt()))
        })
    }.toString()

    /**
     * HeliBoard custom 3-row layout (for Secondary Layouts -> More symbols or Symbols)
     * with Arabic numerals, spacious wide honorifics (ﷺ, ﷻ, ﷽, ؓ, ۞), and Tashkeel diacritics.
     */
    fun honorificsRowJson(): String {
        fun rowOf(items: List<Pair<String, Double>>): JSONArray {
            val arr = JSONArray()
            for ((label, w) in items) {
                arr.put(JSONObject().apply {
                    put("label", label)
                    put("width", w)
                })
            }
            return arr
        }
        val row1 = rowOf(listOf("١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", "٠").map { it to 0.10 })
        val row2 = rowOf(listOf("ﷺ", "ﷻ", "﷽", "ؓ", "۞").map { it to 0.20 })
        val row3 = rowOf(listOf("َ", "ُ", "ِ", "ْ", "ّ", "ً", "ٌ", "ٍ").map { it to 0.125 })
        return JSONArray().apply {
            put(row1)
            put(row2)
            put(row3)
        }.toString(2)
    }

    fun todayHijriString(context: Context): String = try {
        val loc = context.resources.configuration.locales[0]
        DateTimeFormatter.ofPattern("d MMMM yyyy هـ", loc).format(HijrahDate.now())
    } catch (_: Exception) {
        "1448 هـ"
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun exportPackToDownloads(context: Context): Int {
        val files = listOf(
            "Sahl_Emerald_Night.json" to darkThemeJson(),
            "Sahl_Daylight_Cream.json" to lightThemeJson(),
            "Sahl_Arabic_Honorifics_Row.json" to honorificsRowJson()
        )
        var count = 0
        val resolver = context.contentResolver
        for ((name, content) in files) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, "application/json")
                        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/SahlHeliBoard")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: continue
                    try {
                        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                        values.clear()
                        values.put(MediaStore.Downloads.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                        count++
                    } catch (_: Exception) {
                        try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SahlHeliBoard").apply { mkdirs() }
                    File(dir, name).writeText(content, Charsets.UTF_8)
                    count++
                }
            } catch (_: Exception) {
            }
        }
        return count
    }

    fun sharePackZip(context: Context): Boolean {
        val files = listOf(
            "Sahl_Emerald_Night.json" to darkThemeJson(),
            "Sahl_Daylight_Cream.json" to lightThemeJson(),
            "Sahl_Arabic_Honorifics_Row.json" to honorificsRowJson()
        )
        return try {
            val zipFile = File(context.cacheDir, "Sahl_HeliBoard_Pack.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                for ((name, content) in files) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(content.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Sahl HeliBoard Theme & Honorifics Pack")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(share, context.getString(R.string.hb_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }
}
