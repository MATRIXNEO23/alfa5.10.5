from pathlib import Path

AI = Path("app/src/main/java/com/neontides/nativeapp/ai/AiEngine.kt")

ai = AI.read_text(encoding="utf-8")

def replace_once(old: str, new: str, label: str) -> None:
    global ai
    if ai.count(old) != 1:
        raise RuntimeError(f"{label}: atteso 1 match, trovati {ai.count(old)}")
    ai = ai.replace(old, new, 1)

# Stato vivo leggero: calcolato solo nel laboratorio, nessuna modifica al gioco principale.
replace_once(
'''            val localPrompt = userPrompt(turnBody, preparedTurns > 0)
            val labDirectiveSources = if (labEngineAuditFixes) buildString {''',
'''            val livingState = if (labEngineAuditFixes) {
                labLivingState(character, relationship, historyWithoutCurrent, userText)
            } else ""
            val livingTurnBody = if (livingState.isNotBlank()) {
                val prefix = "Situazione interiore: $livingState\\n"
                (prefix + turnBody).take(turnBudget + messageLine.length + 1)
            } else turnBody
            val localPrompt = userPrompt(livingTurnBody, preparedTurns > 0)
            val labDirectiveSources = if (labEngineAuditFixes) buildString {''',
"inserimento stato vivo"
)

replace_once(
'''                appendLine("STATO_RELAZIONE -> stage=${relationship.stage}; affetto=${relationship.affection}; attrazione=${relationship.attraction}; fiducia=${relationship.trust}")
                appendLine("MESSAGGIO_GIOCATORE -> $compactMessage")''',
'''                appendLine("STATO_RELAZIONE -> stage=${relationship.stage}; affetto=${relationship.affection}; attrazione=${relationship.attraction}; fiducia=${relationship.trust}")
                appendLine("STATO_VIVO -> ${livingState.ifBlank { "non attivo" }}")
                appendLine("MESSAGGIO_GIOCATORE -> $compactMessage")''',
"diagnostica stato vivo"
)

anchor = '''    private fun labAdjustedRoute(
'''
helper = '''    private fun labLivingState(
        character: CharacterProfile,
        relationship: Relationship,
        history: List<ChatMessage>,
        userText: String
    ): String {
        // Non detta una risposta: fornisce soltanto una disposizione interna compatta.
        // L'intensità deriva da relazione e continuità recente; il GGUF resta libero di interpretarla.
        val warmth = ((relationship.affection + relationship.trust) / 2).coerceIn(0, 100)
        val chemistry = relationship.attraction.coerceIn(0, 100)
        val recentTurns = history.takeLast(4)
        val hasContinuity = recentTurns.isNotEmpty()
        val question = userText.trim().endsWith("?")
        val disposition = when {
            warmth >= 80 && chemistry >= 70 -> "molto a suo agio e coinvolta"
            warmth >= 60 -> "a suo agio e interessata"
            warmth >= 35 -> "cordiale ma ancora prudente"
            else -> "riservata e ancora poco sicura del rapporto"
        }
        val intention = when {
            question && warmth >= 60 -> "vuole rispondere sinceramente, con il proprio punto di vista"
            question -> "vuole capire la domanda e rispondere senza forzare confidenza"
            hasContinuity -> "vuole continuare naturalmente il filo della conversazione"
            else -> "vuole reagire spontaneamente a ciò che ha appena sentito"
        }
        return "$disposition; $intention"
    }

'''
if ai.count(anchor) != 1:
    raise RuntimeError("anchor helper stato vivo inatteso")
ai = ai.replace(anchor, helper + anchor, 1)

AI.write_text(ai, encoding="utf-8")
print("living Luna transform applicata")
