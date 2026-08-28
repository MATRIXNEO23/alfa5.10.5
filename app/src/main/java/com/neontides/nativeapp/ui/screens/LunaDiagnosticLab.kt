package com.neontides.nativeapp.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.neontides.nativeapp.GameViewModel
import com.neontides.nativeapp.R
import com.neontides.nativeapp.ai.BaseDialogueTestRecord
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Chat reale su stato copiato: non scrive mai nella partita o nella relazione di Luna. */
@Composable
fun LunaDiagnosticLab(vm: GameViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val allHistory by vm.baseDialogueTestHistory.collectAsState()
    val history = remember(allHistory) { allHistory.filter { it.characterId == "luna" } }
    val streaming by vm.baseDialogueTestStreaming.collectAsState()
    val running by vm.baseDialogueTestRunning.collectAsState()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var affection by remember { mutableFloatStateOf(0f) }
    var attraction by remember { mutableFloatStateOf(0f) }
    var trust by remember { mutableFloatStateOf(0f) }
    var exportText by remember { mutableStateOf("") }
    var exportStatus by remember { mutableStateOf("") }
    val report = remember(history) { lunaLabReport(history) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(exportText) }
                    ?: error("Impossibile aprire il file scelto")
            }.isSuccess
            exportStatus = if (saved) "Diagnostica Luna esportata" else "Esportazione non riuscita"
        }
    }

    fun submit() {
        val message = input.trim()
        if (message.isBlank() || running) return
        input = ""
        scope.launch {
            vm.runBaseDialogueTest(
                characterId = "luna",
                text = message,
                affection = affection.toInt(),
                attraction = attraction.toInt(),
                trust = trust.toInt()
            )
        }
    }

    LaunchedEffect(history.size, streaming.length / 32, running) {
        if (history.isNotEmpty() || streaming.isNotBlank()) {
            val target = if (running) history.size else (history.size - 1).coerceAtLeast(0)
            listState.scrollToItem(target)
        }
    }

    Card(Modifier.fillMaxWidth().imePadding()) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Laboratorio isolato · Luna", style = MaterialTheme.typography.titleLarge)
            Text(
                "Stessa pipeline della chat reale e modello attivo. Cronologia, valori e diagnostica restano separati dalla partita.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Fase simulata: ${labRelationshipStage(affection.toInt(), attraction.toInt(), trust.toInt())}",
                color = Color(0xFFFF9BC4),
                style = MaterialTheme.typography.labelMedium
            )
            LabValueSlider("❤️ Affetto", affection) { affection = it }
            LabValueSlider("🔥 Attrazione", attraction) { attraction = it }
            LabValueSlider("🤝 Fiducia", trust) { trust = it }

            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 430.dp, max = 560.dp)
                    .background(Color(0xFF0D0F1A), MaterialTheme.shapes.large)
            ) {
                Image(
                    painter = painterResource(R.drawable.character_luna),
                    contentDescription = "Luna Hayashi",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Transparent, Color(0xE60D0F1A))
                        )
                    )
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 170.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (history.isEmpty() && !running) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Text(
                                    "Scrivi a Luna: ogni scambio apparirà qui come nella chat del gioco.",
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                    items(history, key = { it.timestampEpochMs }) { record ->
                        LabBubble("Tu", record.question, player = true)
                        LabBubble(
                            "Luna",
                            if (record.result.engine == "Nessuno") {
                                "Nessuna risposta finale · il testo parziale è conservato nella diagnostica"
                            } else record.result.reply,
                            player = false
                        )
                    }
                    if (running) {
                        item(key = "luna-lab-streaming") {
                            LabBubble(
                                "Luna",
                                if (streaming.isBlank()) "Sta preparando la risposta…" else "$streaming ▌",
                                player = false
                            )
                        }
                    }
                }
            }

            if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Scrivi a Luna…") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !running,
                minLines = 2,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() })
            )
            Button(
                onClick = { submit() },
                enabled = !running && input.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (running) "MOTORE IN ESECUZIONE…" else "INVIA A LUNA") }

            Text("Diagnostica separata Luna", style = MaterialTheme.typography.titleMedium)
            Surface(color = Color(0xFF171927), shape = MaterialTheme.shapes.large) {
                Text(
                    history.lastOrNull()?.let(::lunaRecordReport)
                        ?: "Nessuno scambio diagnostico registrato.",
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(report)) },
                    enabled = history.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("COPIA") }
                OutlinedButton(
                    onClick = {
                        exportText = report
                        exportLauncher.launch("NeonTides-Luna-${fileTimestamp()}.txt")
                    },
                    enabled = history.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("ESPORTA TXT") }
            }
            TextButton(
                onClick = { vm.clearBaseDialogueTestHistory() },
                enabled = history.isNotEmpty() && !running,
                modifier = Modifier.fillMaxWidth()
            ) { Text("AZZERA SOLO CHAT E DIAGNOSTICA LUNA") }
            if (exportStatus.isNotBlank()) {
                Text(exportStatus, style = MaterialTheme.typography.labelSmall, color = Color(0xFF65D98B))
            }
        }
    }
}

