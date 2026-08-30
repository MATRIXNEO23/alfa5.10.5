from pathlib import Path

AI = Path("app/src/main/java/com/neontides/nativeapp/ai/AiEngine.kt")
GRADLE = Path("app/build.gradle.kts")

ai = AI.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global ai
    if ai.count(old) != 1:
        raise RuntimeError(f"{label}: atteso 1 match, trovati {ai.count(old)}")
    ai = ai.replace(old, new, 1)


# 1) Tutte le nuove correzioni sono opt-in e vengono abilitate solo dal laboratorio.
replace_once(
'''    suspend fun replyAndEvaluate(
        character: CharacterProfile,
        state: GameState,
        relationship: Relationship,
        userText: String,
        onPartial: ((String) -> Unit)? = null
    ): AiDialogueResult = withContext(Dispatchers.IO) {''',
'''    suspend fun replyAndEvaluate(
        character: CharacterProfile,
        state: GameState,
        relationship: Relationship,
        userText: String,
        onPartial: ((String) -> Unit)? = null,
        labEngineAuditFixes: Boolean = false
    ): AiDialogueResult = withContext(Dispatchers.IO) {''',
"firma replyAndEvaluate"
)

replace_once(
'''            replyAndEvaluate(character, state, relationship, userText, onPartial)''',
'''            replyAndEvaluate(
                character,
                state,
                relationship,
                userText,
                onPartial,
                labEngineAuditFixes = true
            )''',
"chiamata laboratorio"
)

# 2) Nel laboratorio correggiamo solo la classificazione prima della selezione memoria.
replace_once(
'''            val route = dialogueRouter.route(character, relationship, historyWithoutCurrent, userText)
            val memoryTopic = dialogueRouter.memoryTopic(route)
            val semanticSelection = semanticMemorySelector.select(
                character = character,
                relationship = relationship,
                route = route,
                history = historyWithoutCurrent,
                userText = userText
            )''',
'''            val baseRoute = dialogueRouter.route(character, relationship, historyWithoutCurrent, userText)
            val labIntimacy = labEngineAuditFixes && isLabExplicitIntimacy(userText)
            val route = if (labEngineAuditFixes) labAdjustedRoute(baseRoute, userText, labIntimacy) else baseRoute
            val memoryTopic = dialogueRouter.memoryTopic(route)
            val rawSemanticSelection = semanticMemorySelector.select(
                character = character,
                relationship = relationship,
                route = route,
                history = historyWithoutCurrent,
                userText = userText
            )
            val semanticSelection = if (labEngineAuditFixes) {
                labFilterSemanticSelection(rawSemanticSelection, route, labIntimacy)
            } else rawSemanticSelection''',
"router e selettore"
)

# 3) L'eco potenzialmente naturale non viene più sostituita nel laboratorio.
replace_once(
'''                technicalReplyFailure(parsed.reply, userText)?.let { reason ->
                    // Dopo lo streaming non si giudicano più significato, nomi,
                    // sinonimi o somiglianza: una risposta valida del GGUF resta
                    // quella vista dall'utente. Si interviene solo su corruzione
                    // tecnica del formato.
                    parsed = parsed.copy(reply = dialogueRouter.groundedFallback(
                        route, character, relationship, userText
                    ))
                    fallbackApplied = true
                    correctionReason = reason
                }''',
'''                technicalReplyFailure(parsed.reply, userText)?.let { reason ->
                    val harmlessLabEcho = labEngineAuditFixes &&
                        reason == "eco quasi letterale del messaggio del giocatore"
                    if (!harmlessLabEcho) {
                        parsed = parsed.copy(reply = dialogueRouter.groundedFallback(
                            route, character, relationship, userText
                        ))
                        fallbackApplied = true
                        correctionReason = reason
                    }
                }''',
"fallback tecnico"
)

# 4) La diagnostica classifica refusal/role break senza cambiare la risposta del GGUF.
replace_once(
'''                diagnosticSemantics = semanticSelection.diagnostic,
                diagnosticFallback = fallbackApplied,''',
'''                diagnosticSemantics = if (labEngineAuditFixes) {
                    semanticSelection.diagnostic + "; risposta=" + labReplyClass(
                        localRaw = localRaw,
                        finalReply = finalResult.reply,
                        userText = userText
                    )
                } else semanticSelection.diagnostic,
                diagnosticFallback = fallbackApplied,''',
"diagnostica risposta"
)

# 5) Helper laboratorio. Nessun testo viene aggiunto al prompt permanente.
anchor = '''    private fun relationshipTurnGuidance(
        userText: String,
        relationship: Relationship,
        character: CharacterProfile
    ): String {'''
