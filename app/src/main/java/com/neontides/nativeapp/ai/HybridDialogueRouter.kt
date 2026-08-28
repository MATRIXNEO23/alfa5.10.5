package com.neontides.nativeapp.ai

import com.neontides.nativeapp.model.CharacterProfile
import com.neontides.nativeapp.model.DialogueMessage
import com.neontides.nativeapp.model.Relationship
import java.text.Normalizer

/**
 * Comprensione offline a costo quasi nullo.
 *
 * Il router non prova a scrivere la risposta: riconosce argomento, soggetto,
 * tipo di domanda, negazioni e continuita; quindi consegna al GGUF un solo
 * fatto pertinente. In questo modo il modello resta creativo nello stile, ma
 * non deve indovinare identita, biografia o stato della conversazione.
 */
class HybridDialogueRouter {
    enum class Topic(val label: String) {
        IDENTITY("identita"), WORK("lavoro"), HOBBY("tempo libero"), FOOD("cibo"),
        CINEMA("cinema"), MUSIC("musica"), PLACES("luoghi"), HABITS("abitudini"),
        ANECDOTES("aneddoti"), MEMORIES("ricordi"), DREAMS("sogni"), FEARS("paure"),
        FAMILY("famiglia"), RELATIONSHIP("rapporto tra i due"), GENERAL("conversazione generale")
    }

    enum class Target { CHARACTER, PLAYER, BOTH, THIRD_PARTY, UNCLEAR }
    enum class Question { WHO, WHAT, WHERE, WHEN, WHY, PREFERENCE, EXPERIENCE, YES_NO, STATEMENT }

    data class Route(
        val topic: Topic,
        val target: Target,
        val question: Question,
        val confidence: Float,
        val fact: String? = null,
        val factTopicKey: String? = null,
        val privacyBoundary: Boolean = false,
        val continuedTopic: Boolean = false,
        val correctionRequested: Boolean = false,
        val avoidTopic: Topic? = null,
        val relevantExchange: String? = null
    )

    private data class TopicRule(
        val topic: Topic,
        val phrases: Set<String>,
        val roots: Set<String>,
        val baseTrust: Int = 0
    )

    private val rules = listOf(
        TopicRule(Topic.IDENTITY,
            setOf("come ti chiami", "qual e il tuo nome", "il tuo nome", "quanti anni hai", "qual e la tua eta", "che eta hai", "chi sei", "presentati", "sei una ragazza", "sei una donna", "sei un ragazzo", "sei un uomo", "che genere sei"),
            setOf("nom", "eta", "anni", "identit", "chiam", "ragazz", "donn", "femmin", "maschi", "gener")),
        TopicRule(Topic.WORK,
            setOf("lavoro", "il tuo lavoro", "che lavoro fai", "qual e il tuo lavoro", "cosa fai nella vita", "di cosa ti occupi", "dove lavori", "cosa fai al lavoro", "che mestiere fai", "qual e il tuo mestiere", "che professione fai", "qual e la tua professione", "che musica fai", "che cosa disegni", "che tipo di cucina", "a cosa stai lavorando"),
            setOf("lavor", "mestier", "profession", "occup", "carriera", "uffici", "studio", "client", "progett", "udienz", "allen", "concert", "illustr", "ristor")),
        TopicRule(Topic.HOBBY,
            setOf("hobby", "tempo libero", "cosa fai per divertirti", "cosa ti piace fare", "quali sono le tue passioni"),
            setOf("hobby", "passion", "svago", "divert", "interess")),
        TopicRule(Topic.FOOD,
            setOf("cibo", "cibo preferito", "piatto preferito", "cosa ti piace mangiare", "cosa ti piace bere"),
            setOf("cib", "mangi", "piatt", "pranz", "cen", "colazion", "bev", "ristorant", "cucin")),
        TopicRule(Topic.CINEMA,
            setOf("film", "cinema", "film preferito", "che film guardi", "serie preferita", "andare al cinema"),
            setOf("film", "cinem", "serie", "attor", "attric", "documentar")),
        TopicRule(Topic.MUSIC,
            setOf("musica", "che musica ascolti", "musica preferita", "canzone preferita"),
            setOf("music", "canzon", "ascolt", "cantant", "playlist", "album")),
        TopicRule(Topic.PLACES,
            setOf("posto", "luogo", "posto preferito", "luogo preferito", "dove vai per rilassarti", "dove ti senti bene"),
            setOf("post", "luog", "dove", "spiaggi", "parc", "local", "rifugi"), 2),
        TopicRule(Topic.HABITS,
            setOf("abitudini", "vizio", "che abitudini hai", "cosa fai sempre", "hai qualche vizio"),
            setOf("abitudin", "vizi", "routin", "sempre"), 5),
        TopicRule(Topic.ANECDOTES,
            setOf("aneddoto", "raccontami un aneddoto", "cosa buffa", "figuraccia", "episodio divertente"),
            setOf("aneddot", "buff", "divertent", "figuracci", "episod"), 3),
        TopicRule(Topic.MEMORIES,
            setOf("ricordo", "ricordi", "raccontami un ricordo", "ricordo piu bello", "parlami del tuo passato", "quando eri piccola", "quando eri giovane"),
            setOf("ricord", "passat", "infanzi", "bambin", "cresciut"), 9),
        TopicRule(Topic.DREAMS,
            setOf("sogno", "sogni", "qual e il tuo sogno", "cosa desideri", "cosa vorresti dal futuro"),
            setOf("sogn", "desider", "futur", "vorrest"), 14),
        TopicRule(Topic.FEARS,
            setOf("paura", "paure", "di cosa hai paura", "cosa temi", "cosa ti preoccupa", "cosa ti ha ferita"),
            setOf("paur", "tem", "preoccup", "ferit", "traum", "ansia"), 20),
        TopicRule(Topic.FAMILY,
            setOf("famiglia", "genitori", "parlami della tua famiglia", "come sono i tuoi genitori"),
            setOf("famigli", "genitor", "madr", "padr", "nonna", "nonno", "sorell", "fratell"), 8),
        TopicRule(Topic.RELATIONSHIP,
            setOf(
                "cosa pensi di me", "ti piaccio", "noi due", "che rapporto abbiamo", "stare con me", "innamorarti di me",
                "sotto la gonna", "porti le mutandine", "senza mutandine", "che intimo porti", "non mi vuoi rispondere"
            ),
            setOf(
                "rapport", "fiduci", "amic", "amore", "innamor", "piaccio", "insiem", "appuntament", "relazion",
                "mutandin", "biancher", "intim", "reggisen", "baciar", "spogli"
            ), 0)
    )

