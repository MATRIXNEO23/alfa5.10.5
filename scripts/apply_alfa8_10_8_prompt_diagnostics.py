from pathlib import Path

AI = Path("app/src/main/java/com/neontides/nativeapp/ai/AiEngine.kt")
MODELS = Path("app/src/main/java/com/neontides/nativeapp/model/Models.kt")
LAB = Path("app/src/main/java/com/neontides/nativeapp/ui/screens/LunaDiagnosticLab.kt")

ai = AI.read_text(encoding="utf-8")
models = MODELS.read_text(encoding="utf-8")
lab = LAB.read_text(encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: atteso 1 match, trovati {count}")
    return text.replace(old, new, 1)

# Campi diagnostici: solo osservabilità, nessuna influenza sulla generazione.
models = replace_once(
    models,
    '''    /** Output completo del motore prima di pulizia o correzione deterministica. */
    val diagnosticRawReply: String = ""
)''',
    '''    /** Output completo del motore prima di pulizia o correzione deterministica. */
    val diagnosticRawReply: String = "",
    /** Cache/system context realmente preparato prima del turno. */
    val diagnosticPromptCache: String = "",
    /** Prompt del turno passato realmente a generate(). */
    val diagnosticPromptTurn: String = "",
    /** Provenienza delle direttive che hanno composto il contesto del turno. */
    val diagnosticDirectiveSources: String = "",
    /** Parametri di sampling usati dal runtime locale. */
    val diagnosticSampling: String = ""
)''',
    "campi AiDialogueResult"
)

# Dopo la creazione del prompt del turno costruiamo la mappa di provenienza.
ai = replace_once(
    ai,
    '''            val localPrompt = userPrompt(turnBody, preparedTurns > 0)

            val onlineConfigured = settings.hasGemini() || settings.hasOpenAi()''',
    '''            val localPrompt = userPrompt(turnBody, preparedTurns > 0)
            val labDirectiveSources = if (labEngineAuditFixes) buildString {
                appendLine("CACHE_BASE -> buildCachedContext: identità personaggio, identità giocatore, carattere compatto, stile di risposta, distinzione identità, sintonia/consenso")
                appendLine("ROUTER -> topic=${route.topic.label}; target=${route.target.name}; question=${route.question.name}; confidence=${"%.2f".format(java.util.Locale.US, route.confidence)}")
                if (semanticSelection.selected.isNotEmpty()) {
                    appendLine("MEMORIA_SEMANTICA -> " + semanticSelection.selected.joinToString { module ->
                        "${module.id}[${module.owner.name}/${module.topic}/score=${"%.2f".format(java.util.Locale.US, module.score)}]"
                    })
                } else appendLine("MEMORIA_SEMANTICA -> nessun modulo inserito")
                if (semanticSelection.blockedIds.isNotEmpty()) {
                    appendLine("PRIVACY -> moduli bloccati=${semanticSelection.blockedIds.joinToString()}; direttiva=limite naturale")
                }
                if (!simpleTurn) appendLine("ROUTER_HINT -> ${dialogueRouter.promptHint(route, character, relationship)}")
                if (intimacyGuidance.isNotBlank()) appendLine("RELAZIONE_INTIMITA -> ${intimacyGuidance.trim()}")
                if (worldGuidance.isNotBlank()) appendLine("MONDO -> ${worldGuidance.trim()}")
                if (liveContinuity.isNotBlank()) appendLine("CONTINUITA_CHAT -> $liveContinuity")
                appendLine("STATO_RELAZIONE -> stage=${relationship.stage}; affetto=${relationship.affection}; attrazione=${relationship.attraction}; fiducia=${relationship.trust}")
                appendLine("MESSAGGIO_GIOCATORE -> $compactMessage")
                append("FORMATO_PROMPT -> ${promptFormat().name}; budget_turno=$turnBudget; caratteri_turno=${localPrompt.length}")
            }.trim() else ""

            val onlineConfigured = settings.hasGemini() || settings.hasOpenAi()''',
    "tracciamento provenienza prompt"
)

# Salviamo cache + prompt del turno esattamente come usati dal motore.
ai = replace_once(
    ai,
    '''                diagnosticCorrectionReason = correctionReason,
                diagnosticRawReply = localRaw ?: onlineResult?.text.orEmpty(),
                relationshipEvent = finalResult.relationshipEvent''',
    '''                diagnosticCorrectionReason = correctionReason,
                diagnosticRawReply = localRaw ?: onlineResult?.text.orEmpty(),
                diagnosticPromptCache = if (labEngineAuditFixes) preparedContextText else "",
                diagnosticPromptTurn = if (labEngineAuditFixes) localPrompt else "",
                diagnosticDirectiveSources = if (labEngineAuditFixes) labDirectiveSources else "",
                diagnosticSampling = if (labEngineAuditFixes) "temperature=0.52; max_tokens=" +
                    (if (activeEngineLabel().startsWith("SmolLM")) 64 else 56) else "",
                relationshipEvent = finalResult.relationshipEvent''',
    "salvataggio prompt diagnostico"
)

# Il report esportato deve mostrare l'intera catena che ha portato alla risposta.
lab = replace_once(
    lab,
    '''    appendLine("Semantica: ${record.result.diagnosticSemantics.ifBlank { "nessun modulo" }}")
    appendLine("Correzione deterministica: ${if (record.result.diagnosticFallback) "SÌ" else "NO"}")''',
    '''    appendLine("Semantica: ${record.result.diagnosticSemantics.ifBlank { "nessun modulo" }}")
    appendLine("--- CONTESTO CACHE/SYSTEM ATTIVO ---")
    appendLine(record.result.diagnosticPromptCache.ifBlank { "non disponibile" })
    appendLine("--- PROMPT TURNO ESATTO INVIATO A GENERATE() ---")
    appendLine(record.result.diagnosticPromptTurn.ifBlank { "non disponibile" })
    appendLine("--- PROVENIENZA DIRETTIVE ---")
    appendLine(record.result.diagnosticDirectiveSources.ifBlank { "non disponibile" })
    appendLine("Sampler: ${record.result.diagnosticSampling.ifBlank { "non disponibile" }}")
    appendLine("Correzione deterministica: ${if (record.result.diagnosticFallback) "SÌ" else "NO"}")''',
    "report Luna prompt"
)

AI.write_text(ai, encoding="utf-8")
MODELS.write_text(models, encoding="utf-8")
LAB.write_text(lab, encoding="utf-8")
print("prompt diagnostics transform applicata")
