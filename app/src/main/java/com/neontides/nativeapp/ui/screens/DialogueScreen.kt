package com.neontides.nativeapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.neontides.nativeapp.R
import com.neontides.nativeapp.model.*

@Composable
fun DialogueScreen(
    character: CharacterProfile,
    gameClock: GameClockSnapshot,
    relationship: Relationship,
    lastDelta: RelationshipDelta?,
    history: List<DialogueMessage>,
    isThinking: Boolean,
    streamingReply: String,
    aiReady: Boolean,
    aiStatus: String,
    turnsRemaining: Int?,
    conversationLimitReached: Boolean,
    canInviteHome: Boolean,
    inviteRequirements: String,
    onSend: (String) -> Unit,
    onInviteHome: () -> Unit,
    onClose: () -> Unit,
    onMainMenu: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var submittedText by remember { mutableStateOf<String?>(null) }
    var elapsed by remember { mutableIntStateOf(0) }
    val chatListState = rememberLazyListState()
    val sendEnabled = !isThinking &&
        !conversationLimitReached &&
        aiReady &&
        !aiStatus.startsWith("Caricamento") &&
        !aiStatus.startsWith("Preparazione")
    fun submitInput() {
        val text = input.trim()
        if (sendEnabled && text.isNotEmpty()) {
            submittedText = text
            onSend(text)
        }
    }
    LaunchedEffect(isThinking) {
        elapsed = 0
        while (isThinking) {
            kotlinx.coroutines.delay(1000)
            elapsed++
        }
    }
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) chatListState.animateScrollToItem(history.lastIndex)
        submittedText?.let { submitted ->
            if (history.any { it.speaker == "Tu" && it.text == submitted }) {
                input = ""
                submittedText = null
            }
        }
    }
    LaunchedEffect(streamingReply.length / 48) {
        if (streamingReply.isNotBlank()) {
            chatListState.scrollToItem(history.size)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color(0xF20D0F1A),
        tonalElevation = 12.dp
    ) {
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(character.name, style = MaterialTheme.typography.headlineSmall)
                Text("${character.age} anni · ${character.job} · ${relationship.stage}", style = MaterialTheme.typography.bodySmall)
                Text("Giorno ${gameClock.day} · ${gameClock.timeText} · ${gameClock.phaseName}", color = Color(0xFFFF9BC4), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onMainMenu) { Text("☰ Menu") }
        }

        Spacer(Modifier.height(8.dp))
        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.large) {
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatValue("❤️", relationship.affection, lastDelta?.affection)
                StatValue("🔥", relationship.attraction, lastDelta?.attraction)
                StatValue("🤝", relationship.trust, lastDelta?.trust)
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Image(
                painter = painterResource(characterDrawable(character.id)),
                contentDescription = character.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.82f)
                            )
                        )
                    )
            )
            LazyColumn(
                state = chatListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 190.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { msg ->
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.91f),
                        tonalElevation = 4.dp
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(msg.speaker, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(msg.text)
                        }
                    }
                }
                if (isThinking && streamingReply.isNotBlank()) {
                    item(key = "streaming-${character.id}") {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            tonalElevation = 6.dp
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(character.name, style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.height(4.dp))
                                Text("$streamingReply ▌")
                            }
                        }
                    }
                }
            }
        }

        if (isThinking) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$aiStatus · ${elapsed}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.height(6.dp))
        } else if (!conversationLimitReached) {
            Text(
                if (aiStatus.contains("pronto · contesto memorizzato")) "● $aiStatus" else aiStatus,
                style = MaterialTheme.typography.labelSmall,
                color = if (aiStatus.contains("pronto · contesto memorizzato")) Color(0xFF65D98B) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
        }

        if (turnsRemaining != null && conversationLimitReached) {
            Text(
                "${character.name} deve andare · potrete riprendere più tardi",
                style = MaterialTheme.typography.labelSmall,
                color = if (conversationLimitReached) Color(0xFFFF9BC4) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text("Scrivi la tua risposta…") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !conversationLimitReached && aiReady && !isThinking,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submitInput() }),
            minLines = 2,
            maxLines = 5
        )

        Spacer(Modifier.height(8.dp))
        if (canInviteHome) {
            Button(
                onClick = onInviteHome,
                enabled = !isThinking,
                modifier = Modifier.fillMaxWidth()
            ) { Text("🏠 Invita nel tuo appartamento") }
            Spacer(Modifier.height(8.dp))
        } else {
            Text(inviteRequirements, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Chiudi") }
            Button(
                onClick = { submitInput() },
                enabled = sendEnabled,
                modifier = Modifier.weight(1f)
            ) { Text("Invia") }
        }
    }
    }
}

private fun characterDrawable(characterId: String): Int = when (characterId) {
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

@Composable
private fun StatValue(icon: String, value: Int, delta: Int?) {
    Column {
        Text("$icon $value")
        if (delta != null && delta != 0) {
            Text(
                if (delta > 0) "+$delta" else "$delta",
                style = MaterialTheme.typography.labelSmall,
                color = if (delta > 0)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.error
            )
        }
    }
}