    private val stopWords = setOf(
        "anche", "avere", "come", "cosa", "della", "delle", "dello", "dopo", "essere", "fare",
        "mentre", "molto", "perche", "pero", "posso", "questa", "questo", "quindi", "sono",
        "stai", "tanto", "voglio", "vorrei"
    )

    fun route(
        character: CharacterProfile,
        relationship: Relationship,
        history: List<DialogueMessage>,
        userText: String
    ): Route {
        val normalized = normalize(userText)
        val tokens = tokens(normalized)
        val correction = isCorrectionRequest(normalized)
        val directScores = scoreTopics(normalized, tokens)
        val directlyMatched = directScores.maxByOrNull { it.value }
        val topicChange = isTopicChangeRequest(normalized)
        val negated = if (topicChange) {
            negatedTopic(directScores) ?: previousTopic(history)
        } else null
        if (negated != null) directScores[negated] = -20f

        var continued = false
        if (!correction && !topicChange && (directlyMatched?.value ?: 0f) < 3f && shouldContinueTopic(normalized, tokens)) {
            previousTopic(history)?.let { previous ->
                directScores[previous] = (directScores[previous] ?: 0f) + 3.25f
                continued = true
            }
        }

        val ranked = directScores.entries.sortedByDescending { it.value }
        val best = ranked.firstOrNull()?.takeIf { it.value >= 2.25f }
        val topic = if (correction || topicChange) Topic.GENERAL else best?.key ?: Topic.GENERAL
        val second = ranked.getOrNull(1)?.value?.coerceAtLeast(0f) ?: 0f
        val confidence = if (best == null) 0f else ((best.value - second + 2f) / 8f).coerceIn(0.15f, 1f)
        // "Ti ricordi come mi chiamo?" riguarda ciò che il personaggio sa del
        // giocatore, non i ricordi privati del personaggio. Nella 8.7 questo
        // caso attivava erroneamente il limite di fiducia sui segreti.
        val target = if (isPlayerRecallRequest(normalized)) Target.PLAYER else detectTarget(normalized, topic)
        val question = detectQuestion(normalized)
        val trustRequired = requiredTrust(topic, character)
        val privacyBoundary = target != Target.PLAYER && relationship.trust < trustRequired
        val fact = if (!privacyBoundary && target != Target.PLAYER && topic != Topic.GENERAL) {
            selectFact(topic, question, character, relationship, userText)
        } else null
        val exchange = when {
            correction -> history.asReversed().firstOrNull { it.speaker == "Tu" }?.text?.take(100)
            continued && topic != Topic.GENERAL -> relevantExchange(history, topic)
            else -> null
        }

        return Route(
            topic = topic,
            target = target,
            question = question,
            confidence = confidence,
            fact = fact,
            factTopicKey = topicKey(topic),
            privacyBoundary = privacyBoundary,
            continuedTopic = continued,
            correctionRequested = correction,
            avoidTopic = negated,
            relevantExchange = exchange
        )
    }

    fun promptHint(route: Route, character: CharacterProfile, relationship: Relationship): String {
        // Nota naturale e breve: evita che un piccolo GGUF ripeta etichette
        // tecniche come "tema" o "soggetto" dentro la risposta.
        return buildList {
            when {
                route.correctionRequested -> add("Il giocatore sta correggendo un equivoco: rispondi alla sua domanda attuale.")
                route.privacyBoundary -> add("Questo dettaglio richiede più fiducia: poni un limite personale e gentile.")
                route.avoidTopic != null -> add("Il giocatore vuole cambiare argomento: seguilo.")
                route.question == Question.WHY -> add("Spiega direttamente il motivo.")
                route.question == Question.WHERE -> add("Indica un luogo concreto.")
                route.question == Question.PREFERENCE -> add("Esprimi una preferenza concreta.")
            }
            // I fatti vengono aggiunti dal selettore modulare, che mantiene
            // separati PERSONAGGIO, GIOCATORE e RICORDI CONDIVISI.
            route.relevantExchange?.takeIf { it.isNotBlank() }?.let {
                add("Continuità utile: ${it.take(75)}.")
            }
            when (route.target) {
                Target.PLAYER -> add("Ciò che il giocatore dice di sé appartiene a lui, non a ${character.name}.")
                Target.BOTH -> add("Distingui chiaramente il giocatore da ${character.name}.")
                else -> Unit
            }
            add("Rispondi in modo naturale al messaggio, senza inventare nomi o fatti.")
        }.joinToString(" ")
    }

