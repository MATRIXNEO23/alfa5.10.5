package com.neontides.nativeapp.ai

import com.neontides.nativeapp.model.CharacterProfile
import com.neontides.nativeapp.model.DialogueMessage
import com.neontides.nativeapp.model.Relationship
import java.text.Normalizer

/**
 * Estrae solo dichiarazioni esplicite del giocatore. Il testo salvato mantiene
 * il formato storico `argomento|fatto`, quindi gli slot delle versioni
 * precedenti restano compatibili.
 */
object PlayerFactExtractor {
    fun extract(userText: String): List<String> {
        val clean = userText.replace(Regex("\\s+"), " ").trim()
        if (clean.isBlank()) return emptyList()
        val facts = mutableListOf<String>()

        fun captureAll(regex: Regex, topic: String, prefix: String) {
            regex.findAll(clean).forEach { match ->
                match.groupValues.getOrNull(1)
                    ?.let(::cleanValue)
                    ?.takeIf { it.length >= 2 }
                    ?.let { facts += "$topic|$prefix $it." }
            }
        }

        captureAll(
            Regex("(?<!non )\\b(?:mi piace|mi piacciono|adoro|preferisco|mi interessa)\\s+([^,.!?;]{2,90})", RegexOption.IGNORE_CASE),
            "hobby",
            "Gli piace"
        )
        captureAll(
            Regex("\\b(?:non mi piace|non mi piacciono|odio|detesto|non sopporto)\\s+([^,.!?;]{2,90})", RegexOption.IGNORE_CASE),
            "hobby",
            "Non gradisce"
        )
        captureAll(
            Regex("\\b(?:lavoro come|faccio il|faccio la|di lavoro faccio|il mio mestiere(?: al momento)? [èe]|la mia professione [èe])\\s+([^,.!?;]{2,80})", RegexOption.IGNORE_CASE),
            "lavoro",
            "Lavora come"
        )
        captureAll(
            Regex("\\b(?:vivo a|abito a|sono di)\\s+([^,.!?;]{2,70})", RegexOption.IGNORE_CASE),
            "luoghi",
            "Vive a"
        )
        captureAll(
            Regex("\\bho\\s+(\\d{1,3}\\s+anni)\\b", RegexOption.IGNORE_CASE),
            "identita",
            "Ha"
        )
        captureAll(
            Regex("\\bho (un fratello|una sorella|un figlio|una figlia|dei figli|delle figlie)\\b", RegexOption.IGNORE_CASE),
            "famiglia",
            "Ha"
        )
        captureAll(
            Regex("\\b(?:ho paura di|temo)\\s+([^,.!?;]{2,80})", RegexOption.IGNORE_CASE),
            "paure",
            "Ha paura di"
        )
        captureAll(
            Regex("\\b(?:il mio sogno [èe]|sogno di)\\s+([^,.!?;]{2,80})", RegexOption.IGNORE_CASE),
            "sogni",
            "Sogna di"
        )

        return facts.distinctBy(::canonical)
    }

    fun merge(existing: List<String>, incoming: List<String>, limit: Int = 24): List<String> {
        val result = existing.toMutableList()
        incoming.forEach { fact ->
            singularKind(fact)?.let { kind -> result.removeAll { singularKind(it) == kind } }
            if (result.none { equivalent(it, fact) }) result += fact
        }
        return result.takeLast(limit)
    }

    fun isNovel(fact: String, existing: List<String>): Boolean = existing.none { equivalent(it, fact) }

    fun topic(encoded: String): String = encoded.substringBefore('|').trim().ifBlank { "generale" }

    fun text(encoded: String): String = encoded.substringAfter('|', encoded).trim()

    private fun cleanValue(value: String): String {
        return value
            .replace(Regex("\\s+(?:e|ma)\\s+tu\\b.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+tu\\s+(?:che|cosa|come|dove|quanti|quanto|qual).*$", RegexOption.IGNORE_CASE), "")
            .trim(' ', '.', ',', ';', ':', '!', '?')
            .take(85)
            .trimEnd()
    }

    private fun singularKind(encoded: String): String? {
        val topic = topic(encoded)
        val text = text(encoded).lowercase()
        return when {
            topic == "identita" && Regex("^ha \\d{1,3} anni").containsMatchIn(text) -> "eta"
            topic == "lavoro" && text.startsWith("lavora come ") -> "lavoro"
            topic == "luoghi" && text.startsWith("vive a ") -> "residenza"
            else -> null
        }
    }

