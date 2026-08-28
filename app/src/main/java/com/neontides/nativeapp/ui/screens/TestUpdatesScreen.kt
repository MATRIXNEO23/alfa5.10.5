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
import com.neontides.nativeapp.ai.BaseDialogueTestRecord
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

private enum class TestArea(val label: String) {
    BASE("Dialogo base"),
    MODULAR("Semantica"),
    COMPARISON("Confronto"),
    PROGRESSION("Progressione"),
    GALLERY("Galleria")
}

@Composable
fun TestUpdatesScreen(vm: GameViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val characters = GameData.characters
    var selectedId by remember { mutableStateOf(characters.first().id) }
    var selectedArea by remember { mutableStateOf(TestArea.BASE) }
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
    var labRunning by remember { mutableStateOf(false) }
    var showLabSummary by remember { mutableStateOf(false) }
    var baseText by remember { mutableStateOf("Raccontami cosa pensi di me") }
    var baseAffection by remember { mutableFloatStateOf(0f) }
    var baseAttraction by remember { mutableFloatStateOf(0f) }
    var baseTrust by remember { mutableFloatStateOf(0f) }
    var baseRunning by remember { mutableStateOf(false) }
    var pendingBaseRecord by remember { mutableStateOf<BaseDialogueTestRecord?>(null) }
    var showBaseSummary by remember { mutableStateOf(false) }
    var showComparisonSummary by remember { mutableStateOf(false) }
    val labHistory by vm.modularLabHistory.collectAsState()
    val baseHistory by vm.baseDialogueTestHistory.collectAsState()
    val lastBaseRecord = baseHistory.lastOrNull()
    val lastLabRecord = labHistory.lastOrNull()
    val labSummary = remember(labHistory) {
        if (labHistory.isEmpty()) {
            "Nessun test modulare eseguito"
        } else {
            labHistory.mapIndexed { index, record ->
                """
TEST ${index + 1}
Domanda: ${record.question}
Valori: Affetto ${record.affection} · Attrazione ${record.attraction} · Fiducia ${record.trust}
Risposta: ${record.result.reply}

${record.result.diagnostic}
                """.trimIndent()
            }.joinToString("\n\n====================\n\n")
        }
    }
    val baseSummary = remember(baseHistory) {
        if (baseHistory.isEmpty()) {
            "Nessun test del dialogo base eseguito"
        } else {
            baseHistory.mapIndexed { index, record ->
                """
TEST BASE ${index + 1} · ${record.characterName}
Domanda: ${record.question}
Valori iniziali: Affetto ${record.affection} · Attrazione ${record.attraction} · Fiducia ${record.trust} · Fase ${record.stage}
Risposta: ${record.result.reply}
Variazione proposta: Affetto ${signed(record.result.delta.affection)} · Attrazione ${signed(record.result.delta.attraction)} · Fiducia ${signed(record.result.delta.trust)}
Valutazione utente: ${if (record.changeConfirmed == true) "CONFERMATA" else "NON CORRETTA"}
Percorso: ${record.result.diagnosticPath.ifBlank { record.result.engine }}
Tema: ${record.result.diagnosticTopic}
Semantica: ${record.result.diagnosticSemantics.ifBlank { "nessun modulo" }}
Correzione deterministica: ${if (record.result.diagnosticFallback) "SÌ · ${record.result.diagnosticCorrectionReason}" else "NO"}
Cache: ${record.preparationDiagnostic}
Tempo totale: ${record.elapsedMs} ms
${record.resourceDiagnostic}
                """.trimIndent()
            }.joinToString("\n\n====================\n\n")
        }
    }
    val comparisons = remember(baseHistory, labHistory) {
        baseHistory.mapNotNull { base ->
            labHistory.lastOrNull { modular ->
                modular.question.trim().equals(base.question.trim(), ignoreCase = true) &&
                    modular.affection == base.affection &&
                    modular.attraction == base.attraction &&
                    modular.trust == base.trust
            }?.let { modular -> base to modular }
        }
    }
    val comparisonSummary = remember(comparisons) {
        if (comparisons.isEmpty()) {
            "Nessun confronto disponibile. Ripeti la stessa domanda con gli stessi tre valori in Dialogo base e Semantica."
        } else {
            comparisons.mapIndexed { index, (base, modular) ->
                """
CONFRONTO ${index + 1}
Domanda: ${base.question}
Valori: Affetto ${base.affection} · Attrazione ${base.attraction} · Fiducia ${base.trust}

DIALOGO BASE · ${base.characterName}
Risposta: ${base.result.reply}
Variazione: Affetto ${signed(base.result.delta.affection)} · Attrazione ${signed(base.result.delta.attraction)} · Fiducia ${signed(base.result.delta.trust)}
Valutazione utente: ${if (base.changeConfirmed == true) "CONFERMATA" else "NON CORRETTA"}
Tempo: ${base.elapsedMs} ms

SEMANTICA MODULARE · LUNA
Risposta: ${modular.result.reply}
${modular.result.diagnostic}
                """.trimIndent()
            }.joinToString("\n\n====================\n\n")
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
                Text("Area test", style = MaterialTheme.typography.headlineLarge)
                Text("Ogni prova è isolata e non modifica partita, salvataggi o galleria reale. Apri una sola sezione alla volta.")
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TestArea.entries) { area ->
                        FilterChip(
                            selected = selectedArea == area,
                            onClick = { selectedArea = area },
                            label = { Text(area.label) }
                        )
                    }
                }
            }
            when (selectedArea) {
                TestArea.BASE -> {
                    item { CharacterSelector(characters, selectedId) { selectedId = it } }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Test dialogo del gioco · ${character.name}", style = MaterialTheme.typography.titleLarge)
                                Text("Usa il normale motore del gioco. I valori scelti valgono solo per questa domanda e il risultato viene aggiunto alla sessione.")
                                LabSlider("Affetto", baseAffection) { baseAffection = it }
                                LabSlider("Attrazione", baseAttraction) { baseAttraction = it }
                                LabSlider("Fiducia", baseTrust) { baseTrust = it }
                                Text("Fase simulata: ${testRelationshipStage(baseAffection.toInt(), baseAttraction.toInt(), baseTrust.toInt())}")
                                OutlinedTextField(
                                    value = baseText,
                                    onValueChange = { baseText = it },
                                    label = { Text("Domanda esatta al personaggio") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2
                                )
                                TestQuestionPresets { baseText = it }
                                Button(
                                    onClick = {
                                        baseRunning = true
                                        scope.launch {
                                            try {
                                                pendingBaseRecord = vm.runBaseDialogueTest(
                                                    selectedId,
                                                    baseText,
                                                    baseAffection.toInt(),
                                                    baseAttraction.toInt(),
                                                    baseTrust.toInt()
                                                )
                                            } finally {
                                                baseRunning = false
                                            }
                                        }
                                    },
                                    enabled = !baseRunning && pendingBaseRecord == null && baseText.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(if (baseRunning) "GGUF IN ESECUZIONE…" else "AGGIUNGI TEST BASE ALLA SESSIONE") }
                                pendingBaseRecord?.let { pendingRecord ->
                                    HorizontalDivider()
                                    Text("Risultato in attesa di conferma", style = MaterialTheme.typography.titleMedium)
                                    Text("Domanda: ${pendingRecord.question}")
                                    Text(pendingRecord.result.reply)
                                    Text(
                                        "Variazione proposta: ❤️${signed(pendingRecord.result.delta.affection)} · 🔥${signed(pendingRecord.result.delta.attraction)} · 🤝${signed(pendingRecord.result.delta.trust)}"
                                    )
                                    Text("Conferma se l'aumento, la diminuzione o lo zero sono coerenti con la risposta.", style = MaterialTheme.typography.bodySmall)
                                    Button(
                                        onClick = {
                                            vm.confirmBaseDialogueTest(pendingRecord, true)
                                            pendingBaseRecord = null
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("CONFERMA E REGISTRA") }
                                    OutlinedButton(
                                        onClick = {
                                            vm.confirmBaseDialogueTest(pendingRecord, false)
                                            pendingBaseRecord = null
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("REGISTRA COME NON CORRETTA") }
                                }
                                Text("Test raccolti: ${baseHistory.size} / 30", style = MaterialTheme.typography.titleMedium)
                                if (lastBaseRecord != null) {
                                    Text("Ultima risposta · ${lastBaseRecord.characterName}", style = MaterialTheme.typography.titleMedium)
                                    Text(lastBaseRecord.result.reply)
                                    Text(
                                        "Variazione: ❤️${signed(lastBaseRecord.result.delta.affection)} · 🔥${signed(lastBaseRecord.result.delta.attraction)} · 🤝${signed(lastBaseRecord.result.delta.trust)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                SummaryControls(
                                    shown = showBaseSummary,
                                    enabled = baseHistory.isNotEmpty(),
                                    onToggle = { showBaseSummary = !showBaseSummary },
                                    onCopy = { clipboard.setText(AnnotatedString(baseSummary)) }
                                )
                                if (showBaseSummary && baseHistory.isNotEmpty()) {
                                    Text("Riepilogo dialogo base", style = MaterialTheme.typography.titleMedium)
                                    Text(baseSummary, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(
                                    onClick = {
                                        vm.clearBaseDialogueTestHistory()
                                        pendingBaseRecord = null
                                        showBaseSummary = false
                                    },
                                    enabled = baseHistory.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("AZZERA SESSIONE BASE") }
                            }
                        }
                    }
                }
                TestArea.MODULAR -> item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Laboratorio memoria modulare · Luna", style = MaterialTheme.typography.titleLarge)
                            Text("Ogni prova viene aggiunta alla sessione. Usa la stessa domanda e gli stessi valori del test base per ottenere un confronto automatico.")
                            LabSlider("Affetto", labAffection) { labAffection = it }
                            LabSlider("Attrazione", labAttraction) { labAttraction = it }
                            LabSlider("Fiducia", labTrust) { labTrust = it }
                            OutlinedTextField(
                                value = labText,
                                onValueChange = { labText = it },
                                label = { Text("Domanda esatta a Luna") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                            TestQuestionPresets { labText = it }
                            Button(
                                onClick = {
                                    labRunning = true
                                    scope.launch {
                                        try {
                                            vm.runModularMemoryLab(
                                                labText,
                                                labAffection.toInt(),
                                                labAttraction.toInt(),
                                                labTrust.toInt()
                                            )
                                        } finally {
                                            labRunning = false
                                        }
                                    }
                                },
                                enabled = !labRunning && labText.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (labRunning) "GGUF IN ESECUZIONE…" else "AGGIUNGI TEST MODULARE ALLA SESSIONE") }
                            Text("Test raccolti: ${labHistory.size} / 30", style = MaterialTheme.typography.titleMedium)
                            if (lastLabRecord != null) {
                                Text("Ultima risposta di Luna", style = MaterialTheme.typography.titleMedium)
                                Text(lastLabRecord.result.reply)
                            }
                            SummaryControls(
                                shown = showLabSummary,
                                enabled = labHistory.isNotEmpty(),
                                onToggle = { showLabSummary = !showLabSummary },
                                onCopy = { clipboard.setText(AnnotatedString(labSummary)) }
                            )
                            if (showLabSummary && labHistory.isNotEmpty()) {
                                Text("Riepilogo semantica modulare", style = MaterialTheme.typography.titleMedium)
                                Text(labSummary, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(
                                onClick = {
                                    vm.clearModularLabHistory()
                                    showLabSummary = false
                                },
                                enabled = labHistory.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("AZZERA SESSIONE MODULARE") }
                        }
                    }
                }
                TestArea.COMPARISON -> item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Confronto base ↔ modulare", style = MaterialTheme.typography.titleLarge)
                            Text("Gli abbinamenti richiedono la stessa domanda e gli stessi valori di Affetto, Attrazione e Fiducia.")
                            Text("Confronti disponibili: ${comparisons.size}", style = MaterialTheme.typography.titleMedium)
                            if (comparisons.isEmpty()) Text(comparisonSummary)
                            SummaryControls(
                                shown = showComparisonSummary,
                                enabled = comparisons.isNotEmpty(),
                                onToggle = { showComparisonSummary = !showComparisonSummary },
                                onCopy = { clipboard.setText(AnnotatedString(comparisonSummary)) }
                            )
                            if (showComparisonSummary && comparisons.isNotEmpty()) {
                                Text(comparisonSummary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                TestArea.PROGRESSION -> {
                    item { CharacterSelector(characters, selectedId) { selectedId = it } }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Text("Progressione relazione · ${character.name}", style = MaterialTheme.typography.titleLarge)
                                Text("Giorno ${relation.day} · ${relation.stage} · difficoltà ${character.conquestDifficulty}/5")
                                Text("❤️ ${relation.affection}   🔥 ${relation.attraction}   🤝 ${relation.trust}")
                                Text(relation.lastResult, style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { applyInteraction("Ascolto e interesse", 1, 0, 1) }, modifier = Modifier.weight(1f)) { Text("+ Sintonia") }
                                    OutlinedButton(onClick = { applyInteraction("Conversazione neutra", 0, 0, 0) }, modifier = Modifier.weight(1f)) { Text("Neutra") }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { applyInteraction("Flirt consensuale", 1, 1, 0) }, modifier = Modifier.weight(1f)) { Text("+ Flirt") }
                                    OutlinedButton(onClick = { applyInteraction("Offesa o pressione", -2, -1, -3) }, modifier = Modifier.weight(1f)) { Text("− Rapporto") }
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
                                    TextButton(onClick = { simulations = simulations - selectedId }, modifier = Modifier.weight(1f)) { Text("Azzera") }
                                }
                            }
                        }
                    }
                }
                TestArea.GALLERY -> {
                    item { CharacterSelector(characters, selectedId) { selectedId = it } }
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
                                ) { Text(if (unlocked < tiers.size) "SIMULA IMMAGINE ${unlocked + 1}" else "TUTTE LE IMMAGINI VERIFICATE") }
                            }
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
private fun CharacterSelector(
    characters: List<CharacterProfile>,
    selectedId: String,
    onSelected: (String) -> Unit
) {
    Column {
        Text("Personaggio del test", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(characters) { character ->
                FilterChip(
                    selected = selectedId == character.id,
                    onClick = { onSelected(character.id) },
                    label = { Text(character.name.substringBefore(' ')) }
                )
            }
        }
    }
}

@Composable
private fun TestQuestionPresets(onSelected: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(listOf(
            "Quanti anni hai?",
            "Cosa fai quando non lavori?",
            "Dimmi qualcosa che non sa nessuno",
            "Ti ricordi chi sono e che musica ascolto?",
            "Ricordi il nostro primo bacio?"
        )) { preset ->
            AssistChip(onClick = { onSelected(preset) }, label = { Text(preset) })
        }
    }
}

@Composable
private fun SummaryControls(
    shown: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onToggle, enabled = enabled, modifier = Modifier.weight(1f)) {
            Text(if (shown) "NASCONDI" else "MOSTRA RIEPILOGO")
        }
        OutlinedButton(onClick = onCopy, enabled = enabled, modifier = Modifier.weight(1f)) {
            Text("COPIA RIEPILOGO")
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