    /** Chiave stabile usata dalla memoria persistente senza esporre l'enum al salvataggio. */
    fun memoryTopic(route: Route): String = route.factTopicKey ?: when (route.topic) {
        Topic.IDENTITY -> "identita"
        Topic.WORK -> "lavoro"
        Topic.FAMILY -> "famiglia"
        Topic.RELATIONSHIP -> "relazione"
        Topic.GENERAL -> "generale"
        else -> route.topic.name.lowercase()
    }

    /**
     * Risposte certe che non richiedono formulazione generativa. Questi casi
     * devono restare davanti al GGUF: oltre a essere quasi istantanei evitano
     * che il modello contraddica dati gia presenti nel profilo.
     */
    fun instantGroundedReply(
        route: Route,
        character: CharacterProfile,
        relationship: Relationship,
        history: List<DialogueMessage>,
        userText: String
    ): String? {
        val normalized = normalize(userText)
        if (isPlayerRecallRequest(normalized)) {
            val remembered = buildList {
                relationship.knownPlayerName?.takeIf { it.isNotBlank() }?.let { add("ti chiami $it") }
                relationship.playerFacts.asReversed()
                    .distinctBy { it.substringBefore('|') }
                    .take(4)
                    .asReversed()
                    .map { it.substringAfter('|', it).trim().trimEnd('.') }
                    .map(::playerFactInSecondPerson)
                    .forEach { add(it) }
            }
            return if (remembered.isEmpty()) {
                "Non me l'hai ancora raccontato, oppure non l'ho ancora memorizzato. Dimmi qualcosa di te e lo ricorderò."
            } else {
                "Certo: ${remembered.joinToString(", ")}."
            }
        }
        if (isGreetingOnly(normalized)) {
            val playerName = relationship.knownPlayerName?.takeIf { it.isNotBlank() }
            val variants = if (playerName == null) {
                listOf("Ciao! Come stai?", "Ehi, ciao. Come va?", "Ciao. Dimmi, come stai?")
            } else {
                listOf("Ciao, $playerName! Come stai?", "Ehi, $playerName. Come va?", "Ciao, $playerName. Che mi racconti?")
            }
            return variants[(character.id.hashCode() and Int.MAX_VALUE) % variants.size]
        }

        // Solo domande biografiche inequivocabili saltano il GGUF. Tutto ciò
        // che richiede tono, sinonimi o continuità viene formulato dal modello.
        if (asksCharacterAge(normalized))
            return "Ho ${character.age} anni."
        if (Regex("\\b(come ti chiami|qual e il tuo nome)\\b").containsMatchIn(normalized))
            return "Mi chiamo ${character.name}."
        if (Regex("\\b(che lavoro fai|qual e il tuo lavoro|cosa fai nella vita|di cosa ti occupi|che mestiere fai|qual e il tuo mestiere|che professione fai|qual e la tua professione)\\b")
                .containsMatchIn(normalized)
        ) return "Sono ${character.job.lowercase()}."
        if (Regex("\\b(che genere sei|sei un uomo|sei una donna|sei un ragazzo|sei una ragazza)\\b")
                .containsMatchIn(normalized)
        ) return if (character.gender == "Maschio") "Sono un uomo." else "Sono una donna."
        return null
    }

    private fun asksCharacterAge(normalized: String): Boolean {
        if (!Regex("\\b(tu|te|ti|tuo|tua|hai)\\b").containsMatchIn(normalized)) return false
        val hasAgeConcept = Regex("\\b(anni|anno|hanno|eta)\\b").containsMatchIn(normalized)
        val hasQuestionShape = listOf("quanti", "quanto", "qual e", "che eta").any(normalized::contains)
        // Tollera errori comuni come "quanti hanno hai" senza affidare un
        // fatto anagrafico certo al modello generativo.
        return hasAgeConcept && hasQuestionShape
    }

    /** Turni socialmente neutri: non devono produrre punti neppure se un
     * modello remoto propone accidentalmente un delta positivo. */
    fun isNeutralRelationshipTurn(userText: String): Boolean {
        val normalized = normalize(userText)
        return isGreetingOnly(normalized) || isBareNegative(normalized) || isBarePositive(normalized) ||
            isCorrectionRequest(normalized) || isPlayerRecallRequest(normalized) ||
            asksCharacterAge(normalized) ||
            Regex("\\b(come ti chiami|qual e il tuo nome|che lavoro fai|qual e il tuo lavoro|che mestiere fai|qual e il tuo mestiere|che professione fai)\\b")
                .containsMatchIn(normalized)
    }

