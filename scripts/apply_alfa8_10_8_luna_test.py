from pathlib import Path

AI_PATH = Path("app/src/main/java/com/neontides/nativeapp/ai/AiEngine.kt")
VM_PATH = Path("app/src/main/java/com/neontides/nativeapp/GameViewModel.kt")
UI_PATH = Path("app/src/main/java/com/neontides/nativeapp/ui/screens/LunaDiagnosticLab.kt")
OLD_LAB_PATH = Path("app/src/main/java/com/neontides/nativeapp/ai/ModularMemoryLab.kt")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: attesa 1 occorrenza, trovate {count}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# AiEngine: rimuove il vecchio laboratorio non esposto e applica il contesto
# naturale SOLO al test isolato runBaseDialogueTest(). Il percorso normale
# replyAndEvaluate() mantiene il fallback 8.10.7 invariato.
# ---------------------------------------------------------------------------
ai = AI_PATH.read_text(encoding="utf-8")

ai = replace_once(
    ai,
    "    private val modularMemoryLab = ModularMemoryLab()\n",
    "",
    "campo ModularMemoryLab",
)
ai = replace_once(
    ai,
    "    @Volatile private var modularLabFingerprint: String = \"\"\n",
    "",
    "fingerprint vecchio laboratorio",
)

reset_line = "        modularLabFingerprint = \"\"\n"
if ai.count(reset_line) != 2:
    raise RuntimeError(f"reset fingerprint: attese 2 occorrenze, trovate {ai.count(reset_line)}")
ai = ai.replace(reset_line, "")

old_lab_start = ai.find("    suspend fun runModularMemoryLab(request: ModularLabRequest): ModularLabResult")
old_lab_end_marker = "    /**\n     * Esegue lo stesso percorso del dialogo reale senza scrivere nello stato di"
old_lab_end = ai.find(old_lab_end_marker)
if old_lab_start < 0 or old_lab_end < 0 or old_lab_end <= old_lab_start:
    raise RuntimeError("blocco runModularMemoryLab non trovato in modo univoco")
ai = ai[:old_lab_start] + ai[old_lab_end:]

helper = '''    private fun lunaRelationshipGuidance(affection: Int, attraction: Int, trust: Int): String {
        val closeness = minOf(affection, trust)
        return when {
            closeness >= 75 && attraction >= 75 ->
                "Tra voi c'è molta confidenza, fiducia e forte attrazione reciproca. Reagisci con naturalezza secondo il tuo carattere."
            closeness >= 50 && attraction >= 40 ->
                "Tra voi c'è una confidenza solida e una chiara attrazione reciproca. Reagisci con naturalezza secondo il tuo carattere."
            closeness >= 25 ->
                "Vi conoscete abbastanza e sta crescendo la confidenza. Reagisci con naturalezza secondo il tuo carattere."
            else ->
                "Vi conoscete ancora poco e non c'è ancora particolare confidenza. Reagisci con naturalezza secondo il tuo carattere."
        }
    }

'''
if ai.count(old_lab_end_marker) != 1:
    raise RuntimeError("punto inserimento helper Luna non univoco")
ai = ai.replace(old_lab_end_marker, helper + old_lab_end_marker, 1)

ai = replace_once(
    ai,
    '''        return try {
            replyAndEvaluate(character, state, relationship, userText, onPartial)
        } finally {''',
    '''        return try {
            replyAndEvaluate(
                character = character,
                state = state,
                relationship = relationship,
                userText = userText,
                onPartial = onPartial,
                relationshipGuidanceOverride = lunaRelationshipGuidance(
                    relationship.affection,
                    relationship.attraction,
                    relationship.trust
                )
            )
        } finally {''',
    "chiamata runBaseDialogueTest",
)

