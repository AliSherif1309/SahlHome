package com.sahl.home

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Sahl System Sound Pack & Ringtone Manager.
 * Bundles curated acoustic Islamic & Sahl signature sounds and installs them
 * as system-wide Ringtones, Notification tones, and Alarms via MediaStore + RingtoneManager.
 */
object SahlSounds {
    data class SoundItem(
        val id: String,
        val rawRes: Int,
        val fileName: String,
        val titleRes: Int,
        val descRes: Int,
        val primaryType: Int
    )

    val items: List<SoundItem> = listOf(
        SoundItem(
            id = "sahl_ring_hijaz",
            rawRes = R.raw.sahl_ring_hijaz,
            fileName = "Sahl_Hijaz_Melody.wav",
            titleRes = R.string.snd_hijaz_title,
            descRes = R.string.snd_hijaz_desc,
            primaryType = RingtoneManager.TYPE_RINGTONE
        ),
        SoundItem(
            id = "sahl_chime",
            rawRes = R.raw.sahl_chime,
            fileName = "Sahl_Chime.wav",
            titleRes = R.string.snd_chime_title,
            descRes = R.string.snd_chime_desc,
            primaryType = RingtoneManager.TYPE_NOTIFICATION
        ),
        SoundItem(
            id = "sahl_notif_drop",
            rawRes = R.raw.sahl_notif_drop,
            fileName = "Sahl_Emerald_Dewdrop.wav",
            titleRes = R.string.snd_drop_title,
            descRes = R.string.snd_drop_desc,
            primaryType = RingtoneManager.TYPE_NOTIFICATION
        ),
        SoundItem(
            id = "sahl_notif_oud",
            rawRes = R.raw.sahl_notif_oud,
            fileName = "Sahl_Oud_Pluck.wav",
            titleRes = R.string.snd_oud_title,
            descRes = R.string.snd_oud_desc,
            primaryType = RingtoneManager.TYPE_NOTIFICATION
        ),
        SoundItem(
            id = "sahl_alarm_fajr",
            rawRes = R.raw.sahl_alarm_fajr,
            fileName = "Sahl_Fajr_Dawn.wav",
            titleRes = R.string.snd_fajr_title,
            descRes = R.string.snd_fajr_desc,
            primaryType = RingtoneManager.TYPE_ALARM
        )
    )

    private var player: MediaPlayer? = null
    var playingId: String? = null
        private set

