package com.neontides.nativeapp.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.neontides.nativeapp.data.GameData
import com.neontides.nativeapp.data.SaveGameManager
import com.neontides.nativeapp.model.GameState
import java.text.DateFormat
import java.util.Date

@Composable
fun SaveSlotsScreen(
    manager: SaveGameManager,
    currentState: GameState,
    canSave: Boolean,
    onLoad: (GameState) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(manager.exportAll()) }
                ?: error("Impossibile creare il backup")
        }.onSuccess { status = "Backup esportato correttamente." }
            .onFailure { status = "Esportazione fallita: ${it.message}" }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Impossibile leggere il backup")
            manager.importAll(raw)
        }.onSuccess { count -> refresh++; status = "Importati $count salvataggi." }
            .onFailure { status = "Importazione fallita: ${it.message}" }
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF090B13), Color(0xFF20102A))))) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("SALVATAGGI", color = Color(0xFFFF6DA8), style = MaterialTheme.typography.labelLarge)
            Text("Le tue storie", style = MaterialTheme.typography.headlineMedium)
            Text("Ogni slot conserva luogo, tempo, relazioni e chat.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(18.dp))
            val autoInfo = remember(refresh) { manager.info(0) }
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xDD251A36))
            ) {
                Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("SALVATAGGIO AUTOMATICO", color = Color(0xFF6EE7FF), style = MaterialTheme.typography.titleSmall)
                        Text(if (autoInfo.timestamp == null) "Non ancora disponibile" else "Giorno ${autoInfo.day} · aggiornato durante il gioco", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        onClick = { manager.load(0)?.let(onLoad) },
                        enabled = autoInfo.timestamp != null
                    ) { Text("Carica") }
                }
            }
            repeat(3) { index ->
                val slot = index + 1
                val info = remember(refresh, slot) { manager.info(slot) }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xDD191625))
                ) {
                    Column(Modifier.padding(15.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("SLOT $slot", style = MaterialTheme.typography.titleMedium)
                            Text(if (info.timestamp == null) "Vuoto" else "Giorno ${info.day}", color = Color(0xFFAAA3C2))
                        }
                        if (info.timestamp != null) {
                            val location = GameData.locations.firstOrNull { it.id == info.locationId }?.name ?: "Neon Bay"
                            Text("$location · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(info.timestamp))}", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                manager.save(slot, currentState); refresh++; status = "Partita salvata nello slot $slot."
                            }, enabled = canSave, modifier = Modifier.weight(1f)) { Text("Salva") }
                            OutlinedButton(onClick = {
                                manager.load(slot)?.let(onLoad) ?: run { status = "Lo slot $slot è vuoto." }
                            }, enabled = info.timestamp != null, modifier = Modifier.weight(1f)) { Text("Carica") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!canSave) Text("Avvia o carica una partita prima di salvare.", color = Color(0xFFFF9BC4), style = MaterialTheme.typography.bodySmall)
            Text("BACKUP", color = Color(0xFFFF6DA8), style = MaterialTheme.typography.titleSmall)
            Text("Esporta i salvataggi prima di disinstallare l’app.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { exportLauncher.launch("NeonTides-backup.json") },
                    modifier = Modifier.weight(1f)
                ) { Text("Esporta") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.weight(1f)
                ) { Text("Importa") }
            }
            status?.let { Text(it, color = Color(0xFF6EE7FF), style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("← Menu principale") }
        }
    }
}
