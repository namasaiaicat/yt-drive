package dev.brillianwan.ytdrive

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DownloadRepository(private val ctx: Context) {

    data class Result(val fileName: String, val bytes: Long)

    companion object {
        const val PROCESS_ID = "ytdrive-dl"
        const val FORMAT =
            "bv*[vcodec^=avc1]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b"
    }

    /**
     * Download + merge ke MP4 (tanpa re-encode) ke cache, salin ke folder
     * tujuan via SAF, lalu hapus cache. Selalu membersihkan cache walau gagal.
     */
    suspend fun download(
        url: String,
        folderUri: Uri,
        onProgress: (percent: Int, etaSec: Long, status: String) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        onProgress(0, -1, "Mengambil info video…")
        val title = try {
            YoutubeDL.getInstance().getInfo(url).title ?: "video"
        } catch (e: Exception) {
            throw Exception(mapDownloadError(e))
        }
        val safe = sanitizeFileName(title)

        val cache = File(ctx.cacheDir, "ytdrive").apply { mkdirs() }
        val outTemplate = File(cache, "$safe.%(ext)s").absolutePath

        val req = YoutubeDLRequest(url)
        req.addOption("-f", FORMAT)
        req.addOption("--merge-output-format", "mp4")
        req.addOption("--continue")
        req.addOption("--no-playlist")
        req.addOption("-o", outTemplate)

        try {
            YoutubeDL.getInstance().execute(req, PROCESS_ID) { progress, eta, _ ->
                onProgress(progress.toInt().coerceIn(0, 100), eta, "Mengunduh…")
            }
        } catch (e: YoutubeDL.CanceledException) {
            throw Exception("Unduhan dibatalkan.")
        } catch (e: Exception) {
            throw Exception(mapDownloadError(e))
        }

        // File MP4 hasil (terbaru di cache, bukan .part).
        val produced = (cache.listFiles() ?: emptyArray())
            .filter { it.isFile && it.extension.equals("mp4", ignoreCase = true) }
            .maxByOrNull { it.lastModified() }
            ?: throw Exception("File hasil tidak ditemukan di cache.")

        try {
            onProgress(100, -1, "Menyalin ke folder tujuan…")

            val tree = DocumentFile.fromTreeUri(ctx, folderUri)
                ?: throw Exception(
                    "Folder tujuan tidak bisa dibuka. Pilih ulang folder tujuan."
                )
            if (!tree.canWrite()) {
                throw Exception(
                    "Folder tujuan tidak bisa ditulis. Pilih ulang folder tujuan."
                )
            }
            // Provider Drive kadang menolak video/mp4 langsung → fallback octet-stream.
            val dest = runCatching {
                tree.createFile("video/mp4", produced.nameWithoutExtension)
            }.getOrNull() ?: runCatching {
                tree.createFile("application/octet-stream", produced.nameWithoutExtension)
            }.getOrNull()
                ?: throw Exception(
                    "Folder tujuan menolak pembuatan file. Coba lagi atau pilih folder lain."
                )

            try {
                ctx.contentResolver.openOutputStream(dest.uri, "w")?.use { out ->
                    produced.inputStream().use { inp ->
                        val buf = ByteArray(128 * 1024)
                        var n: Int
                        while (inp.read(buf).also { n = it } >= 0) {
                            if (n > 0) out.write(buf, 0, n)
                        }
                        out.flush()
                    }
                } ?: throw Exception("Tidak bisa menulis ke folder tujuan.")
            } catch (e: Exception) {
                runCatching { dest.delete() }
                throw e
            }

            val bytes = produced.length()
            return@withContext Result(dest.name ?: produced.name, bytes)
        } finally {
            // Cache selalu dibersihkan, sukses maupun gagal.
            runCatching { produced.delete() }
            runCatching {
                cache.listFiles()
                    ?.forEach { if (it.extension.equals("part", true)) it.delete() }
            }
        }
    }

    fun cancel() {
        YoutubeDL.getInstance().destroyProcessById(PROCESS_ID)
    }
}