ai = replace_once(
    ai,
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
        relationshipGuidanceOverride: String? = null
    ): AiDialogueResult = withContext(Dispatchers.IO) {''',
    "firma replyAndEvaluate",
)

ai = replace_once(
    ai,
    '            val messageLine = "Rapporto: ${relationship.stage}. Messaggio: $compactMessage"',
    '''            val messageLine = relationshipGuidanceOverride?.let {
                "$it Messaggio: $compactMessage"
            } ?: "Rapporto: ${relationship.stage}. Messaggio: $compactMessage"''',
    "messageLine relazione",
)

if "runModularMemoryLab" in ai or "modularMemoryLab" in ai or "modularLabFingerprint" in ai:
    raise RuntimeError("riferimenti al vecchio laboratorio ancora presenti in AiEngine")
if "relationshipGuidanceOverride" not in ai:
    raise RuntimeError("override relazione test assente")
if '?: "Rapporto: ${relationship.stage}. Messaggio: $compactMessage"' not in ai:
    raise RuntimeError("fallback chat principale 8.10.7 non preservato")
if "Tra voi c'è molta confidenza, fiducia e forte attrazione reciproca." not in ai:
    raise RuntimeError("contesto naturale Luna assente")

AI_PATH.write_text(ai, encoding="utf-8")


# ---------------------------------------------------------------------------
# GameViewModel: elimina API/stato del vecchio laboratorio, lasciando intatto
# il test corrente runBaseDialogueTest() usato da LunaDiagnosticLab.
# ---------------------------------------------------------------------------
vm = VM_PATH.read_text(encoding="utf-8")
for import_line in (
    "import com.neontides.nativeapp.ai.ModularLabRequest\n",
    "import com.neontides.nativeapp.ai.ModularLabRecord\n",
    "import com.neontides.nativeapp.ai.ModularLabResult\n",
):
    vm = replace_once(vm, import_line, "", f"rimozione {import_line.strip()}")

vm_start = vm.find("    private val _modularLabHistory = MutableStateFlow<List<ModularLabRecord>>(emptyList())")
vm_end = vm.find("    suspend fun runBaseDialogueTest(", vm_start)
if vm_start < 0 or vm_end < 0 or vm_end <= vm_start:
    raise RuntimeError("blocco GameViewModel del vecchio laboratorio non trovato")
vm = vm[:vm_start] + vm[vm_end:]

for obsolete in ("ModularLabRequest", "ModularLabRecord", "ModularLabResult", "runModularMemoryLab", "modularLabHistory"):
    if obsolete in vm:
        raise RuntimeError(f"riferimento obsoleto ancora presente in GameViewModel: {obsolete}")
if "runBaseDialogueTest(" not in vm:
    raise RuntimeError("test Luna corrente rimosso accidentalmente")

VM_PATH.write_text(vm, encoding="utf-8")


# ---------------------------------------------------------------------------
# UI diagnostica: registra esplicitamente il contesto naturale che corrisponde
# ai valori del test. I valori tecnici restano diagnostici e non sono prompt.
# ---------------------------------------------------------------------------
ui = UI_PATH.read_text(encoding="utf-8")
ui = replace_once(
    ui,
    '''    appendLine("Affetto ${record.affection} · Attrazione ${record.attraction} · Fiducia ${record.trust} · Fase ${record.stage}")
    appendLine("Domanda: ${record.question}")''',
    '''    appendLine("Affetto ${record.affection} · Attrazione ${record.attraction} · Fiducia ${record.trust} · Fase ${record.stage}")
    appendLine("Contesto relazione inviato al GGUF: ${lunaRelationshipGuidanceForReport(record.affection, record.attraction, record.trust)}")
    appendLine("Domanda: ${record.question}")''',
    "diagnostica contesto relazione",
)

report_helper_anchor = "private fun labRelationshipStage(affection: Int, attraction: Int, trust: Int): String = when {"
report_helper = '''private fun lunaRelationshipGuidanceForReport(affection: Int, attraction: Int, trust: Int): String {
    val closeness = minOf(affection, trust)
    return when {
        closeness >= 75 && attraction >= 75 ->
            "Tra voi c'è molta confidenza, fiducia e forte attrazione reciproca. Reagisci con naturalezza secondo il tuo carattere."
        closeness >= 50 && attraction >= 40 ->
            "Tra voi c'è una confidenza solida e una chiara attrazione reciproca. Reagisci con naturalezza secondo il tuo carattere."
        closeness >= 25 ->
            "Vi conoscete abbastanza e sta crescendo la confidenza. Reagisci con naturalezza secondo il tuo carattere."
        else ->
            "Vi conoscete ancora poco e non c'è ancora particolare confidenza. Reagisci con naturalezza secondo il tuo carattere."
    }
}

'''
if ui.count(report_helper_anchor) != 1:
    raise RuntimeError("punto inserimento helper report non univoco")
ui = ui.replace(report_helper_anchor, report_helper + report_helper_anchor, 1)
if "Contesto relazione inviato al GGUF:" not in ui:
    raise RuntimeError("diagnostica del contesto naturale non inserita")
UI_PATH.write_text(ui, encoding="utf-8")


# Il file del vecchio laboratorio non fa più parte della build 8.10.8.
if OLD_LAB_PATH.exists():
    OLD_LAB_PATH.unlink()

# Verifica finale sui sorgenti trasformati.
combined = AI_PATH.read_text(encoding="utf-8") + VM_PATH.read_text(encoding="utf-8")
for obsolete in ("runModularMemoryLab", "modularMemoryLab", "ModularLabRequest", "ModularLabRecord", "ModularLabResult"):
    if obsolete in combined:
        raise RuntimeError(f"vecchio laboratorio ancora referenziato: {obsolete}")
if OLD_LAB_PATH.exists():
    raise RuntimeError("ModularMemoryLab.kt non eliminato")

print("Trasformazione alfa8.10.8 applicata al test Luna corrente; vecchio laboratorio rimosso")
