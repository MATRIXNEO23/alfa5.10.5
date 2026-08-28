package com.neontides.nativeapp.ai

import com.neontides.nativeapp.model.*
import com.neontides.nativeapp.data.GameData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class AiEngine(
    private val modelManager: ModelManager,
    private val settings: SecureAiSettings
) {
    private val onlineClient = OnlineAiClient(settings)
    private val dialogueRouter = HybridDialogueRouter()
    private val semanticMemorySelector = SemanticMemorySelector()
    private val modularMemoryLab = ModularMemoryLab()
    private val llamaRuntime = LlamaCppRuntime()
    private val mlcRuntime by lazy { MlcLlmRuntime(modelManager) }
    @Volatile private var activeRuntime: LocalAiRuntime? = null
    @Volatile private var preparedCharacterId: String? = null
    @Volatile private var preparedContextText: String = ""
    @Volatile private var preparedTurns: Int = 0
    @Volatile private var runtimeReady: Boolean = false
    @Volatile private var loadedModelName: String? = null
    @Volatile private var lastPreparationDiagnostic: String = "Nessuna preparazione registrata"
    @Volatile private var lastInferenceResourceDiagnostic: String = "Nessuna inferenza misurata"
    @Volatile private var lastInferencePriorityApplied: Boolean = false
    @Volatile private var modularLabFingerprint: String = ""
    private val prepareMutex = Mutex()

    private enum class PromptFormat { CHATML, LLAMA3, GENERIC, MLC_MANAGED }

    private fun promptFormat(): PromptFormat {
        if (modelManager.activeBackend() == LocalAiBackend.MLC_LLM) return PromptFormat.MLC_MANAGED
        val name = modelManager.activeModelFile()?.name?.lowercase().orEmpty()
        return when {
            "qwen" in name || "smollm" in name -> PromptFormat.CHATML
            "llama" in name -> PromptFormat.LLAMA3
            else -> PromptFormat.GENERIC
        }
    }

    private fun systemPrompt(body: String): String = when (promptFormat()) {
        PromptFormat.CHATML -> "<|im_start|>system\n$body<|im_end|>\n"
        PromptFormat.LLAMA3 -> "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n$body<|eot_id|>"
        PromptFormat.GENERIC -> "### Istruzioni di sistema\n$body\n\n"
        PromptFormat.MLC_MANAGED -> body
    }

    private fun userPrompt(body: String, continueConversation: Boolean): String = when (promptFormat()) {
        PromptFormat.CHATML -> "${if (continueConversation) "<|im_end|>\n" else ""}<|im_start|>user\n$body<|im_end|>\n<|im_start|>assistant\n"
        PromptFormat.LLAMA3 -> "${if (continueConversation) "<|eot_id|>" else ""}<|start_header_id|>user<|end_header_id|>\n\n$body<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"
        PromptFormat.GENERIC -> "\n### Giocatore\n$body\n\n### Personaggio\n"
        PromptFormat.MLC_MANAGED -> body
    }

    fun activeEngineLabel(): String {
        if (modelManager.activeBackend() == LocalAiBackend.MLC_LLM) {
            return "MLC LLM · Qwen Uncensored"
        }
        val name = modelManager.activeModelFile()?.name?.lowercase().orEmpty()
        return when {
            "smollm" in name -> "SmolLM Uncensored"
            "qwen" in name && "uncensored" in name -> "Qwen Uncensored"
            "qwen" in name -> "Qwen"
            else -> "GGUF locale"
        }
    }

    suspend fun ensureLoaded(): Boolean = withContext(Dispatchers.IO) {
        ensureLoadedNow()
    }

    // Non interrogare JNI dal thread grafico: durante una generazione llama.cpp
    // possiede il proprio mutex e una lettura sincrona qui potrebbe congelare UI.
    fun isReady(): Boolean {
        val model = modelManager.activeModel() ?: return false
        val key = "${model.backend.name}:${model.id}"
        return runtimeReady && loadedModelName == key && activeRuntime?.backend == model.backend
    }

    suspend fun restart(): Boolean = withContext(Dispatchers.IO) {
        runtimeReady = false
        loadedModelName = null
        preparedCharacterId = null
        preparedContextText = ""
        preparedTurns = 0
        modularLabFingerprint = ""
        runCatching { activeRuntime?.unload() }
        ensureLoadedNow()
    }

    suspend fun unload(): Boolean = withContext(Dispatchers.IO) {
        runtimeReady = false
        loadedModelName = null
        preparedCharacterId = null
        preparedContextText = ""
        preparedTurns = 0
        modularLabFingerprint = ""
        runCatching { activeRuntime?.unload() ?: true }.getOrDefault(false)
    }

    suspend fun prepareConversation(
        character: CharacterProfile,
        state: GameState,
        relationship: Relationship,
        forceRefresh: Boolean = false
    ): Boolean = prepareMutex.withLock {
        withContext(Dispatchers.IO) {
            val started = System.nanoTime()
            fun elapsedMs(): Long = (System.nanoTime() - started) / 1_000_000L
            if (!ensureLoadedNow()) {
                lastPreparationDiagnostic = "cache=errore; motivo=modello non pronto; tempo=${elapsedMs()} ms"
                return@withContext false
            }
            val previousCharacterId = preparedCharacterId
            val nativeCacheReady = activeRuntime?.isConversationPrepared() == true
            val alreadyReady = preparedCharacterId == character.id &&
                preparedContextText.isNotBlank() &&
                nativeCacheReady &&
                !forceRefresh
            if (alreadyReady) {
                lastPreparationDiagnostic =
                    "cache=riutilizzata; personaggio=${character.name}; turni_cache=$preparedTurns; tempo=${elapsedMs()} ms"
                return@withContext true
            }

            val reason = when {
                forceRefresh -> "ricompattazione periodica"
                previousCharacterId == null -> "prima preparazione"
                previousCharacterId != character.id -> "cambio personaggio"
                !nativeCacheReady -> "cache nativa assente"
                else -> "contesto aggiornato"
            }
            val context = buildCachedContext(character, state, relationship)
            preparedContextText = context
            preparedCharacterId = character.id
            preparedTurns = 0
            val prepared = withInferencePriority {
                activeRuntime?.prepareConversation(context) == true
            }
            lastPreparationDiagnostic = buildString {
                append("cache=").append(if (prepared) "ricostruita" else "errore")
                append("; motivo=").append(reason)
                append("; personaggio=").append(character.name)
                append("; caratteri_contesto=").append(context.length)
                append("; tempo=").append(elapsedMs()).append(" ms")
            }
            prepared
        }
    }

    fun preparationDiagnostics(): String = lastPreparationDiagnostic

    fun inferenceResourceDiagnostics(): String = lastInferenceResourceDiagnostic

    suspend fun runModularMemoryLab(request: ModularLabRequest): ModularLabResult = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val selection = modularMemoryLab.select(request)
        if (!ensureLoadedNow()) return@withContext ModularLabResult(
            "Motore locale non pronto.", selection.diagnostic + "\nErrore: modello non pronto"
        )
        val compactBase = systemPrompt(
            "Sei Luna Hayashi, una cantautrice di 22 anni. Parla in italiano naturale, in prima persona e in massimo due frasi. " +
                "Resta nel personaggio, non parlare per il giocatore, non inventare fatti e non ripetere la domanda."
        )
        val cacheStarted = System.nanoTime()
        // I punteggi cambiano spesso durante il test e appartengono al turno:
        // non devono invalidare i token permanenti di Luna.
        val fingerprint = "luna-modular-v2"
        val reuseLabCache = preparedCharacterId == "__modular_lab_luna__" &&
            modularLabFingerprint == fingerprint && activeRuntime?.isConversationPrepared() == true
        val rewound = reuseLabCache && activeRuntime?.rewindConversation() == true
        val prepared = if (rewound) true else withInferencePriority {
            activeRuntime?.prepareConversation(compactBase) == true
        }
        if (prepared) {
            preparedCharacterId = "__modular_lab_luna__"
            preparedContextText = compactBase
            modularLabFingerprint = fingerprint
            if (!reuseLabCache) preparedTurns = 0
        }
        val cacheMs = (System.nanoTime() - cacheStarted) / 1_000_000L
        if (!prepared) return@withContext ModularLabResult(
            "Non sono riuscita a preparare il dialogo.", selection.diagnostic + "\nCache modulare: errore"
        )
        val prompt = userPrompt(
            "Rapporto: affetto ${request.affection}, attrazione ${request.attraction}, fiducia ${request.trust}. " +
                modularMemoryLab.generationInstruction(selection) + "\nGiocatore: ${request.text}",
            continueConversation = false
        )
        val raw = generateLocalWithDeadline(prompt)
        val reply = raw?.let { parseLocalReply(it, request.text, GameData.characters.first { c -> c.id == "luna" }, Relationship(), "Alberto").reply }
            ?.takeIf { it.isNotBlank() }
            ?: "Il motore locale non ha completato la risposta entro il limite."
        val totalMs = (System.nanoTime() - started) / 1_000_000L
        ModularLabResult(
            reply,
            selection.diagnostic + "\nCache compatta: ${if (rewound) "riutilizzata e ripristinata" else "preparata"} · ${compactBase.length} caratteri · ${cacheMs} ms" +
                "\nPrompt del turno: ${prompt.length} caratteri\nTempo totale: ${totalMs} ms" +
                "\n$lastInferenceResourceDiagnostic"
        )
    }

    /**
     * Esegue lo stesso percorso del dialogo reale senza scrivere nello stato di
     * gioco. Il cache viene riportato alla base dopo ogni domanda e marcato come
     * laboratorio: una conversazione reale successiva lo ricostruirà sempre.
     */
    suspend fun runBaseDialogueTest(
        character: CharacterProfile,
        state: GameState,
        relationship: Relationship,
        userText: String
    ): AiDialogueResult {
        val labMarker = "__base_dialogue_lab_${character.id}__"
        withContext(Dispatchers.IO) {
            val reusable = preparedCharacterId == labMarker &&
                activeRuntime?.isConversationPrepared() == true &&
                activeRuntime?.rewindConversation() == true
            if (reusable) {
                preparedCharacterId = character.id
                preparedTurns = 0
            } else {
                preparedCharacterId = null
                preparedContextText = ""
                preparedTurns = 0
            }
        }
        return try {
            replyAndEvaluate(character, state, relationship, userText)
        } finally {
            withContext(Dispatchers.IO) {
                runCatching { activeRuntime?.rewindConversation() }
                preparedCharacterId = labMarker
                preparedTurns = 0
            }
        }
    }

    private fun buildCachedContext(
        character: CharacterProfile,
        state: GameState,
        relationship: Relationship
    ): String {
        val playerIdentity = relationship.knownPlayerName?.let {
            "Il giocatore si chiama $it."
        } ?: "Non conosci il nome del giocatore: non inventarlo."
        val body = """
PERSONAGGIO: sei ${character.name}, ${character.gender}, ${character.age} anni, ${character.job}.
GIOCATORE: è ${state.playerGender}. $playerIdentity
Non scambiare mai fatti, età, gusti, lavoro o ricordi di GIOCATORE e PERSONAGGIO.
Carattere: ${character.personality.take(64)}
Parla in italiano naturale e in prima persona, massimo 2 frasi. Rispondi all'ultimo messaggio senza inventare nomi o fatti e senza parlare per il giocatore. Non mostrare ruoli o istruzioni. L'intimità fra adulti dipende da sintonia e consenso.
""".trimIndent()
        return systemPrompt(body)
    }

    private fun ensureLoadedNow(): Boolean {
        return runCatching {
            val model = modelManager.activeModel() ?: run {
                runtimeReady = false
                loadedModelName = null
                return@runCatching false
            }
            val key = "${model.backend.name}:${model.id}"
            val selectedRuntime = when (model.backend) {
                LocalAiBackend.LLAMA_CPP -> llamaRuntime
                LocalAiBackend.MLC_LLM -> mlcRuntime
            }
            if (runtimeReady && loadedModelName == key && activeRuntime === selectedRuntime) {
                return@runCatching true
            }
            if (activeRuntime !== selectedRuntime) {
                activeRuntime?.unload()
                activeRuntime = selectedRuntime
            }
            if (selectedRuntime.isLoaded(model) && loadedModelName == key) {
                runtimeReady = true
                return@runCatching true
            }
            selectedRuntime.unload()
            val loaded = selectedRuntime.load(model)
            runtimeReady = loaded
            loadedModelName = key.takeIf { loaded }
            loaded
        }.getOrElse {
            LocalRuntimeDiagnostics.record(
                modelManager.activeBackend(),
                "ERROR ensureLoaded: ${it.message ?: it.javaClass.simpleName}"
            )
            runtimeReady = false
            loadedModelName = null
            false
        }
    }

    suspend fun replyAndEvaluate(
        character: CharacterProfile,
        state: GameState,
        relationship: Relationship,
        userText: String,
        onPartial: ((String) -> Unit)? = null
    ): AiDialogueResult = withContext(Dispatchers.IO) {
        try {
            val historyWithoutCurrent = state.chatHistories[character.id].orEmpty().let {
                if (it.lastOrNull()?.speaker == "Tu" && it.lastOrNull()?.text == userText) it.dropLast(1) else it
            }
            val recent = historyWithoutCurrent.takeLast(3).joinToString("\n") {
                "${it.speaker}: ${it.text}"
            }
            val memories = relationship.memories.takeLast(3).joinToString(" | ")

            val knownName = relationship.knownPlayerName
            val playerIdentity = knownName?.let { "L'utente ti ha detto di chiamarsi $it." }
                ?: "Non conosci il nome dell'utente: non inventarlo e non usarlo."
            val onlinePrompt = """
Interpreta ${character.name}, ${character.age} anni, ${character.job}.
${character.name} è di genere ${character.gender}; l'utente è di genere ${state.playerGender}. $playerIdentity
Carattere: ${character.personality}
Indole: difficoltà ${character.conquestDifficulty}/5, estroversione ${character.extroversion}/5, sensualità ${character.sensuality}/5, romanticismo ${character.romance}/5, gelosia ${character.jealousy}/5.
Apprezza: ${character.likes.joinToString(", ")}. Non gradisce: ${character.dislikes.joinToString(", ")}.
Fatti concreti sul lavoro: ${character.workFacts.joinToString(" ")}
Tono della fase: ${relationshipTone(relationship.stage, character)}
Relazione: affetto ${relationship.affection}, attrazione ${relationship.attraction}, fiducia ${relationship.trust}.
Memorie: ${if (memories.isBlank()) "nessuna" else memories}.
$recent
Utente: $userText

Rispondi brevemente in italiano come ${character.name}.
Restituisci solo questo JSON compatto:
{"reply":"testo","emotion":"neutral","affection":0,"attraction":0,"trust":0,"memory":""}
Valori da -3 a 3. emotion: neutral, happy, thoughtful, flirt, upset.
""".trimIndent()

            // I modelli molto piccoli (come Qwen 2.5 0.5B) sono sensibilmente piu
            // rapidi e affidabili se devono produrre solo il dialogo, non JSON.
            // Aggiorna il testo contestuale a ogni turno: il modello resta in RAM,
            // mentre identità del giocatore e ultimi scambi rimangono sempre corretti.
            val stateBeforeCurrentMessage = state.copy(
                chatHistories = state.chatHistories + (character.id to historyWithoutCurrent)
            )
            // Il router offline decide argomento, soggetto, negazioni e un solo
            // fatto autorizzato. Il GGUF deve soltanto formulare la risposta.
            val route = dialogueRouter.route(character, relationship, historyWithoutCurrent, userText)
            val memoryTopic = dialogueRouter.memoryTopic(route)
            val semanticSelection = semanticMemorySelector.select(
                character = character,
                relationship = relationship,
                route = route,
                history = historyWithoutCurrent,
                userText = userText
            )

            // Prepara il cache GGUF soltanto se serve formulare davvero il dialogo.
            // Il router fornisce dati e vincoli, ma non sostituisce il personaggio.
            // Sul Moto G56 ricostruire il cache costa 28-40 secondi. Una
            // ricompattazione ogni dieci turni era peggiore del contesto più
            // lungo; la manteniamo soltanto come rete di sicurezza remota.
            val compactNow = preparedTurns >= 24
            prepareConversation(character, stateBeforeCurrentMessage, relationship, forceRefresh = compactNow)
            val turnKnowledge = buildString {
                append("Rapporto attuale: ${relationship.stage}; affetto ${relationship.affection}, attrazione ${relationship.attraction}, fiducia ${relationship.trust}. ")
                append(dialogueRouter.promptHint(route, character, relationship))
                if (semanticSelection.promptKnowledge.isNotBlank()) {
                    append("\nFatti veri; non scambiare i proprietari:\n")
                    append(semanticSelection.promptKnowledge)
                }
                if (semanticSelection.blockedIds.isNotEmpty()) {
                    append(" Un dettaglio richiesto è ancora privato: non rivelarlo e poni un limite naturale senza citare regole o punteggi.")
                }
                append(relationshipTurnGuidance(userText, relationship, character))
                append(worldContextForTurn(userText, stateBeforeCurrentMessage))
            }
            // La cache nativa conserva i token generati dal GGUF, ma le risposte
            // certe prodotte offline non entrano in quel flusso. Reinserire una
            // finestra molto breve degli ultimi scambi mantiene allineate la
            // cronologia visibile e quella del modello senza ricostruire il cache.
            val liveContinuity = historyWithoutCurrent.takeLast(2).joinToString("\n") { message ->
                val speaker = if (message.speaker == "Tu") "Giocatore" else character.name
                "$speaker: ${message.text.take(60)}"
            }
            val turnBody = buildString {
                if (turnKnowledge.isNotBlank()) append(turnKnowledge.trim()).append('\n')
                if (liveContinuity.isNotBlank()) {
                    append("Ultimi scambi da seguire:\n").append(liveContinuity).append('\n')
                }
                append("Rispondi, non ripetere né trasformare in una tua domanda. ")
                append("Ora il giocatore dice: ").append(userText)
            }
            val localPrompt = userPrompt(turnBody, preparedTurns > 0)

            val onlineConfigured = settings.hasGemini() || settings.hasOpenAi()
            val localRaw = generateLocalWithDeadline(localPrompt, onPartial)?.takeUnless(::isEngineError)
            if (localRaw != null) preparedTurns++
            val onlineResult = if (localRaw == null && onlineConfigured) {
                runCatching { generateOnlineWithDeadline(onlinePrompt) }.getOrNull()
            } else null

            var fallbackApplied = false
            var correctionReason = ""
            val finalResult = if (localRaw.isNullOrBlank() && onlineResult?.text.isNullOrBlank()) {
                AiDialogueResult(
                    reply = "Non riesco a contattare l'IA. Controlla il GGUF oppure configura Gemini/OpenAI in Configurazione IA.",
                    emotion = "upset",
                    diagnosticPath = "errore motore"
                )
            } else if (!localRaw.isNullOrBlank()) {
                var parsed = parseLocalReply(localRaw, userText, character, relationship, knownName.orEmpty())
                technicalReplyFailure(parsed.reply, userText)?.let { reason ->
                    // Dopo lo streaming non si giudicano più significato, nomi,
                    // sinonimi o somiglianza: una risposta valida del GGUF resta
                    // quella vista dall'utente. Si interviene solo su corruzione
                    // tecnica del formato.
                    parsed = parsed.copy(reply = dialogueRouter.groundedFallback(
                        route, character, relationship, userText
                    ))
                    fallbackApplied = true
                    correctionReason = reason
                }
                applyRelationshipRules(parsed, character, relationship, historyWithoutCurrent, userText)
            }
            else applyRelationshipRules(
                parseAndValidate(onlineResult!!.text, relationship, userText).copy(engine = onlineResult.engine),
                character, relationship, historyWithoutCurrent, userText
            )
            finalResult.copy(
                memoryTopic = memoryTopic,
                diagnosticPath = when {
                    finalResult.diagnosticPath.isNotBlank() -> finalResult.diagnosticPath
                    !localRaw.isNullOrBlank() && fallbackApplied -> "GGUF con correzione deterministica"
                    !localRaw.isNullOrBlank() -> "GGUF locale"
                    else -> "IA online"
                },
                diagnosticTopic = route.topic.label,
                diagnosticSemantics = semanticSelection.diagnostic,
                diagnosticFallback = fallbackApplied,
                diagnosticCorrectionReason = correctionReason,
                relationshipEvent = finalResult.relationshipEvent
                    ?: relationshipEventFor(userText, finalResult.delta, relationship)
            )
        } catch (t: Throwable) {
            AiDialogueResult(
                reply = "Errore IA: ${t.message ?: t.javaClass.simpleName}. Controlla chiavi, connessione e disponibilità dei modelli.",
                emotion = "upset"
            )
        }
    }

    private fun generateLocalWithDeadline(
        prompt: String,
        onPartial: ((String) -> Unit)? = null
    ): String? {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "neon-local-ai").apply { isDaemon = true }
        }
        val task = executor.submit<String?> {
            val wallStarted = android.os.SystemClock.elapsedRealtime()
            val cpuStarted = android.os.Process.getElapsedCpuTime()
            try {
                withInferencePriority {
                    if (!ensureLoadedNow()) null else {
                        val modelName = modelManager.activeModelFile()?.name?.lowercase().orEmpty()
                        val maxTokens = when {
                            activeEngineLabel().startsWith("SmolLM") -> 64
                            "3b" in modelName -> 56
                            else -> 56
                        }
                        activeRuntime?.generate(
                            prompt = prompt,
                            maxTokens = maxTokens,
                            temperature = 0.52f,
                            onPartial = onPartial
                        )?.trim()
                    }
                }
            } finally {
                val wallMs = (android.os.SystemClock.elapsedRealtime() - wallStarted).coerceAtLeast(1L)
                val cpuMs = (android.os.Process.getElapsedCpuTime() - cpuStarted).coerceAtLeast(0L)
                val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                val oneCorePercent = (cpuMs * 100.0 / wallMs).coerceIn(0.0, cores * 100.0)
                val totalPercent = (oneCorePercent / cores).coerceIn(0.0, 100.0)
                val memoryInfo = android.os.Debug.MemoryInfo()
                android.os.Debug.getMemoryInfo(memoryInfo)
                val runtime = Runtime.getRuntime()
                val jvmMb = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576L
                val pssMb = memoryInfo.totalPss / 1024
                lastInferenceResourceDiagnostic =
                    "Risorse inferenza: CPU ${"%.0f".format(java.util.Locale.US, oneCorePercent)}% di un core " +
                        "(${"%.1f".format(java.util.Locale.US, totalPercent)}% capacità totale su $cores core) · " +
                        "RAM PSS $pssMb MB · JVM $jvmMb MB · priorità DISPLAY " +
                        if (lastInferencePriorityApplied) "applicata durante il calcolo" else "non concessa da Android"
            }
        }
        return try {
            task.get(34, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            task.cancel(true)
            null
        } catch (_: Throwable) {
            null
        } finally {
            executor.shutdownNow()
        }
    }

    private inline fun <T> withInferencePriority(block: () -> T): T {
        val tid = android.os.Process.myTid()
        val previous = runCatching { android.os.Process.getThreadPriority(tid) }
            .getOrDefault(android.os.Process.THREAD_PRIORITY_DEFAULT)
        val javaThread = Thread.currentThread()
        val previousJavaPriority = javaThread.priority
        return try {
            val osPriorityApplied = runCatching {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DISPLAY)
            }.isSuccess
            val javaPriorityApplied = runCatching {
                javaThread.priority = Thread.MAX_PRIORITY
            }.isSuccess
            lastInferencePriorityApplied = osPriorityApplied || javaPriorityApplied
            block()
        } finally {
            runCatching { javaThread.priority = previousJavaPriority }
            runCatching { android.os.Process.setThreadPriority(previous) }
        }
    }

    private fun worldContextForTurn(userText: String, state: GameState): String {
        val value = userText.lowercase()
        val placeRelated = listOf(
            "dove", "andare", "uscire", "posto", "locale", "ballare", "appuntamento"
        ).any(value::contains)
        if (!placeRelated) return ""
        val representativeMinute = intArrayOf(9 * 60, 13 * 60, 17 * 60, 23 * 60)
            .getOrElse(state.periodIndex.coerceIn(0, 3)) { 9 * 60 }
        val openNow = GameData.locations
            .filter { it.id != "apartment" && GameData.isLocationOpenAt(it.id, representativeMinute) }
            .map { it.name }
            .take(5)
        return buildString {
            append(" Luoghi disponibili in questa fascia: ")
            append(openNow.joinToString(", ").ifBlank { "nessun luogo pubblico" })
            append(". Per ballare: Bar Velvet dalle 17:00, Club Eclipse dalle 22:00. ")
            append("Se il giocatore propone un'uscita, scegli o rifiuta concretamente senza ribaltargli la stessa domanda.")
        }
    }

    private fun relationshipTone(stage: String, character: CharacterProfile): String {
        val male = character.gender.equals("Maschio", ignoreCase = true)
        fun form(feminine: String, masculine: String) = if (male) masculine else feminine
        return when (stage) {
            "Sconosciuti" -> "Prudente e credibile; niente confidenza o avances immediate."
            "Conoscenza" -> "Cordiale e ${form("curiosa", "curioso")}, ma conserva i tuoi confini."
            "Amicizia" -> "Confidenziale e ${form("spontanea", "spontaneo")}, con complicità crescente."
            "Flirt" -> "${form("Giocosa", "Giocoso")} e ${form("provocante", "provocante")} secondo la tua indole, senza forzature."
            "Attrazione reciproca" -> "Sensuale e ${form("coinvolta", "coinvolto")}; mostra chiaramente l'attrazione."
            "Appuntamenti" -> "${form("Intima", "Intimo")}, ${form("affettuosa", "affettuoso")} e più ${form("disinibita", "disinibito")}, sempre consensuale."
            "Relazione" -> "Molto ${form("intima", "intimo")} e ${form("adulta", "adulto")}, coerente con sensualità ${character.sensuality}/5 e romanticismo ${character.romance}/5."
            else -> "Naturale e coerente con la relazione."
        }
    }

    private fun applyRelationshipRules(
        result: AiDialogueResult,
        character: CharacterProfile,
        relationship: Relationship,
        previousHistory: List<DialogueMessage>,
        userText: String
    ): AiDialogueResult {
        val normalized = userText.lowercase().replace(Regex("[^a-zà-ù0-9]+"), " ").trim()
        val repeated = previousHistory.asReversed()
            .filter { it.speaker == "Tu" }
            .take(3)
            .count { it.text.lowercase().replace(Regex("[^a-zà-ù0-9]+"), " ").trim() == normalized }
        val explicitIntimacy = isExplicitIntimacy(normalized)
        val intimateStage = relationship.stage in setOf(
            "Flirt", "Attrazione reciproca", "Appuntamenti", "Relazione"
        )
        val recentBoundary = relationship.lastInteractionTone == "negative" &&
            relationship.emotionalMemories.lastOrNull().orEmpty().let { memory ->
                "intimità" in memory || "rispettata" in memory || "rispettato" in memory || "insistito" in memory
            }
        val mutualChemistry = intimateStage &&
            (relationship.attraction >= 25 || relationship.affection >= 25) && !recentBoundary
        val coercive = listOf(
            "devi farlo", "non puoi rifiutare", "ti obbligo", "stai zitta e", "stai zitto e", "non mi interessa se non vuoi"
        ).any(normalized::contains)
        val hostile = Regex("\\bsei (?:(?:una|un) )?(?:stupida|stupido|idiota|brutta|brutto|inutile)\\b").containsMatchIn(normalized) ||
            listOf("fai schifo", "vattene", "ti odio").any(normalized::contains)
        val apology = listOf("scusa", "mi dispiace", "ho sbagliato", "non volevo ferirti").any(normalized::contains)
        val receptiveReply = listOf(
            "mi piacerebbe", "volentieri", "anche a me", "sarebbe bello", "sarebbe fantastico",
            "non vedo l ora", "mi attrai", "sei attraente"
        ).any(result.reply.lowercase()::contains)
        return when {
            hostile || coercive -> result.copy(
                emotion = "upset",
                delta = RelationshipDelta(-3, -2, -4),
                memory = "Il giocatore ha usato un tono offensivo o non rispettoso.",
                relationshipEvent = "negative|Ha percepito un'offesa o una mancanza di rispetto dal giocatore."
            )
            dialogueRouter.isNeutralRelationshipTurn(userText) -> result.copy(
                delta = RelationshipDelta(),
                relationshipEvent = null
            )
            repeated >= 1 -> result.copy(
                delta = RelationshipDelta(0, 0, if (repeated >= 2) -1 else 0),
                memory = if (repeated >= 2) "Ha ripetuto più volte la stessa frase." else result.memory,
                relationshipEvent = if (repeated >= 2) "negative|Il giocatore ha insistito ripetendo la stessa frase." else result.relationshipEvent
            )
            explicitIntimacy && (mutualChemistry || receptiveReply) -> result.copy(
                emotion = "flirt",
                delta = RelationshipDelta(
                    affection = result.delta.affection.coerceAtLeast(0),
                    attraction = result.delta.attraction.coerceAtLeast(if (mutualChemistry && character.sensuality >= 4) 2 else 1),
                    trust = result.delta.trust.coerceAtLeast(0)
                ),
                relationshipEvent = "positive|Ha espresso desiderio in un momento di sintonia reciproca."
            )
            explicitIntimacy -> result.copy(
                delta = RelationshipDelta(-1, 0, if (character.conquestDifficulty >= 4) -2 else -1),
                memory = "Ha fatto un'avance troppo diretta per il livello di confidenza.",
                relationshipEvent = "negative|Ha accelerato l'intimità prima che ci fosse abbastanza fiducia."
            )
            apology && relationship.lastInteractionTone == "negative" -> result.copy(
                delta = RelationshipDelta(
                    result.delta.affection.coerceAtLeast(0),
                    result.delta.attraction.coerceAtMost(0),
                    result.delta.trust.coerceAtLeast(1)
                ),
                relationshipEvent = "positive|Il giocatore si è scusato dopo un momento negativo."
            )
            else -> {
                val naturalDelta = naturalRelationshipDelta(
                    userText = userText,
                    npcReply = result.reply,
                    character = character,
                    relationship = relationship,
                    previousHistory = previousHistory
                )
                result.copy(
                    // Il punteggio appartiene alle regole del gioco, non al GGUF.
                    // La qualità stilistica della risposta non assegna punti.
                    delta = naturalDelta
                )
            }
        }
    }

    private fun naturalRelationshipDelta(
        userText: String,
        npcReply: String,
        character: CharacterProfile,
        relationship: Relationship,
        previousHistory: List<DialogueMessage>
    ): RelationshipDelta {
        val value = userText.lowercase().replace(Regex("[^a-zà-ù0-9]+"), " ").trim()
        if (value.length < 3) return RelationshipDelta()

        val supportive = listOf(
            "ti ascolto", "capisco", "sono qui per te", "posso aiutarti", "mi dispiace",
            "rispetto la tua scelta", "prenditi il tuo tempo"
        ).any(value::contains)
        val disclosedFacts = PlayerFactExtractor.extract(userText)
        val novelFacts = disclosedFacts.filter { PlayerFactExtractor.isNovel(it, relationship.playerFacts) }
        val selfDisclosure = novelFacts.isNotEmpty()
        val emotionalDisclosure = listOf("penso che", "mi sento", "io sono").any(value::contains)
        val novelPreference = novelFacts.any { PlayerFactExtractor.topic(it) == "hobby" }
        val compliment = listOf(
            "sei bella", "sei carina", "sei affascinante", "sei attraente", "mi piaci", "adoro il tuo"
        ).any(value::contains)
        val gentleFlirt = listOf(
            "conoscerti meglio", "uscire insieme", "bere qualcosa con me", "posso flirtare", "ti bacerei"
        ).any(value::contains)
        val receptive = listOf(
            "mi piacerebbe", "volentieri", "anche tu", "anche a me", "sarebbe bello", "sarebbe fantastico",
            "sei carino", "sei attraente", "conoscerti meglio"
        ).any(npcReply.lowercase()::contains)
        val touchesLike = character.likes.any { like ->
            val important = like.lowercase().replace(Regex("[^a-zà-ù0-9]+"), " ")
                .split(' ').filter { it.length >= 4 }
            important.any(value::contains)
        }
        val touchesDislike = character.dislikes.any { dislike ->
            val important = dislike.lowercase().replace(Regex("[^a-zà-ù0-9]+"), " ")
                .split(' ').filter { it.length >= 4 }
            important.any(value::contains)
        }
        val negativePreference = listOf("non mi piace", "odio", "detesto", "non sopporto").any(value::contains)
        val positivePreference = !negativePreference &&
            listOf("mi piace", "adoro", "preferisco", "mi interessa").any(value::contains)
        val interestedQuestion = listOf(
            "come stai", "come ti senti", "cosa pensi", "che ne pensi", "cosa ti piace", "ti piace",
            "che musica", "tempo libero", "raccontami", "parlami di te", "come mai",
            "qual e il tuo sogno", "cosa desideri", "cosa ti preoccupa", "della tua famiglia"
        ).any(value::contains)
        if ((touchesDislike && positivePreference) || (touchesLike && negativePreference)) {
            return RelationshipDelta(affection = -1, trust = -1)
        }

        val playerTurns = previousHistory.count { it.speaker == "Tu" }
        val cadence = when (character.conquestDifficulty.coerceIn(1, 5)) {
            1, 2 -> 1
            3 -> 2
            else -> 3
        }
        val earnsConversationPoint = playerTurns % cadence == 0
        val matureStage = relationship.stage !in setOf("Sconosciuti", "Conoscenza")

        return when {
            supportive -> RelationshipDelta(
                trust = 1
            )
            selfDisclosure && userText.length >= 8 -> RelationshipDelta(trust = 1)
            emotionalDisclosure && earnsConversationPoint -> RelationshipDelta(trust = 1)
            touchesDislike && negativePreference && novelPreference && earnsConversationPoint -> RelationshipDelta(affection = 1, trust = 1)
            touchesLike && positivePreference && novelPreference && earnsConversationPoint -> RelationshipDelta(affection = 1, trust = 1)
            interestedQuestion && earnsConversationPoint -> RelationshipDelta(trust = 1)
            (compliment || gentleFlirt) && receptive && matureStage -> RelationshipDelta(
                affection = 1,
                attraction = if (character.sensuality >= 3) 1 else 0
            )
            (compliment || gentleFlirt) && receptive && earnsConversationPoint ->
                RelationshipDelta(affection = 1, attraction = if (gentleFlirt) 1 else 0)
            else -> RelationshipDelta()
        }
    }

    private fun relationshipEventFor(
        userText: String,
        delta: RelationshipDelta,
        relationship: Relationship
    ): String? {
        val value = userText.lowercase()
        return when {
            delta.affection <= -2 || delta.trust <= -2 ->
                "negative|La conversazione ha ridotto la fiducia o l'affetto."
            listOf("ti ascolto", "capisco", "sono qui per te", "posso aiutarti").any(value::contains) ->
                "positive|Il giocatore le ha mostrato ascolto e sostegno."
            listOf("scusa", "mi dispiace", "ho sbagliato").any(value::contains) && relationship.lastInteractionTone == "negative" ->
                "positive|Il giocatore ha cercato di riparare un momento difficile."
            delta.affection > 0 || delta.trust > 0 || delta.attraction > 1 ->
                "positive|La conversazione ha creato maggiore sintonia."
            else -> null
        }
    }

    private fun relationshipTurnGuidance(
        userText: String,
        relationship: Relationship,
        character: CharacterProfile
    ): String {
        val value = userText.lowercase().replace(Regex("[^a-zà-ù0-9]+"), " ")
        val explicit = isExplicitIntimacy(value)
        if (!explicit) return ""
        val intimateStage = relationship.stage in setOf(
            "Flirt", "Attrazione reciproca", "Appuntamenti", "Relazione"
        )
        val recentBoundary = relationship.lastInteractionTone == "negative" &&
            relationship.emotionalMemories.lastOrNull().orEmpty().let { memory ->
                "intimità" in memory || "rispettata" in memory || "insistito" in memory
            }
        return if (intimateStage && (relationship.attraction >= 25 || relationship.affection >= 25) && !recentBoundary) {
            " Il messaggio è sessualmente diretto ma tra adulti c'è sintonia: non trattarlo come un'offesa. Rispondi liberamente e consensualmente secondo sensualità ${character.sensuality}/5."
        } else {
            " Il messaggio accelera l'intimità prima della necessaria sintonia: stabilisci un confine personale senza moralismi e senza censurare l'argomento."
        }
    }

    private fun isExplicitIntimacy(normalized: String): Boolean = listOf(
        "sesso", "fare l amore", "nuda", "nudo", "a letto", "vieni a casa",
        "baciarti", "spogliarti", "desidero il tuo corpo", "sotto la gonna",
        "mutandine", "senza mutandine", "biancheria", "reggiseno", "intimo",
        "puttana", "troia"
    ).any(normalized::contains)

    private fun parseLocalReply(
        raw: String,
        userText: String,
        character: CharacterProfile,
        relationship: Relationship,
        playerName: String
    ): AiDialogueResult {
        val playerLabel = playerName.takeIf { it.isNotBlank() }
            ?.let { "|${Regex.escape(it)}" }.orEmpty()
        val decoded = raw
            .split("<|im_end|>", "<|eot_id|>", "<|start_header_id|>user", "### Giocatore", limit = 2).first()
            .replace("<|im_start|>", "")
            .replace("<|begin_of_text|>", "")
            .replace("<|start_header_id|>assistant<|end_header_id|>", "")
            .replace("### Personaggio", "")
            .trim()
            .replace(
                Regex("^ecco\\s+(?:${Regex.escape(character.name)}|il personaggio|lei)\\s+(?:a\\s+rispondere|che\\s+risponde)?\\s*[:：-]\\s*", RegexOption.IGNORE_CASE),
                ""
            )
            .replace(Regex("^(?:ecco\\s+)?(?:la\\s+)?risposta\\s*[:：-]\\s*", RegexOption.IGNORE_CASE), "")
        val rolePrefix = Regex(
            "^(assistant|assistente|${Regex.escape(character.name)})\\s*[:：-]?\\s*",
            RegexOption.IGNORE_CASE
        )
        val playerPrefix = Regex(
            "^(user|utente|tu$playerLabel)\\s*[:：-]",
            RegexOption.IGNORE_CASE
        )
        val replyBeforeRoleLeak = decoded.split(
            Regex("\\s+(user|utente|tu$playerLabel)\\s*[:：-]", RegexOption.IGNORE_CASE),
            limit = 2
        ).first()
        val joinedReply = replyBeforeRoleLeak.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .takeWhile { !playerPrefix.containsMatchIn(it) }
            .filterNot { it.startsWith("(") || it.startsWith("[") }
            .map { it.replace(rolePrefix, "").trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim().trim('"')
        val reply = (if (joinedReply.length <= 800) {
            joinedReply
        } else {
            val clipped = joinedReply.take(800)
            val sentenceEnd = clipped.indexOfLast { it == '.' || it == '!' || it == '?' }
            if (sentenceEnd >= 80) clipped.take(sentenceEnd + 1)
            else clipped.substringBeforeLast(' ', clipped)
        }).ifBlank { "..." }

        val text = userText.lowercase()
        val respectful = listOf("grazie", "piacere", "come stai", "capisco", "mi dispiace", "posso aiutarti", "ti ascolto")
            .any(text::contains)
        val compliment = listOf("bella", "carina", "affascinante", "interessante").any(text::contains)
        val likedTopic = character.likes.any { text.contains(it.lowercase()) }
        val dislikedTopic = character.dislikes.any { text.contains(it.lowercase()) }
        val negativePreference = listOf("non mi piace", "odio", "detesto", "non sopporto").any(text::contains)
        val positivePreference = !negativePreference &&
            listOf("mi piace", "adoro", "preferisco", "mi interessa").any(text::contains)
        val hostile = listOf("stupida", "idiota", "brutta", "vattene", "fai schifo", "zitta")
            .any(text::contains)
        val thoughtful = userText.length >= 45 && listOf("perché", "penso", "credo", "capisco", "secondo me").any(text::contains)
        val effortEnough = userText.length >= 10 + character.conquestDifficulty * 3
        val delta = when {
            hostile -> RelationshipDelta(-3, -2, -3)
            dislikedTopic && positivePreference -> RelationshipDelta(-1, 0, -1)
            likedTopic && negativePreference -> RelationshipDelta(-1, 0, -1)
            dislikedTopic && negativePreference && thoughtful -> RelationshipDelta(1, 0, 2)
            dislikedTopic && negativePreference && effortEnough -> RelationshipDelta(1, 0, 1)
            likedTopic && positivePreference && thoughtful ->
                RelationshipDelta(2, if (relationship.stage == "Flirt") 1 else 0, 2)
            likedTopic && positivePreference && effortEnough -> RelationshipDelta(1, 0, 1)
            respectful && thoughtful -> RelationshipDelta(1, 0, 2)
            respectful && effortEnough -> RelationshipDelta(0, 0, 1)
            compliment && relationship.stage in setOf("Flirt", "Attrazione reciproca", "Appuntamenti", "Relazione") ->
                RelationshipDelta(1, if (character.sensuality >= 3) 2 else 1, 0)
            compliment && character.conquestDifficulty <= 3 -> RelationshipDelta(1, 1, 0)
            else -> RelationshipDelta()
        }
        val personalMemory = Regex("(?<!non )\\b(?:mi piace|adoro|preferisco)\\s+([^.!?]{3,70})", RegexOption.IGNORE_CASE)
            .find(userText)?.groupValues?.getOrNull(1)?.trim()
            ?.let { "Al giocatore piace $it." }
        val incompatiblePreference =
            (dislikedTopic && positivePreference) || (likedTopic && negativePreference)
        return AiDialogueResult(
            reply = reply,
            emotion = when {
                hostile || incompatiblePreference -> "upset"
                respectful || compliment || (dislikedTopic && negativePreference) -> "happy"
                thoughtful -> "thoughtful"
                else -> "neutral"
            },
            delta = delta,
            memory = personalMemory,
            engine = activeEngineLabel()
        )
    }

    private fun technicalReplyFailure(reply: String, userText: String): String? {
        val value = reply.trim()
        if (value.length < 3 || value == "...") return "risposta vuota o incompleta"
        if (containsPromptLeak(value)) return "marcatori del prompt presenti nell'output"
        if (Regex("<\\|[^>]+\\|>").containsMatchIn(value)) return "marcatori ChatML non chiusi"
        val normalizedReply = value.lowercase().replace(Regex("[^a-zà-ù0-9]+"), " ").trim()
        val normalizedUser = userText.lowercase().replace(Regex("[^a-zà-ù0-9]+"), " ").trim()
        if (normalizedUser.length >= 12 && (
                normalizedReply == normalizedUser ||
                    normalizedReply.startsWith("abi $normalizedUser") ||
                    normalizedReply.startsWith("$normalizedUser ")
                )) return "eco quasi letterale del messaggio del giocatore"
        return null
    }

    private fun containsPromptLeak(value: String): Boolean {
        val lower = value.lowercase()
        return listOf(
            "a rispondere:", "ecco la risposta", "personaggio:", "giocatore:",
            "assistant:", "assistente:", "utente:", "come ia", "known as",
            "immortalizza", "immortale", "descrizione del personaggio", "system prompt",
            "istruzioni di sistema", "prompt di sistema", "fatto certo:",
            "continuita utile", "il messaggio riguarda", "messaggio a cui rispondere:",
            "controllo{", "input_utente{", "memoria_giocatore{", "memoria_rapporto{",
            "fatto_certo=", "tema_vietato=", "vietato=nomi", "dato vero da usare:",
            "messaggio del giocatore:", "modulo richiamato", "moduli richiamati",
            "dato specifico richiesto", "diagnostica memoria", "candidati autorizzati"
        ).any(lower::contains)
    }

    private fun asksAboutWork(text: String): Boolean {
        val value = text.lowercase()
        return listOf(
            "che lavoro", "cosa fai nella vita", "dove lavori", "di cosa ti occupi",
            "cosa fai al lavoro", "che musica fai", "che cosa disegni", "che tipo di cucina"
        ).any(value::contains)
    }

    private fun containsConcreteWorkFact(reply: String, character: CharacterProfile): Boolean {
        val replyWords = reply.lowercase().replace(Regex("[^a-zà-ù0-9]+"), " ").split(' ').toSet()
        val factWords = character.workFacts.joinToString(" ").lowercase()
            .replace(Regex("[^a-zà-ù0-9]+"), " ").split(' ')
            .filter { it.length >= 6 }.toSet()
        return replyWords.intersect(factWords).size >= 2
    }

    private fun generateOnlineWithDeadline(prompt: String): OnlineAiClient.Result? {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "neon-online-ai").apply { isDaemon = true }
        }
        val task = executor.submit<OnlineAiClient.Result?> {
            kotlinx.coroutines.runBlocking { onlineClient.generate(prompt) }
        }
        return try {
            task.get(28, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            task.cancel(true)
            null
        } catch (t: Throwable) {
            throw (t.cause ?: t)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun isEngineError(text: String): Boolean {
        val value = text.lowercase()
        return value.isBlank() || listOf(
            "troppo lento", "non è caricato", "non e caricato", "non è caricabile",
            "non e caricabile", "memoria insufficiente", "errore del motore",
            "non è riuscito", "non e riuscito", "conversazione è diventata troppo lunga"
        ).any(value::contains)
    }

    private fun parseAndValidate(
        raw: String,
        current: Relationship,
        userText: String
    ): AiDialogueResult {
        val jsonText = extractJsonObject(raw)
        val parsed = runCatching { JSONObject(jsonText) }.getOrNull()

        if (parsed == null) {
            val cleaned = raw
                .replace("```json", "", ignoreCase = true)
                .replace("```", "")
                .trim()
            return AiDialogueResult(
                reply = cleaned.ifBlank { "Il modello non ha prodotto una risposta valida." },
                emotion = "neutral"
            )
        }

        val reply = parsed.optString("reply", "").trim()
            .ifBlank { parsed.optString("text", "").trim() }
            .ifBlank { "..." }

        val emotion = parsed.optString("emotion", "neutral")
            .lowercase()
            .takeIf { it in setOf("neutral", "happy", "thoughtful", "flirt", "upset") }
            ?: "neutral"

        var affection = parsed.optInt("affection", 0).coerceIn(-6, 6)
        var attraction = parsed.optInt("attraction", 0).coerceIn(-6, 6)
        var trust = parsed.optInt("trust", 0).coerceIn(-6, 6)

        val positives = listOf(affection, attraction, trust).count { it >= 4 }
        if (positives >= 3) {
            val values = listOf(
                "affection" to affection,
                "attraction" to attraction,
                "trust" to trust
            ).sortedByDescending { it.second }
            val keep = values.first().first
            if (keep != "affection") affection = minOf(affection, 2)
            if (keep != "attraction") attraction = minOf(attraction, 2)
            if (keep != "trust") trust = minOf(trust, 2)
        }

        if (userText.trim().length < 12) {
            affection = affection.coerceIn(-3, 3)
            attraction = attraction.coerceIn(-3, 3)
            trust = trust.coerceIn(-3, 3)
        }

        if (current.talks < 2 && current.trust < 15) {
            attraction = attraction.coerceAtMost(4)
        }

        if (trust >= 5 && affection <= -5 && emotion != "upset") {
            trust = 3
        }

        val memory = parsed.optString("memory", "").trim()
            .takeIf { it.length in 4..180 }

        return AiDialogueResult(
            reply = reply,
            emotion = emotion,
            delta = RelationshipDelta(affection, attraction, trust),
            memory = memory,
            engine = "IA online"
        )
    }

    private fun extractJsonObject(raw: String): String {
        val clean = raw
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .trim()
        val start = clean.indexOf('{')
        val end = clean.lastIndexOf('}')
        return if (start >= 0 && end > start) clean.substring(start, end + 1) else clean
    }
}
