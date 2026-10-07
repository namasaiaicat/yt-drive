package dev.brillianwan.ytdrive

import android.app.Application
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class YtDriveApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
        } catch (e: YoutubeDLException) {
            Log.e(TAG, "Init youtubedl gagal", e)
        }
        // Update yt-dlp otomatis di background setiap app dibuka.
        scope.launch {
            try {
                YoutubeDL.getInstance()
                    .updateYoutubeDL(this@YtDriveApp, YoutubeDL.UpdateChannel.STABLE)
            } catch (e: Exception) {
                Log.w(TAG, "Auto-update yt-dlp gagal: ${e.message}")
            }
        }
    }

    /** Dipanggil tombol "Update yt-dlp" manual. Callback jalan di thread background. */
    fun updateYtDlpManual(onDone: (ok: Boolean, msg: String) -> Unit) {
        scope.launch {
            try {
                val status = YoutubeDL.getInstance()
                    .updateYoutubeDL(this@YtDriveApp, YoutubeDL.UpdateChannel.STABLE)
                val msg = if (status == YoutubeDL.UpdateStatus.DONE) {
                    "yt-dlp berhasil diperbarui."
                } else {
                    "yt-dlp sudah versi terbaru."
                }
                onDone(true, msg)
            } catch (e: Exception) {
                onDone(false, "Update gagal: ${(e.message ?: "error jaringan").take(150)}")
            }
        }
    }

    companion object {
        private const val TAG = "YtDriveApp"
    }
}