helpers = '''    private fun labAdjustedRoute(
        route: HybridDialogueRouter.Route,
        userText: String,
        explicitIntimacy: Boolean
    ): HybridDialogueRouter.Route {
        val raw = userText.trim()
        if (explicitIntimacy) {
            val normalized = normalizeLabText(raw)
            val sharedProposal = listOf(
                "facciamo l amore", "facciamo sesso", "andiamo a letto", "dormiamo insieme",
                "scopiamo", "trombiamo", "lo facciamo", "vieni a letto con me"
            ).any(normalized::contains)
            val target = if (sharedProposal) HybridDialogueRouter.Target.BOTH else HybridDialogueRouter.Target.CHARACTER
            val question = if (raw.endsWith("?")) HybridDialogueRouter.Question.YES_NO else route.question
            return route.copy(
                topic = HybridDialogueRouter.Topic.RELATIONSHIP,
                target = target,
                question = question,
                confidence = maxOf(route.confidence, 0.90f),
                fact = null,
                factTopicKey = "relazione",
                privacyBoundary = false
            )
        }
        if (route.question == HybridDialogueRouter.Question.STATEMENT && raw.endsWith("?")) {
            return route.copy(
                question = HybridDialogueRouter.Question.YES_NO,
                confidence = maxOf(route.confidence, 0.45f)
            )
        }
        return route
    }

    private fun labFilterSemanticSelection(
        selection: SemanticTurnSelection,
        route: HybridDialogueRouter.Route,
        explicitIntimacy: Boolean
    ): SemanticTurnSelection {
        val filtered = when {
            explicitIntimacy -> selection.selected.filter {
                it.owner == SemanticOwner.SHARED && it.topic in setOf("relazione", "ricordi")
            }
            route.topic == HybridDialogueRouter.Topic.GENERAL && route.confidence < 0.60f -> emptyList()
            else -> selection.selected
        }
        if (filtered.size == selection.selected.size) return selection
        val knowledge = filtered.joinToString("\\n") { "${it.owner.promptLabel}: ${it.text}" }
        val reason = if (explicitIntimacy) "intimita_senza_background_casuale" else "general_bassa_confidenza"
        return selection.copy(
            selected = filtered,
            promptKnowledge = knowledge,
            diagnostic = selection.diagnostic + "; filtro_lab=" + reason
        )
    }

    private fun isLabExplicitIntimacy(userText: String): Boolean {
        val value = normalizeLabText(userText)
        val phrases = listOf(
            "sesso", "fare l amore", "facciamo l amore", "fare sesso", "facciamo sesso",
            "scopare", "scopiamo", "scopata", "trombare", "trombiamo", "chiavare",
            "andare a letto", "andiamo a letto", "venire a letto", "vieni a letto",
            "dormire insieme", "nuda", "nudo", "spogliarti", "spogliati", "spogliare",
            "mutandine", "mutande", "biancheria", "reggiseno", "intimo", "senza vestiti",
            "ti desidero", "ho voglia di te", "mi ecciti", "eccitata", "eccitato", "horny",
            "limonare", "baciarti", "toccare", "toccarti", "accarezzarti",
            "pecorina", "a quattro zampe", "a 4 zampe", "da dietro",
            "deep throat", "deepthroat", "face fuck", "facefuck", "orale",
            "fellatio", "pompino", "sesso orale"
        )
        return phrases.any(value::contains)
    }

    private fun labReplyClass(localRaw: String?, finalReply: String, userText: String): String {
        if (localRaw.isNullOrBlank()) return "EMPTY_TIMEOUT"
        val value = normalizeLabText(finalReply)
        val rawValue = normalizeLabText(localRaw)
        val user = normalizeLabText(userText)
        if (listOf(
                "non riesco a contattare l ia", "controlla il gguf", "configura gemini",
                "configura openai", "sono un assistente", "come intelligenza artificiale",
                "as an ai", "i am an ai"
            ).any { it in rawValue || it in value }) return "ROLE_BREAK"
        if (listOf(
                "non posso continuare questa conversazione", "non posso aiutarti con",
                "non posso fornire", "non posso partecipare", "non posso soddisfare",
                "i cannot assist", "i can t assist", "i cannot help", "i m sorry but i can t"
            ).any { it in rawValue || it in value }) return "MODEL_REFUSAL"
        if (listOf(
                "non mi va", "non voglio", "preferisco di no", "non me la sento",
                "fermati", "smettila", "no grazie"
            ).any(value::contains)) return "CHARACTER_REFUSAL"
        if (user.length >= 4 && (value == user || value.startsWith(user + " "))) return "ECHO"
        return "VALID_REPLY"
    }

    private fun normalizeLabText(value: String): String = java.text.Normalizer
        .normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9à-ù]+"), " ")
        .replace(Regex("\\\\s+"), " ")
        .trim()

'''
if ai.count(anchor) != 1:
    raise RuntimeError("anchor relationshipTurnGuidance inatteso")
ai = ai.replace(anchor, helpers + anchor, 1)

AI.write_text(ai, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
old_code = "        versionCode = 41"
old_name = '        versionName = "alfa8.10.5-dual-engine-test"'
if gradle.count(old_code) != 1 or gradle.count(old_name) != 1:
    raise RuntimeError("Versione sorgente inattesa")
gradle = gradle.replace(old_code, "        versionCode = 46", 1)
gradle = gradle.replace(old_name, '        versionName = "alfa8.10.8-engine-lab"', 1)
GRADLE.write_text(gradle, encoding="utf-8")

print("alfa8.10.8 engine-lab transform applicata")
