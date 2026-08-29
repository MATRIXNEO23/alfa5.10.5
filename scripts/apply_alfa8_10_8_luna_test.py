from pathlib import Path

path = Path("app/src/main/java/com/neontides/nativeapp/ai/AiEngine.kt")
text = path.read_text(encoding="utf-8")

replacements = [
    (
        '        val fingerprint = "luna-modular-v2"',
        '        val fingerprint = "luna-modular-v3-natural-relationship"',
    ),
    (
        '''        val prompt = userPrompt(\n            "Rapporto: affetto ${request.affection}, attrazione ${request.attraction}, fiducia ${request.trust}. " +\n                modularMemoryLab.generationInstruction(selection) + "\\nGiocatore: ${request.text}",\n            continueConversation = false\n        )''',
        '''        val relationshipGuidance = lunaRelationshipGuidance(\n            request.affection,\n            request.attraction,\n            request.trust\n        )\n        val prompt = userPrompt(\n            relationshipGuidance + " " +\n                modularMemoryLab.generationInstruction(selection) + "\\nGiocatore: ${request.text}",\n            continueConversation = false\n        )''',
    ),
    (
        '''            "Motore testato: ${activeRuntime?.backend?.label ?: "nessuno"}\\n" + selection.diagnostic +\n                "\\nCache compatta: ${if (rewound) "riutilizzata e ripristinata" else "preparata"} · ${compactBase.length} caratteri · ${cacheMs} ms" +''',
        '''            "Motore testato: ${activeRuntime?.backend?.label ?: "nessuno"}\\n" + selection.diagnostic +\n                "\\nValori test (solo diagnostica): affetto ${request.affection}, attrazione ${request.attraction}, fiducia ${request.trust}" +\n                "\\nContesto relazione inviato al GGUF: $relationshipGuidance" +\n                "\\nCache compatta: ${if (rewound) "riutilizzata e ripristinata" else "preparata"} · ${compactBase.length} caratteri · ${cacheMs} ms" +''',
    ),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Blocco 8.10.7 inatteso: occorrenze={count}\n{old}")
    text = text.replace(old, new, 1)

anchor = '''    /**\n     * Esegue lo stesso percorso del dialogo reale senza scrivere nello stato di'''
helper = '''    private fun lunaRelationshipGuidance(affection: Int, attraction: Int, trust: Int): String {\n        val closeness = minOf(affection, trust)\n        return when {\n            closeness >= 75 && attraction >= 75 ->\n                "Tra voi c'è molta confidenza, fiducia e forte attrazione reciproca. Reagisci con naturalezza secondo il tuo carattere."\n            closeness >= 50 && attraction >= 40 ->\n                "Tra voi c'è una confidenza solida e una chiara attrazione reciproca. Reagisci con naturalezza secondo il tuo carattere."\n            closeness >= 25 ->\n                "Vi conoscete abbastanza e sta crescendo la confidenza. Reagisci con naturalezza secondo il tuo carattere."\n            else ->\n                "Vi conoscete ancora poco e non c'è ancora particolare confidenza. Reagisci con naturalezza secondo il tuo carattere."\n        }\n    }\n\n'''

if text.count(anchor) != 1:
    raise RuntimeError("Punto di inserimento helper Luna non univoco")
text = text.replace(anchor, helper + anchor, 1)

if 'Rapporto: affetto ${request.affection}, attrazione ${request.attraction}, fiducia ${request.trust}.' in text:
    raise RuntimeError("Vecchia iniezione numerica Luna ancora presente")
if 'luna-modular-v3-natural-relationship' not in text:
    raise RuntimeError("Fingerprint Luna v3 assente")
if 'Valori test (solo diagnostica)' not in text:
    raise RuntimeError("Diagnostica valori test assente")

path.write_text(text, encoding="utf-8")
print("Trasformazione alfa8.10.8 Luna applicata con successo")
