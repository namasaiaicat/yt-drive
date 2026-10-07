package dev.brillianwan.ytdrive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** State unduhan lintas Activity–Service (satu proses, tanpa database). */
object DownloadBus {
    val running = MutableStateFlow(false)
    val progress = MutableStateFlow(0)
    val status = MutableStateFlow("")
    val finished = MutableStateFlow<String?>(null)
}

/**
 * Foreground Service (tipe dataSync) supaya unduhan video berjam-jam
 * tidak dimatikan sistem.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "YT Drive", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "YT Drive selesai", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            YoutubeDL.getInstance().destroyProcessById(DownloadRepository.PROCESS_ID)
            DownloadBus.finished.value = "Unduhan dibatalkan."
            DownloadBus.running.value = false
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val url = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        val folder = intent.getStringExtra(EXTRA_FOLDER) ?: return START_NOT_STICKY
        if (DownloadBus.running.value) return START_NOT_STICKY

        DownloadBus.running.value = true
        DownloadBus.progress.value = 0
        DownloadBus.status.value = "Menyiapkan…"
        DownloadBus.finished.value = null
        startFg(buildProgress("Menyiapkan…", 0))

        scope.launch(Dispatchers.IO) {
            val repo = DownloadRepository(this@DownloadService)
            var lastTick = 0L
            try {
                val res = repo.download(url, Uri.parse(folder)) { p, eta, st ->
                    DownloadBus.progress.value = p
                    val etaTxt = if (eta >= 0) " • ETA ${eta}s" else ""
                    DownloadBus.status.value = "$st$etaTxt"
                    val now = System.currentTimeMillis()
                    if (now - lastTick > 1200 || p >= 100) {
                        lastTick = now
                        notifyProgress(DownloadBus.status.value, p)
                    }
                }
                val mb = res.bytes / 1048576.0
                val doneMsg = "Tersimpan: ${res.fileName} (${"%.1f".format(mb)} MB)"
                DownloadBus.finished.value = doneMsg
                notifyDone("Unduhan selesai", doneMsg)
            } catch (e: Exception) {
                val msg = e.message ?: "Unduhan gagal."
                DownloadBus.finished.value = msg
                notifyDone("Unduhan gagal", msg)
            } finally {
                DownloadBus.running.value = false
                ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startFg(notif: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 29) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_PROGRESS, notif, type)
    }

    private fun cancelIntent(): PendingIntent {
        val i = Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getService(this, 0, i, flags)
    }

    private fun openIntent(): PendingIntent {
        val i = packageManager.getLaunchIntentForPackage(packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getActivity(this, 0, i, flags)
    }

    private fun buildProgress(text: String, percent: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("YT Drive mengunduh")
            .setContentText(text)
            .setProgress(100, percent, percent <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Batal", cancelIntent())
            .setContentIntent(openIntent())
            .build()

    private fun notifyProgress(text: String, percent: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(NOTIF_PROGRESS, buildProgress(text, percent)) }
    }

    private fun notifyDone(title: String, text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent())
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIF_DONE, n) }
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_FOLDER = "extra_folder"
        const val ACTION_CANCEL = "dev.brillianwan.ytdrive.CANCEL"
        private const val CHANNEL_PROGRESS = "ytdrive_progress"
        private const val CHANNEL_DONE = "ytdrive_done"
        private const val NOTIF_PROGRESS = 1
        private const val NOTIF_DONE = 2
    }
}
