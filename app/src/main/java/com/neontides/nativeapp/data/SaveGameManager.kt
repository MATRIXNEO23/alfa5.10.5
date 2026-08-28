package com.neontides.nativeapp.data

import android.content.Context
import com.neontides.nativeapp.model.*
import org.json.JSONArray
import org.json.JSONObject

data class SaveSlotInfo(val slot: Int, val timestamp: Long?, val day: Int?, val locationId: String?)

class SaveGameManager(context: Context) {
    private val prefs = context.getSharedPreferences("neon_tides_saves", Context.MODE_PRIVATE)

    fun save(slot: Int, state: GameState) {
        require(slot in 0..3)
        prefs.edit().putString("slot_$slot", encode(state).put("savedAt", System.currentTimeMillis()).toString()).apply()
    }

    fun load(slot: Int): GameState? = prefs.getString("slot_$slot", null)?.let { raw ->
        runCatching { decode(JSONObject(raw)) }.getOrNull()
    }

    fun info(slot: Int): SaveSlotInfo {
        val json = prefs.getString("slot_$slot", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        return SaveSlotInfo(slot, json?.optLong("savedAt")?.takeIf { it > 0 }, json?.optInt("day"), json?.optString("locationId"))
    }

    fun exportAll(): String = JSONObject().apply {
        put("format", "neon-tides-save")
        put("version", 5)
        put("slots", JSONObject().apply {
            (0..3).forEach { slot ->
                prefs.getString("slot_$slot", null)?.let { put(slot.toString(), JSONObject(it)) }
            }
        })
    }.toString(2)

    fun importAll(raw: String): Int {
        val root = JSONObject(raw)
        require(root.optString("format") == "neon-tides-save") { "File di salvataggio non valido" }
        val slots = root.optJSONObject("slots") ?: error("Nessuno slot nel backup")
        val editor = prefs.edit()
        var imported = 0
        (0..3).forEach { slot ->
            val data = slots.optJSONObject(slot.toString()) ?: return@forEach
            decode(data) // convalida prima di scrivere
            editor.putString("slot_$slot", data.toString())
            imported++
        }
        editor.apply()
        return imported
    }

    private fun encode(s: GameState) = JSONObject().apply {
        put("schemaVersion", 5)
        put("day", s.day); put("periodIndex", s.periodIndex); put("simulationEpochMs", s.simulationEpochMs); put("locationId", s.locationId)
        put("money", s.money); put("energy", s.energy); put("playerName", s.playerName)
        put("playerAge", s.playerAge); put("playerGender", s.playerGender); put("playerAppearanceId", s.playerAppearanceId); put("playerStyle", s.playerStyle)
        put("guestCharacterId", s.guestCharacterId ?: JSONObject.NULL)
        put("apartmentVisits", JSONObject().apply {
            s.apartmentVisits.forEach { (id, visits) -> put(id, visits) }
        })
        put("unlockedGallery", JSONArray(s.unlockedGallery.toList()))
        put("stats", JSONObject().apply {
            put("charisma", s.stats.charisma); put("intelligence", s.stats.intelligence)
            put("fitness", s.stats.fitness); put("confidence", s.stats.confidence); put("reputation", s.stats.reputation)
        })
        put("relationships", JSONObject().apply { s.relationships.forEach { (id, r) ->
            put(id, JSONObject().apply {
                put("affection", r.affection); put("attraction", r.attraction); put("trust", r.trust)
                put("stage", r.stage); put("talks", r.talks); put("dates", r.dates)
                put("memories", JSONArray(r.memories))
                put("firstMetDay", r.firstMetDay); put("lastStageChangeDay", r.lastStageChangeDay)
                put("knownPlayerName", r.knownPlayerName ?: JSONObject.NULL)
                put("conversationSummary", r.conversationSummary)
                put("playerFacts", JSONArray(r.playerFacts))
                put("emotionalMemories", JSONArray(r.emotionalMemories))
                put("lastConversationTopic", r.lastConversationTopic)
                put("lastConversationDay", r.lastConversationDay)
                put("lastInteractionTone", r.lastInteractionTone)
                put("lastCompletedConversationSlot", r.lastCompletedConversationSlot)
                put("activeConversationSlot", r.activeConversationSlot)
                put("activeConversationTurns", r.activeConversationTurns)
                put("activeConversationLimit", r.activeConversationLimit)
                put("lastPhoneCallSlot", r.lastPhoneCallSlot)
                put("recentFarewellIds", JSONArray(r.recentFarewellIds))
                put("knownSecrets", JSONArray(r.knownSecrets))
            })
        } })
        put("chats", JSONObject().apply { s.chatHistories.forEach { (id, messages) ->
            put(id, JSONArray().apply { messages.forEach { m -> put(JSONObject().apply {
                put("speaker", m.speaker); put("text", m.text); put("emotion", m.emotion); put("channel", m.channel)
            }) } })
        } })
    }

    private fun decode(j: JSONObject): GameState {
        val schemaVersion = j.optInt("schemaVersion", 1)
        val statsJson = j.optJSONObject("stats") ?: JSONObject()
        val relationships = mutableMapOf<String, Relationship>()
        val relJson = j.optJSONObject("relationships") ?: JSONObject()
        relJson.keys().forEach { id ->
            val r = relJson.getJSONObject(id)
            val savedTalks = r.optInt("talks")
            relationships[id] = Relationship(
                affection = r.optInt("affection"),
                attraction = r.optInt("attraction"),
                trust = r.optInt("trust"),
                stage = r.optString("stage", "Sconosciuti"),
                talks = if (schemaVersion >= 2) savedTalks else (savedTalks + 7) / 8,
                dates = r.optInt("dates"),
                memories = r.optJSONArray("memories").strings(),
                firstMetDay = r.optInt("firstMetDay", 0),
                lastStageChangeDay = r.optInt("lastStageChangeDay", 0),
                knownPlayerName = r.optString("knownPlayerName").takeIf { it.isNotBlank() && it != "null" },
                conversationSummary = r.optString("conversationSummary", ""),
                playerFacts = r.optJSONArray("playerFacts").strings(),
                emotionalMemories = r.optJSONArray("emotionalMemories").strings(),
                lastConversationTopic = r.optString("lastConversationTopic", ""),
                lastConversationDay = r.optInt("lastConversationDay", 0),
                lastInteractionTone = r.optString("lastInteractionTone", "neutral")
                    .takeIf { it in setOf("positive", "neutral", "negative") } ?: "neutral",
                lastCompletedConversationSlot = r.optInt("lastCompletedConversationSlot", -1),
                activeConversationSlot = r.optInt("activeConversationSlot", -1),
                activeConversationTurns = r.optInt("activeConversationTurns", 0),
                activeConversationLimit = r.optInt("activeConversationLimit", 0),
                lastPhoneCallSlot = r.optInt("lastPhoneCallSlot", -1),
                recentFarewellIds = r.optJSONArray("recentFarewellIds").strings().takeLast(3),
                knownSecrets = r.optJSONArray("knownSecrets").strings().distinct()
            )
        }
        val chats = mutableMapOf<String, List<DialogueMessage>>()
        val chatsJson = j.optJSONObject("chats") ?: JSONObject()
        chatsJson.keys().forEach { id ->
            val array = chatsJson.getJSONArray(id)
            chats[id] = (0 until array.length()).map { index -> array.getJSONObject(index).let { m ->
                DialogueMessage(
                    m.optString("speaker"),
                    m.optString("text"),
                    m.optString("emotion", "neutral"),
                    m.optString("channel", "in_person").takeIf {
                        it in setOf("in_person", "phone_message", "phone_call", "system")
                    } ?: "in_person"
                )
            } }
        }
        val visits = mutableMapOf<String, Int>()
        val visitsJson = j.optJSONObject("apartmentVisits") ?: JSONObject()
        visitsJson.keys().forEach { id -> visits[id] = visitsJson.optInt(id) }
        val savedDay = j.optInt("day", 1).coerceAtLeast(1)
        val savedPeriod = j.optInt("periodIndex").coerceIn(0, 3)
        // Dalla 8.8 ogni fascia dura 25 minuti reali (100 minuti per giorno).
        // I salvataggi precedenti conservano giorno e fascia, senza saltare
        // avanti o indietro quando cambia la velocità dell'orologio.
        val migratedElapsed = (savedDay - 1L) * 6_000_000L + savedPeriod * 1_500_000L
        val simulationEpoch = if (schemaVersion >= 5) {
            j.optLong("simulationEpochMs", 0L).takeIf { it > 0L }
                ?: (System.currentTimeMillis() - migratedElapsed)
        } else {
            System.currentTimeMillis() - migratedElapsed
        }
        return GameState(
            day = savedDay, periodIndex = savedPeriod, simulationEpochMs = simulationEpoch, locationId = j.optString("locationId", "apartment"),
            money = j.optInt("money", 150), energy = j.optInt("energy", 100), playerName = j.optString("playerName", "Protagonista"),
            playerAge = j.optInt("playerAge", 25),
            playerGender = j.optString("playerGender", "Maschio").takeIf { it in setOf("Maschio", "Femmina") } ?: "Maschio",
            playerAppearanceId = j.optString("playerAppearanceId", "").takeIf { it.matches(Regex("^(male|female)_[1-5]$")) }
                ?: if (j.optString("playerGender", "Maschio") == "Femmina") "female_1" else "male_1",
            playerStyle = j.optString("playerStyle", "Naturale"),
            guestCharacterId = j.optString("guestCharacterId").takeIf { it.isNotBlank() && it != "null" },
            apartmentVisits = visits,
            unlockedGallery = j.optJSONArray("unlockedGallery").strings().toSet(),
            stats = PlayerStats(statsJson.optInt("charisma", 10), statsJson.optInt("intelligence", 10), statsJson.optInt("fitness", 10), statsJson.optInt("confidence", 10), statsJson.optInt("reputation")),
            relationships = relationships, chatHistories = chats
        )
    }

    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }
}