    private fun equivalent(first: String, second: String): Boolean {
        if (topic(first) != topic(second)) return false
        if (canonical(first) == canonical(second)) return true
        val firstKind = singularKind(first)
        val secondKind = singularKind(second)
        if (firstKind != null && firstKind == secondKind) return true
        val ignored = setOf(
            "al", "alla", "agli", "alle", "che", "come", "dei", "del", "della", "gli", "ha", "il", "la",
            "lavora", "non", "piace", "gradisce", "giocatore", "vive", "anni", "una", "uno", "un"
        )
        fun words(value: String) = normalize(text(value)).split(' ')
            .filter { it.length >= 3 && it !in ignored }
            .toSet()
        val a = words(first)
        val b = words(second)
        if (a.isEmpty() || b.isEmpty()) return false
        return a.intersect(b).size >= minOf(a.size, b.size).coerceAtMost(2)
    }

    private fun canonical(value: String): String = normalize(value).replace(" ", "")

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

enum class SemanticOwner(val promptLabel: String) {
    NPC("PERSONAGGIO"), PLAYER("GIOCATORE"), SHARED("RICORDO CONDIVISO")
}

data class SemanticModuleSelection(
    val id: String,
    val owner: SemanticOwner,
    val topic: String,
    val text: String,
    val score: Float
)

data class SemanticTurnSelection(
    val selected: List<SemanticModuleSelection>,
    val blockedIds: List<String>,
    val currentPlayerFacts: List<String>,
    val promptKnowledge: String,
    val diagnostic: String
)

/**
 * Selettore semantico usato dal dialogo reale. Non scrive mai la battuta: crea
 * al massimo tre blocchi di conoscenza, separati per proprietario, che il GGUF
 * può trasformare in linguaggio naturale.
 */
class SemanticMemorySelector {
    private data class Module(
        val id: String,
        val owner: SemanticOwner,
        val topic: String,
        val text: String,
        val minTrust: Int = 0,
        val minAffection: Int = 0,
        val minAttraction: Int = 0,
        val knownSecretId: String? = null,
        val cues: Set<String> = emptySet()
    )

