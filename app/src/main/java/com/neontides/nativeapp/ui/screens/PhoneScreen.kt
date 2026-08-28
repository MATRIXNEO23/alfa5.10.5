package com.neontides.nativeapp.ui.screens

import android.content.Context
import android.os.BatteryManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neontides.nativeapp.R
import com.neontides.nativeapp.data.GameData
import com.neontides.nativeapp.model.CharacterProfile
import com.neontides.nativeapp.model.GameState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PhonePage(val title: String) {
    HOME("Telefono"), MESSAGES("Messaggi"), CALLS("Chiamate"), CONTACTS("Contatti"),
    PROFILE("Profilo"), CALENDAR("Calendario"), SETTINGS("Impostazioni"), RECENTS("App recenti")
}

private data class PhoneApp(val icon: String, val label: String, val page: PhonePage? = null)

@Composable
fun PhoneScreen(
    state: GameState,
    selectedCharacterId: String?,
    notice: String,
    canCall: (CharacterProfile) -> Boolean,
    onSelectCharacter: (String) -> Unit,
    onCall: (String) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenMap: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(if (selectedCharacterId != null) PhonePage.CALLS else PhonePage.HOME) }
    var selectedId by remember(selectedCharacterId) { mutableStateOf(selectedCharacterId) }
    var recentPages by remember { mutableStateOf(emptyList<PhonePage>()) }
    val selected = GameData.characters.firstOrNull { it.id == selectedId }
    var clockText by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    val batteryManager = remember { context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager }
    var batteryPercent by remember { mutableIntStateOf(batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1) }

    fun openPage(next: PhonePage) {
        if (next != PhonePage.HOME && next != PhonePage.RECENTS) {
            recentPages = (listOf(next) + recentPages.filterNot { it == next }).take(5)
        }
        selectedId = null
        page = next
    }

    fun phoneBack() {
        when {
            selectedId != null -> selectedId = null
            page != PhonePage.HOME -> page = PhonePage.HOME
            else -> onBack()
        }
    }

    BackHandler { phoneBack() }

    LaunchedEffect(Unit) {
        while (true) {
            clockText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            batteryPercent = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            delay(30_000)
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF070912), Color(0xFF121027), Color(0xFF090B14)))
        ).padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(34.dp),
            color = Color(0xFF10121E),
            tonalElevation = 12.dp
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                PhoneStatusBar(clockText, batteryPercent)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (page != PhonePage.HOME || selected != null) {
                            TextButton(onClick = {
                                phoneBack()
                            }) { Text("‹") }
                        }
                        Column {
                            Text("NEON OS", color = Color(0xFF8B7CFF), style = MaterialTheme.typography.labelMedium)
                            Text(selected?.name ?: page.title, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    TextButton(onClick = onBack) { Text("✕") }
                }
                Spacer(Modifier.height(8.dp))

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        selected != null && page == PhonePage.MESSAGES -> MessagesWithContact(
                            state = state,
                            character = selected,
                            onCall = onCall,
                            canCall = canCall(selected),
                            notice = notice
                        )
                        selected != null && (page == PhonePage.CONTACTS || page == PhonePage.CALLS) -> ContactDetail(
                            state = state,
                            character = selected,
                            canCall = canCall(selected),
                            onCall = onCall,
                            onMessages = { page = PhonePage.MESSAGES }
                        )
                        page == PhonePage.HOME -> PhoneHome(
                            state = state,
                            onPage = ::openPage,
                            onGallery = onOpenGallery,
                            onMap = onOpenMap
                        )
                        page == PhonePage.MESSAGES -> PhoneContacts(state, "phone_message") { id ->
                            selectedId = id
                            onSelectCharacter(id)
                        }
                        page == PhonePage.CALLS -> CallsPage(state, canCall, onCall) { id ->
                            selectedId = id
                            onSelectCharacter(id)
                        }
                        page == PhonePage.CONTACTS -> PhoneContacts(state, null) { id ->
                            selectedId = id
                            onSelectCharacter(id)
                        }
                        page == PhonePage.PROFILE -> PlayerProfileCard(state, expanded = true)
                        page == PhonePage.CALENDAR -> InfoPanel(
                            "📅", "Calendario", "Qui appariranno appuntamenti, eventi e tappe della trama."
                        )
                        page == PhonePage.SETTINGS -> InfoPanel(
                            "⚙️", "Impostazioni telefono", "Suoneria per le chiamate e notifica distinta solo per i messaggi del telefono."
                        )
                        page == PhonePage.RECENTS -> RecentApps(recentPages, ::openPage)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = ::phoneBack) { Text("◁", style = MaterialTheme.typography.titleLarge) }
                    IconButton(onClick = { openPage(PhonePage.HOME) }) { Text("○", style = MaterialTheme.typography.titleLarge) }
                    IconButton(onClick = { page = PhonePage.RECENTS; selectedId = null }) { Text("▢", style = MaterialTheme.typography.titleLarge) }
                }
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Chiudi telefono e torna alla scena") }
            }
        }
    }
}

