package com.neontides.nativeapp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.neontides.nativeapp.ai.ModelManager
import com.neontides.nativeapp.ai.ModelDownloadManager
import com.neontides.nativeapp.ai.ModelDownloadStatus
import com.neontides.nativeapp.ai.SecureAiSettings
import com.neontides.nativeapp.ai.LocalModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ModelManagerScreen(
    manager: ModelManager,
    settings: SecureAiSettings,
    onModelChanged: () -> Unit,
    onUnloadModel: () -> Unit,
    onBack: () -> Unit
) {
    var models by remember { mutableStateOf(manager.listLocalModels()) }
    var active by remember { mutableStateOf(activeKey(manager.activeModel())) }
    var status by remember { mutableStateOf<String?>(null) }
    var geminiKey by remember { mutableStateOf(settings.geminiKey) }
    var openAiKey by remember { mutableStateOf(settings.openAiKey) }
    var confirmDownload by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val downloader = remember(manager) { manager.recommendedDownloader() }
    val download by downloader.state.collectAsState()

    LaunchedEffect(download.status) {
        if (download.status == ModelDownloadStatus.COMPLETED) {
            models = manager.listLocalModels()
            val downloaded = models.firstOrNull { it.path.name == ModelDownloadManager.MODEL_NAME }
            if (downloaded != null) {
                active = activeKey(manager.activeModel())
                status = "Qwen verificato · pronto per il caricamento in RAM"
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            status = "Importazione in corso…"
            val result = runCatching {
                withContext(Dispatchers.IO) { manager.importModel(uri) }
            }
            models = manager.listLocalModels()
            active = activeKey(manager.activeModel())
            status = result.fold(
                {
                    onModelChanged()
                    "Modello importato · viene caricato solo il motore selezionato…"
                },
                { "Importazione fallita: ${it.message}" }
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Configurazione IA", style = MaterialTheme.typography.headlineMedium)
            Text("Un'unica app, due motori locali selezionabili: llama.cpp oppure MLC LLM.")
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Importa modello locale", style = MaterialTheme.typography.titleMedium)
                    Text("GGUF usa llama.cpp sulla CPU. Il pacchetto MLC .tar usa la GPU; in RAM viene caricato un solo motore alla volta.")
                    Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Text("📂 Importa GGUF oppure MLC .tar")
                    }
                    if (models.isEmpty()) Text("Nessun modello locale installato.")
                }
            }
        }
        item {
            Text("Scarica il modello consigliato", style = MaterialTheme.typography.titleLarge)
            Text("Il GGUF viene salvato direttamente nella cartella privata di Neon Tides.")
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Qwen 2.5 3B Instruct Uncensored", style = MaterialTheme.typography.titleMedium)
                    Text("Q4_K_M · circa 1,93 GB (1,80 GiB) · modello di riferimento", style = MaterialTheme.typography.bodySmall)
                    if (download.status != ModelDownloadStatus.IDLE || download.downloadedBytes > 0L) {
                        LinearProgressIndicator(
                            progress = { download.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${download.percent}% · ${formatBytes(download.downloadedBytes)} / ${formatBytes(download.totalBytes)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (download.status == ModelDownloadStatus.DOWNLOADING) {
                            val remaining = (download.totalBytes - download.downloadedBytes).coerceAtLeast(0L)
                            val eta = if (download.bytesPerSecond > 0L) remaining / download.bytesPerSecond else 0L
                            Text(
                                "${formatBytes(download.bytesPerSecond)}/s${if (eta > 0L) " · circa ${formatDuration(eta)}" else ""}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (download.message.isNotBlank()) Text(download.message, style = MaterialTheme.typography.bodySmall)
                    }
                    when (download.status) {
                        ModelDownloadStatus.DOWNLOADING -> Row(
                            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(onClick = downloader::pause, modifier = Modifier.weight(1f)) { Text("Pausa") }
                            TextButton(onClick = downloader::cancel, modifier = Modifier.weight(1f)) { Text("Annulla") }
                        }
                        ModelDownloadStatus.PAUSED, ModelDownloadStatus.ERROR -> Row(
                            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    downloader.startOrResume { file ->
                                        manager.setActive(file)
                                        onModelChanged()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Riprendi") }
                            TextButton(onClick = downloader::cancel, modifier = Modifier.weight(1f)) { Text("Annulla") }
                        }
                        ModelDownloadStatus.COMPLETED -> Text("✅ Modello scaricato e verificato")
                        ModelDownloadStatus.IDLE -> Button(
                            onClick = { confirmDownload = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("⬇ Scarica nell'app") }
                    }
                }
            }
        }
        items(models, key = { "${it.backend.name}:${it.id}" }) { model ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(model.displayName)
                    Text(model.backend.label, style = MaterialTheme.typography.labelLarge)
                    Text("%.2f GB".format(model.sizeBytes / 1024.0 / 1024.0 / 1024.0))
                    if (active == activeKey(model)) {
                        Text("✅ Selezionato")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onModelChanged()
                                    status = "Caricamento del modello in RAM…"
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Carica in RAM") }
                            OutlinedButton(
                                onClick = {
                                    onUnloadModel()
                                    status = "Modello scaricato dalla RAM; il file resta disponibile."
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Scarica RAM") }
                        }
                    } else TextButton(onClick = {
                        manager.setActive(model)
                        active = activeKey(model)
                        onModelChanged()
                        status = "Cambio motore · il precedente viene scaricato dalla RAM."
                    }) { Text("Usa questo modello") }
                    TextButton(onClick = {
                        val removed = manager.deleteModel(model.path)
                        models = manager.listLocalModels()
                        active = activeKey(manager.activeModel())
                        if (removed) {
                            if (active == null) onModelChanged()
                            status = "Modello eliminato dal dispositivo."
                        } else status = "Impossibile eliminare il modello."
                    }) { Text("Elimina dal dispositivo") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Fallback online", style = MaterialTheme.typography.titleMedium)
                    Text("Le chiavi sono cifrate sul dispositivo. Non inserirle mai nel codice o su GitHub.")
                    OutlinedTextField(
                        value = geminiKey, onValueChange = { geminiKey = it }, label = { Text("Chiave Gemini") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = openAiKey, onValueChange = { openAiKey = it }, label = { Text("Chiave OpenAI") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        settings.geminiKey = geminiKey; settings.openAiKey = openAiKey
                        status = "Chiavi salvate sul dispositivo."
                    }, modifier = Modifier.fillMaxWidth()) { Text("Salva chiavi") }
                    TextButton(onClick = {
                        settings.clear(); geminiKey = ""; openAiKey = ""; status = "Chiavi eliminate."
                    }, modifier = Modifier.fillMaxWidth()) { Text("Elimina chiavi") }
                }
            }
        }
        status?.let { message -> item { Text(message, style = MaterialTheme.typography.bodySmall) } }
        item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Indietro") } }
    }

    if (confirmDownload) {
        AlertDialog(
            onDismissRequest = { confirmDownload = false },
            title = { Text("Scaricare circa 1,93 GB?") },
            text = {
                Text("È consigliata una rete Wi-Fi. Puoi mettere in pausa, riprendere o annullare il download in qualsiasi momento.")
            },
            confirmButton = {
                Button(onClick = {
                    confirmDownload = false
                    downloader.startOrResume { file ->
                        manager.setActive(file)
                        onModelChanged()
                    }
                }) { Text("Scarica") }
            },
            dismissButton = { TextButton(onClick = { confirmDownload = false }) { Text("Annulla") } }
        )
    }
}

private fun formatBytes(value: Long): String = when {
    value <= 0L -> "–"
    value >= 1024L * 1024L * 1024L -> "%.2f GiB".format(value / 1024.0 / 1024.0 / 1024.0)
    else -> "%.1f MiB".format(value / 1024.0 / 1024.0)
}

private fun formatDuration(seconds: Long): String = when {
    seconds >= 3600L -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
    seconds >= 60L -> "${seconds / 60} min ${seconds % 60} s"
    else -> "$seconds s"
}

private fun activeKey(model: LocalModel?): String? =
    model?.let { "${it.backend.name}:${it.id}" }
