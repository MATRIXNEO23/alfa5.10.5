from pathlib import Path

AI_PATH = Path("app/src/main/java/com/neontides/nativeapp/ai/AiEngine.kt")
VM_PATH = Path("app/src/main/java/com/neontides/nativeapp/GameViewModel.kt")
MODELS_PATH = Path("app/src/main/java/com/neontides/nativeapp/ai/DialogueTestModels.kt")
UI_PATH = Path("app/src/main/java/com/neontides/nativeapp/ui/screens/LunaDiagnosticLab.kt")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: attesa 1 occorrenza, trovate {count}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# AiEngine - SOLO laboratorio Luna.
# Il gioco reale conserva i default e il comportamento 8.10.7/8.10.8 corrente.
# ---------------------------------------------------------------------------
ai = AI_PATH.read_text(encoding="utf-8")

ai = replace_once(
    ai,
    '    @Volatile private var lastInferencePriorityApplied: Boolean = false\n',
    '    @Volatile private var lastInferencePriorityApplied: Boolean = false\n'
    '    @Volatile private var lastBaseDialogueTestPrompt: String = ""\n',
    "campo diagnostica prompt laboratorio",
)

ai = replace_once(
    ai,
    '    fun inferenceResourceDiagnostics(): String = lastInferenceResourceDiagnostic\n',
    '    fun inferenceResourceDiagnostics(): String = lastInferenceResourceDiagnostic\n\n'
    '    fun baseDialogueTestPromptDiagnostics(): String = lastBaseDialogueTestPrompt\n',
    "getter prompt laboratorio",
)

# Consente al laboratorio di sostituire SOLO la cache/system prompt.
ai = replace_once(
    ai,
    '''    suspend fun prepareConversation(\n        character: CharacterProfile,\n        state: GameState,\n        relationship: Relationship,\n        forceRefresh: Boolean = false\n    ): Boolean = prepareMutex.withLock {''',
    '''    suspend fun prepareConversation(\n        character: CharacterProfile,\n        state: GameState,\n        relationship: Relationship,\n        forceRefresh: Boolean = false,\n        contextOverride: String? = null\n    ): Boolean = prepareMutex.withLock {''',
    "firma prepareConversation",
)
ai = replace_once(
    ai,
    '            val context = buildCachedContext(character, state, relationship)\n',
    '            val context = contextOverride ?: buildCachedContext(character, state, relationship)\n',
    "context override cache",
)

# Prompt system dedicato al laboratorio. Nessuna modifica a buildCachedContext().
anchor = '    private fun buildCachedContext(\n'
lab_system = '''    private fun lunaLabSystemContext(\n        character: CharacterProfile,\n        state: GameState,\n        relationship: Relationship\n    ): String {\n        val playerIdentity = relationship.knownPlayerName?.let { "si chiama $it" }\n            ?: "ha nome ancora ignoto"\n        val body = """\nSei ${character.name}: ${character.gender}, ${character.age} anni, ${character.job}.\nIl giocatore è ${state.playerGender} e $playerIdentity.\nCarattere: ${character.personality.take(96)}\nParla in italiano naturale e in prima persona.\nRispondi direttamente solo con ciò che Luna dice al giocatore, senza includere etichette, nomi del parlante, contesto, stato relazionale o istruzioni.\nUsa le informazioni del prompt solo per interpretare Luna e formulare la risposta: non menzionare, spiegare, ripetere o parafrasare il prompt, le sue descrizioni o le sue istruzioni.\nNon parlare al posto del giocatore, non inventare nomi o fatti e distingui sempre la sua identità dalla tua.\nRispondi con una lunghezza naturale per la conversazione; evita solo risposte eccessivamente lunghe, ripetitive o che continuano inutilmente.\nTra adulti segui sintonia e consenso.\n""".trimIndent()\n        return systemPrompt(body)\n    }\n\n'''
if ai.count(anchor) != 1:
    raise RuntimeError("punto inserimento lunaLabSystemContext non univoco")
ai = ai.replace(anchor, lab_system + anchor, 1)