    fun groundedFallback(
        route: Route,
        character: CharacterProfile,
        relationship: Relationship,
        userText: String
    ): String {
        val normalizedUser = normalize(userText)
        val intimateQuestion = listOf(
            "sesso", "fare l amore", "nuda", "nudo", "a letto", "sotto la gonna",
            "mutandine", "biancheria", "reggiseno", "baciarti", "spogliarti", "puttana", "troia"
        ).any(normalizedUser::contains)
        if (intimateQuestion) {
            return if (relationship.stage in setOf("Flirt", "Attrazione reciproca", "Appuntamenti", "Relazione")) {
                if (character.sensuality >= 4) {
                    "Che domanda sfacciata. Con la confidenza che c'è tra noi posso risponderti senza fingere di scandalizzarmi."
                } else "È una domanda molto diretta, ma ormai abbiamo abbastanza confidenza perché possa risponderti con sincerità."
            } else {
                "È una domanda molto intima per una persona che conosco appena. Non mi offende, ma prima voglio capire se posso fidarmi di te."
            }
        }
        if (listOf("non mi vuoi rispondere", "perche non rispondi", "non vuoi rispondere").any(normalizedUser::contains)) {
            return if (relationship.stage in setOf("Sconosciuti", "Conoscenza")) {
                "Ti sto rispondendo: su certe cose preferisco aspettare che ci sia più confidenza tra noi."
            } else "Voglio risponderti, ma dimmi chiaramente a quale domanda ti riferisci."
        }
        if (route.correctionRequested) {
            return "Hai ragione, la mia risposta precedente non era coerente con quello che avevi detto. Ignoriamola: resto sull'argomento reale senza aggiungere persone o dettagli inventati."
        }
        if (route.privacyBoundary) {
            return when (route.topic) {
                Topic.FAMILY -> "Preferisco non parlare ancora della mia famiglia. Magari te ne racconterò quando ci conosceremo meglio."
                Topic.FEARS, Topic.MEMORIES -> "È una cosa personale che per ora preferisco tenere per me. Non è mancanza di interesse: ho bisogno di più fiducia."
                else -> "Non me la sento ancora di raccontarti questo dettaglio, ma possiamo conoscerci con calma."
            }
        }
        route.fact?.let { fact ->
            val firstPerson = toFirstPerson(fact)
            return when (route.question) {
                Question.WHY -> "$firstPerson È questo il motivo principale."
                Question.PREFERENCE -> "$firstPerson È una preferenza a cui tengo davvero."
                else -> firstPerson
            }
        }
        if (route.topic == Topic.RELATIONSHIP) {
            return when (relationship.stage) {
                "Sconosciuti" -> "Ci conosciamo ancora poco. Preferisco capire chi sei prima di dare un nome a quello che c'è tra noi."
                "Conoscenza" -> "Mi fa piacere parlare con te, ma voglio conoscerti senza correre."
                "Amicizia" -> "Mi fido di te e sto bene quando parliamo. Per me questa amicizia conta."
                "Flirt", "Attrazione reciproca" -> "Tra noi sento attrazione, ma voglio che cresca insieme alla fiducia."
                else -> "Quello che abbiamo costruito per me è importante e voglio viverlo con sincerità."
            }
        }
        val value = normalizedUser
        if (listOf("ciao", "buongiorno", "buonasera", "ehi").any(value::startsWith)) {
            return "Ciao. Mi fa piacere parlare con te: dimmi pure."
        }
        if (listOf("non capisco", "cosa intendi", "che significa", "cosa stai dicendo").any(value::contains)) {
            return "Mi sono spiegata male, scusami. Rispondo a quello che mi hai detto senza cambiare argomento."
        }
        val variants = listOf(
            "A quale dettaglio ti riferisci esattamente? Preferisco risponderti su qualcosa di concreto.",
            "Sii più preciso: quale parte vuoi conoscere?",
            "Non sono sicura di aver seguito il riferimento. Dimmi il punto preciso e resto su quello."
        )
        return variants[(normalizedUser.hashCode() and Int.MAX_VALUE) % variants.size]
    }

    private fun isGreetingOnly(normalized: String): Boolean = normalized in setOf(
        "ciao", "salve", "ehi", "hey", "buongiorno", "buonasera", "buonanotte"
    )

    private fun isPlayerRecallRequest(normalized: String): Boolean {
        val asksToRemember = listOf(
            "ti ricordi", "ricordi cosa ti ho detto", "ricordi quello che ti ho detto",
            "te l avevo detto", "cosa sai di me", "cosa ricordi di me"
        ).any(normalized::contains)
        val aboutPlayer = Regex("\\b(io|me|mi|mio|mia|miei|mie)\\b").containsMatchIn(normalized) ||
            listOf("come mi chiamo", "quanti anni ho", "cosa mi piace", "che lavoro faccio").any(normalized::contains)
        return asksToRemember && aboutPlayer
    }

    private fun playerFactInSecondPerson(fact: String): String {
        val value = fact.trim().trimEnd('.')
        return when {
            value.startsWith("Gli piace ", ignoreCase = true) ->
                "ti piace ${value.substringAfter("Gli piace ", "")}"
            value.startsWith("Non gradisce ", ignoreCase = true) ->
                "non ti piace ${value.substringAfter("Non gradisce ", "")}"
            value.startsWith("Lavora come ", ignoreCase = true) ->
                "lavori come ${value.substringAfter("Lavora come ", "")}"
            value.startsWith("Vive a ", ignoreCase = true) ->
                "vivi a ${value.substringAfter("Vive a ", "")}"
            value.startsWith("Ha ", ignoreCase = true) ->
                "hai ${value.substringAfter(' ')}"
            else -> value.replaceFirstChar { it.lowercase() }
        }
    }

