package com.neontides.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neontides.nativeapp.data.GameData
import com.neontides.nativeapp.GameViewModel
import com.neontides.nativeapp.model.CharacterProfile

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
    val characters = GameData.characters
    var selectedId by remember { mutableStateOf(characters.first().id) }
    var simulations by remember { mutableStateOf(emptyMap<String, SimulatedRelationship>()) }
    var simulatedUnlocks by remember { mutableStateOf(emptySet<String>()) }
    var pending by remember { mutableStateOf<String?>(null) }
    val character = characters.first { it.id == selectedId }
    val relation = simulations[selectedId] ?: SimulatedRelationship()
    val tiers = listOf("profile", "casual", "flirt", "date", "intimate")
    val unlocked = tiers.count { "${character.id}_$it" in simulatedUnlocks }

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
                LunaDiagnosticLab(vm)
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
