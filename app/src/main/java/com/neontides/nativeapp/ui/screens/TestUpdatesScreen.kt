package com.neontides.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neontides.nativeapp.data.GameData
import com.neontides.nativeapp.GameViewModel
import com.neontides.nativeapp.model.CharacterProfile
import kotlinx.coroutines.launch

private data class SimulatedRelationship(
    val affection: Int = 0,
    val attraction: Int = 0,
    val trust: Int = 0,
    val stage: String = "Sconosciuti",
    val day: Int = 1,
    val firstMetDay: Int = 1,
    val lastStageChangeDay: Int = 0,
    val interactions: Int = 0,
    val lastResult: String = "Nessuna interazione simulata"
)

private val testStages = listOf(
    "Sconosciuti", "Conoscenza", "Amicizia", "Flirt",
    "Attrazione reciproca", "Appuntamenti", "Relazione"
)

@Composable
fun TestUpdatesScreen(vm: GameViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val characters = GameData.characters
    var selectedId by remember { mutableStateOf(characters.first().id) }
    var simulations by remember { mutableStateOf(emptyMap<String, SimulatedRelationship>()) }
    var simulatedUnlocks by remember { mutableStateOf(emptySet<String>()) }
    var pending by remember { mutableStateOf<String?>(null) }
    val character = characters.first { it.id == selectedId }
    val relation = simulations[selectedId] ?: SimulatedRelationship()
    val tiers = listOf("profile", "casual", "flirt", "date", "intimate")
    val unlocked = tiers.count { "${character.id}_$it" in simulatedUnlocks }
    var labText by remember { mutableStateOf("Dimmi qualcosa che non hai mai raccontato a nessuno") }
    var labAffection by remember { mutableFloatStateOf(0f) }
    var labAttraction by remember { mutableFloatStateOf(0f) }
    var labTrust by remember { mutableFloatStateOf(0f) }
    var labReply by remember { mutableStateOf("") }
    var labDiagnostic by remember { mutableStateOf("Nessun test modulare eseguito") }
    var labRunning by remember { mutableStateOf(false) }
    val labHistory by vm.modularLabHistory.collectAsState()
    val labReport = remember(labHistory) {
        if (labHistory.isEmpty()) {
            "Nessuno scambio registrato nel laboratorio Luna."
        } else {
            labHistory.mapIndexed { index, record ->
                """
SCAMBIO ${index + 1}
Giocatore: ${record.question}
Luna: ${record.result.reply}
Affetto ${record.affection} · Attrazione ${record.attraction} · Fiducia ${record.trust}
${record.result.diagnostic}
                """.trimIndent()
            }.joinToString("\n\n----------------\n\n")
        }
    }

    fun applyInteraction(kind: String, affection: Int, attraction: Int, trust: Int) {
        simulations = simulations + (
            selectedId to advanceTestRelationship(
                current = relation,
                character = character,
                affectionDelta = affection,
                attractionDelta = attraction,
                trustDelta = trust,
                label = kind
            )
        )
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF090B16))) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Test Aggiornamenti", style = MaterialTheme.typography.headlineLarge)
                Text("Ambiente isolato: queste prove non modificano partita, salvataggi o galleria reale.")
            }
            item {
                Text("Personaggio da simulare", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(characters) { item ->
                        FilterChip(
                            selected = selectedId == item.id,
                            onClick = { selectedId = item.id },
                            label = { Text(item.name.substringBefore(' ')) }
                        )
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Affinità graduale · ${character.name}", style = MaterialTheme.typography.titleLarge)
                        Text("Giorno ${relation.day} · ${relation.stage} · difficoltà ${character.conquestDifficulty}/5")
                        Text("❤️ ${relation.affection}   🔥 ${relation.attraction}   🤝 ${relation.trust}")
                        Text(relation.lastResult, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { applyInteraction("Ascolto e interesse", 1, 0, 1) },
                                modifier = Modifier.weight(1f)
                            ) { Text("+ Sintonia") }
                            OutlinedButton(
                                onClick = { applyInteraction("Conversazione neutra", 0, 0, 0) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Neutra") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { applyInteraction("Flirt consensuale", 1, 1, 0) },
                                modifier = Modifier.weight(1f)
                            ) { Text("+ Flirt") }
                            OutlinedButton(
                                onClick = { applyInteraction("Offesa o pressione", -2, -1, -3) },
                                modifier = Modifier.weight(1f)
                            ) { Text("− Rapporto") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    simulations = simulations + (selectedId to relation.copy(
                                        day = relation.day + 1,
                                        lastResult = "Avanzato al giorno ${relation.day + 1}: ora può maturare un nuovo livello."
                                    ))
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Giorno +1") }
                            TextButton(
                                onClick = { simulations = simulations - selectedId },
                                modifier = Modifier.weight(1f)
                            ) { Text("Azzera") }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Laboratorio memoria modulare · Luna", style = MaterialTheme.typography.titleLarge)
                        Text("Esperimento isolato. I moduli selezionano dati; la risposta finale è sempre del GGUF. Non modifica la partita.")
                        LabSlider("Affetto", labAffection) { labAffection = it }
                        LabSlider("Attrazione", labAttraction) { labAttraction = it }
                        LabSlider("Fiducia", labTrust) { labTrust = it }
                        OutlinedTextField(
                            value = labText,
                            onValueChange = { labText = it },
                            label = { Text("Messaggio libero per Luna") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf(
                                "Quanti anni hai?",
                                "Cosa fai quando non lavori?",
                                "Dimmi qualcosa che non sa nessuno",
                                "Ti ricordi chi sono e che musica ascolto?",
                                "Ricordi il nostro primo bacio?"
                            )) { preset ->
                                AssistChip(onClick = { labText = preset }, label = { Text(preset) })
                            }
                        }
                        Button(
                            onClick = {
                                labRunning = true
                                scope.launch {
                                    val result = vm.runModularMemoryLab(
                                        labText,
                                        labAffection.toInt(),
                                        labAttraction.toInt(),
                                        labTrust.toInt()
                                    )
                                    labReply = result.reply
                                    labDiagnostic = result.diagnostic
                                    labRunning = false
                                }
                            },
                            enabled = !labRunning && labText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (labRunning) "GGUF IN ESECUZIONE…" else "GENERA CON MODULI + GGUF") }
                        if (labReply.isNotBlank()) {
                            Text("Risposta di Luna", style = MaterialTheme.typography.titleMedium)
                            Text(labReply)
                        }
                        Text("Diagnostica percorso modulare", style = MaterialTheme.typography.titleMedium)
                        Text(labDiagnostic, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(labReport)) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("COPIA DIAGNOSTICA LUNA") }
                        Text("Messaggi scambiati nel laboratorio", style = MaterialTheme.typography.titleMedium)
                        if (labHistory.isEmpty()) {
                            Text("Nessuno scambio registrato", style = MaterialTheme.typography.bodySmall)
                        } else {
                            labHistory.takeLast(10).asReversed().forEach { record ->
                                Text("Giocatore: ${record.question}", style = MaterialTheme.typography.bodySmall)
                                Text("Luna: ${record.result.reply}", style = MaterialTheme.typography.bodySmall)
                                HorizontalDivider()
                            }
                            TextButton(
                                onClick = {
                                    vm.clearModularLabHistory()
                                    labReply = ""
                                    labDiagnostic = "Nessun test modulare eseguito"
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("AZZERA DIAGNOSTICA LUNA") }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Galleria · ${character.name}", style = MaterialTheme.typography.titleLarge)
                        Text("Livelli verificati: $unlocked / 5")
                        Button(
                            onClick = {
                                val tier = tiers[unlocked]
                                val key = "${character.id}_$tier"
                                simulatedUnlocks = simulatedUnlocks + key
                                pending = key
                            },
                            enabled = unlocked < tiers.size,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (unlocked < tiers.size) "SIMULA IMMAGINE ${unlocked + 1}" else "TUTTE LE IMMAGINI VERIFICATE")
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Indietro") }
            }
        }

        pending?.let { key ->
            val characterId = key.substringBeforeLast('_')
            val tierId = key.substringAfterLast('_')
            val characterName = characters.firstOrNull { it.id == characterId }?.name ?: characterId
            GalleryUnlockDialog(
                characterName = characterName,
                tierTitle = galleryTierTitle(tierId),
                drawable = galleryCharacterDrawable(characterId, tierId),
                onContinue = { pending = null },
                onOpenGallery = { pending = null }
            )
        }
    }
}

@Composable
private fun LabSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Text("$label: ${value.toInt()}")
        Slider(value = value, onValueChange = onValueChange, valueRange = 0f..100f, steps = 19)
    }
}