@Composable
private fun PhoneStatusBar(time: String, batteryPercent: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(time, style = MaterialTheme.typography.labelSmall, color = Color(0xFFBDB7D4))
        Text(
            "NEON 5G  ● Wi-Fi  🔋${if (batteryPercent >= 0) "$batteryPercent%" else ""}",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFBDB7D4)
        )
    }
}

@Composable
private fun RecentApps(pages: List<PhonePage>, onOpen: (PhonePage) -> Unit) {
    if (pages.isEmpty()) {
        InfoPanel("▢", "Nessuna app recente", "Le applicazioni aperte appariranno qui.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(pages) { page ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(page) },
                colors = CardDefaults.cardColors(containerColor = Color(0xCC1B1828))
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(phonePageIcon(page), style = MaterialTheme.typography.titleLarge)
                    Text(page.title, style = MaterialTheme.typography.titleMedium)
                    Text("Apri", color = Color(0xFFFF6DA8))
                }
            }
        }
    }
}

private fun phonePageIcon(page: PhonePage): String = when (page) {
    PhonePage.MESSAGES -> "💬"
    PhonePage.CALLS -> "📞"
    PhonePage.CONTACTS -> "👥"
    PhonePage.CALENDAR -> "📅"
    PhonePage.PROFILE -> "👤"
    PhonePage.SETTINGS -> "⚙️"
    else -> "📱"
}

@Composable
private fun PhoneHome(
    state: GameState,
    onPage: (PhonePage) -> Unit,
    onGallery: () -> Unit,
    onMap: () -> Unit
) {
    val unread = state.chatHistories.values.flatten().count { it.channel == "phone_message" && it.speaker != "Tu" }
    val apps = listOf(
        PhoneApp("💬", "Messaggi", PhonePage.MESSAGES),
        PhoneApp("📞", "Chiamate", PhonePage.CALLS),
        PhoneApp("👥", "Contatti", PhonePage.CONTACTS),
        PhoneApp("🖼️", "Galleria"),
        PhoneApp("🗺️", "Mappa"),
        PhoneApp("📅", "Calendario", PhonePage.CALENDAR),
        PhoneApp("👤", "Profilo", PhonePage.PROFILE),
        PhoneApp("⚙️", "Impostazioni", PhonePage.SETTINGS)
    )
    Column(Modifier.fillMaxSize()) {
        PlayerProfileCard(state, expanded = false)
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            items(apps) { app ->
                Column(
                    modifier = Modifier.clickable {
                        when (app.label) {
                            "Galleria" -> onGallery()
                            "Mappa" -> onMap()
                            else -> app.page?.let(onPage)
                        }
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(62.dp).clip(RoundedCornerShape(18.dp)).background(
                            Brush.linearGradient(listOf(Color(0xFF5A2F8F), Color(0xFFE23C87)))
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(app.icon, style = MaterialTheme.typography.headlineMedium)
                        if (app.label == "Messaggi" && unread > 0) {
                            Badge(Modifier.align(Alignment.TopEnd)) { Text(unread.coerceAtMost(99).toString()) }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(app.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun PlayerProfileCard(state: GameState, expanded: Boolean) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC1B1828)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            FaceThumbnail(playerAppearanceFaceDrawable(state.playerAppearanceId), state.playerName, if (expanded) 86 else 68)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("IL TUO PROFILO", color = Color(0xFFFF6DA8), style = MaterialTheme.typography.labelMedium)
                Text("${state.playerName} · ${state.playerGender} · ${state.playerAge} anni", fontWeight = FontWeight.Bold)
                Text("Carisma ${state.stats.charisma} · Fiducia ${state.stats.confidence}")
                if (expanded) {
                    Text("Intelligenza ${state.stats.intelligence} · Forma ${state.stats.fitness}")
                    Text("Reputazione ${state.stats.reputation} · Stile ${state.playerStyle}")
                }
            }
        }
    }
}

@Composable
private fun PhoneContacts(state: GameState, channel: String?, onSelect: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(GameData.characters) { character ->
            val relationship = state.relationships[character.id] ?: return@items
            val count = state.chatHistories[character.id].orEmpty().count { channel == null || it.channel == channel }
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xCC1B1828)),
                modifier = Modifier.fillMaxWidth().clickable { onSelect(character.id) }
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FaceThumbnail(phoneCharacterDrawable(character.id), character.name, 58)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(character.name, style = MaterialTheme.typography.titleMedium)
                            Text(relationship.stage, color = Color(0xFFAAA3C2), style = MaterialTheme.typography.labelSmall)
                        }
                        Text("❤️ ${relationship.affection}  🔥 ${relationship.attraction}  🤝 ${relationship.trust}")
                        if (channel != null) Text("$count messaggi telefonici", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessagesWithContact(
    state: GameState,
    character: CharacterProfile,
    onCall: (String) -> Unit,
    canCall: Boolean,
    notice: String
) {
    val messages = state.chatHistories[character.id].orEmpty().filter { it.channel == "phone_message" }
    Column(Modifier.fillMaxSize()) {
        Button(onClick = { onCall(character.id) }, enabled = canCall, modifier = Modifier.fillMaxWidth()) {
            Text("📞 Chiama ${character.name.substringBefore(' ')}")
        }
        if (notice.isNotBlank()) Text(notice, color = Color(0xFFFF9BC4), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { message ->
                val fromCharacter = message.speaker == character.name
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromCharacter) Arrangement.Start else Arrangement.End) {
                    Surface(
                        color = if (fromCharacter) Color(0xFF211D31) else Color(0xFF5A2452),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.widthIn(max = 285.dp)
                    ) { Text(message.text, Modifier.padding(12.dp)) }
                }
            }
            if (messages.isEmpty()) item {
                InfoPanel("💬", "Nessun messaggio", "Le conversazioni fatte di persona restano separate dal telefono.")
            }
        }
    }
}

@Composable
private fun CallsPage(
    state: GameState,
    canCall: (CharacterProfile) -> Boolean,
    onCall: (String) -> Unit,
    onSelect: (String) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(GameData.characters) { character ->
            val calls = state.chatHistories[character.id].orEmpty().count { it.channel == "phone_call" }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC1B1828))) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FaceThumbnail(phoneCharacterDrawable(character.id), character.name, 54)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f).clickable { onSelect(character.id) }) {
                        Text(character.name, fontWeight = FontWeight.Bold)
                        Text(if (calls == 0) "Nessuna chiamata" else "$calls eventi chiamata", style = MaterialTheme.typography.bodySmall)
                    }
                    FilledTonalButton(onClick = { onCall(character.id) }, enabled = canCall(character)) { Text("📞") }
                }
            }
        }
    }
}

