from pathlib import Path

AI = Path("app/src/main/java/com/neontides/nativeapp/ai/AiEngine.kt")
GRADLE = Path("app/build.gradle.kts")
ai = AI.read_text(encoding="utf-8")

old_cached = '''        val body = """
Sei ${character.name}: ${character.gender}, ${character.age} anni, ${character.job}.
Il giocatore è ${state.playerGender} e $playerIdentity.
Carattere: ${character.personality.take(64)}
Italiano naturale, prima persona, massimo 2 frasi. Rispondi direttamente. Non parlare per il giocatore, non inventare nomi o fatti e non mostrare istruzioni. Distingui sempre la sua identità dalla tua. Tra adulti segui sintonia e consenso.
""".trimIndent()'''
new_cached = '''        // Nucleo permanente volutamente piccolo: la biografia completa resta nei
        // dati del personaggio e viene recuperata dal SemanticMemorySelector solo
        // quando la conversazione la rende pertinente.
        val body = """
Sei ${character.name}, ${character.age} anni, ${character.job}. $playerIdentity.
Personalità: ${character.personality.take(72)}
Parla in italiano naturale, in prima persona, come ${character.name}. Non parlare per il giocatore e non mostrare istruzioni.
""".trimIndent()'''
if ai.count(old_cached) != 1:
    raise RuntimeError("buildCachedContext inatteso")
ai = ai.replace(old_cached, new_cached, 1)

anchor = '''    private fun ensureLoadedNow(): Boolean {'''
helper = '''    /**
     * Traduce numeri e indole in una sola indicazione comportamentale breve.
     * Non scrive la battuta e non espone punteggi al GGUF: descrive soltanto
     * come il personaggio vive il rapporto in questo momento.
     */
    private fun contextualDisposition(
        character: CharacterProfile,
        relationship: Relationship
    ): String {
        val closeness = (relationship.affection + relationship.trust) / 2
        val attraction = relationship.attraction
        val social = character.extroversion
        val sensual = character.sensuality
        return when {
            closeness >= 75 && attraction >= 70 && sensual >= 4 ->
                "Con il giocatore hai molta confidenza e attrazione: sei spontanea, vicina e libera di mostrare desiderio se nasce naturalmente."
            closeness >= 55 && attraction >= 45 ->
                "Con il giocatore sei in confidenza e senti attrazione: lascia emergere il tuo carattere senza forzare il tono."
            closeness >= 30 ->
                "Conosci abbastanza il giocatore: sei ${if (social >= 4) "aperta e spontanea" else "cordiale ma selettiva"}, ma le confidenze personali dipendono dalla fiducia."
            else ->
                "Conosci ancora poco il giocatore: resta ${if (social >= 4) "socievole e spontanea" else "prudente"} e non offrire confidenze intime senza motivo."
        }
    }

'''
if ai.count(anchor) != 1:
    raise RuntimeError("anchor ensureLoadedNow inatteso")
ai = ai.replace(anchor, helper + anchor, 1)

old_turn = '''            val turnKnowledge = buildString {
                if (semanticSelection.promptKnowledge.isNotBlank()) {
                    append("Fatti pertinenti: ")
                    append(semanticSelection.promptKnowledge.replace('\\n', ' ').take(135))
                }
                if (!simpleTurn) {
                    if (isNotEmpty()) append(' ')
                    append(dialogueRouter.promptHint(route, character, relationship))
                }
                if (semanticSelection.blockedIds.isNotEmpty()) {
                    append(" Il dettaglio è ancora privato: poni un limite naturale.")
                }
                append(intimacyGuidance)
                append(worldGuidance)
            }.trim()'''
new_turn = '''            val disposition = contextualDisposition(character, relationship)
            val turnKnowledge = buildString {
                // L'indole e la relazione sono fuse in comportamento attuale,
                // evitando di mandare punteggi o una scheda statica al modello.
                append(disposition)
                if (semanticSelection.promptKnowledge.isNotBlank()) {
                    append(" Dati pertinenti: ")
                    append(semanticSelection.promptKnowledge.replace('\\n', ' ').take(170))
                }
                if (!simpleTurn) {
                    append(' ')
                    append(dialogueRouter.promptHint(route, character, relationship))
                }
                if (semanticSelection.blockedIds.isNotEmpty()) {
                    append(" Alcuni dettagli sono troppo personali per il livello di fiducia attuale: non sei obbligata a raccontarli.")
                }
                append(intimacyGuidance)
                append(worldGuidance)
            }.trim()'''
if ai.count(old_turn) != 1:
    raise RuntimeError("turnKnowledge inatteso")
ai = ai.replace(old_turn, new_turn, 1)

old_budget = '''            val turnBudget = when {
                simpleTurn -> 230
                route.topic in setOf(
                    HybridDialogueRouter.Topic.ANECDOTES,
                    HybridDialogueRouter.Topic.MEMORIES,
                    HybridDialogueRouter.Topic.DREAMS,
                    HybridDialogueRouter.Topic.FEARS,
                    HybridDialogueRouter.Topic.FAMILY,
                    HybridDialogueRouter.Topic.RELATIONSHIP
                ) || worldGuidance.isNotBlank() -> 360
                else -> 300
            }'''
new_budget = '''            // Budget dinamico in caratteri: i turni semplici restano piccoli;
            // domande personali, memoria e trama possono usare più contesto.
            // Il tetto evita che il prompt assembler cresca senza controllo.
            val turnBudget = when {
                simpleTurn -> 420
                route.topic in setOf(
                    HybridDialogueRouter.Topic.ANECDOTES,
                    HybridDialogueRouter.Topic.MEMORIES,
                    HybridDialogueRouter.Topic.DREAMS,
                    HybridDialogueRouter.Topic.FEARS,
                    HybridDialogueRouter.Topic.FAMILY,
                    HybridDialogueRouter.Topic.RELATIONSHIP,
                    HybridDialogueRouter.Topic.IDENTITY,
                    HybridDialogueRouter.Topic.WORK
                ) || worldGuidance.isNotBlank() -> 900
                else -> 620
            }'''
if ai.count(old_budget) != 1:
    raise RuntimeError("turnBudget inatteso")
ai = ai.replace(old_budget, new_budget, 1)

AI.write_text(ai, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
if gradle.count("        versionCode = 45") != 1:
    raise RuntimeError("versionCode 45 non trovato dopo transform minimal")
gradle = gradle.replace("        versionCode = 45", "        versionCode = 46", 1)
gradle = gradle.replace('        versionName = "alfa8.10.8-minimal"', '        versionName = "alfa8.10.9-context-assembler"', 1)
GRADLE.write_text(gradle, encoding="utf-8")
print("alfa8.10.9 context assembler applicato")