    fun playPreview(context: Context, item: SoundItem, onDone: () -> Unit) {
        val wasSame = playingId == item.id
        stopPreview()
        if (wasSame) {
            onDone()
            return
        }
        try {
            val uri = Uri.parse("android.resource://${context.packageName}/${item.rawRes}")
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context, uri)
                setOnCompletionListener {
                    stopPreview()
                    onDone()
                }
                setOnErrorListener { _, _, _ ->
                    stopPreview()
                    onDone()
                    true
                }
                prepare()
                start()
            }
            player = mp
            playingId = item.id
        } catch (_: Exception) {
            stopPreview()
            onDone()
        }
    }

    fun stopPreview() {
        playingId = null
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }

    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun openWriteSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun activeId(context: Context, ringtoneType: Int): String? {
        val key = prefKeyForType(ringtoneType)
        return Sahl.prefs(context).getString(key, null)
    }

    private fun prefKeyForType(ringtoneType: Int): String = when (ringtoneType) {
        RingtoneManager.TYPE_RINGTONE -> "sound_active_ringtone"
        RingtoneManager.TYPE_ALARM -> "sound_active_alarm"
        else -> "sound_active_notification"
    }

    fun installAndSetDefault(context: Context, item: SoundItem, ringtoneType: Int): Boolean {
        if (!canWriteSettings(context)) return false
        return try {
            val uri = ensureMediaStoreUri(context, item) ?: return false
            RingtoneManager.setActualDefaultRingtoneUri(context, ringtoneType, uri)
            Sahl.prefs(context).edit().putString(prefKeyForType(ringtoneType), item.id).apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun applyFullPack(context: Context): Boolean {
        if (!canWriteSettings(context)) return false
        val ring = items.firstOrNull { it.id == "sahl_ring_hijaz" } ?: return false
        val notif = items.firstOrNull { it.id == "sahl_chime" } ?: return false
        val alarm = items.firstOrNull { it.id == "sahl_alarm_fajr" } ?: return false
        val ok1 = installAndSetDefault(context, ring, RingtoneManager.TYPE_RINGTONE)
        val ok2 = installAndSetDefault(context, notif, RingtoneManager.TYPE_NOTIFICATION)
        val ok3 = installAndSetDefault(context, alarm, RingtoneManager.TYPE_ALARM)
        return ok1 && ok2 && ok3
    }

    private fun ensureMediaStoreUri(context: Context, item: SoundItem): Uri? {
        val resolver = context.contentResolver
        val title = context.getString(item.titleRes)

        // Reuse existing MediaStore entry if already installed
        findExistingUri(context, item.fileName)?.let { existing ->
            // Ensure flags allow all 3 uses
            try {
                val update = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_RINGTONE, 1)
                    put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
                    put(MediaStore.Audio.Media.IS_ALARM, 1)
                }
                resolver.update(existing, update, null, null)
            } catch (_: Exception) {
            }
            return existing
        }

        val relativeSubDir = when (item.primaryType) {
            RingtoneManager.TYPE_RINGTONE -> Environment.DIRECTORY_RINGTONES
            RingtoneManager.TYPE_ALARM -> Environment.DIRECTORY_ALARMS
            else -> Environment.DIRECTORY_NOTIFICATIONS
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, item.fileName)
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, "Sahl")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "$relativeSubDir/Sahl")
                put(MediaStore.Audio.Media.IS_RINGTONE, 1)
                put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
                put(MediaStore.Audio.Media.IS_ALARM, 1)
                put(MediaStore.Audio.Media.IS_MUSIC, 0)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    context.resources.openRawResource(item.rawRes).use { input ->
                        input.copyTo(out)
                    }
                }
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (_: Exception) {
                try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                null
            }
        } else {
            @Suppress("DEPRECATION")
            val baseDir = Environment.getExternalStoragePublicDirectory(relativeSubDir)
            val sahlDir = File(baseDir, "Sahl").apply { mkdirs() }
            val outFile = File(sahlDir, item.fileName)
            context.resources.openRawResource(item.rawRes).use { input ->
                FileOutputStream(outFile).use { out -> input.copyTo(out) }
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DATA, outFile.absolutePath)
                put(MediaStore.MediaColumns.TITLE, title)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                put(MediaStore.Audio.Media.ARTIST, "Sahl")
                put(MediaStore.Audio.Media.IS_RINGTONE, 1)
                put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
                put(MediaStore.Audio.Media.IS_ALARM, 1)
                put(MediaStore.Audio.Media.IS_MUSIC, 0)
            }
            resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        }
    }

    private fun findExistingUri(context: Context, fileName: String): Uri? {
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.ARTIST} = ?"
        val args = arrayOf(fileName, "Sahl")
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            null
        )?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                return Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
            }
        }
        return null
    }

    /** Exports all Sahl sound pack WAV files to Music/SahlSoundPack so any app or file manager can pick them. */
    fun exportAllToDevice(context: Context): Int {
        var count = 0
        val resolver = context.contentResolver
        for (item in items) {
            try {
                val title = context.getString(item.titleRes)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, item.fileName)
                        put(MediaStore.Audio.Media.TITLE, title)
                        put(MediaStore.Audio.Media.ARTIST, "Sahl")
                        put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                        put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/SahlSoundPack")
                        put(MediaStore.Audio.Media.IS_RINGTONE, 1)
                        put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
                        put(MediaStore.Audio.Media.IS_ALARM, 1)
                        put(MediaStore.Audio.Media.IS_MUSIC, 1)
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: continue
                    try {
                        resolver.openOutputStream(uri)?.use { out ->
                            context.resources.openRawResource(item.rawRes).use { input -> input.copyTo(out) }
                        }
                        values.clear()
                        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                        count++
                    } catch (_: Exception) {
                        try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "SahlSoundPack").apply { mkdirs() }
                    val outFile = File(dir, item.fileName)
                    context.resources.openRawResource(item.rawRes).use { input ->
                        FileOutputStream(outFile).use { out -> input.copyTo(out) }
                    }
                    count++
                }
            } catch (_: Exception) {
            }
        }
        return count
    }

    /** Packages all Sahl sound pack WAV files into Sahl_Sound_Pack.zip and opens the Android share sheet. */
    fun sharePackZip(context: Context): Boolean {
        return try {
            val zipFile = File(context.cacheDir, "Sahl_Sound_Pack.zip")
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                for (item in items) {
                    zos.putNextEntry(ZipEntry(item.fileName))
                    context.resources.openRawResource(item.rawRes).use { input ->
                        input.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Sahl Sound Pack")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(share, context.getString(R.string.share_sound_pack)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }
}
