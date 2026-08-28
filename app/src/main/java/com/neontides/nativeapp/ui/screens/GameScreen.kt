package com.neontides.nativeapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.neontides.nativeapp.R
import com.neontides.nativeapp.data.GameData
import com.neontides.nativeapp.model.GameState
import com.neontides.nativeapp.model.GameClockSnapshot

@Composable
fun GameScreen(
    state: GameState,
    gameClock: GameClockSnapshot,
    onOpenMap: () -> Unit,
    onOpenPhone: () -> Unit,
    onMainMenu: () -> Unit,
    onTalk: (String) -> Unit
) {
    val minuteOfDay = gameClock.minuteOfDay
    val availableCharacters = GameData.charactersAt(
        state.locationId, state.day, state.periodIndex, minuteOfDay, state.guestCharacterId
    )
    Box(
        Modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(locationBackground(state.locationId)),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(Modifier.fillMaxSize().background(timeOverlay(state.periodIndex)))
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(top = 100.dp, bottom = 104.dp)
        ) {
            val count = availableCharacters.size
            val figureHeight = (maxHeight * 0.98f).coerceIn(275.dp, 390.dp)
            val figureWidth = figureHeight * 0.70f
            val travel = (maxWidth - figureWidth).coerceAtLeast(0.dp)
            availableCharacters.forEachIndexed { index, character ->
                val x = when {
                    count <= 1 -> travel / 2f
                    else -> travel * (index.toFloat() / (count - 1).toFloat())
                }
                val feetCorrection = sceneFeetCorrection(character.id)
                Image(
                    painter = painterResource(gameCharacterDrawable(character.id)),
                    contentDescription = character.name,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = x, y = feetCorrection)
                        .width(figureWidth)
                        .height(figureHeight)
                        .zIndex(index.toFloat())
                        .clickable { onTalk(character.id) },
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomCenter
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0x33000000), Color.Transparent, Color(0xCC090B13))
                )
            )
        )
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Surface(shape = MaterialTheme.shapes.large, color = Color(0xCC151827)) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("NEON BAY", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF6DA8))
                        Text("Giorno ${gameClock.day} · ${gameClock.timeText}")
                        Text(gameClock.phaseName, style = MaterialTheme.typography.labelMedium, color = Color(0xFFFF9BC4))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("€${state.money} · ⚡${state.energy}")
                        TextButton(onClick = onMainMenu, contentPadding = PaddingValues(0.dp)) { Text("☰ Menu") }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                GameData.locations.firstOrNull { it.id == state.locationId }?.name ?: "Neon Bay",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.weight(1f))
            Text("Personaggi disponibili", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(availableCharacters) { c ->
                    AssistChip(onClick = { onTalk(c.id) }, label = { Text(c.name) })
                }
            }
            if (availableCharacters.isEmpty()) {
                Text(
                    "Nessun personaggio è qui in questo momento.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(12.dp))
            Surface(shape = MaterialTheme.shapes.large, color = Color(0xCC151827)) {
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenMap, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Map, null); Spacer(Modifier.width(6.dp)); Text("Mappa")
                }
                OutlinedButton(onClick = onOpenPhone, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Smartphone, null); Spacer(Modifier.width(6.dp)); Text("Telefono")
                }
            }
            }
        }
    }
}

/** Compensa esclusivamente il margine trasparente sotto i piedi del sorgente. */
private fun sceneFeetCorrection(id: String) = when (id) {
    "elena", "luna" -> 8.dp
    "maya" -> 9.dp
    "nadia" -> 7.dp
    else -> 0.dp
}

private fun gameCharacterDrawable(id: String): Int = when (id) {
    "sofia" -> R.drawable.character_sofia
    "maya" -> R.drawable.character_maya
    "elena" -> R.drawable.character_elena
    "luna" -> R.drawable.character_luna
    "chiara" -> R.drawable.character_chiara
    "nadia" -> R.drawable.character_nadia
    "luca" -> R.drawable.character_luca
    "matteo" -> R.drawable.character_matteo
    "kenji" -> R.drawable.character_kenji
    else -> R.drawable.character_sofia
}

private fun locationBackground(locationId: String): Int = when (locationId) {
    "apartment" -> R.drawable.bg_apartment
    "cafe" -> R.drawable.bg_cafe
    "downtown" -> R.drawable.bg_downtown
    "park" -> R.drawable.bg_park
    "beach" -> R.drawable.bg_beach
    "gym" -> R.drawable.bg_gym
    "restaurant" -> R.drawable.bg_restaurant
    "mall" -> R.drawable.bg_mall
    "bar" -> R.drawable.bg_bar
    "supermarket" -> R.drawable.bg_supermarket
    "rooftop" -> R.drawable.bg_rooftop
    "club" -> R.drawable.bg_club
    else -> R.drawable.bg_downtown
}

private fun timeOverlay(periodIndex: Int): Brush = when (periodIndex) {
    0 -> Brush.verticalGradient(listOf(Color(0x22FFD38A), Color(0x11000000)))
    1 -> Brush.verticalGradient(listOf(Color.Transparent, Color(0x220B0E18)))
    2 -> Brush.verticalGradient(listOf(Color(0x22FF7A66), Color(0x44251B4A)))
    else -> Brush.verticalGradient(listOf(Color(0x66101A44), Color(0x88050918)))
}