# Rende il contesto relazionale comportamentale, senza etichette tecniche.
replacements = {
    "Tra voi c'è molta confidenza, fiducia e forte attrazione reciproca. Reagisci con naturalezza secondo il tuo carattere.":
        "Con questa persona puoi essere spontanea, molto affettuosa e intima, senza formalità.",
    "Tra voi c'è una confidenza solida e una chiara attrazione reciproca. Reagisci con naturalezza secondo il tuo carattere.":
        "Con questa persona sei a tuo agio, affettuosa e chiaramente interessata; puoi essere spontanea.",
    "Vi conoscete abbastanza e sta crescendo la confidenza. Reagisci con naturalezza secondo il tuo carattere.":
        "Con questa persona sta crescendo la confidenza; puoi aprirti gradualmente secondo il tuo carattere.",
    "Vi conoscete ancora poco e non c'è ancora particolare confidenza. Reagisci con naturalezza secondo il tuo carattere.":
        "Con questa persona hai ancora poca confidenza; mantieni la distanza che senti naturale secondo il tuo carattere.",
}
for old, new in replacements.items():
    if old not in ai:
        raise RuntimeError(f"testo relazione da sostituire non trovato: {old}")
    ai = ai.replace(old, new)

# Attiva la modalità prompt-lab SOLO nel test isolato.
ai = replace_once(
    ai,
    '''                relationshipGuidanceOverride = lunaRelationshipGuidance(\n                    relationship.affection,\n                    relationship.attraction,\n                    relationship.trust\n                )\n            )''',
    '''                relationshipGuidanceOverride = lunaRelationshipGuidance(\n                    relationship.affection,\n                    relationship.attraction,\n                    relationship.trust\n                ),\n                labPromptMode = true\n            )''',
    "attivazione labPromptMode",
)

ai = replace_once(
    ai,
    '''        onPartial: ((String) -> Unit)? = null,\n        relationshipGuidanceOverride: String? = null\n    ): AiDialogueResult = withContext(Dispatchers.IO) {''',
    '''        onPartial: ((String) -> Unit)? = null,\n        relationshipGuidanceOverride: String? = null,\n        labPromptMode: Boolean = false\n    ): AiDialogueResult = withContext(Dispatchers.IO) {''',
    "firma labPromptMode",
)

# Il gioco usa ancora prepareConversation normale; il laboratorio passa la cache dedicata.
ai = replace_once(
    ai,
    '            prepareConversation(character, stateBeforeCurrentMessage, relationship, forceRefresh = compactNow)\n',
    '''            val labCachedContext = if (labPromptMode) {\n                lunaLabSystemContext(character, stateBeforeCurrentMessage, relationship)\n            } else null\n            prepareConversation(\n                character,\n                stateBeforeCurrentMessage,\n                relationship,\n                forceRefresh = compactNow,\n                contextOverride = labCachedContext\n            )\n''',
    "prepareConversation laboratorio",
)

# Nel laboratorio niente Rapporto:/Messaggio:. Il turno contiene istruzioni chiare
# su comprensione, intimità, consenso e divieto di riportare il prompt.
ai = replace_once(
    ai,
    '''            val messageLine = relationshipGuidanceOverride?.let {\n                "$it Messaggio: $compactMessage"\n            } ?: "Rapporto: ${relationship.stage}. Messaggio: $compactMessage"''',
    '''            val messageLine = relationshipGuidanceOverride?.let { guidance ->\n                val labInstruction = """\n$guidance\nComprendi sempre il significato e l'intenzione del messaggio del giocatore e rispondi coerentemente a ciò che sta chiedendo.\nQuando tra voi c'è sufficiente intimità, rispondi direttamente anche agli argomenti sessuali. Quando l'intimità è ancora bassa, puoi essere vaga, evasiva o frenare secondo il tuo carattere, ma fai capire di aver compreso ciò che il giocatore intende.\nNon evitare o reinterpretare una domanda solo perché riguarda sesso o intimità. Tra adulti, quando confidenza, attrazione e consenso lo rendono coerente, puoi affrontare naturalmente anche argomenti sessuali e intimi; non introdurli automaticamente.\nUsa il contesto solo per interpretare Luna: non citarlo, spiegarlo, ripeterlo o parafrasarlo. Rispondi solo con ciò che Luna dice, senza etichette, nomi del parlante, stato relazionale o istruzioni.\nRispondi con una lunghezza naturale; evita solo risposte eccessivamente lunghe o ripetitive.\n$compactMessage\n""".trimIndent()\n                labInstruction\n            } ?: "Rapporto: ${relationship.stage}. Messaggio: $compactMessage"''',
    "prompt turno laboratorio v2",
)