    fun select(
        character: CharacterProfile,
        relationship: Relationship,
        route: HybridDialogueRouter.Route,
        history: List<DialogueMessage>,
        userText: String
    ): SemanticTurnSelection {
        val started = System.nanoTime()
        val currentFacts = PlayerFactExtractor.extract(userText)
        val modules = buildModules(character, relationship, currentFacts, route)
        val normalizedInput = normalize(userText)
        val queryRoots = contentRoots(normalizedInput)
        val recall = listOf(
            "ricordi", "ti ricordi", "cosa sai di me", "cosa ricordi di me", "te l avevo detto"
        ).any(normalizedInput::contains)
        val routeTopic = memoryTopic(route)

        val ranked = modules.map { module ->
            var score = 0f
            if (module.topic == routeTopic) score += 7f
            if (!route.fact.isNullOrBlank() && canonical(module.text) == canonical(route.fact)) score += 12f
            score += when (route.target) {
                HybridDialogueRouter.Target.PLAYER -> if (module.owner == SemanticOwner.PLAYER) 7f else -3f
                HybridDialogueRouter.Target.CHARACTER -> if (module.owner == SemanticOwner.NPC) 7f else -2f
                HybridDialogueRouter.Target.BOTH -> if (module.owner != SemanticOwner.SHARED) 4f else 2f
                HybridDialogueRouter.Target.THIRD_PARTY -> if (module.owner == SemanticOwner.SHARED) 3f else 0f
                HybridDialogueRouter.Target.UNCLEAR -> 0f
            }
            val overlap = contentRoots(module.text + " " + module.cues.joinToString(" ")).intersect(queryRoots).size
            score += overlap * 2.25f
            if (recall && module.owner == SemanticOwner.PLAYER) score += 5f
            if (recall && module.owner == SemanticOwner.SHARED) score += 2f
            if (route.continuedTopic && module.topic == routeTopic) score += 1.5f
            module to score
        }.filter { (module, score) ->
            val ownerCompatible = when (route.target) {
                HybridDialogueRouter.Target.PLAYER -> module.owner == SemanticOwner.PLAYER
                HybridDialogueRouter.Target.CHARACTER -> module.owner == SemanticOwner.NPC
                HybridDialogueRouter.Target.BOTH -> true
                HybridDialogueRouter.Target.THIRD_PARTY -> module.owner == SemanticOwner.SHARED
                HybridDialogueRouter.Target.UNCLEAR -> true
            }
            ownerCompatible && score >= 3f && (route.topic != HybridDialogueRouter.Topic.GENERAL ||
                score >= 6f || module.owner == SemanticOwner.PLAYER || module.owner == SemanticOwner.SHARED)
        }.sortedWith(compareByDescending<Pair<Module, Float>> { it.second }.thenBy { it.first.id })

        val candidates = ranked.take(3)
        val authorized = candidates.filter { (module, _) ->
            relationship.trust >= module.minTrust &&
                relationship.affection >= module.minAffection &&
                relationship.attraction >= module.minAttraction &&
                (module.knownSecretId == null || module.knownSecretId in relationship.knownSecrets)
        }
        val blocked = candidates.filterNot(authorized::contains)
        val selected = authorized.map { (module, score) ->
            SemanticModuleSelection(module.id, module.owner, module.topic, compact(module.text, 100), score)
        }
        val knowledge = selected.joinToString("\n") { module ->
            "${module.owner.promptLabel}: ${module.text}"
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        val recentTopic = history.asReversed().firstOrNull { it.speaker == "Tu" }
            ?.text?.let(::normalize)?.take(45).orEmpty()
        val diagnostic = buildString {
            append("tema=").append(route.topic.label)
            append("; soggetto=").append(route.target.name.lowercase())
            append("; domanda=").append(route.question.name.lowercase())
            append("; confidenza=").append("%.2f".format(java.util.Locale.US, route.confidence))
            append("; moduli=").append(selected.joinToString { it.id }.ifBlank { "nessuno" })
            append("; bloccati=").append(blocked.joinToString { it.first.id }.ifBlank { "nessuno" })
            append("; fatti_nuovi=").append(currentFacts.size)
            append("; precedente=").append(recentTopic.ifBlank { "nessuno" })
            append("; selezione_ms=").append(elapsedMs)
        }
        return SemanticTurnSelection(
            selected = selected,
            blockedIds = blocked.map { it.first.id },
            currentPlayerFacts = currentFacts,
            promptKnowledge = knowledge,
            diagnostic = diagnostic
        )
    }

    private fun buildModules(
        character: CharacterProfile,
        relationship: Relationship,
        currentFacts: List<String>,
        route: HybridDialogueRouter.Route
    ): List<Module> = buildList {
        route.fact?.takeIf { it.isNotBlank() }?.let { fact ->
            add(Module("npc_fatto_turno", SemanticOwner.NPC, memoryTopic(route), fact))
        }
        add(Module(
            "npc_identita",
            SemanticOwner.NPC,
            "identita",
            "${character.name} ha ${character.age} anni, è ${genderNoun(character)} e lavora come ${character.job}.",
            cues = setOf("nome", "eta", "anni", "chi sei", "genere")
        ))
        character.workFacts.forEachIndexed { index, fact ->
            add(Module("npc_lavoro_$index", SemanticOwner.NPC, "lavoro", fact, cues = setOf("lavoro", "mestiere", "professione")))
        }
        character.personalFacts.forEach { (topic, facts) ->
            facts.forEachIndexed { index, fact ->
                add(Module(
                    id = "npc_${topic}_$index",
                    owner = SemanticOwner.NPC,
                    topic = topic,
                    text = fact,
                    minTrust = trustForTopic(topic, character),
                    cues = setOf(topic)
                ))
            }
        }
        if (character.background.isNotBlank()) {
            character.background.split(Regex("(?<=[.!?])\\s+"))
                .filter { it.isNotBlank() }
                .take(5)
                .forEachIndexed { index, fact ->
                    add(Module("npc_famiglia_$index", SemanticOwner.NPC, "famiglia", fact, minTrust = trustForTopic("famiglia", character)))
                }
        }
        if (character.innerConflict.isNotBlank()) {
            add(Module(
                "npc_segreto_conflitto",
                SemanticOwner.NPC,
                "paure",
                character.innerConflict,
                minTrust = 30,
                minAffection = 12,
                knownSecretId = "${character.id}_conflict",
                cues = setOf("segreto", "nascondi", "paura", "conflitto")
            ))
        }
        character.storyBeats.forEachIndexed { index, fact ->
            add(Module(
                "npc_segreto_storia_$index",
                SemanticOwner.NPC,
                "ricordi",
                fact,
                minTrust = 45 + index * 15,
                knownSecretId = "${character.id}_story_$index",
                cues = setOf("segreto", "passato", "ricordo", "confessione")
            ))
        }

        relationship.knownPlayerName?.takeIf { it.isNotBlank() }?.let { name ->
            add(Module("player_nome", SemanticOwner.PLAYER, "identita", "Il giocatore si chiama $name.", cues = setOf("nome", "come mi chiamo")))
        }
        PlayerFactExtractor.merge(relationship.playerFacts, currentFacts).forEachIndexed { index, encoded ->
            add(Module(
                "player_${PlayerFactExtractor.topic(encoded)}_$index",
                SemanticOwner.PLAYER,
                PlayerFactExtractor.topic(encoded),
                PlayerFactExtractor.text(encoded)
            ))
        }
        relationship.memories.forEachIndexed { index, memory ->
            val owner = if (memory.startsWith("Al giocatore", ignoreCase = true)) SemanticOwner.PLAYER else SemanticOwner.SHARED
            add(Module("memoria_$index", owner, if (owner == SemanticOwner.PLAYER) "hobby" else "ricordi", memory))
        }
        relationship.emotionalMemories.forEachIndexed { index, memory ->
            add(Module("rapporto_$index", SemanticOwner.SHARED, "relazione", PlayerFactExtractor.text(memory)))
        }
    }.distinctBy { it.owner to canonical(it.text) }

    private fun memoryTopic(route: HybridDialogueRouter.Route): String = route.factTopicKey ?: when (route.topic) {
        HybridDialogueRouter.Topic.IDENTITY -> "identita"
        HybridDialogueRouter.Topic.WORK -> "lavoro"
        HybridDialogueRouter.Topic.FAMILY -> "famiglia"
        HybridDialogueRouter.Topic.RELATIONSHIP -> "relazione"
        HybridDialogueRouter.Topic.GENERAL -> "generale"
        else -> route.topic.name.lowercase()
    }

    private fun trustForTopic(topic: String, character: CharacterProfile): Int {
        val base = when (topic) {
            "luoghi" -> 2
            "aneddoti" -> 3
            "abitudini" -> 5
            "famiglia" -> 8
            "ricordi" -> 9
            "sogni" -> 14
            "paure" -> 20
            else -> 0
        }
        return if (topic in setOf("famiglia", "ricordi", "sogni", "paure")) {
            base + (character.conquestDifficulty - 3).coerceAtLeast(0) * 2
        } else base
    }

    private fun genderNoun(character: CharacterProfile): String =
        if (character.gender.equals("Maschio", ignoreCase = true)) "un uomo" else "una donna"

    private fun contentRoots(value: String): Set<String> = normalize(value).split(' ')
        .filter { it.length >= 3 && it !in stopWords }
        .map(::lightRoot)
        .toSet()

    private fun lightRoot(token: String): String {
        if (token.length <= 5) return token
        val ending = listOf("mente", "zione", "zioni", "ando", "endo", "ato", "ata", "ati", "ate", "ito", "ita", "iti", "ite", "are", "ere", "ire")
            .firstOrNull { token.endsWith(it) && token.length - it.length >= 4 }
        return if (ending != null) token.dropLast(ending.length)
        else if (token.length >= 7 && token.last() in setOf('a', 'e', 'i', 'o')) token.dropLast(1)
        else token
    }

    private fun canonical(value: String): String = normalize(value).replace(" ", "")

    private fun compact(value: String, maxLength: Int): String {
        val clean = value.replace(Regex("\\s+"), " ").trim()
        if (clean.length <= maxLength) return clean
        return clean.take(maxLength).substringBeforeLast(' ', clean.take(maxLength)).trimEnd(',', ';', ':')
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val stopWords = setOf(
        "anche", "avere", "come", "cosa", "della", "delle", "dello", "essere", "fare", "sono",
        "stai", "questo", "questa", "quello", "quella", "perche", "pero", "voglio", "vorrei"
    )
}