    private fun isBareNegative(normalized: String): Boolean = normalized in setOf(
        "no", "no grazie", "non proprio", "direi di no"
    )

    private fun isBarePositive(normalized: String): Boolean = normalized in setOf(
        "si", "si grazie", "certo", "va bene", "okay", "ok"
    )

    private fun isConcernQuestion(normalized: String): Boolean = listOf(
        "c e qualche problema", "ce qualche problema", "c e un problema", "ce un problema",
        "va tutto bene", "qualcosa non va", "sei arrabbiata", "sei arrabbiato",
        "sei infastidita", "sei infastidito"
    ).any(normalized::contains)

    private fun scoreTopics(normalized: String, tokens: Set<String>): MutableMap<Topic, Float> {
        val scores = Topic.values().associateWith { 0f }.toMutableMap()
        rules.forEach { rule ->
            var score = 0f
            rule.phrases.forEach { phrase ->
                if (containsPhrase(normalized, phrase)) score += if (' ' in phrase) 5f else 2.5f
            }
            rule.roots.forEach { root ->
                if (tokens.any { it.startsWith(root) }) score += 1.35f
            }
            scores[rule.topic] = score
        }
        // Frasi che disambiguano casi frequenti: "musica" puo essere gusto o lavoro.
        if (normalized.contains("che musica fai") || normalized.contains("che pezzo stai scrivendo")) {
            scores[Topic.WORK] = (scores[Topic.WORK] ?: 0f) + 6f
            scores[Topic.MUSIC] = (scores[Topic.MUSIC] ?: 0f) - 2f
        }
        if (normalized.contains("che musica ascolti")) scores[Topic.MUSIC] = (scores[Topic.MUSIC] ?: 0f) + 6f
        if (listOf("parlami di te", "raccontami di te", "dimmi qualcosa di te", "vorrei conoscerti").any(normalized::contains)) {
            scores[Topic.HOBBY] = (scores[Topic.HOBBY] ?: 0f) + 4f
        }
        if (normalized.contains("cosa ti piace") && (scores.values.maxOrNull() ?: 0f) < 3f) {
            scores[Topic.HOBBY] = 3.5f
        }
        return scores
    }

    private fun previousTopic(history: List<DialogueMessage>): Topic? = history.asReversed()
        .filter { it.speaker == "Tu" }
        .take(3)
        .mapNotNull { message ->
            val normalized = normalize(message.text)
            scoreTopics(normalized, tokens(normalized)).maxByOrNull { it.value }
                ?.takeIf { it.value >= 2.5f }?.key
        }
        .firstOrNull()

    private fun relevantExchange(history: List<DialogueMessage>, topic: Topic): String? {
        for (index in history.indices.reversed()) {
            val message = history[index]
            if (message.speaker != "Tu") continue
            val normalized = normalize(message.text)
            val match = scoreTopics(normalized, tokens(normalized)).maxByOrNull { it.value }
            if (match?.key != topic || match.value < 2.5f) continue
            return "il giocatore aveva detto: ${message.text.take(110)}"
        }
        return null
    }

    private fun shouldContinueTopic(normalized: String, tokens: Set<String>): Boolean {
        val referencesPrevious = listOf(
            "e poi", "e invece", "perche", "come mai", "quello", "quella", "questo", "questa",
            "ne parli", "parlami meglio", "dimmi altro", "continua", "davvero", "e tu", "e io"
        ).any { containsPhrase(normalized, it) }
        if (referencesPrevious) return true
        return tokens.size <= 2 && tokens.any { it in setOf("lui", "lei", "loro", "quindi") }
    }

    private fun isCorrectionRequest(normalized: String): Boolean = listOf(
        "non capisco", "non ti capisco", "cosa stai dicendo", "che risposta", "cosa intendi",
        "che significa", "chi e non lo conosco", "non lo conosco", "non la conosco",
        "lo hai nominato tu", "l hai nominato tu", "io che c entro", "non c entra",
        "risposta secca", "sei incoerente", "rispondi male", "ti ho chiesto",
        "avevo chiesto", "non ti ho chiesto", "rispondi alla domanda", "non era la domanda"
    ).any(normalized::contains)

    private fun isTopicChangeRequest(normalized: String): Boolean = listOf(
            "non voglio parlare", "non parliamo", "basta parlare", "smetti di parlare",
            "cambiamo argomento", "lascia perdere", "non mi interessa parlare"
        ).any(normalized::contains)

    private fun negatedTopic(scores: Map<Topic, Float>): Topic? {
        return scores.filterKeys { it != Topic.GENERAL }.maxByOrNull { it.value }
            ?.takeIf { it.value >= 1.2f }?.key
    }