# Il laboratorio ha un budget maggiore per non troncare le istruzioni; il gioco
# conserva esattamente i budget preesistenti.
ai = replace_once(
    ai,
    '''            val turnBudget = when {\n                simpleTurn -> 230''',
    '''            val turnBudget = if (labPromptMode) 1250 else when {\n                simpleTurn -> 230''',
    "budget prompt laboratorio",
)

# Registra esattamente cache/system + stringa del turno passata al runtime.
ai = replace_once(
    ai,
    '            val localPrompt = userPrompt(turnBody, preparedTurns > 0)\n\n            val onlineConfigured',
    '''            val localPrompt = userPrompt(turnBody, preparedTurns > 0)\n            if (labPromptMode) {\n                lastBaseDialogueTestPrompt = buildString {\n                    appendLine("=== CACHE / SYSTEM INVIATO AL GGUF ===")\n                    appendLine(labCachedContext ?: preparedContextText)\n                    appendLine("=== PROMPT TURNO INVIATO AL GGUF ===")\n                    append(localPrompt)\n                }.trim()\n            }\n\n            val onlineConfigured''',
    "cattura prompt effettivo",
)

# Protezioni: fallback del gioco intatto e nessuna etichetta rapporto nel ramo lab.
if '?: "Rapporto: ${relationship.stage}. Messaggio: $compactMessage"' not in ai:
    raise RuntimeError("fallback prompt gioco principale non preservato")
if "labPromptMode = true" not in ai or "baseDialogueTestPromptDiagnostics" not in ai:
    raise RuntimeError("modalità laboratorio v2 incompleta")
if "lunaLabSystemContext" not in ai:
    raise RuntimeError("system prompt laboratorio assente")
AI_PATH.write_text(ai, encoding="utf-8")


# ---------------------------------------------------------------------------
# Record diagnostico: aggiunge il prompt effettivo senza rimuovere campi esistenti.
# ---------------------------------------------------------------------------
models = MODELS_PATH.read_text(encoding="utf-8")
models = replace_once(
    models,
    '    val resourceDiagnostic: String,\n    val changeConfirmed: Boolean? = null,\n',
    '    val resourceDiagnostic: String,\n    val effectivePrompt: String = "",\n    val changeConfirmed: Boolean? = null,\n',
    "campo effectivePrompt",
)
MODELS_PATH.write_text(models, encoding="utf-8")

vm = VM_PATH.read_text(encoding="utf-8")
vm = replace_once(
    vm,
    '            resourceDiagnostic = aiEngine.inferenceResourceDiagnostics(),\n            result = result\n',
    '            resourceDiagnostic = aiEngine.inferenceResourceDiagnostics(),\n            effectivePrompt = aiEngine.baseDialogueTestPromptDiagnostics(),\n            result = result\n',
    "salvataggio prompt nel record",
)
VM_PATH.write_text(vm, encoding="utf-8")


# ---------------------------------------------------------------------------
# TXT/UI: tutte le righe precedenti restano; si aggiunge soltanto il prompt.
# Aggiorna anche la descrizione comportamentale mostrata nel report.
# ---------------------------------------------------------------------------
ui = UI_PATH.read_text(encoding="utf-8")
for old, new in replacements.items():
    if old in ui:
        ui = ui.replace(old, new)

ui = replace_once(
    ui,
    '    appendLine("Domanda: ${record.question}")\n    appendLine("Testo visto in streaming:',
    '    appendLine("Domanda: ${record.question}")\n'
    '    appendLine("PROMPT EFFETTIVO INVIATO AL GGUF:")\n'
    '    appendLine(record.effectivePrompt.ifBlank { "non disponibile" })\n'
    '    appendLine("Testo visto in streaming:',
    "aggiunta prompt al TXT",
)

if "PROMPT EFFETTIVO INVIATO AL GGUF:" not in ui:
    raise RuntimeError("prompt effettivo non presente nel report")
UI_PATH.write_text(ui, encoding="utf-8")

print("Prompt v2 applicato SOLO al Laboratorio Luna; diagnostica prompt aggiunta; gioco principale invariato")