@Composable
private fun LabValueSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Text("$label: ${value.toInt()}", style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onValueChange, valueRange = 0f..100f, steps = 19)
    }
}

@Composable
private fun LabBubble(speaker: String, text: String, player: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (player) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.84f),
            color = if (player) Color(0xE646244D) else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(11.dp)) {
                Text(speaker, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(3.dp))
                Text(text)
            }
        }
    }
}

private fun lunaLabReport(history: List<BaseDialogueTestRecord>): String = if (history.isEmpty()) {
    "Nessuno scambio registrato nel laboratorio Luna."
} else {
    buildString {
        appendLine("NEON TIDES - DIAGNOSTICA LUNA ISOLATA")
        appendLine("Scambi registrati: ${history.size}")
        appendLine("La prova non modifica partita, salvataggi o relazione reale.")
        history.forEachIndexed { index, record ->
            appendLine()
            appendLine("----------------")
            appendLine("TEST ${index + 1}")
            append(lunaRecordReport(record))
            appendLine()
        }
    }.trim()
}

private fun lunaRecordReport(record: BaseDialogueTestRecord): String = buildString {
    appendLine("Data e ora: ${displayTimestamp(record.timestampEpochMs)}")
    appendLine("Modello: ${record.modelName}")
    appendLine("Backend: ${record.backendLabel}")
    appendLine("Affetto ${record.affection} · Attrazione ${record.attraction} · Fiducia ${record.trust} · Fase ${record.stage}")
    appendLine("Domanda: ${record.question}")
    appendLine("Testo visto in streaming: ${record.streamedReply.ifBlank { "nessuno" }}")
    appendLine("Stato streaming: ${when {
        record.streamedReply.isBlank() -> "nessun testo ricevuto"
        record.result.engine == "Nessuno" -> "interrotto dal timeout e non salvato come risposta"
        record.result.diagnosticFallback -> "completato, poi sostituito dal deterministico"
        else -> "completato e conservato"
    }}")
    appendLine("Output grezzo/parziale motore: ${record.result.diagnosticRawReply.ifBlank { record.rawStreamedReply.ifBlank { "non disponibile" } }}")
    appendLine("Risposta finale mostrata: ${if (record.result.engine == "Nessuno") "nessuna — timeout o errore del motore" else record.result.reply}")
    appendLine("Percorso: ${record.result.diagnosticPath.ifBlank { record.result.engine }}")
    appendLine("Tema: ${record.result.diagnosticTopic}")
    appendLine("Semantica: ${record.result.diagnosticSemantics.ifBlank { "nessun modulo" }}")
    appendLine("Correzione deterministica: ${if (record.result.diagnosticFallback) "SÌ" else "NO"}")
    appendLine("Motivo correzione: ${record.result.diagnosticCorrectionReason.ifBlank { "nessuno" }}")
    appendLine("Possibile falso positivo: ${if (record.result.diagnosticFallback) "DA VALUTARE" else "non applicabile"}")
    appendLine("Primo testo: ${record.firstTextMs?.let { "$it ms" } ?: "nessun testo in streaming"}")
    appendLine("Tempo totale: ${record.elapsedMs} ms")
    appendLine("Cache: ${record.preparationDiagnostic}")
    append(record.resourceDiagnostic)
}

private fun labRelationshipStage(affection: Int, attraction: Int, trust: Int): String = when {
    affection >= 80 && attraction >= 65 && trust >= 70 -> "Relazione"
    affection >= 65 && attraction >= 55 && trust >= 50 -> "Appuntamenti"
    attraction >= 45 && affection >= 35 -> "Attrazione reciproca"
    attraction >= 30 && affection >= 25 -> "Flirt"
    affection >= 20 || trust >= 25 -> "Amicizia"
    affection >= 8 || trust >= 8 -> "Conoscenza"
    else -> "Sconosciuti"
}

private fun displayTimestamp(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(epochMs))

private fun fileTimestamp(): String =
    SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
