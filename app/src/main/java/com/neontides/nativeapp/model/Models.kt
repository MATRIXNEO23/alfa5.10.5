package com.neontides.nativeapp.model

data class PlayerStats(
    val charisma: Int = 10,
    val intelligence: Int = 10,
    val fitness: Int = 10,
    val confidence: Int = 10,
    val reputation: Int = 0
)

data class Relationship(
    val affection: Int = 0,
    val attraction: Int = 0,
    val trust: Int = 0,
    val stage: String = "Sconosciuti",
    val talks: Int = 0,
    val dates: Int = 0,
    val memories: List<String> = emptyList(),
    val firstMetDay: Int = 0,
    val lastStageChangeDay: Int = 0,
    val knownPlayerName: String? = null,
    val conversationSummary: String = "",
    val playerFacts: List<String> = emptyList(),
    val emotionalMemories: List<String> = emptyList(),
    val lastConversationTopic: String = "",
    val lastConversationDay: Int = 0,
    val lastInteractionTone: String = "neutral",
    val lastCompletedConversationSlot: Int = -1,
    val activeConversationSlot: Int = -1,
    val activeConversationTurns: Int = 0,
    val activeConversationLimit: Int = 0,
    val lastPhoneCallSlot: Int = -1,
    val recentFarewellIds: List<String> = emptyList(),
    val knownSecrets: List<String> = emptyList()
)

data class RelationshipDelta(
    val affection: Int = 0,
    val attraction: Int = 0,
    val trust: Int = 0
)

data class AiDialogueResult(
    val reply: String,
    val emotion: String = "neutral",
    val delta: RelationshipDelta = RelationshipDelta(),
    val memory: String? = null,
    val memoryTopic: String = "general",
    val relationshipEvent: String? = null,
    val engine: String = "Nessuno",
    val diagnosticPath: String = "",
    val diagnosticTopic: String = "generale",
    val diagnosticSemantics: String = "",
    val diagnosticFallback: Boolean = false,
    val diagnosticCorrectionReason: String = "",
    /** Output completo del motore prima di pulizia o correzione deterministica. */
    val diagnosticRawReply: String = ""
)

data class CharacterProfile(
    val id: String,
    val name: String,
    val age: Int,
    val job: String,
    val personality: String,
    val likes: List<String>,
    val dislikes: List<String>,
    val gender: String = "Femmina",
    val conquestDifficulty: Int = 3,
    val extroversion: Int = 3,
    val sensuality: Int = 3,
    val romance: Int = 3,
    val jealousy: Int = 2,
    val background: String = "",
    val innerConflict: String = "",
    val storyBeats: List<String> = emptyList(),
    val workFacts: List<String> = emptyList(),
    val personalFacts: Map<String, List<String>> = emptyMap(),
    val inviteTrust: Int = 25,
    val inviteAffection: Int = 15,
    val inviteTalks: Int = 3
)

data class OpeningWindow(
    val opensAtMinute: Int,
    val closesAtMinute: Int
)

data class Location(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val openingWindows: List<OpeningWindow> = listOf(OpeningWindow(0, 0))
)

data class DialogueMessage(
    val speaker: String,
    val text: String,
    val emotion: String = "neutral",
    val channel: String = "in_person"
)

data class GameClockSnapshot(
    val day: Int = 1,
    val periodIndex: Int = 0,
    val timeText: String = "00:00",
    val phaseName: String = "Notte",
    val minuteOfDay: Int = 0
)

data class GameState(
    val day: Int = 1,
    val periodIndex: Int = 0,
    val simulationEpochMs: Long = System.currentTimeMillis(),
    val locationId: String = "apartment",
    val money: Int = 150,
    val energy: Int = 100,
    val playerName: String = "Protagonista",
    val playerAge: Int = 25,
    val playerGender: String = "Maschio",
    val playerAppearanceId: String = "male_1",
    val playerStyle: String = "Naturale",
    val guestCharacterId: String? = null,
    val apartmentVisits: Map<String, Int> = emptyMap(),
    val unlockedGallery: Set<String> = emptySet(),
    val stats: PlayerStats = PlayerStats(),
    val relationships: Map<String, Relationship> = emptyMap(),
    val chatHistories: Map<String, List<DialogueMessage>> = emptyMap()
)
