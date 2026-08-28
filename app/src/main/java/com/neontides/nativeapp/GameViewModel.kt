package com.neontides.nativeapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neontides.nativeapp.ai.AiEngine
import com.neontides.nativeapp.ai.BaseDialogueTestRecord
import com.neontides.nativeapp.ai.ModelManager
import com.neontides.nativeapp.ai.SecureAiSettings
import com.neontides.nativeapp.ai.ModularLabRequest
import com.neontides.nativeapp.ai.ModularLabRecord
import com.neontides.nativeapp.ai.ModularLabResult
import com.neontides.nativeapp.ai.PlayerFactExtractor
import com.neontides.nativeapp.data.GameData
import com.neontides.nativeapp.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val _baseDialogueTestHistory = MutableStateFlow<List<BaseDialogueTestRecord>>(emptyList())
    val baseDialogueTestHistory: StateFlow<List<BaseDialogueTestRecord>> =
        _baseDialogueTestHistory.asStateFlow()

    private val _modularLabHistory = MutableStateFlow<List<ModularLabRecord>>(emptyList())
    val modularLabHistory: StateFlow<List<ModularLabRecord>> = _modularLabHistory.asStateFlow()

    suspend fun runModularMemoryLab(text: String, affection: Int, attraction: Int, trust: Int): ModularLabResult {
        val result = aiEngine.runModularMemoryLab(ModularLabRequest(text, affection, attraction, trust))
        val updated = (_modularLabHistory.value + ModularLabRecord(
            question = text.trim(),
            affection = affection,
            attraction = attraction,
            trust = trust,
            result = result
        )).takeLast(30)
        _modularLabHistory.value = updated
        return result
    }

    fun clearModularLabHistory() {
        _modularLabHistory.value = emptyList()
    }

    suspend fun runBaseDialogueTest(
        characterId: String,
        text: String,
        affection: Int,
        attraction: Int,
        trust: Int
    ): BaseDialogueTestRecord {
        val character = GameData.characters.first { it.id == characterId }
        val snapshot = _state.value
        val liveRelationship = snapshot.relationships[characterId] ?: Relationship()
        val stage = relationshipStageForTest(affection, attraction, trust)
        val testRelationship = liveRelationship.copy(
            affection = affection,
            attraction = attraction,
            trust = trust,
            stage = stage,
            activeConversationTurns = 0,
            activeConversationLimit = 0
        )
        val testState = snapshot.copy(
            relationships = snapshot.relationships + (characterId to testRelationship),
            chatHistories = snapshot.chatHistories + (characterId to emptyList())
        )
        val started = android.os.SystemClock.elapsedRealtime()
        val result = aiEngine.runBaseDialogueTest(character, testState, testRelationship, text.trim())
        val record = BaseDialogueTestRecord(
            characterId = character.id,
            characterName = character.name,
            question = text.trim(),
            affection = affection,
            attraction = attraction,
            trust = trust,
            stage = stage,
            elapsedMs = android.os.SystemClock.elapsedRealtime() - started,
            preparationDiagnostic = aiEngine.preparationDiagnostics(),
            resourceDiagnostic = aiEngine.inferenceResourceDiagnostics(),
            result = result
        )
        return record
    }

    fun confirmBaseDialogueTest(record: BaseDialogueTestRecord, accepted: Boolean) {
        val confirmed = record.copy(changeConfirmed = accepted)
        val updated = (_baseDialogueTestHistory.value + confirmed).takeLast(30)
        _baseDialogueTestHistory.value = updated
    }

    fun clearBaseDialogueTestHistory() {
        _baseDialogueTestHistory.value = emptyList()
    }

    private fun relationshipStageForTest(affection: Int, attraction: Int, trust: Int): String = when {
        affection >= 80 && attraction >= 65 && trust >= 70 -> "Relazione"
        affection >= 65 && attraction >= 55 && trust >= 50 -> "Appuntamenti"
        attraction >= 45 && affection >= 35 -> "Attrazione reciproca"
        attraction >= 30 && affection >= 25 -> "Flirt"
        affection >= 20 || trust >= 25 -> "Amicizia"
        affection >= 8 || trust >= 8 -> "Conoscenza"
        else -> "Sconosciuti"
    }

    companion object {
        private const val PHASE_REAL_MS = 25L * 60_000L
        private const val DAY_REAL_MS = PHASE_REAL_MS * 4L
        private val PHASE_START_GAME_MINUTES = intArrayOf(6 * 60, 12 * 60, 15 * 60, 20 * 60)
        private val PHASE_GAME_DURATIONS = intArrayOf(6 * 60, 3 * 60, 5 * 60, 10 * 60)
        private val PHASE_NAMES = arrayOf("Mattina", "Metà giorno", "Pomeriggio", "Notte")
    }

    private val modelManager = ModelManager(app)
    private val aiEngine = AiEngine(modelManager, SecureAiSettings(app))

    private val initialRelationships = GameData.characters.associate {
        it.id to Relationship()
    }

    private val _state = MutableStateFlow(GameState(relationships = initialRelationships))
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _gameClock = MutableStateFlow(clockSnapshot(_state.value))
    val gameClock: StateFlow<GameClockSnapshot> = _gameClock.asStateFlow()

    private val _activeCharacter = MutableStateFlow<CharacterProfile?>(null)
    val activeCharacter: StateFlow<CharacterProfile?> = _activeCharacter.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _lastDelta = MutableStateFlow<RelationshipDelta?>(null)
    val lastDelta: StateFlow<RelationshipDelta?> = _lastDelta.asStateFlow()

    private val _sessionTurns = MutableStateFlow(0)
    val sessionTurns: StateFlow<Int> = _sessionTurns.asStateFlow()

    private val _conversationLimitReached = MutableStateFlow(false)
    val conversationLimitReached: StateFlow<Boolean> = _conversationLimitReached.asStateFlow()

    private val _incomingCallCharacterId = MutableStateFlow<String?>(null)
    val incomingCallCharacterId: StateFlow<String?> = _incomingCallCharacterId.asStateFlow()

    private val _phoneSelectedCharacterId = MutableStateFlow<String?>(null)
    val phoneSelectedCharacterId: StateFlow<String?> = _phoneSelectedCharacterId.asStateFlow()

    private val _phoneNotice = MutableStateFlow("")
    val phoneNotice: StateFlow<String> = _phoneNotice.asStateFlow()

    private val _aiStatus = MutableStateFlow("IA locale non caricata")
    val aiStatus: StateFlow<String> = _aiStatus.asStateFlow()

    private val _streamingReply = MutableStateFlow("")
    val streamingReply: StateFlow<String> = _streamingReply.asStateFlow()

    private val _firstTokenMs = MutableStateFlow<Long?>(null)
    val firstTokenMs: StateFlow<Long?> = _firstTokenMs.asStateFlow()

    private val _aiDiagnosticsSummary = MutableStateFlow(
        "Nessuna risposta ancora misurata. Esegui una conversazione, poi premi AGGIORNA."
    )
    val aiDiagnosticsSummary: StateFlow<String> = _aiDiagnosticsSummary.asStateFlow()
    private val aiDiagnosticEntries = ArrayDeque<String>()

    private val _aiReady = MutableStateFlow(false)
    val aiReady: StateFlow<Boolean> = _aiReady.asStateFlow()

    private val _pendingGalleryUnlock = MutableStateFlow<String?>(null)
    val pendingGalleryUnlock: StateFlow<String?> = _pendingGalleryUnlock.asStateFlow()
    private val galleryUnlockQueue = ArrayDeque<String>()

    init {
        preloadLocalAi()
        startGameClock()
    }

    private fun startGameClock() {
        viewModelScope.launch {
            while (true) {
                val snapshot = clockSnapshot(_state.value)
                _gameClock.value = snapshot
                val current = _state.value
                if (current.locationId != "apartment" && !GameData.isLocationOpenAt(current.locationId, snapshot.minuteOfDay)) {
                    closeConversationForSchedule("location_closed")
                    _state.value = _state.value.copy(locationId = "apartment", guestCharacterId = null)
                    _aiStatus.value = "Il luogo ha chiuso · sei tornato nell'appartamento"
                    delay(1_000)
                    continue
                }
                if (current.day != snapshot.day || current.periodIndex != snapshot.periodIndex) {
                    val conversationPhaseEnded = current.day != snapshot.day || current.periodIndex != snapshot.periodIndex
                    val locationStillOpen = GameData.isLocationOpenAt(current.locationId, snapshot.minuteOfDay)
                    if (conversationPhaseEnded && _activeCharacter.value != null) {
                        closeConversationForSchedule(if (locationStillOpen) "schedule" else "location_closed")
                    }
                    val latest = _state.value
                    val advanced = latest.copy(
                        day = snapshot.day,
                        periodIndex = snapshot.periodIndex,
                        locationId = if (locationStillOpen) latest.locationId else "apartment",
                        guestCharacterId = if (latest.periodIndex != snapshot.periodIndex) null else latest.guestCharacterId,
                        energy = if (latest.day != snapshot.day) 100 else latest.energy
                    )
                    _state.value = advanced
                    if (conversationPhaseEnded) {
                        _aiStatus.value = if (locationStillOpen) {
                            "La fascia oraria è terminata · potrai iniziare una nuova conversazione"
                        } else "Il luogo ha chiuso · sei tornato nell'appartamento"
                    } else if (!locationStillOpen) {
                        _aiStatus.value = "Il luogo ha chiuso · sei tornato nell'appartamento"
                    }
                    maybeScheduleIncomingCall(advanced)
                }
                delay(1_000)
            }
        }
    }

    private fun clockSnapshot(state: GameState, now: Long = System.currentTimeMillis()): GameClockSnapshot {
        val elapsed = (now - state.simulationEpochMs).coerceAtLeast(0L)
        val day = (elapsed / DAY_REAL_MS).toInt() + 1
        val withinDay = elapsed % DAY_REAL_MS
        val period = (withinDay / PHASE_REAL_MS).toInt().coerceIn(0, 3)
        val withinPhase = withinDay % PHASE_REAL_MS
        val phaseGameMinutes = (
            PHASE_GAME_DURATIONS[period].toLong() * withinPhase / PHASE_REAL_MS
        ).toInt()
        val totalGameMinutes = (PHASE_START_GAME_MINUTES[period] + phaseGameMinutes) % 1_440
        val hour = totalGameMinutes / 60
        val minute = totalGameMinutes % 60
        return GameClockSnapshot(
            day,
            period,
            "%02d:%02d".format(hour, minute),
            PHASE_NAMES[period],
            totalGameMinutes
        )
    }

    private fun preloadLocalAi() {
        viewModelScope.launch {
            val recovery = modelManager.startupRecoveryMessage()
            _aiStatus.value = recovery ?: "Caricamento IA locale…"
            _aiReady.value = aiEngine.ensureLoaded()
            _aiStatus.value = if (_aiReady.value) {
                buildString {
                    append("${aiEngine.activeEngineLabel()} pronta")
                    if (recovery != null) append(" · ").append(recovery)
                }
            } else recovery ?: "Nessun motore locale attivo · apri Configurazione IA"
        }
    }

    fun reloadLocalAi() {
        viewModelScope.launch {
            _streamingReply.value = ""
            _firstTokenMs.value = null
            _aiReady.value = false
            _aiStatus.value = "Cambio modello · caricamento in RAM…"
            _aiReady.value = aiEngine.restart()
            _aiStatus.value = if (_aiReady.value) {
                "${aiEngine.activeEngineLabel()} pronta"
            } else "Caricamento motore locale fallito · controlla Diagnostica IA"
        }
    }

    fun unloadLocalAi() {
        viewModelScope.launch {
            _streamingReply.value = ""
            _firstTokenMs.value = null
            _aiStatus.value = "Scaricamento del modello dalla RAM…"
            val unloaded = aiEngine.unload()
            _aiReady.value = false
            _aiStatus.value = if (unloaded) {
                "Modello scaricato dalla RAM · il file resta sul dispositivo"
            } else {
                "Scaricamento RAM non completato · prova Riavvia IA"
            }
        }
    }

    fun newGame(playerName: String, playerAge: Int, playerGender: String, playerAppearanceId: String, playerStyle: String) {
        galleryUnlockQueue.clear()
        _pendingGalleryUnlock.value = null
        _incomingCallCharacterId.value = null
        _phoneSelectedCharacterId.value = null
        _phoneNotice.value = ""
        _state.value = GameState(
            playerName = playerName.trim().ifBlank { "Alex" },
            playerAge = playerAge.coerceIn(18, 80),
            playerGender = playerGender.takeIf { it in setOf("Maschio", "Femmina") } ?: "Maschio",
            playerAppearanceId = playerAppearanceId.takeIf {
                it in setOf("male_1", "male_2", "male_3", "male_4", "male_5", "female_1", "female_2", "female_3", "female_4", "female_5")
            } ?: if (playerGender == "Femmina") "female_1" else "male_1",
            playerStyle = playerStyle,
            simulationEpochMs = System.currentTimeMillis(),
            relationships = initialRelationships
        )
        _gameClock.value = clockSnapshot(_state.value)
        clearConversationUi()
    }

    fun loadGame(saved: GameState) {
        galleryUnlockQueue.clear()
        _pendingGalleryUnlock.value = null
        _incomingCallCharacterId.value = null
        _phoneSelectedCharacterId.value = null
        _phoneNotice.value = ""
        _state.value = saved.copy(
            relationships = initialRelationships + saved.relationships
        )
        _gameClock.value = clockSnapshot(_state.value)
        clearConversationUi()
    }

    fun visitLocation(id: String) {
        val current = _state.value
        val minuteOfDay = _gameClock.value.minuteOfDay
        if (!GameData.isLocationOpenAt(id, minuteOfDay)) {
            _aiStatus.value = "Questo luogo è chiuso · orario ${GameData.openingLabel(GameData.locations.first { it.id == id })}"
            return
        }
        _state.value = current.copy(
            locationId = id,
            guestCharacterId = if (id == "apartment") current.guestCharacterId else null
        )
    }

    fun canInviteHome(character: CharacterProfile, relationship: Relationship): Boolean =
        relationship.trust >= character.inviteTrust &&
            relationship.affection >= character.inviteAffection &&
            relationship.talks >= character.inviteTalks

    fun inviteRequirements(character: CharacterProfile): String =
        "Richiede ❤️${character.inviteAffection} · 🤝${character.inviteTrust} · ${character.inviteTalks} conversazioni"

    fun inviteHome(characterId: String): Boolean {
        val character = GameData.characters.firstOrNull { it.id == characterId } ?: return false
        val current = _state.value
        val relationship = current.relationships[characterId] ?: Relationship()
        if (!canInviteHome(character, relationship)) return false

        val visitNumber = (current.apartmentVisits[characterId] ?: 0) + 1
        val visitMemory = "Visita $visitNumber nell'appartamento di ${current.playerName}."
        _state.value = current.copy(
            locationId = "apartment",
            guestCharacterId = characterId,
            apartmentVisits = current.apartmentVisits + (characterId to visitNumber),
            relationships = current.relationships + (characterId to relationship.copy(
                dates = relationship.dates + 1,
                memories = (relationship.memories + visitMemory).distinct().takeLast(20)
            )),
            chatHistories = current.chatHistories + (characterId to (
                current.chatHistories[characterId].orEmpty() + DialogueMessage(
                    speaker = character.name,
                    text = "Va bene, andiamo nel tuo appartamento.",
                    emotion = "happy"
                )
            ))
        )
        _aiStatus.value = "${character.name} è nel tuo appartamento"
        return true
    }

    fun startConversation(characterId: String) {
        if (_isThinking.value) {
            _aiStatus.value = "Attendi la risposta in corso prima di cambiare chat"
            return
        }
        if (!_aiReady.value || !aiEngine.isReady()) {
            _aiReady.value = false
            _aiStatus.value = "Motore locale non pronto · caricalo dalla Configurazione IA"
            return
        }
        val character = GameData.characters.firstOrNull { it.id == characterId }
        _activeCharacter.value = character
        _lastDelta.value = null
        if (character != null) {
            var relationship = _state.value.relationships[character.id] ?: Relationship()
            val currentSlot = conversationSlot(_state.value)
            if (relationship.activeConversationSlot != currentSlot) {
                val unfinishedPrevious = relationship.activeConversationSlot >= 0 &&
                    relationship.activeConversationTurns > 0 &&
                    relationship.lastCompletedConversationSlot != relationship.activeConversationSlot
                val limit = calculateConversationTurnLimit(character, relationship)
                relationship = relationship.copy(
                    talks = relationship.talks + if (unfinishedPrevious) 1 else 0,
                    lastCompletedConversationSlot = if (unfinishedPrevious) {
                        relationship.activeConversationSlot
                    } else relationship.lastCompletedConversationSlot,
                    activeConversationSlot = currentSlot,
                    activeConversationTurns = 0,
                    activeConversationLimit = limit ?: 0
                )
                val carriedUnlocks = galleryUnlocksFor(character.id, relationship)
                val newUnlocks = carriedUnlocks - _state.value.unlockedGallery
                _state.value = _state.value.copy(
                    relationships = _state.value.relationships + (character.id to relationship),
                    unlockedGallery = _state.value.unlockedGallery + carriedUnlocks
                )
                enqueueGalleryUnlocks(newUnlocks)
            }
            _sessionTurns.value = relationship.activeConversationTurns
            _conversationLimitReached.value = relationship.lastCompletedConversationSlot == currentSlot
            if (_conversationLimitReached.value) {
                _aiStatus.value = "Avete già parlato abbastanza: riprova nella prossima fascia oraria"
                return
            }
            _aiStatus.value = if (relationship.conversationSummary.isNotBlank()) {
                "${aiEngine.activeEngineLabel()} pronto · ricordi disponibili"
            } else "${aiEngine.activeEngineLabel()} pronto · contesto su richiesta"
            val stateSnapshot = _state.value
            val relationshipSnapshot = relationship
            viewModelScope.launch {
                val prepared = aiEngine.prepareConversation(
                    character,
                    stateSnapshot,
                    relationshipSnapshot
                )
                if (_activeCharacter.value?.id == character.id && !_isThinking.value) {
                    _aiStatus.value = if (prepared) {
                        "${aiEngine.activeEngineLabel()} pronto · cache del personaggio preparata"
                    } else {
                        "${aiEngine.activeEngineLabel()} · preparazione cache non riuscita"
                    }
                }
            }
        } else if (_aiStatus.value != "IA locale pronta") preloadLocalAi()
    }

    fun closeConversation() {
        finishConversation(null)
    }

    private fun closeConversationForSchedule(reason: String) {
        finishConversation(reason)
    }

    private fun finishConversation(farewellReason: String?) {
        val character = _activeCharacter.value
        if (character != null) {
            var current = _state.value
            val savedRelationship = current.relationships[character.id]
            if (savedRelationship != null) {
                var relationship = savedRelationship
                if (farewellReason != null && relationship.activeConversationTurns > 0 &&
                    relationship.lastCompletedConversationSlot != relationship.activeConversationSlot
                ) {
                    val farewell = departureLine(character, relationship, farewellReason)
                    relationship = relationship.copy(
                        recentFarewellIds = (relationship.recentFarewellIds + farewell.id).takeLast(3)
                    )
                    current = current.copy(
                        relationships = current.relationships + (character.id to relationship),
                        chatHistories = current.chatHistories + (character.id to (
                            current.chatHistories[character.id].orEmpty() + DialogueMessage(
                                speaker = character.name,
                                text = farewell.text,
                                emotion = if (relationship.lastInteractionTone == "negative") "neutral" else "thoughtful",
                                channel = "in_person"
                            )
                        ))
                    )
                    _state.value = current
                }
                if (farewellReason != null && relationship.activeConversationTurns > 0 &&
                    relationship.lastCompletedConversationSlot != relationship.activeConversationSlot
                ) {
                    val completed = relationship.copy(
                        talks = relationship.talks + 1,
                        lastCompletedConversationSlot = relationship.activeConversationSlot
                    )
                    val unlocks = galleryUnlocksFor(character.id, completed)
                    val newUnlocks = unlocks - current.unlockedGallery
                    _state.value = current.copy(
                        relationships = current.relationships + (character.id to completed),
                        unlockedGallery = current.unlockedGallery + unlocks
                    )
                    enqueueGalleryUnlocks(newUnlocks)
                }
            }
        }
        clearConversationUi()
    }

    private fun clearConversationUi() {
        _activeCharacter.value = null
        _lastDelta.value = null
        _streamingReply.value = ""
        _firstTokenMs.value = null
        _sessionTurns.value = 0
        _conversationLimitReached.value = false
    }

    fun selectPhoneCharacter(characterId: String) {
        _phoneSelectedCharacterId.value = characterId
        _phoneNotice.value = ""
    }

    fun canPhoneCall(character: CharacterProfile, relationship: Relationship): Boolean =
        relationship.stage in setOf("Amicizia", "Flirt", "Attrazione reciproca", "Appuntamenti", "Relazione") &&
            relationship.lastPhoneCallSlot != conversationSlot(_state.value)

    fun placePhoneCall(characterId: String) {
        val character = GameData.characters.firstOrNull { it.id == characterId } ?: return
        val current = _state.value
        val relationship = current.relationships[characterId] ?: Relationship()
        if (!canPhoneCall(character, relationship)) {
            _phoneNotice.value = if (relationship.stage in setOf("Sconosciuti", "Conoscenza")) {
                "Le chiamate si sbloccano dal livello Amicizia"
            } else "Avete già effettuato una chiamata in questa fascia oraria"
            return
        }
        val slot = conversationSlot(current)
        val interest = relationship.affection + relationship.attraction + relationship.trust
        val answer = when {
            interest >= 150 -> "Che bello sentirti. Avevo proprio voglia di parlare con te."
            interest >= 85 -> "Ciao! Sì, ho qualche minuto. Dimmi pure."
            else -> "Ciao, posso parlare solo un momento. È successo qualcosa?"
        }
        _state.value = current.copy(
            relationships = current.relationships + (characterId to relationship.copy(lastPhoneCallSlot = slot)),
            chatHistories = current.chatHistories + (characterId to (
                current.chatHistories[characterId].orEmpty() +
                    DialogueMessage("Tu", "📞 Chiamata in uscita", channel = "phone_call") +
                    DialogueMessage(character.name, answer, "happy", "phone_call")
            ))
        )
        _phoneSelectedCharacterId.value = characterId
        _phoneNotice.value = "Chiamata con ${character.name} registrata nella chat"
    }

    fun acceptIncomingCall() {
        val id = _incomingCallCharacterId.value ?: return
        val character = GameData.characters.firstOrNull { it.id == id } ?: return
        val current = _state.value
        val relationship = current.relationships[id] ?: Relationship()
        _state.value = current.copy(
            relationships = current.relationships + (id to relationship.copy(lastPhoneCallSlot = conversationSlot(current))),
            chatHistories = current.chatHistories + (id to (
                current.chatHistories[id].orEmpty() + DialogueMessage(
                    character.name,
                    "📞 Ti ho chiamato perché mi andava di sentirti. Come stai?",
                    "happy",
                    "phone_call"
                )
            ))
        )
        _phoneSelectedCharacterId.value = id
        _phoneNotice.value = "Hai risposto alla chiamata di ${character.name}"
        _incomingCallCharacterId.value = null
    }

    fun declineIncomingCall() {
        _incomingCallCharacterId.value = null
    }

    private fun maybeScheduleIncomingCall(state: GameState) {
        if (_incomingCallCharacterId.value != null) return
        val slot = conversationSlot(state)
        val candidates = GameData.characters.mapNotNull { character ->
            val relationship = state.relationships[character.id] ?: return@mapNotNull null
            if (relationship.stage !in setOf("Amicizia", "Flirt", "Attrazione reciproca", "Appuntamenti", "Relazione")) return@mapNotNull null
            if (relationship.lastPhoneCallSlot == slot) return@mapNotNull null
            val interest = relationship.affection + relationship.attraction + relationship.trust
            val chance = (5 + interest / 6 + character.extroversion * 2).coerceIn(8, 45)
            if (Random.nextInt(100) < chance) character.id else null
        }
        _incomingCallCharacterId.value = candidates.randomOrNull()
    }

    fun sendMessage(text: String) {
        val character = _activeCharacter.value ?: return
        if (text.isBlank() || _isThinking.value || _conversationLimitReached.value) return
        if (!_aiReady.value || !aiEngine.isReady()) {
            _aiReady.value = false
            _aiStatus.value = "Invio bloccato · nessun motore locale pronto"
            return
        }

        val before = _state.value
        val baseRelationship = before.relationships[character.id] ?: Relationship()
        val introducedName = introducedPlayerName(text, before.playerName)
        val currentRelationship = if (baseRelationship.knownPlayerName == null && introducedName != null) {
            baseRelationship.copy(knownPlayerName = introducedName)
        } else baseRelationship
        val currentHistory = before.chatHistories[character.id].orEmpty()

        _isThinking.value = true
        _streamingReply.value = ""
        _firstTokenMs.value = null
        val requestStartedAt = android.os.SystemClock.elapsedRealtime()
        _aiStatus.value = "${aiEngine.activeEngineLabel()} · analisi e preparazione…"
        _lastDelta.value = null

        viewModelScope.launch {
            try {
                var result = aiEngine.replyAndEvaluate(
                    character = character,
                    state = before,
                    relationship = currentRelationship,
                    userText = text,
                    onPartial = { fragment ->
                        if (_activeCharacter.value?.id == character.id) {
                            val combined = sanitizeStreamingText(
                                _streamingReply.value + fragment,
                                character.name
                            )
                            _streamingReply.value = combined
                            if (_firstTokenMs.value == null && combined.isNotBlank()) {
                                _firstTokenMs.value =
                                    android.os.SystemClock.elapsedRealtime() - requestStartedAt
                                _aiStatus.value =
                                    "${aiEngine.activeEngineLabel()} · risposta in streaming"
                            }
                        }
                    }
                )

                if (result.engine == "Nessuno") {
                    val totalRequestMs = android.os.SystemClock.elapsedRealtime() - requestStartedAt
                    _aiReady.value = aiEngine.isReady()
                    _aiStatus.value = if (_aiReady.value) {
                        "Richiesta scaduta · cache conservata · puoi riprovare"
                    } else {
                        "Motore non pronto · controlla Diagnostica IA"
                    }
                    recordAiDiagnostic("""
ULTIMA RISPOSTA
Personaggio: ${character.name}
Domanda: ${text.trim()}
Risposta: nessuna — timeout o errore del motore
Percorso: ${result.diagnosticPath.ifBlank { "timeout o errore locale" }}
Tema riconosciuto: ${result.diagnosticTopic}
Semantica: ${result.diagnosticSemantics.ifBlank { "nessun modulo" }}
Cache: ${aiEngine.preparationDiagnostics()}
Primo testo percepito: ${_firstTokenMs.value?.let { "$it ms" } ?: "nessun testo valido"}
Tempo totale percepito: $totalRequestMs ms
${aiEngine.inferenceResourceDiagnostics()}
                    """.trimIndent())
                    return@launch
                }
                if (result.reply.isBlank()) {
                    _aiStatus.value = "Risposta vuota · cache conservata · puoi riprovare"
                    return@launch
                }

                val turnsAfterReply = currentRelationship.activeConversationTurns + 1
                val limit = currentRelationship.activeConversationLimit.takeIf { it > 0 }
                val reachedLimit = limit != null && turnsAfterReply >= limit
                var updatedRel = updateRelationship(currentRelationship, result, character, _state.value.day, text)
                var closingMessage: DialogueMessage? = null
                if (reachedLimit) {
                    val farewell = departureLine(character, updatedRel, "session_limit")
                    updatedRel = updatedRel.copy(
                        talks = updatedRel.talks + 1,
                        lastCompletedConversationSlot = conversationSlot(_state.value),
                        activeConversationTurns = turnsAfterReply,
                        recentFarewellIds = (updatedRel.recentFarewellIds + farewell.id).takeLast(3)
                    )
                    closingMessage = DialogueMessage(
                        speaker = character.name,
                        text = farewell.text,
                        emotion = if (updatedRel.lastInteractionTone == "negative") "neutral" else "thoughtful",
                        channel = "in_person"
                    )
                } else {
                    updatedRel = updatedRel.copy(activeConversationTurns = turnsAfterReply)
                }
                val galleryUnlocks = galleryUnlocksFor(character.id, updatedRel)
                val newUnlocks = galleryUnlocks - _state.value.unlockedGallery

                _state.value = _state.value.copy(
                    relationships = _state.value.relationships + (character.id to updatedRel),
                    unlockedGallery = _state.value.unlockedGallery + galleryUnlocks,
                    chatHistories = _state.value.chatHistories + (
                        character.id to (
                            currentHistory + DialogueMessage("Tu", text) + DialogueMessage(
                                speaker = character.name,
                                text = result.reply,
                                emotion = result.emotion
                            ) + listOfNotNull(closingMessage)
                        )
                    )
                )
                _sessionTurns.value = turnsAfterReply
                _conversationLimitReached.value = reachedLimit
                _lastDelta.value = result.delta
                enqueueGalleryUnlocks(newUnlocks)
                val totalRequestMs = android.os.SystemClock.elapsedRealtime() - requestStartedAt
                val firstVisibleText = _firstTokenMs.value?.let { "$it ms" }
                    ?: if (result.diagnosticPath == "deterministico offline") {
                        "non applicabile: risposta diretta"
                    } else "nessun testo in streaming"
                fun signed(value: Int): String = if (value > 0) "+$value" else value.toString()
                recordAiDiagnostic("""
ULTIMA RISPOSTA
Personaggio: ${character.name}
Domanda: ${text.trim()}
Risposta: ${result.reply}
Percorso: ${result.diagnosticPath.ifBlank { result.engine }}
Tema riconosciuto: ${result.diagnosticTopic}
Semantica: ${result.diagnosticSemantics.ifBlank { "nessun modulo" }}
Motore indicato: ${result.engine}
Cache: ${if (result.diagnosticPath == "deterministico offline") "saltata" else aiEngine.preparationDiagnostics()}
Primo testo percepito: $firstVisibleText
Tempo totale percepito: $totalRequestMs ms
Dimensioni: input ${text.length} caratteri · output ${result.reply.length} caratteri
Correzione deterministica dopo IA locale: ${if (result.diagnosticFallback) "SÌ" else "NO"}
Motivo correzione: ${result.diagnosticCorrectionReason.ifBlank { "nessuno" }}
Punteggio turno: ❤️${signed(result.delta.affection)} · 🔥${signed(result.delta.attraction)} · 🤝${signed(result.delta.trust)}
${aiEngine.inferenceResourceDiagnostics()}
                """.trimIndent())
                val firstText = _firstTokenMs.value?.let { " · primo testo ${it} ms" }.orEmpty()
                _aiStatus.value = if (reachedLimit) {
                    "Conversazione conclusa · riprendi nella prossima fascia"
                } else if (result.engine == "Nessuno") {
                    "Nessun motore IA ha risposto"
                } else "Risposta ricevuta da: ${result.engine}$firstText"
            } catch (t: Throwable) {
                _aiReady.value = aiEngine.isReady()
                _aiStatus.value = "Errore IA · messaggio non salvato · puoi riprovare"
                recordAiDiagnostic("""
ULTIMA RISPOSTA
Personaggio: ${character.name}
Domanda: ${text.trim()}
Risposta: nessuna — errore applicazione
Percorso: errore applicazione
Tempo trascorso: ${android.os.SystemClock.elapsedRealtime() - requestStartedAt} ms
Errore: ${t.javaClass.simpleName} · ${t.message ?: "nessun dettaglio"}
                """.trimIndent())
            } finally {
                _streamingReply.value = ""
                _isThinking.value = false
            }
        }
    }

    private fun recordAiDiagnostic(entry: String) {
        aiDiagnosticEntries.addFirst(entry)
        while (aiDiagnosticEntries.size > 6) aiDiagnosticEntries.removeLast()
        _aiDiagnosticsSummary.value = aiDiagnosticEntries.mapIndexed { index, value ->
            "TEST ${index + 1}${if (index == 0) " (più recente)" else ""}\n$value"
        }.joinToString("\n\n----------------\n\n")
    }

    fun clearAiDiagnostics() {
        aiDiagnosticEntries.clear()
        _aiDiagnosticsSummary.value =
            "Registro test app pulito. Esegui una conversazione, poi premi AGGIORNA."
    }

    private fun sanitizeStreamingText(raw: String, characterName: String): String {
        val markers = listOf(
            "<|im_end|>",
            "<|eot_id|>",
            "<|start_header_id|>",
            "\n### Giocatore",
            "\nUser:",
            "\nUtente:",
            "\nGIOCATORE:",
            "CONTROLLO{",
            "INPUT_UTENTE{",
            "Messaggio del giocatore:"
        )
        val cutoff = markers.map { raw.indexOf(it) }.filter { it >= 0 }.minOrNull()
        var clean = if (cutoff != null) raw.take(cutoff) else raw
        clean = clean.replace(Regex("<\\|[^>]+\\|>"), "")
        val speakerPrefix = Regex(
            "(?i)^\\s*(?:assistant|assistente|personaggio|${Regex.escape(characterName)})\\s*:\\s*"
        )
        return clean.replace(speakerPrefix, "").trimStart().take(1_200)
    }

    fun conversationTurnsRemaining(relationship: Relationship, turnsUsed: Int): Int? =
        relationship.activeConversationLimit.takeIf {
            relationship.activeConversationSlot == conversationSlot(_state.value) && it > 0
        }?.let { (it - turnsUsed).coerceAtLeast(0) }

    private fun calculateConversationTurnLimit(character: CharacterProfile, relationship: Relationship): Int? {
        // Durata stabile durante la sessione ma diversa in base a persona,
        // giornata, indole e rapporto: non viene mostrato un contatore artificiale.
        val seed = (character.id.hashCode() and Int.MAX_VALUE) +
            _state.value.day * 31 + _state.value.periodIndex * 7 + relationship.talks * 3 +
            character.extroversion * 5 - character.conquestDifficulty * 2
        val positiveSeed = seed and Int.MAX_VALUE
        val limit = when (relationship.stage) {
            "Sconosciuti" -> 8 + positiveSeed % 8       // 8–15
            "Conoscenza" -> 10 + positiveSeed % 11     // 10–20
            "Amicizia" -> 15 + positiveSeed % 11       // 15–25
            "Flirt" -> 20 + positiveSeed % 16          // 20–35
            else -> return null
        }
        return limit.coerceAtLeast(6)
    }

    private fun conversationSlot(state: GameState): Int = (state.day - 1) * 4 + state.periodIndex

    private data class FarewellLine(val id: String, val text: String)

    private fun departureLine(
        character: CharacterProfile,
        relationship: Relationship,
        reason: String
    ): FarewellLine {
        val warm = relationship.stage in setOf(
            "Amicizia", "Flirt", "Attrazione reciproca", "Appuntamenti", "Relazione"
        ) && relationship.lastInteractionTone != "negative"
        val jobLines = when (character.job) {
            "Architetta d'interni" -> listOf(
                FarewellLine("design_1", "Devo tornare a un progetto. La prossima volta riprendiamo da qui."),
                FarewellLine("design_2", "Mi aspetta una revisione importante. Ci vediamo presto."),
                FarewellLine("design_3", "È ora che torni ai miei disegni. Continuiamo un'altra volta.")
            )
            "Personal trainer" -> listOf(
                FarewellLine("trainer_1", "Tra poco inizia un allenamento. Devo andare, a presto."),
                FarewellLine("trainer_2", "Mi aspetta una sessione in palestra. Riprendiamo più tardi."),
                FarewellLine("trainer_3", "Devo rimettermi in movimento. Ci sentiamo appena finisco.")
            )
            "Avvocata" -> listOf(
                FarewellLine("law_1", "Devo tornare ai miei impegni. Riprendiamo il discorso più tardi."),
                FarewellLine("law_2", "Mi aspetta una pratica urgente. Ci vediamo un'altra volta."),
                FarewellLine("law_3", "È ora che torni al lavoro. Continueremo questa conversazione.")
            )
            "Chef" -> listOf(
                FarewellLine("chef_1", "Devo rientrare in cucina. Riprendiamo quando avrò un momento libero."),
                FarewellLine("chef_2", "Il servizio mi chiama. Ci sentiamo appena riesco a respirare."),
                FarewellLine("chef_3", "Devo controllare la cucina prima che inizi il caos. A più tardi.")
            )
            "Cantautrice" -> listOf(
                FarewellLine("music_1", "Devo andare a provare un pezzo. Ci sentiamo più tardi, va bene?"),
                FarewellLine("music_2", "Mi aspetta una prova. La prossima volta ripartiamo da qui."),
                FarewellLine("music_3", "Devo scappare in studio, ma non ho dimenticato il nostro discorso.")
            )
            "Illustratrice" -> listOf(
                FarewellLine("art_1", "Mi è venuta un'idea che devo mettere su carta. Riparliamo presto."),
                FarewellLine("art_2", "Devo consegnare una tavola. Continuiamo alla prossima pausa."),
                FarewellLine("art_3", "La mia scrivania mi sta aspettando. A più tardi.")
            )
            "Fotografo urbano" -> listOf(
                FarewellLine("photo_1", "Devo preparare un servizio fotografico. Riprendiamo più tardi."),
                FarewellLine("photo_2", "La luce giusta non aspetta. Devo andare, ci vediamo presto."),
                FarewellLine("photo_3", "Ho uno scatto da inseguire. Continuiamo quando torno.")
            )
            "Barman e mixologist" -> listOf(
                FarewellLine("bar_1", "Devo preparare il bancone per il turno. Passa a trovarmi più tardi."),
                FarewellLine("bar_2", "Tra poco il locale si riempie. Devo andare, a presto."),
                FarewellLine("bar_3", "Il bancone mi reclama. La prossima chiacchierata te la devo.")
            )
            "Sviluppatore di videogiochi" -> listOf(
                FarewellLine("dev_1", "Devo tornare su una parte del progetto. Continuiamo alla prossima pausa."),
                FarewellLine("dev_2", "Mi aspetta un bug ostinato. Riprendiamo questo discorso più tardi."),
                FarewellLine("dev_3", "Devo rimettermi al codice. Ci sentiamo appena stacco.")
            )
            else -> listOf(
                FarewellLine("generic_1", "Adesso devo andare. Possiamo continuare un'altra volta."),
                FarewellLine("generic_2", "È arrivato il momento di salutarci. Ci vediamo più tardi."),
                FarewellLine("generic_3", "Devo rimettermi in movimento. Riprendiamo presto.")
            )
        }
        val contextLines = when {
            reason == "location_closed" -> listOf(
                FarewellLine("close_1", "Stanno chiudendo. È meglio che vada, ci vediamo presto."),
                FarewellLine("close_2", "Si è fatto tardi e il posto sta chiudendo. Continuiamo un'altra volta."),
                FarewellLine("close_3", "È ora di andare prima che ci mandino fuori. A presto.")
            )
            relationship.lastInteractionTone == "negative" -> listOf(
                FarewellLine("cold_1", "Ora preferisco andare. Ne riparleremo con più calma."),
                FarewellLine("cold_2", "Per stasera basta così. Ho bisogno di prendere un po' d'aria."),
                FarewellLine("cold_3", "Vado. Se ci rivedremo, spero che il tono sarà diverso.")
            )
            warm -> listOf(
                FarewellLine("warm_1", "Devo andare, ma mi ha fatto davvero piacere stare con te. A presto."),
                FarewellLine("warm_2", "Mi spiace interrompere proprio ora. Promettimi che riprendiamo presto."),
                FarewellLine("warm_3", "È ora di andare. Però questa conversazione me la porto dietro.")
            )
            else -> emptyList()
        }
        val pool = (contextLines + jobLines).distinctBy { it.id }
        val available = pool.filterNot { it.id in relationship.recentFarewellIds }.ifEmpty { pool }
        val seed = (character.id.hashCode() + _state.value.day * 31 + _state.value.periodIndex * 11 +
            relationship.talks * 7 + relationship.activeConversationTurns).and(Int.MAX_VALUE)
        return available[seed % available.size]
    }

    private fun updateRelationship(
        current: Relationship,
        result: AiDialogueResult,
        character: CharacterProfile,
        currentDay: Int,
        userText: String
    ): Relationship {
        val newAffection = (current.affection + result.delta.affection).coerceIn(0, 100)
        val newAttraction = (current.attraction + result.delta.attraction).coerceIn(0, 100)
        val newTrust = (current.trust + result.delta.trust).coerceIn(0, 100)

        val newMemories = if (!result.memory.isNullOrBlank()) {
            (current.memories + result.memory).distinct().takeLast(20)
        } else current.memories
        val extractedFacts = PlayerFactExtractor.extract(userText)
        val newPlayerFacts = PlayerFactExtractor.merge(current.playerFacts, extractedFacts, limit = 24)
        val eventWithDay = result.relationshipEvent?.takeIf { it.isNotBlank() }
            ?.let { event -> "${event.substringBefore('|')}|Giorno $currentDay: ${event.substringAfter('|', event)}" }
        val newEmotionalMemories = if (eventWithDay != null) {
            (current.emotionalMemories + eventWithDay)
                .distinctBy { it.substringAfter('|', it).substringAfter(": ").lowercase() }
                .takeLast(12)
        } else current.emotionalMemories
        val interactionTone = when {
            result.delta.affection < 0 || result.delta.attraction < 0 || result.delta.trust < 0 -> "negative"
            result.delta.affection > 0 || result.delta.attraction > 0 || result.delta.trust > 0 -> "positive"
            else -> "neutral"
        }

        val firstMetDay = current.firstMetDay.takeIf { it > 0 } ?: currentDay
        val scoreStage = relationshipStage(newAffection, newAttraction, newTrust)
        val stage = gatedRelationshipStage(
            currentStage = current.stage,
            scoreStage = scoreStage,
            character = character,
            firstMetDay = firstMetDay,
            lastStageChangeDay = current.lastStageChangeDay,
            currentDay = currentDay
        )
        val unlockedSecrets = buildList {
            if (character.innerConflict.isNotBlank() && newTrust >= 30 && newAffection >= 12 && current.talks >= 2) {
                add("${character.id}_conflict")
            }
            character.storyBeats.indices.forEach { index ->
                val requiredTrust = 45 + index * 15
                val requiredTalks = 4 + index * 2
                if (newTrust >= requiredTrust && current.talks >= requiredTalks) {
                    add("${character.id}_story_$index")
                }
            }
        }

        return current.copy(
            affection = newAffection,
            attraction = newAttraction,
            trust = newTrust,
            stage = stage,
            memories = newMemories,
            conversationSummary = compactConversationSummary(
                current.conversationSummary,
                userText,
                result.reply,
                result.memoryTopic
            ),
            playerFacts = newPlayerFacts,
            emotionalMemories = newEmotionalMemories,
            lastConversationTopic = result.memoryTopic,
            lastConversationDay = currentDay,
            lastInteractionTone = interactionTone,
            knownSecrets = (current.knownSecrets + unlockedSecrets).distinct(),
            firstMetDay = firstMetDay,
            lastStageChangeDay = if (stage != current.stage) currentDay else current.lastStageChangeDay
        )
    }

    private fun introducedPlayerName(text: String, configuredName: String): String? {
        val escaped = Regex.escape(configuredName.trim())
        if (escaped.isBlank()) return null
        val patterns = listOf(
            Regex("\\bmi\\s+chiamo\\s+$escaped\\b", RegexOption.IGNORE_CASE),
            Regex("\\bil\\s+mio\\s+nome\\s+[èe]\\s+$escaped\\b", RegexOption.IGNORE_CASE),
            Regex("\\bpiacere[,! ]+$escaped\\b", RegexOption.IGNORE_CASE)
        )
        return configuredName.trim().takeIf { name -> patterns.any { it.containsMatchIn(text) } && name.isNotBlank() }
    }

    private fun compactConversationSummary(
        previous: String,
        userText: String,
        reply: String,
        topic: String
    ): String {
        val playerPart = compactText(userText, 75)
        val characterPart = compactText(reply, 90)
        val exchange = "Tema ${topic.ifBlank { "generale" }}: il giocatore ha detto «$playerPart»; il personaggio ha risposto «$characterPart»."
        return (previous.split(" || ").filter { it.isNotBlank() }.takeLast(1) + exchange)
            .takeLast(2)
            .joinToString(" || ")
    }

    private fun compactText(value: String, maxLength: Int): String {
        val clean = value.replace(Regex("\\s+"), " ").trim()
        if (clean.length <= maxLength) return clean
        val clipped = clean.take(maxLength)
        val sentenceEnd = clipped.indexOfLast { it == '.' || it == '!' || it == '?' }
        return if (sentenceEnd >= maxLength / 2) clipped.take(sentenceEnd + 1)
        else clipped.substringBeforeLast(' ', clipped).trimEnd(',', ';', ':')
    }

    private val relationshipStages = listOf(
        "Sconosciuti", "Conoscenza", "Amicizia", "Flirt",
        "Attrazione reciproca", "Appuntamenti", "Relazione"
    )

    private fun gatedRelationshipStage(
        currentStage: String,
        scoreStage: String,
        character: CharacterProfile,
        firstMetDay: Int,
        lastStageChangeDay: Int,
        currentDay: Int
    ): String {
        val currentIndex = relationshipStages.indexOf(currentStage).coerceAtLeast(0)
        val targetIndex = relationshipStages.indexOf(scoreStage).coerceAtLeast(0)
        if (targetIndex < currentIndex) return relationshipStages[(currentIndex - 1).coerceAtLeast(targetIndex)]
        if (targetIndex == currentIndex) return relationshipStages[currentIndex]
        if (lastStageChangeDay == currentDay) return relationshipStages[currentIndex]

        val daysKnown = (currentDay - firstMetDay).coerceAtLeast(0)
        val difficulty = character.conquestDifficulty.coerceIn(1, 5)
        val minimumDays = when (currentIndex + 1) {
            1 -> 0
            2 -> maxOf(1, difficulty - 2)
            3 -> 2 + difficulty / 2
            4 -> 3 + difficulty
            5 -> 5 + difficulty
            else -> 8 + difficulty * 2
        }
        return if (daysKnown >= minimumDays) relationshipStages[currentIndex + 1]
        else relationshipStages[currentIndex]
    }

    private fun relationshipStage(affection: Int, attraction: Int, trust: Int): String = when {
        affection >= 80 && attraction >= 65 && trust >= 70 -> "Relazione"
        affection >= 65 && attraction >= 55 && trust >= 50 -> "Appuntamenti"
        attraction >= 45 && affection >= 35 -> "Attrazione reciproca"
        attraction >= 30 && affection >= 25 -> "Flirt"
        affection >= 20 || trust >= 25 -> "Amicizia"
        affection >= 8 || trust >= 8 -> "Conoscenza"
        else -> "Sconosciuti"
    }

    private fun galleryUnlocksFor(characterId: String, relationship: Relationship): Set<String> = buildSet {
        val stageIndex = relationshipStages.indexOf(relationship.stage).coerceAtLeast(0)
        if (relationship.talks >= 1) add("${characterId}_profile")
        if (stageIndex >= 2) add("${characterId}_casual")
        if (stageIndex >= 3) add("${characterId}_flirt")
        if (stageIndex >= 4) add("${characterId}_date")
        if (stageIndex >= 5) add("${characterId}_intimate")
    }

    fun consumeGalleryUnlock() {
        _pendingGalleryUnlock.value = if (galleryUnlockQueue.isEmpty()) null else galleryUnlockQueue.removeFirst()
    }

    private fun enqueueGalleryUnlocks(keys: Set<String>) {
        val order = listOf("profile", "casual", "flirt", "date", "intimate")
        val ordered = keys.sortedBy { key ->
            order.indexOf(key.substringAfterLast('_')).let { if (it < 0) Int.MAX_VALUE else it }
        }
        ordered.forEach { key ->
            if (key != _pendingGalleryUnlock.value && key !in galleryUnlockQueue) galleryUnlockQueue.addLast(key)
        }
        if (_pendingGalleryUnlock.value == null && galleryUnlockQueue.isNotEmpty()) {
            _pendingGalleryUnlock.value = galleryUnlockQueue.removeFirst()
        }
    }

    fun advanceTime() {
        val s = _state.value
        val now = System.currentTimeMillis()
        val elapsed = (now - s.simulationEpochMs).coerceAtLeast(0L)
        val withinPhase = elapsed % PHASE_REAL_MS
        val realMsToAdvance = (PHASE_REAL_MS - withinPhase).coerceAtLeast(1L)
        _state.value = s.copy(
            simulationEpochMs = s.simulationEpochMs - realMsToAdvance,
            guestCharacterId = null
        )
        _gameClock.value = clockSnapshot(_state.value)
    }

    fun addMoney(amount: Int) {
        val s = _state.value
        _state.value = s.copy(money = (s.money + amount).coerceAtLeast(0))
    }

    fun spendEnergy(amount: Int) {
        val s = _state.value
        _state.value = s.copy(energy = (s.energy - amount).coerceAtLeast(0))
    }
}
