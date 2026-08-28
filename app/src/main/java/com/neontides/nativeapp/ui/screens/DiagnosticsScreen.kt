package com.neontides.nativeapp.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.neontides.nativeapp.ai.ModelManager
import com.neontides.nativeapp.ai.NativeLlama
import com.neontides.nativeapp.ai.LocalAiBackend
import com.neontides.nativeapp.ai.LocalRuntimeDiagnostics
import com.neontides.nativeapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    manager: ModelManager,
    appSummary: String,
    onRestartAi: () -> Unit,
    onClearAppDiagnostics: () -> Unit,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf("Raccolta diagnostica in corso…") }
    var loading by remember { mutableStateOf(true) }
    var copied by remember { mutableStateOf(false) }
    var resources by remember { mutableStateOf(PerformanceSnapshot.empty()) }

    LaunchedEffect(refresh, appSummary) {
        loading = true
        copied = false
        val measuredResources = withContext(Dispatchers.IO) { samplePerformance(context) }
        resources = measuredResources
        report = withContext(Dispatchers.IO) {
            val active = manager.activeModel()
            val runtime = Runtime.getRuntime()
            val nativeLoaded = NativeLlama.libraryLoaded()
            val modelLoaded = when (active?.backend) {
                LocalAiBackend.LLAMA_CPP ->
                    if (nativeLoaded) runCatching { NativeLlama.isModelLoaded() }.getOrDefault(false) else false
                LocalAiBackend.MLC_LLM -> LocalRuntimeDiagnostics.loadedBackend == LocalAiBackend.MLC_LLM
                null -> false
            }
            val nativeLog = if (nativeLoaded) runCatching { NativeLlama.getDiagnostics() }
                .getOrElse { "Errore lettura diagnostica: ${it.message}" }
            else "La libreria neontides_llm non è stata caricata."

            """
NEON TIDES - DIAGNOSTICA IA
Versione app: ${BuildConfig.VERSION_NAME}
Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}
Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
Processori disponibili: ${runtime.availableProcessors()}
Memoria JVM usata: ${(runtime.totalMemory() - runtime.freeMemory()) / 1048576} MB
Memoria JVM massima: ${runtime.maxMemory() / 1048576} MB

Libreria llama.cpp: ${if (nativeLoaded) "CARICATA" else "NON CARICATA"}
Runtime MLC nell'APK: ${if (BuildConfig.MLC_RUNTIME_PACKAGED) "CARICATO" else "NON INCLUSO"}
Motore selezionato: ${active?.backend?.label ?: "nessuno"}
Modello in RAM: ${if (modelLoaded) "SÌ" else "NO"}
Modello attivo: ${active?.displayName ?: "nessuno"}
Dimensione modello: ${active?.let { "${it.sizeBytes / 1048576} MB" } ?: "-"}

RISORSE DISPOSITIVO · CAMPIONE 400 MS
${measuredResources.asReport()}
Priorità inferenza: richiesta DISPLAY temporanea durante cache e generazione; l'esito effettivo dell'ultima generazione è nel riepilogo app. Android conserva il controllo su limiti termici e disponibilità del sistema.

RIEPILOGO APP
$appSummary

RIEPILOGO BACKEND LOCALE
${LocalRuntimeDiagnostics.report()}

RIEPILOGO NATIVO
${compactNativeSummary(nativeLog)}

REGISTRO NATIVO
$nativeLog
            """.trimIndent()
        }
        loading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF090B13))
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("DIAGNOSTICA IA", color = Color(0xFFFF5A9E), style = MaterialTheme.typography.headlineMedium)
        Text("Dopo il test premi AGGIORNA, poi COPIA RISULTATO e incollalo nella chat.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                clipboard.setText(AnnotatedString(report))
                copied = true
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (copied) "RISULTATO COPIATO" else "COPIA RISULTATO") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { refresh++ },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (loading) "RACCOLTA…" else "AGGIORNA RISULTATO") }
        Spacer(Modifier.height(8.dp))
        Text(
            "Confronto consigliato: prova «quanti anni hai?» e poi una domanda libera più complessa. La copia conserva gli ultimi sei passaggi.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFB9B4CA)
        )
        Spacer(Modifier.height(16.dp))

        Surface(color = Color(0xFF171927), shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("RISORSE APP", style = MaterialTheme.typography.titleMedium)
                Text("CPU: ${resources.cpuLabel}")
                LinearProgressIndicator(
                    progress = { (resources.totalCpuPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("RAM app: ${resources.appPssMb} MB PSS · JVM ${resources.jvmMb} MB")
                Text("RAM telefono: ${resources.availableRamMb} MB liberi su ${resources.totalRamMb} MB")
                LinearProgressIndicator(
                    progress = { resources.systemRamUsedFraction.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "La priorità CPU viene richiesta solo durante cache e generazione; Android può non concederla. MLC usa soprattutto la GPU.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB9B4CA)
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        Surface(color = Color(0xFF171927), shape = MaterialTheme.shapes.large) {
            Text(report, modifier = Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                onRestartAi()
                report += "\n\nRiavvio IA richiesto. Attendi qualche secondo, poi premi AGGIORNA."
            },
            enabled = !loading && manager.activeModelFile() != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("RIAVVIA IA") }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        if (NativeLlama.libraryLoaded()) runCatching { NativeLlama.clearDiagnostics() }
                        LocalRuntimeDiagnostics.clear()
                    }
                    onClearAppDiagnostics()
                    refresh++
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("PULISCI REGISTRO") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("← MENU PRINCIPALE") }
    }
}

private data class PerformanceSnapshot(
    val oneCorePercent: Double,
    val totalCpuPercent: Double,
    val cores: Int,
    val appPssMb: Int,
    val jvmMb: Long,
    val nativeHeapMb: Long,
    val availableRamMb: Long,
    val totalRamMb: Long,
    val lowMemory: Boolean
) {
    val cpuLabel: String
        get() = "${format(oneCorePercent)}% di un core · ${format(totalCpuPercent)}% capacità totale su $cores core"

    val systemRamUsedFraction: Double
        get() = if (totalRamMb > 0) 1.0 - availableRamMb.toDouble() / totalRamMb else 0.0

    fun asReport(): String = buildString {
        appendLine("CPU processo: $cpuLabel")
        appendLine("RAM app PSS: $appPssMb MB · heap nativo: $nativeHeapMb MB · JVM: $jvmMb MB")
        append("RAM telefono disponibile: $availableRamMb / $totalRamMb MB · memoria bassa: ${if (lowMemory) "SÌ" else "NO"}")
    }

    companion object {
        fun empty() = PerformanceSnapshot(0.0, 0.0, 1, 0, 0, 0, 0, 0, false)
        private fun format(value: Double): String = "%.1f".format(Locale.US, value)
    }
}

private suspend fun samplePerformance(context: Context): PerformanceSnapshot {
    val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    val cpuBefore = Process.getElapsedCpuTime()
    val wallBefore = SystemClock.elapsedRealtime()
    delay(400)
    val wallMs = (SystemClock.elapsedRealtime() - wallBefore).coerceAtLeast(1L)
    val cpuMs = (Process.getElapsedCpuTime() - cpuBefore).coerceAtLeast(0L)
    val oneCorePercent = (cpuMs * 100.0 / wallMs).coerceIn(0.0, cores * 100.0)
    val totalCpuPercent = (oneCorePercent / cores).coerceIn(0.0, 100.0)
    val appMemory = Debug.MemoryInfo()
    Debug.getMemoryInfo(appMemory)
    val runtime = Runtime.getRuntime()
    val jvmMb = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576L
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val systemMemory = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(systemMemory)
    return PerformanceSnapshot(
        oneCorePercent = oneCorePercent,
        totalCpuPercent = totalCpuPercent,
        cores = cores,
        appPssMb = appMemory.totalPss / 1024,
        jvmMb = jvmMb,
        nativeHeapMb = Debug.getNativeHeapAllocatedSize() / 1_048_576L,
        availableRamMb = systemMemory.availMem / 1_048_576L,
        totalRamMb = systemMemory.totalMem / 1_048_576L,
        lowMemory = systemMemory.lowMemory
    )
}

private fun compactNativeSummary(nativeLog: String): String {
    val lines = nativeLog.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
    fun last(prefix: String): String? = lines.lastOrNull { it.startsWith(prefix) }
    val cacheCount = lines.count { it.startsWith("CACHE OK:") }
    val rewindCount = lines.count { it.startsWith("CACHE rewind: base_tokens=") }
    val generationCount = lines.count { it.startsWith("GENERATE OK:") }
    val timeout = lines.lastOrNull { it.startsWith("TIMEOUT:") }
    return buildString {
        appendLine("Preparazioni cache registrate: $cacheCount")
        appendLine("Ripristini cache senza ricostruzione: $rewindCount")
        appendLine("Generazioni registrate: $generationCount")
        appendLine("Ultimo caricamento: ${last("LOAD OK:") ?: "non disponibile"}")
        appendLine("Ultima cache: ${last("CACHE OK:") ?: "non disponibile"}")
        appendLine("Ultima analisi: ${last("PROMPT OK:") ?: "non disponibile"}")
        appendLine("Ultimo primo token nativo: ${last("FIRST TOKEN:") ?: "non disponibile"}")
        append("Ultima generazione: ${last("GENERATE OK:") ?: "non disponibile"}")
        if (timeout != null) {
            appendLine()
            append("Ultimo timeout: $timeout")
        }
    }
}
