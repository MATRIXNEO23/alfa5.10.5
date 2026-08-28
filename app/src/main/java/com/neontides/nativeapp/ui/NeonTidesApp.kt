package com.neontides.nativeapp.ui

import android.app.Activity
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neontides.nativeapp.GameViewModel
import com.neontides.nativeapp.R
import com.neontides.nativeapp.ai.ModelManager
import com.neontides.nativeapp.ai.SecureAiSettings
import com.neontides.nativeapp.data.SaveGameManager
import com.neontides.nativeapp.ui.screens.*

private enum class Screen { Menu, PlayerSetup, Game, Map, Phone, Models, Saves, Gallery, TestUpdates, Diagnostics }

@Composable
fun NeonTidesApp(vm: GameViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val gameClock by vm.gameClock.collectAsState()
    val active by vm.activeCharacter.collectAsState()
    val thinking by vm.isThinking.collectAsState()
    val lastDelta by vm.lastDelta.collectAsState()
    val sessionTurns by vm.sessionTurns.collectAsState()
    val conversationLimitReached by vm.conversationLimitReached.collectAsState()
    val aiStatus by vm.aiStatus.collectAsState()
    val streamingReply by vm.streamingReply.collectAsState()
    val aiDiagnosticsSummary by vm.aiDiagnosticsSummary.collectAsState()
    val aiReady by vm.aiReady.collectAsState()
    val pendingGalleryUnlock by vm.pendingGalleryUnlock.collectAsState()
    val incomingCallCharacterId by vm.incomingCallCharacterId.collectAsState()
    val phoneSelectedCharacterId by vm.phoneSelectedCharacterId.collectAsState()
    val phoneNotice by vm.phoneNotice.collectAsState()

    val context = LocalContext.current
    val modelManager = remember { ModelManager(context) }
    val aiSettings = remember { SecureAiSettings(context) }
    val saveManager = remember { SaveGameManager(context) }
    var screen by remember { mutableStateOf(Screen.Menu) }
    var showExitDialog by remember { mutableStateOf(false) }
    var gameStarted by remember { mutableStateOf(false) }
    var galleryOpenedFromPhone by remember { mutableStateOf(false) }
    var mapOpenedFromPhone by remember { mutableStateOf(false) }
    val phoneMessages = state.chatHistories.values.flatten().filter { it.channel == "phone_message" }
    var notifiedPhoneMessages by remember { mutableIntStateOf(phoneMessages.size) }

    // La notifica segue il telefono anche quando Neon OS non è aperto, ma non
    // viene mai riprodotta per i dialoghi di persona o per i messaggi inviati
    // dal giocatore.
    LaunchedEffect(phoneMessages.size) {
        val newest = phoneMessages.lastOrNull()
        if (phoneMessages.size > notifiedPhoneMessages && newest?.speaker != "Tu") {
            runCatching {
                MediaPlayer.create(context, R.raw.neontides_phone_message)?.apply {
                    setVolume(1f, 1f)
                    setOnCompletionListener { it.release() }
                    start()
                }
            }
        }
        notifiedPhoneMessages = phoneMessages.size
    }

    DisposableEffect(screen) {
        val menuTheme = if (screen == Screen.Menu) {
            runCatching {
                MediaPlayer.create(context, R.raw.neontides_main_theme)?.apply {
                    isLooping = true
                    setVolume(0.34f, 0.34f)
                    start()
                }
            }.getOrNull()
        } else null
        onDispose {
            menuTheme?.let { player ->
                runCatching { if (player.isPlaying) player.stop() }
                player.release()
            }
        }
    }

    DisposableEffect(incomingCallCharacterId) {
        val ringtone = incomingCallCharacterId?.let {
            runCatching {
                MediaPlayer.create(context, R.raw.neontides_ringtone)?.apply {
                    isLooping = true
                    start()
                }
            }.getOrNull()
        }
        onDispose {
            ringtone?.let { player ->
                runCatching { if (player.isPlaying) player.stop() }
                player.release()
            }
        }
    }

    LaunchedEffect(state, gameStarted) {
        if (!gameStarted) return@LaunchedEffect
        kotlinx.coroutines.delay(500)
        saveManager.save(0, state)
    }

    BackHandler(enabled = !showExitDialog) {
        when (screen) {
            Screen.Map -> screen = if (mapOpenedFromPhone) Screen.Phone else Screen.Game
            Screen.Phone -> screen = Screen.Game
            Screen.Gallery -> screen = if (galleryOpenedFromPhone) Screen.Phone else Screen.Menu
            Screen.PlayerSetup, Screen.Models, Screen.Saves, Screen.TestUpdates, Screen.Diagnostics -> screen = Screen.Menu
            Screen.Game -> if (active != null) vm.closeConversation() else screen = Screen.Menu
            Screen.Menu -> showExitDialog = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            when (screen) {
        Screen.Menu -> MainMenuScreen(
            onNewGame = { screen = Screen.PlayerSetup },
            aiReady = aiReady,
            aiStatus = aiStatus,
            hasAutoSave = saveManager.info(0).timestamp != null,
            onContinue = { saveManager.load(0)?.let { vm.loadGame(it); gameStarted = true; screen = Screen.Game } },
            onSaves = { screen = Screen.Saves },
            onGallery = { galleryOpenedFromPhone = false; screen = Screen.Gallery },
            onTestUpdates = { screen = Screen.TestUpdates },
            onModels = { screen = Screen.Models },
            onDiagnostics = { screen = Screen.Diagnostics }
        )
        Screen.PlayerSetup -> PlayerSetupScreen(
            onStart = { name, age, gender, appearanceId, style ->
                vm.newGame(name, age, gender, appearanceId, style)
                gameStarted = true
                screen = Screen.Game
            },
            onBack = { screen = Screen.Menu }
        )
        Screen.Saves -> SaveSlotsScreen(
            manager = saveManager,
            currentState = state,
            canSave = gameStarted,
            onLoad = { if (aiReady) { vm.loadGame(it); gameStarted = true; screen = Screen.Game } else screen = Screen.Models },
            onBack = { screen = Screen.Menu }
        )
        Screen.Gallery -> GalleryScreen(
            unlocked = state.unlockedGallery,
            onBack = { screen = if (galleryOpenedFromPhone) Screen.Phone else Screen.Menu }
        )
        Screen.TestUpdates -> TestUpdatesScreen(
            vm = vm,
            onBack = { screen = Screen.Menu }
        )
        Screen.Game -> Box(Modifier.fillMaxSize()) {
        GameScreen(
            state = state,
            gameClock = gameClock,
            onOpenMap = { mapOpenedFromPhone = false; screen = Screen.Map },
            onOpenPhone = { screen = Screen.Phone },
            onMainMenu = { screen = Screen.Menu },
            onTalk = {
                vm.startConversation(it)
            }
        )
            val character = active
            if (character != null) {
                Dialog(
                    onDismissRequest = vm::closeConversation,
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    DialogueScreen(
                        character = character,
                        gameClock = gameClock,
                        relationship = state.relationships[character.id] ?: com.neontides.nativeapp.model.Relationship(),
                        lastDelta = lastDelta,
                        history = state.chatHistories[character.id].orEmpty(),
                        isThinking = thinking,
                        streamingReply = streamingReply,
                        aiReady = aiReady,
                        aiStatus = aiStatus,
                        turnsRemaining = vm.conversationTurnsRemaining(
                            state.relationships[character.id] ?: com.neontides.nativeapp.model.Relationship(),
                            sessionTurns
                        ),
                        conversationLimitReached = conversationLimitReached,
                        canInviteHome = vm.canInviteHome(
                            character,
                            state.relationships[character.id] ?: com.neontides.nativeapp.model.Relationship()
                        ),
                        inviteRequirements = vm.inviteRequirements(character),
                        onSend = vm::sendMessage,
                        onInviteHome = { vm.inviteHome(character.id) },
                        onClose = vm::closeConversation,
                        onMainMenu = { vm.closeConversation(); screen = Screen.Menu }
                    )
                }
            }
        }
        Screen.Map -> MapScreen(
            state = state,
            gameClock = gameClock,
            onVisit = {
                vm.visitLocation(it)
                mapOpenedFromPhone = false
                screen = Screen.Game
            },
            onBack = { screen = if (mapOpenedFromPhone) Screen.Phone else Screen.Game },
            onMainMenu = { screen = Screen.Menu }
        )
        Screen.Phone -> PhoneScreen(
            state = state,
            selectedCharacterId = phoneSelectedCharacterId,
            notice = phoneNotice,
            canCall = { character ->
                vm.canPhoneCall(
                    character,
                    state.relationships[character.id] ?: com.neontides.nativeapp.model.Relationship()
                )
            },
            onSelectCharacter = vm::selectPhoneCharacter,
            onCall = vm::placePhoneCall,
            onOpenGallery = { galleryOpenedFromPhone = true; screen = Screen.Gallery },
            onOpenMap = { mapOpenedFromPhone = true; screen = Screen.Map },
            onBack = { screen = Screen.Game }
        )
        Screen.Models -> ModelManagerScreen(
            manager = modelManager,
            settings = aiSettings,
            onModelChanged = vm::reloadLocalAi,
            onUnloadModel = vm::unloadLocalAi,
            onBack = { screen = Screen.Menu }
        )
        Screen.Diagnostics -> DiagnosticsScreen(
            manager = modelManager,
            appSummary = aiDiagnosticsSummary,
            onRestartAi = vm::reloadLocalAi,
            onClearAppDiagnostics = vm::clearAiDiagnostics,
            onBack = { screen = Screen.Menu }
        )
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Uscire da Neon Tides?") },
            text = { Text("La partita può essere salvata automaticamente prima della chiusura.") },
            confirmButton = {
                Button(onClick = {
                    if (gameStarted) saveManager.save(0, state)
                    (context as? Activity)?.finish()
                }) { Text("Salva ed esci") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Annulla") }
            }
        )
    }

    pendingGalleryUnlock?.let { key ->
        val characterId = key.substringBeforeLast('_')
        val tierId = key.substringAfterLast('_')
        val characterName = com.neontides.nativeapp.data.GameData.characters
            .firstOrNull { it.id == characterId }?.name ?: characterId
        GalleryUnlockDialog(
            characterName = characterName,
            tierTitle = galleryTierTitle(tierId),
            drawable = galleryCharacterDrawable(characterId, tierId),
            onContinue = vm::consumeGalleryUnlock,
            onOpenGallery = {
                vm.consumeGalleryUnlock()
                galleryOpenedFromPhone = false
                screen = Screen.Gallery
            }
        )
    }

    incomingCallCharacterId?.let { id ->
        val caller = com.neontides.nativeapp.data.GameData.characters.firstOrNull { it.id == id }
        if (caller != null) {
            AlertDialog(
                onDismissRequest = vm::declineIncomingCall,
                title = { Text("📞 Chiamata in arrivo") },
                text = { Text("${caller.name} ti sta chiamando.") },
                confirmButton = {
                    Button(onClick = {
                        vm.acceptIncomingCall()
                        screen = Screen.Phone
                    }) { Text("Rispondi") }
                },
                dismissButton = {
                    TextButton(onClick = vm::declineIncomingCall) { Text("Rifiuta") }
                }
            )
        }
    }
}