    private fun detectTarget(normalized: String, topic: Topic): Target {
        val both = listOf("noi due", "tra noi", "io e te", "e tu", "anche tu").any(normalized::contains)
        if (both || topic == Topic.RELATIONSHIP) return Target.BOTH
        val character = Regex("\\b(tu|te|ti|tuo|tua|tuoi|tue)\\b").containsMatchIn(normalized)
        val player = Regex("\\b(io|me|mi|mio|mia|miei|mie)\\b").containsMatchIn(normalized)
        return when {
            character && player -> Target.BOTH
            character -> Target.CHARACTER
            player -> Target.PLAYER
            topic != Topic.GENERAL -> Target.CHARACTER
            else -> Target.UNCLEAR
        }
    }

    private fun detectQuestion(normalized: String): Question = when {
        Regex("\\b(chi|come ti chiami|qual e il tuo nome)\\b").containsMatchIn(normalized) -> Question.WHO
        Regex("\\b(dove|in quale posto|in che luogo)\\b").containsMatchIn(normalized) -> Question.WHERE
        Regex("\\b(quando|a che ora|in che giorno)\\b").containsMatchIn(normalized) -> Question.WHEN
        Regex("\\b(perche|come mai|per quale motivo)\\b").containsMatchIn(normalized) -> Question.WHY
        listOf("preferit", "ti piace", "ami di piu", "cosa scegli").any(normalized::contains) -> Question.PREFERENCE
        listOf("raccontami", "e successo", "ricordi quando", "aneddoto", "esperienza").any(normalized::contains) -> Question.EXPERIENCE
        Regex("^(sei|hai|puoi|vuoi|ti|e vero|davvero)").containsMatchIn(normalized) -> Question.YES_NO
        Regex("\\b(cosa|che cosa|quale|quali)\\b").containsMatchIn(normalized) -> Question.WHAT
        else -> Question.STATEMENT
    }

    private fun requiredTrust(topic: Topic, character: CharacterProfile): Int {
        val base = rules.firstOrNull { it.topic == topic }?.baseTrust ?: 0
        return when (topic) {
            Topic.MEMORIES, Topic.DREAMS, Topic.FEARS, Topic.FAMILY ->
                base + (character.conquestDifficulty - 3).coerceAtLeast(0) * 2
            else -> base
        }
    }

    private fun selectFact(
        topic: Topic,
        question: Question,
        character: CharacterProfile,
        relationship: Relationship,
        userText: String
    ): String? = when (topic) {
        Topic.IDENTITY -> when {
            normalize(userText).contains("anni") || normalize(userText).contains("eta") ->
                "${character.name} ha ${character.age} anni."
            listOf("genere", "uomo", "donna", "ragazzo", "ragazza", "maschio", "femmina")
                .any(normalize(userText)::contains) ->
                if (character.gender == "Maschio") "${character.name} è un uomo."
                else "${character.name} è una donna."
            normalize(userText).contains("lavor") || normalize(userText).contains("mestier") ||
                normalize(userText).contains("profession") || normalize(userText).contains("fai nella vita") ->
                "${character.name} lavora come ${character.job}."
            else -> "Il suo nome e ${character.name}."
        }
        Topic.WORK -> selectWorkFact(character, question, userText, relationship.talks)
        Topic.FAMILY -> mostRelevantSentence(character.background, userText)
        Topic.RELATIONSHIP -> "La relazione attuale e ${relationship.stage}, con fiducia ${relationship.trust}."
        Topic.GENERAL -> null
        else -> character.personalFacts[topicKey(topic)]?.let { facts ->
            selectBestFact(facts, userText, relationship.talks, topic)
        }
    }

    private fun selectWorkFact(
        character: CharacterProfile,
        question: Question,
        userText: String,
        talks: Int
    ): String? {
        val facts = character.workFacts
        if (facts.isEmpty()) return null
        val normalized = normalize(userText)
        return when {
            question == Question.WHERE || normalized.contains("dove lavori") -> facts.firstOrNull()
            listOf("ora", "adesso", "attualmente", "in questo periodo", "stai lavorando").any(normalized::contains) -> facts.lastOrNull()
            listOf("cosa fai", "di cosa ti occupi", "che lavoro").any(normalized::contains) -> facts.getOrNull(1) ?: facts.first()
            else -> selectBestFact(facts, userText, talks, Topic.WORK)
        }
    }

    private fun selectBestFact(facts: List<String>, userText: String, talks: Int, topic: Topic): String? {
        if (facts.isEmpty()) return null
        val queryRoots = contentRoots(userText)
        val ranked = facts.mapIndexed { index, fact ->
            index to contentRoots(fact).intersect(queryRoots).size
        }.sortedByDescending { it.second }
        val best = ranked.firstOrNull()
        if (best != null && best.second > 0) return facts[best.first]
        val seed = (talks * 31 + topic.ordinal * 17).and(Int.MAX_VALUE)
        return facts[seed % facts.size]
    }

    private fun mostRelevantSentence(background: String, userText: String): String? {
        val sentences = background.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        return selectBestFact(sentences, userText, 0, Topic.FAMILY)
    }

    private fun topicKey(topic: Topic): String? = when (topic) {
        Topic.HOBBY -> "hobby"
        Topic.FOOD -> "cibo"
        Topic.CINEMA -> "cinema"
        Topic.MUSIC -> "musica"
        Topic.PLACES -> "luoghi"
        Topic.HABITS -> "abitudini"
        Topic.ANECDOTES -> "aneddoti"
        Topic.MEMORIES -> "ricordi"
        Topic.DREAMS -> "sogni"
        Topic.FEARS -> "paure"
        else -> null
    }