private fun advanceTestRelationship(
    current: SimulatedRelationship,
    character: CharacterProfile,
    affectionDelta: Int,
    attractionDelta: Int,
    trustDelta: Int,
    label: String
): SimulatedRelationship {
    val affection = (current.affection + affectionDelta).coerceIn(0, 100)
    val attraction = (current.attraction + attractionDelta).coerceIn(0, 100)
    val trust = (current.trust + trustDelta).coerceIn(0, 100)
    val scoreStage = testRelationshipStage(affection, attraction, trust)
    val currentIndex = testStages.indexOf(current.stage).coerceAtLeast(0)
    val targetIndex = testStages.indexOf(scoreStage).coerceAtLeast(0)
    val nextStage = when {
        targetIndex < currentIndex -> testStages[(currentIndex - 1).coerceAtLeast(targetIndex)]
        targetIndex == currentIndex || current.lastStageChangeDay == current.day -> current.stage
        else -> {
            val difficulty = character.conquestDifficulty.coerceIn(1, 5)
            val minimumDays = when (currentIndex + 1) {
                1 -> 0
                2 -> maxOf(1, difficulty - 2)
                3 -> 2 + difficulty / 2
                4 -> 3 + difficulty
                5 -> 5 + difficulty
                else -> 8 + difficulty * 2
            }
            if (current.day - current.firstMetDay >= minimumDays) testStages[currentIndex + 1] else current.stage
        }
    }
    val deltaText = "❤️${signed(affectionDelta)} · 🔥${signed(attractionDelta)} · 🤝${signed(trustDelta)}"
    val stageText = if (nextStage != current.stage) " · fase: ${current.stage} → $nextStage" else ""
    return current.copy(
        affection = affection,
        attraction = attraction,
        trust = trust,
        stage = nextStage,
        interactions = current.interactions + 1,
        lastStageChangeDay = if (nextStage != current.stage) current.day else current.lastStageChangeDay,
        lastResult = "$label: $deltaText$stageText"
    )
}

private fun testRelationshipStage(affection: Int, attraction: Int, trust: Int): String = when {
    affection >= 80 && attraction >= 65 && trust >= 70 -> "Relazione"
    affection >= 65 && attraction >= 55 && trust >= 50 -> "Appuntamenti"
    attraction >= 45 && affection >= 35 -> "Attrazione reciproca"
    attraction >= 30 && affection >= 25 -> "Flirt"
    affection >= 20 || trust >= 25 -> "Amicizia"
    affection >= 8 || trust >= 8 -> "Conoscenza"
    else -> "Sconosciuti"
}

private fun signed(value: Int): String = if (value > 0) "+$value" else value.toString()
