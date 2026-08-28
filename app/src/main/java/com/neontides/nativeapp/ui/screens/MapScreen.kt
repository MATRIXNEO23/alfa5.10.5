package com.neontides.nativeapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.neontides.nativeapp.R
import com.neontides.nativeapp.data.GameData
import com.neontides.nativeapp.model.GameState
import com.neontides.nativeapp.model.GameClockSnapshot

@Composable
fun MapScreen(state: GameState, gameClock: GameClockSnapshot, onVisit: (String) -> Unit, onBack: () -> Unit, onMainMenu: () -> Unit) {
    val arrivalPeriod = state.periodIndex
    val arrivalDay = state.day
    val minuteOfDay = gameClock.minuteOfDay
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF090B13), Color(0xFF1A1024))))) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("MONDO", color = Color(0xFFFF6DA8), style = MaterialTheme.typography.labelMedium)
                    Text("Mappa di Neon Bay", style = MaterialTheme.typography.headlineMedium)
                    Text("Giorno ${gameClock.day} · ${gameClock.timeText} · ${gameClock.phaseName}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onMainMenu) { Text("☰ Menu") }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(GameData.locations) { loc ->
                    val isOpen = GameData.isLocationOpenAt(loc.id, minuteOfDay)
                    val present = GameData.charactersAt(loc.id, arrivalDay, arrivalPeriod, minuteOfDay)
                    Card(
                        onClick = { onVisit(loc.id) },
                        enabled = isOpen,
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xDD171522),
                            disabledContainerColor = Color(0xCC101018)
                        )
                    ) {
                        Box(Modifier.fillMaxWidth().height(142.dp)) {
                            Image(painterResource(locationImage(loc.id)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xE6090B13), Color(0x66090B13)))))
                            Column(Modifier.align(Alignment.CenterStart).padding(16.dp).fillMaxWidth(0.82f)) {
                                Text("${loc.icon} ${loc.name}", style = MaterialTheme.typography.titleLarge)
                                Text(loc.description, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (isOpen) "APERTO · ${GameData.openingLabel(loc)}" else "CHIUSO · ${GameData.openingLabel(loc)}",
                                    color = if (isOpen) Color(0xFF7DE2C4) else Color(0xFFFF7B91),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                if (present.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text("Qui: ${present.joinToString { it.name.substringBefore(' ') }}", color = Color(0xFFFF9BC4), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("← Torna alla scena") }
        }
    }
}

private fun locationImage(id: String): Int = when (id) {
    "apartment" -> R.drawable.bg_apartment; "cafe" -> R.drawable.bg_cafe
    "downtown" -> R.drawable.bg_downtown; "park" -> R.drawable.bg_park
    "beach" -> R.drawable.bg_beach; "gym" -> R.drawable.bg_gym
    "restaurant" -> R.drawable.bg_restaurant; "mall" -> R.drawable.bg_mall
    "bar" -> R.drawable.bg_bar; "supermarket" -> R.drawable.bg_supermarket
    "rooftop" -> R.drawable.bg_rooftop; "club" -> R.drawable.bg_club
    else -> R.drawable.bg_downtown
}
