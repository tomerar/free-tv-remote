package io.github.tomerar.freetvremote.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.tomerar.freetvremote.R
import io.github.tomerar.freetvremote.diagnostics.DeviceInfo
import io.github.tomerar.freetvremote.diagnostics.EventLog
import io.github.tomerar.freetvremote.ui.LocalAppContainer
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private const val PREVIEW_LINES = 30
private const val BYTES_PER_KB = 1024

/** Shows the app log and lets the user copy the newest part (for pasting into a message) or share all of it as a file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val log = LocalAppContainer.current.eventLog
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var version by remember { mutableIntStateOf(0) }
    val sizeBytes = remember(version) { log.sizeBytes() }
    val preview =
        remember(version) {
            log
                .recent(PREVIEW_BYTES)
                .lines()
                .filter { it.isNotBlank() }
                .takeLast(PREVIEW_LINES)
                .joinToString("\n")
        }
    val copiedMessage = stringResource(R.string.diagnostics_copied)
    val copyFailedMessage = stringResource(R.string.diagnostics_copy_failed)
    val clearedMessage = stringResource(R.string.diagnostics_cleared)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.diagnostics_intro), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.diagnostics_size, sizeText(sizeBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    val ok = copyToClipboard(context, log.recent(EventLog.COPY_LIMIT_BYTES).ifBlank { DeviceInfo.header() })
                    scope.launch { snackbar.showSnackbar(if (ok) copiedMessage else copyFailedMessage) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                Text(stringResource(R.string.diagnostics_copy), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { shareLog(context, log) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Text(stringResource(R.string.diagnostics_share), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = {
                    log.clear()
                    version++
                    scope.launch { snackbar.showSnackbar(clearedMessage) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                Text(stringResource(R.string.diagnostics_clear), modifier = Modifier.padding(start = 8.dp))
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Text(
                    text = preview.ifBlank { stringResource(R.string.diagnostics_empty) },
                    modifier = Modifier.padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                stringResource(R.string.diagnostics_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

private const val PREVIEW_BYTES = 8 * BYTES_PER_KB

private fun sizeText(bytes: Long): String =
    if (bytes < BYTES_PER_KB * BYTES_PER_KB) {
        String.format(Locale.US, "%.0f KB", bytes / BYTES_PER_KB.toDouble())
    } else {
        String.format(Locale.US, "%.1f MB", bytes / (BYTES_PER_KB * BYTES_PER_KB).toDouble())
    }

/** Returns false when the system refuses the text (for example when it is too large for the clipboard). */
private fun copyToClipboard(context: Context, text: String): Boolean =
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Free TV Remote log", DeviceInfo.header() + "\n\n" + text))
        true
    } catch (_: RuntimeException) {
        false
    }

private fun shareLog(context: Context, log: EventLog) {
    val file = File(context.cacheDir, "logs/free-tv-remote-log.txt")
    log.exportTo(file, DeviceInfo.header())
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.logs", file)
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}
