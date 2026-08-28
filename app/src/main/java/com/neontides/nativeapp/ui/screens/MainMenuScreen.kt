package com.neontides.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MainMenuScreen(
    onNewGame: () -> Unit,
    aiReady: Boolean,
    aiStatus: String,
    hasAutoSave: Boolean,
    onContinue: () -> Unit,
    onSaves: () -> Unit,
    onGallery: () -> Unit,
    onTestUpdates: () -> Unit,
    onModels: () -> Unit,
    onDiagnostics: () -> Unit
) {
    var confirmNewGame by remember { mutableStateOf(false) }
    val night = Brush.verticalGradient(listOf(Color(0xFF080814), Color(0xFF1D0E2B), Color(0xFF071827)))
    Box(Modifier.fillMaxSize().background(night).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "NEON\nTIDES",
                style = TextStyle(
                    brush = Brush.linearGradient(listOf(Color(0xFFFF3D9A), Color(0xFF6EE7FF))),
                    fontSize = 58.sp, lineHeight = 50.sp, fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            )
            Spacer(Modifier.height(10.dp))
            Text("VIVI LA CITTÀ. INCONTRA. SCEGLI.", color = Color(0xFFCFC9E8), letterSpacing = 1.4.sp)
            Spacer(Modifier.height(30.dp))
            Surface(color = Color(0x8A171324), shape = RoundedCornerShape(28.dp), tonalElevation = 10.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    if (hasAutoSave) {
                        Button(onClick = onContinue, enabled = aiReady, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                            Text("CONTINUA", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    Button(
                        onClick = { if (hasAutoSave) confirmNewGame = true else onNewGame() },
                        enabled = aiReady,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D9A))
                    ) { Text("NUOVA PARTITA", fontWeight = FontWeight.Bold) }
                    if (!aiReady) {
                        Spacer(Modifier.height(8.dp))
                        Text("⚠ $aiStatus", color = Color(0xFFFF9BC4), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onSaves, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        Text("SALVA / CARICA PARTITA", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        Text("🖼 GALLERIA", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onTestUpdates, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        Text("🧪 TEST AGGIORNAMENTI", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onModels,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6EE7FF))
                    ) { Text("CONFIGURAZIONE IA", fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth()) {
                        Text("🛠 DIAGNOSTICA IA", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("alfa8.10.2 · revisione semantica Moto G56", color = Color(0xFF8D87A4), fontSize = 12.sp)
        }
    }
    if (confirmNewGame) {
        AlertDialog(
            onDismissRequest = { confirmNewGame = false },
            title = { Text("Iniziare una nuova partita?") },
            text = { Text("Esiste già un salvataggio automatico. Iniziando una nuova partita verrà sostituito.") },
            confirmButton = {
                Button(onClick = { confirmNewGame = false; onNewGame() }) { Text("Nuova partita") }
            },
            dismissButton = {
                TextButton(onClick = { confirmNewGame = false }) { Text("Annulla") }
            }
        )
    }
}
