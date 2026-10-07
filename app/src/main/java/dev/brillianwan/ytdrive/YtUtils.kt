package dev.brillianwan.ytdrive

private val YT_PATTERNS = listOf(
    Regex("""(?:https?://)?(?:www\.|m\.)?youtube\.com/watch\?.*v=[\w-]{6,}""", RegexOption.IGNORE_CASE),
    Regex("""(?:https?://)?(?:www\.|m\.)?youtube\.com/shorts/[\w-]{6,}""", RegexOption.IGNORE_CASE),
    Regex("""(?:https?://)?(?:www\.|m\.)?youtube\.com/live/[\w-]{6,}""", RegexOption.IGNORE_CASE),
    Regex("""(?:https?://)?youtu\.be/[\w-]{6,}""", RegexOption.IGNORE_CASE),
    Regex("""(?:https?://)?music\.youtube\.com/watch\?.*v=[\w-]{6,}""", RegexOption.IGNORE_CASE),
    Regex("""(?:https?://)?(?:www\.|m\.)?youtube\.com/embed/[\w-]{6,}""", RegexOption.IGNORE_CASE),
)

/** True jika teks mengandung link YouTube yang valid. */
fun isYouTubeLink(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty()) return false
    return YT_PATTERNS.any { it.containsMatchIn(t) }
}

/** Ambil URL pertama dari teks (hasil share / clipboard). */
fun extractFirstUrl(text: String): String? {
    val m = Regex("""https?://[^\s]+""").find(text.trim()) ?: return null
    return m.value.trimEnd('.', ',', ')', ']', '!', ';', '"', '\'')
}

/** Bersihkan judul video dari karakter terlarang nama file. */
fun sanitizeFileName(name: String): String {
    var s = name.trim()
    if (s.isEmpty()) return "video"
    s = s.replace(Regex("""[\\/:*?"<>|]"""), "_")
    s = s.replace(Regex("""\p{Cntrl}"""), "")
    s = s.trim().trim('.', ' ')
    if (s.isEmpty()) s = "video"
    return if (s.length > 120) s.take(120).trimEnd() else s
}

/** Petakan error teknis yt-dlp menjadi pesan yang jelas untuk user. */
fun mapDownloadError(t: Throwable): String {
    val msg = ((t.message ?: "") + " " + (t.cause?.message ?: ""))
    return when {
        msg.contains("private", ignoreCase = true) ->
            "Video bersifat pribadi dan tidak bisa diunduh."
        msg.contains("age", ignoreCase = true) ||
            msg.contains("sign in", ignoreCase = true) ||
            msg.contains("login required", ignoreCase = true) ->
            "Video dibatasi umur / butuh login dan tidak bisa diunduh."
        msg.contains("unsupported url", ignoreCase = true) ||
            msg.contains("no video", ignoreCase = true) ||
            msg.contains("not a valid url", ignoreCase = true) ->
            "Link tidak valid atau tidak ada video di URL tersebut."
        msg.contains("unable to download", ignoreCase = true) ||
            msg.contains("network", ignoreCase = true) ||
            msg.contains("timed out", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) ||
            msg.contains("connection", ignoreCase = true) ||
            msg.contains("unknownhost", ignoreCase = true) ->
            "Koneksi putus atau tidak stabil. Coba lagi."
        msg.contains("no space", ignoreCase = true) ||
            msg.contains("enospc", ignoreCase = true) ->
            "Ruang penyimpanan tidak cukup."
        msg.contains("permission", ignoreCase = true) ||
            msg.contains("denied", ignoreCase = true) ||
            msg.contains("securityexception", ignoreCase = true) ->
            "Izin folder ditolak. Pilih ulang folder tujuan."
        msg.contains("folder", ignoreCase = true) && msg.contains("pilih", ignoreCase = true) -> msg
        else -> "Gagal mengunduh: ${(t.message ?: "error tidak diketahui").take(220)}"
    }
}
