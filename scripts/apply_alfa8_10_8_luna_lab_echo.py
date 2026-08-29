from pathlib import Path

AI_PATH = Path("app/src/main/java/com/neontides/nativeapp/ai/AiEngine.kt")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: attesa 1 occorrenza, trovate {count}")
    return text.replace(old, new, 1)


ai = AI_PATH.read_text(encoding="utf-8")

# Il controllo tecnico resta identico nel gioco principale. Nel solo laboratorio
# Luna non sostituiamo una risposta esclusivamente perche' e' una naturale eco
# conversazionale (es. "ciao amore mio" -> "Ciao, amore mio"). Tutti gli altri
# errori tecnici, compresi leak del prompt e marcatori corrotti, restano attivi.
ai = replace_once(
    ai,
    '''                technicalReplyFailure(parsed.reply, userText)?.let { reason ->\n                    // Dopo lo streaming non si giudicano più significato, nomi,''',
    '''                technicalReplyFailure(parsed.reply, userText)\n                    ?.takeUnless { reason ->\n                        labPromptMode && reason == "eco quasi letterale del messaggio del giocatore"\n                    }\n                    ?.let { reason ->\n                    // Dopo lo streaming non si giudicano più significato, nomi,''',
    "bypass anti-eco solo laboratorio",
)

if 'labPromptMode && reason == "eco quasi letterale del messaggio del giocatore"' not in ai:
    raise RuntimeError("bypass anti-eco laboratorio non applicato")
if 'private fun technicalReplyFailure(reply: String, userText: String): String?' not in ai:
    raise RuntimeError("controllo tecnico principale rimosso accidentalmente")

AI_PATH.write_text(ai, encoding="utf-8")
print("Anti-eco naturale allentato SOLO nel Laboratorio Luna; controlli tecnici del gioco invariati")
