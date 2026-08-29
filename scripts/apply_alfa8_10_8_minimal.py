from pathlib import Path

AI = Path("app/src/main/java/com/neontides/nativeapp/ai/AiEngine.kt")
GRADLE = Path("app/build.gradle.kts")

ai = AI.read_text(encoding="utf-8")

old_sig = '''    suspend fun replyAndEvaluate(
        character: CharacterProfile,
        state: GameState,
        relationship: Relationship,
        userText: String,
        onPartial: ((String) -> Unit)? = null
    ): AiDialogueResult = withContext(Dispatchers.IO) {'''
new_sig = '''    suspend fun replyAndEvaluate(
        character: CharacterProfile,
        state: GameState,
        relationship: Relationship,
        userText: String,
        onPartial: ((String) -> Unit)? = null,
        labRelaxEchoFallback: Boolean = false
    ): AiDialogueResult = withContext(Dispatchers.IO) {'''
if ai.count(old_sig) != 1:
    raise RuntimeError("Firma replyAndEvaluate inattesa")
ai = ai.replace(old_sig, new_sig, 1)

old_lab_call = '''            replyAndEvaluate(character, state, relationship, userText, onPartial)'''
new_lab_call = '''            replyAndEvaluate(
                character,
                state,
                relationship,
                userText,
                onPartial,
                labRelaxEchoFallback = true
            )'''
if ai.count(old_lab_call) != 1:
    raise RuntimeError("Chiamata laboratorio inattesa")
ai = ai.replace(old_lab_call, new_lab_call, 1)

old_failure = '''                technicalReplyFailure(parsed.reply, userText)?.let { reason ->
                    // Dopo lo streaming non si giudicano più significato, nomi,
                    // sinonimi o somiglianza: una risposta valida del GGUF resta
                    // quella vista dall'utente. Si interviene solo su corruzione
                    // tecnica del formato.
                    parsed = parsed.copy(reply = dialogueRouter.groundedFallback(
                        route, character, relationship, userText
                    ))
                    fallbackApplied = true
                    correctionReason = reason
                }'''
new_failure = '''                technicalReplyFailure(parsed.reply, userText)?.let { reason ->
                    // Nel laboratorio una semplice eco può essere una risposta
                    // naturale e potenzialmente corretta (es. un saluto). Non la
                    // sostituiamo. Prompt leak, ChatML rotto e output incompleto
                    // restano invece errori tecnici bloccanti.
                    val harmlessLabEcho = labRelaxEchoFallback &&
                        reason == "eco quasi letterale del messaggio del giocatore"
                    if (!harmlessLabEcho) {
                        parsed = parsed.copy(reply = dialogueRouter.groundedFallback(
                            route, character, relationship, userText
                        ))
                        fallbackApplied = true
                        correctionReason = reason
                    }
                }'''
if ai.count(old_failure) != 1:
    raise RuntimeError("Blocco technicalReplyFailure inatteso")
ai = ai.replace(old_failure, new_failure, 1)
AI.write_text(ai, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
old_code = "        versionCode = 41"
old_name = '        versionName = "alfa8.10.5-dual-engine-test"'
if gradle.count(old_code) != 1 or gradle.count(old_name) != 1:
    raise RuntimeError("Versione sorgente inattesa")
gradle = gradle.replace(old_code, "        versionCode = 45", 1)
gradle = gradle.replace(old_name, '        versionName = "alfa8.10.8-minimal"', 1)
GRADLE.write_text(gradle, encoding="utf-8")

print("alfa8.10.8 minimal transform applicata")