    private fun contentRoots(value: String): Set<String> = tokens(normalize(value))
        .filter { it.length >= 4 && it !in stopWords }
        .map(::lightRoot)
        .toSet()

    private fun tokens(normalized: String): Set<String> = normalized.split(' ')
        .filter { it.length >= 2 }
        .toSet()

    private fun lightRoot(token: String): String {
        if (token.length <= 5) return token
        val endings = listOf("mente", "zione", "zioni", "ando", "endo", "ato", "ata", "ati", "ate", "ito", "ita", "iti", "ite", "are", "ere", "ire")
        val ending = endings.firstOrNull { token.endsWith(it) && token.length - it.length >= 4 }
        if (ending != null) return token.dropLast(ending.length)
        return if (token.length >= 7 && token.last() in setOf('a', 'e', 'i', 'o')) token.dropLast(1) else token
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun containsPhrase(normalized: String, phrase: String): Boolean =
        Regex("(?:^| )${Regex.escape(phrase)}(?:$| )").containsMatchIn(normalized)

    private fun toFirstPerson(value: String): String {
        var text = value.trim()
            .replace(Regex("^Il suo nome e ", RegexOption.IGNORE_CASE), "Mi chiamo ")
            .replace(Regex("^[A-ZÀ-Ý][^ ]+ [A-ZÀ-Ý][^ ]+ ha ", RegexOption.IGNORE_CASE), "Ho ")
            .replace(Regex("^[A-ZÀ-Ý][^ ]+ [A-ZÀ-Ý][^ ]+ lavora ", RegexOption.IGNORE_CASE), "Lavoro ")
            .replace(Regex("^[A-ZÀ-Ý][^ ]+ [A-ZÀ-Ý][^ ]+ è un uomo", RegexOption.IGNORE_CASE), "Sono un uomo")
            .replace(Regex("^[A-ZÀ-Ý][^ ]+ [A-ZÀ-Ý][^ ]+ è una donna", RegexOption.IGNORE_CASE), "Sono una donna")
            .replace(Regex("^È cresciuta ", RegexOption.IGNORE_CASE), "Sono cresciuta ")
            .replace(Regex("^E cresciuta ", RegexOption.IGNORE_CASE), "Sono cresciuta ")
            .replace(Regex("^È cresciuto ", RegexOption.IGNORE_CASE), "Sono cresciuto ")
            .replace(Regex("^E cresciuto ", RegexOption.IGNORE_CASE), "Sono cresciuto ")
            .replace(Regex("^È nata ", RegexOption.IGNORE_CASE), "Sono nata ")
            .replace(Regex("^E nata ", RegexOption.IGNORE_CASE), "Sono nata ")
            .replace(Regex("^È nato ", RegexOption.IGNORE_CASE), "Sono nato ")
            .replace(Regex("^E nato ", RegexOption.IGNORE_CASE), "Sono nato ")
            .replace(Regex("^Da adolescente era ", RegexOption.IGNORE_CASE), "Da adolescente ero ")
            .replace(Regex("^Proviene ", RegexOption.IGNORE_CASE), "Provengo ")
            .replace(Regex("^Ha lavorato ", RegexOption.IGNORE_CASE), "Ho lavorato ")
            .replace(Regex("^Ha scelto ", RegexOption.IGNORE_CASE), "Ho scelto ")
            .replace(Regex("^Ha imparato ", RegexOption.IGNORE_CASE), "Ho imparato ")
            .replace(Regex("^Disegnava ", RegexOption.IGNORE_CASE), "Disegnavo ")
            .replace(Regex("^Una volta ridisegnò ", RegexOption.IGNORE_CASE), "Una volta ho ridisegnato ")
            .replace(Regex("^Durante la sua prima lezione confuse ", RegexOption.IGNORE_CASE), "Durante la mia prima lezione ho confuso ")
            .replace(Regex("^Al suo primo giorno in tribunale entrò ", RegexOption.IGNORE_CASE), "Al mio primo giorno in tribunale sono entrata ")
            .replace(Regex("^Alla prima serata del Lume bruciò ", RegexOption.IGNORE_CASE), "Alla prima serata del Lume ho bruciato ")
            .replace(Regex("^Lavora ", RegexOption.IGNORE_CASE), "Lavoro ")
            .replace(Regex("^Si occupa ", RegexOption.IGNORE_CASE), "Mi occupo ")
            .replace(Regex("^Progetta ", RegexOption.IGNORE_CASE), "Progetto ")
            .replace(Regex("^Segue ", RegexOption.IGNORE_CASE), "Seguo ")
            .replace(Regex("^Scrive ", RegexOption.IGNORE_CASE), "Scrivo ")
            .replace(" e interpreta ", " e interpreto ", ignoreCase = true)
            .replace(Regex("^Si esibisce ", RegexOption.IGNORE_CASE), "Mi esibisco ")
            .replace(Regex("^Realizza ", RegexOption.IGNORE_CASE), "Realizzo ")
            .replace(Regex("^E chef e proprietaria ", RegexOption.IGNORE_CASE), "Sono chef e proprietaria ")
            .replace(Regex("^Guida ", RegexOption.IGNORE_CASE), "Guido ")
            .replace(Regex("^Sta ", RegexOption.IGNORE_CASE), "Sto ")
            .replace(Regex("^In questo periodo sta ", RegexOption.IGNORE_CASE), "In questo periodo sto ")
            .replace(Regex("^Nei giorni tranquilli coltiva ", RegexOption.IGNORE_CASE), "Nei giorni tranquilli coltivo ")
            .replace(Regex("^Le piace ", RegexOption.IGNORE_CASE), "Mi piace ")
            .replace(Regex("^Adora ", RegexOption.IGNORE_CASE), "Adoro ")
            .replace(Regex("^Ama ", RegexOption.IGNORE_CASE), "Amo ")
            .replace(Regex("^Restaura ", RegexOption.IGNORE_CASE), "Restauro ")
            .replace(Regex("^Fotografa ", RegexOption.IGNORE_CASE), "Fotografo ")
            .replace(Regex("^Disegna ", RegexOption.IGNORE_CASE), "Disegno ")
            .replace(Regex("^Coltiva ", RegexOption.IGNORE_CASE), "Coltivo ")
            .replace(Regex("^Registra ", RegexOption.IGNORE_CASE), "Registro ")
            .replace(Regex("^Colleziona ", RegexOption.IGNORE_CASE), "Colleziono ")
            .replace(Regex("^Improvvisa ", RegexOption.IGNORE_CASE), "Improvviso ")
            .replace(Regex("^Visita ", RegexOption.IGNORE_CASE), "Visito ")
            .replace(Regex("^Gioca ", RegexOption.IGNORE_CASE), "Gioco ")
            .replace(Regex("^Cerca ", RegexOption.IGNORE_CASE), "Cerco ")
            .replace(Regex("^Mastica ", RegexOption.IGNORE_CASE), "Mastico ")
            .replace(Regex("^Nasconde ", RegexOption.IGNORE_CASE), "Nascondo ")
            .replace(Regex("^Assaggia ", RegexOption.IGNORE_CASE), "Assaggio ")
            .replace(Regex("^Cucina ", RegexOption.IGNORE_CASE), "Cucino ")
            .replace(Regex("^Conta ", RegexOption.IGNORE_CASE), "Conto ")
            .replace(Regex("^Allinea ", RegexOption.IGNORE_CASE), "Allineo ")
            .replace(Regex("^Prende ", RegexOption.IGNORE_CASE), "Prendo ")
            .replace(Regex("^Tamburella ", RegexOption.IGNORE_CASE), "Tamburello ")
            .replace(Regex("^Sfida ", RegexOption.IGNORE_CASE), "Sfido ")
            .replace(Regex("^Preferisce ", RegexOption.IGNORE_CASE), "Preferisco ")
            .replace(Regex("^Ascolta ", RegexOption.IGNORE_CASE), "Ascolto ")
            .replace(Regex("^Beve ", RegexOption.IGNORE_CASE), "Bevo ")
            .replace(Regex("^Ordina ", RegexOption.IGNORE_CASE), "Ordino ")
            .replace(Regex("^Guarda ", RegexOption.IGNORE_CASE), "Guardo ")
            .replace(Regex("^Ricorda ", RegexOption.IGNORE_CASE), "Ricordo ")
            .replace(Regex("^Conserva ", RegexOption.IGNORE_CASE), "Conservo ")
            .replace(Regex("^Sogna ", RegexOption.IGNORE_CASE), "Sogno ")
            .replace(Regex("^Vuole ", RegexOption.IGNORE_CASE), "Voglio ")
            .replace(Regex("^Vorrebbe ", RegexOption.IGNORE_CASE), "Vorrei ")
            .replace(Regex("^Desidera ", RegexOption.IGNORE_CASE), "Desidero ")
            .replace(Regex("^Sa ", RegexOption.IGNORE_CASE), "So ")
            .replace(Regex("^Teme ", RegexOption.IGNORE_CASE), "Temo ")
            .replace(Regex("^Ha paura ", RegexOption.IGNORE_CASE), "Ho paura ")
            .replace(Regex("^Il suo ", RegexOption.IGNORE_CASE), "Il mio ")
            .replace(Regex("^La sua ", RegexOption.IGNORE_CASE), "La mia ")
            .replace(Regex("^Si sente ", RegexOption.IGNORE_CASE), "Mi sento ")
            .replace(" della sua ", " della mia ", ignoreCase = true)
            .replace(" la sua ", " la mia ", ignoreCase = true)
            .replace(" il suo ", " il mio ", ignoreCase = true)
            .replace(" in cui va ", " in cui vado ", ignoreCase = true)
            .replace(" in cui si sente ", " in cui mi sento ", ignoreCase = true)
            .replace(" mentre lavora ", " mentre lavoro ", ignoreCase = true)
            .replace(" mentre disegna ", " mentre disegno ", ignoreCase = true)
            .replace("Chiara aveva disegnato", "avevo disegnato", ignoreCase = true)
            .replace(" e salvò il servizio", " e ho salvato il servizio", ignoreCase = true)
            .replace(" suoi concerti", " miei concerti", ignoreCase = true)
        if (text.isNotEmpty()) text = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return text
    }
}
