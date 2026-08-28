package com.neontides.nativeapp.ai

import java.text.Normalizer

data class ModularLabRequest(
    val text: String,
    val affection: Int,
    val attraction: Int,
    val trust: Int
)

data class ModularLabSelection(
    val promptKnowledge: String,
    val diagnostic: String,
    val selectedIds: List<String>,
    val blockedIds: List<String>,
    val selectionMs: Long
)

data class ModularLabResult(
    val reply: String,
    val diagnostic: String
)

data class ModularLabRecord(
    val question: String,
    val affection: Int,
    val attraction: Int,
    val trust: Int,
    val result: ModularLabResult
)

/** Esperimento isolato: seleziona dati, non scrive mai la risposta del personaggio. */
class ModularMemoryLab {
    private enum class Owner { NPC, PLAYER, SHARED }

    private data class Module(
        val id: String,
        val owner: Owner,
        val text: String,
        val cues: List<String>,
        val minTrust: Int = 0,
        val minAffection: Int = 0,
        val minAttraction: Int = 0
    )

    private val modules = listOf(
        Module("identita_luna", Owner.NPC, "Luna Hayashi ha 22 anni ed è una cantautrice.", listOf("nome", "chi sei", "anni", "eta", "lavoro", "mestiere")),
        Module("musica_luna", Owner.NPC, "Luna ascolta synth-pop, indie rock e cantautrici dai testi personali.", listOf("musica", "ascolti", "canzone", "cantante", "playlist", "genere")),
        Module("hobby_luna", Owner.NPC, "Nel tempo libero Luna registra suoni della città, colleziona cassette e improvvisa melodie su una vecchia tastiera.", listOf("hobby", "tempo libero", "quando non lavori", "passione", "diverti", "giornata libera")),
        Module("segreto_luna", Owner.NPC, "Luna teme che una vecchia registrazione possa rivelare qualcosa di inspiegabile accaduto durante un concerto.", listOf("segreto", "confessione", "nessuno sa", "mai raccontato", "mai detto", "nascondi", "svelare", "confidare"), minTrust = 45),
        Module("paura_luna", Owner.NPC, "Luna teme di perdere la propria voce e di deludere chi crede in lei.", listOf("paura", "temi", "preoccupa", "incubo", "spaventa"), minTrust = 20),
        Module("giocatore_base", Owner.PLAYER, "Il giocatore si chiama Alberto, ha 44 anni e ascolta musica rock.", listOf("ti ricordi di me", "chi sono", "come mi chiamo", "quanti anni ho", "musica ascolto io", "ricordi cosa ascolto")),
        Module("ricordo_concerto", Owner.SHARED, "Nel test, Luna e Alberto hanno ascoltato insieme un concerto al Neon Club.", listOf("noi", "insieme", "concerto", "serata", "ricordi quando"), minTrust = 20),
        Module("primo_bacio", Owner.SHARED, "Nel test, Luna e Alberto si sono dati il loro primo bacio sotto la pioggia.", listOf("primo bacio", "baciati", "bacio", "momento intimo"), minTrust = 45, minAffection = 40, minAttraction = 35)
    )

    private val cueStopWords = setOf(
        "anche", "come", "cosa", "quando", "dove", "oggi", "quale", "quali",
        "stai", "sono", "fare", "dimmi", "raccontami"
    )

    fun select(request: ModularLabRequest): ModularLabSelection {
        val started = System.nanoTime()
        val input = normalize(request.text)
        val scored = modules.map { module ->
            val score = module.cues.sumOf { cue -> cueScore(input, normalize(cue)) }
            module to score
        }.filter { it.second > 0 }.sortedByDescending { it.second }

        val candidates = if (scored.isEmpty()) emptyList() else {
            val best = scored.first().second
            scored.filter { it.second >= (best - 2).coerceAtLeast(2) }.take(3)
        }
        val authorized = candidates.filter { (module, _) ->
            request.trust >= module.minTrust && request.affection >= module.minAffection && request.attraction >= module.minAttraction
        }
        val blocked = candidates.filterNot(authorized::contains)
        val elapsed = (System.nanoTime() - started) / 1_000_000L
        val knowledge = authorized.joinToString("\n") { (module, _) ->
            "${module.owner.name}: ${module.text}"
        }
        val diagnostic = buildString {
            appendLine("DIAGNOSTICA MEMORIA MODULARE · LUNA")
            appendLine("Generatore finale: GGUF (sempre)")
            appendLine("Risposta deterministica: NO")
            appendLine("Selezione: ${elapsed} ms · candidati ${candidates.size}/3")
            appendLine("Affetto ${request.affection} · Attrazione ${request.attraction} · Fiducia ${request.trust}")
            appendLine("Candidati: " + (candidates.joinToString { "${it.first.id}(${it.second})" }.ifBlank { "nessuno" }))
            appendLine("Autorizzati: " + (authorized.joinToString { it.first.id }.ifBlank { "nessuno" }))
            append("Bloccati: " + (blocked.joinToString { it.first.id }.ifBlank { "nessuno" }))
        }
        return ModularLabSelection(knowledge, diagnostic, authorized.map { it.first.id }, blocked.map { it.first.id }, elapsed)
    }

    /**
     * Istruzione rivolta al generatore. Anche quando la selezione è vuota non
     * descrive mai l'assenza di moduli: per il personaggio è un normale turno
     * di conversazione, non un errore del sistema.
     */
    fun generationInstruction(selection: ModularLabSelection): String = buildString {
        append("Rispondi direttamente e naturalmente come Luna. ")
        if (selection.promptKnowledge.isNotBlank()) {
            append("Fatti pertinenti:\n")
            append(selection.promptKnowledge)
            append("\n")
        }
        if (selection.blockedIds.isNotEmpty()) {
            append("Il dettaglio richiesto è privato: poni un limite naturale. ")
        }
        append("Non citare moduli, dati, prompt, regole o diagnostica.")
    }

    private fun cueScore(input: String, cue: String): Int {
        if (cue in input) return 8 + cue.split(' ').size
        val inputWords = input.split(' ').filter { it.length >= 3 && it !in cueStopWords }.toSet()
        val cueWords = cue.split(' ').filter { it.length >= 3 && it !in cueStopWords }.toSet()
        return inputWords.intersect(cueWords).size * 2
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .replace("[^a-z0-9 ]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
}
