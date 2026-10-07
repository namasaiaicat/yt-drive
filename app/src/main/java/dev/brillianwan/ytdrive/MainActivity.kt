package dev.brillianwan.ytdrive

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var urlText by mutableStateOf("")
    private var folderUriStr by mutableStateOf<String?>(null)
    private var folderLabel by mutableStateOf("Belum ada folder tujuan")
    private var infoMsg by mutableStateOf<String?>(null)
    private var updating by mutableStateOf(false)

    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
            lifecycleScope.launch {
                Prefs.setFolderUri(this@MainActivity, uri.toString())
                folderUriStr = uri.toString()
                folderLabel = docLabel(uri)
                infoMsg = "Folder tujuan tersimpan."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        lifecycleScope.launch {
            Prefs.getFolderUri(this@MainActivity)?.let { saved ->
                folderUriStr = saved
                folderLabel = docLabel(Uri.parse(saved))
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
            )
        }
        setContent {
            YtDriveTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Android 10+ hanya mengizinkan baca clipboard saat app foreground → di onResume. */
    override fun onResume() {
        super.onResume()
        runCatching {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: return
            val found = extractFirstUrl(clip) ?: return
            if (isYouTubeLink(found) && urlText != found) urlText = found
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val found = extractFirstUrl(text) ?: return
            if (isYouTubeLink(found)) urlText = found
        }
    }

    private fun docLabel(uri: Uri): String =
        runCatching { DocumentFile.fromTreeUri(this, uri)?.name }
            .getOrNull() ?: uri.lastPathSegment ?: uri.toString()

    private fun onGenerate() {
        val raw = urlText.trim()
        val link = extractFirstUrl(raw) ?: raw
        if (!isYouTubeLink(link)) {
            infoMsg = "Link tidak valid. Tempel link YouTube (youtube.com / youtu.be)."
            return
        }
        val folder = folderUriStr
        if (folder == null) {
            infoMsg = "Pilih dulu folder tujuan (Google Drive)."
            folderPicker.launch(null)
            return
        }
        infoMsg = null
        val i = Intent(this, DownloadService::class.java).apply {
            putExtra(DownloadService.EXTRA_URL, link)
            putExtra(DownloadService.EXTRA_FOLDER, folder)
        }
        ContextCompat.startForegroundService(this, i)
    }

    private fun onCancel() {
        val i = Intent(this, DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL
        }
        startService(i)
    }

    private fun onUpdateYtDlp() {
        if (updating) return
        updating = true
        infoMsg = "Memperbarui yt-dlp…"
        (application as YtDriveApp).updateYtDlpManual { _, msg ->
            runOnUiThread {
                updating = false
                infoMsg = msg
            }
        }
    }

    @Composable
    private fun YtDriveTheme(content: @Composable () -> Unit) {
        val scheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
        MaterialTheme(colorScheme = scheme, content = content)
    }

    @Composable
    private fun HomeScreen() {
        val running by DownloadBus.running.collectAsState()
        val progress by DownloadBus.progress.collectAsState()
        val status by DownloadBus.status.collectAsState()
        val finished by DownloadBus.finished.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("YT Drive", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Download video YouTube jadi MP4, tersimpan otomatis ke folder Google Drive.",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text("Link YouTube") },
                placeholder = { Text("Tempel link di sini…") },
                singleLine = true,
                enabled = !running,
                modifier = Modifier.fillMaxWidth(),
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Folder tujuan", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(folderLabel, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { folderPicker.launch(null) }, enabled = !running) {
                            Text(if (folderUriStr == null) "Pilih folder" else "Ganti folder")
                        }
                        TextButton(onClick = { onUpdateYtDlp() }, enabled = !updating && !running) {
                            Text(if (updating) "Memperbarui…" else "Update yt-dlp")
                        }
                    }
                }
            }

            Button(
                onClick = { onGenerate() },
                enabled = !running && urlText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
            ) {
                Text("Generate MP4", style = MaterialTheme.typography.titleMedium)
            }

            if (running) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("$status  ($progress%)", style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = { onCancel() },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Batal")
                }
            }

            finished?.let {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(it, modifier = Modifier.padding(14.dp))
                }
            }

            infoMsg?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