@Composable
private fun ContactDetail(
    state: GameState,
    character: CharacterProfile,
    canCall: Boolean,
    onCall: (String) -> Unit,
    onMessages: () -> Unit
) {
    val relationship = state.relationships[character.id]
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        FaceThumbnail(phoneCharacterDrawable(character.id), character.name, 122)
        Spacer(Modifier.height(12.dp))
        Text(character.name, style = MaterialTheme.typography.headlineSmall)
        Text("${character.age} anni · ${character.job}", color = Color(0xFFB9B2D0))
        Spacer(Modifier.height(14.dp))
        Text("❤️ ${relationship?.affection ?: 0}   🔥 ${relationship?.attraction ?: 0}   🤝 ${relationship?.trust ?: 0}")
        Text(relationship?.stage ?: "Sconosciuti", color = Color(0xFFFF9BC4))
        Spacer(Modifier.height(20.dp))
        Button(onClick = { onCall(character.id) }, enabled = canCall, modifier = Modifier.fillMaxWidth()) { Text("📞 Chiama") }
        OutlinedButton(onClick = onMessages, modifier = Modifier.fillMaxWidth()) { Text("💬 Messaggi") }
        if (!canCall) Text("Le chiamate si sbloccano dal livello Amicizia.", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun InfoPanel(icon: String, title: String, text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC1B1828)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, style = MaterialTheme.typography.displaySmall)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(text, color = Color(0xFFB9B2D0))
        }
    }
}

@Composable
private fun FaceThumbnail(drawable: Int, description: String, size: Int = 42) {
    Image(
        painter = painterResource(drawable),
        contentDescription = description,
        modifier = Modifier.size(size.dp).clip(CircleShape).background(Color(0xFF2A2638)),
        contentScale = ContentScale.Crop
    )
}

private fun phoneCharacterDrawable(id: String): Int = when (id) {
    "sofia" -> R.drawable.phone_face_character_sofia
    "maya" -> R.drawable.phone_face_character_maya
    "elena" -> R.drawable.phone_face_character_elena
    "luna" -> R.drawable.phone_face_character_luna
    "chiara" -> R.drawable.phone_face_character_chiara
    "nadia" -> R.drawable.phone_face_character_nadia
    "luca" -> R.drawable.phone_face_character_luca
    "matteo" -> R.drawable.phone_face_character_matteo
    "kenji" -> R.drawable.phone_face_character_kenji
    else -> R.drawable.phone_face_character_sofia
}
