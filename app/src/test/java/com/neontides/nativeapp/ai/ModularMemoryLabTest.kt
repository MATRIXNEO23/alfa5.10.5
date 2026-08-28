package com.neontides.nativeapp.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModularMemoryLabTest {
    private val lab = ModularMemoryLab()

    @Test fun paraphraseFindsButLocksSecretAtLowTrust() {
        val result = lab.select(ModularLabRequest("Dimmi qualcosa che non sa nessuno", 0, 0, 0))
        assertTrue("segreto_luna" in result.blockedIds)
        assertFalse("segreto_luna" in result.selectedIds)
    }

    @Test fun secretIsAvailableWhenRelationshipAuthorizesIt() {
        val result = lab.select(ModularLabRequest("C'è qualcosa che non hai mai raccontato?", 50, 20, 60))
        assertTrue("segreto_luna" in result.selectedIds)
    }

    @Test fun playerAndNpcFactsRemainSeparated() {
        val result = lab.select(ModularLabRequest("Ti ricordi chi sono e che musica ascolto io?", 10, 0, 15))
        assertTrue("giocatore_base" in result.selectedIds)
        assertTrue("PLAYER:" in result.promptKnowledge)
    }

    @Test fun intimateSharedMemoryRequiresAllThresholds() {
        val blocked = lab.select(ModularLabRequest("Ricordi il nostro primo bacio?", 80, 10, 80))
        assertTrue("primo_bacio" in blocked.blockedIds)
        val allowed = lab.select(ModularLabRequest("Ricordi il nostro primo bacio?", 80, 60, 80))
        assertTrue("primo_bacio" in allowed.selectedIds)
    }

    @Test fun selectorNeverProvidesMoreThanThreeCandidateModules() {
        val result = lab.select(ModularLabRequest("Ricordi musica, concerto, lavoro e tempo libero?", 100, 100, 100))
        assertTrue(result.selectedIds.size + result.blockedIds.size <= 3)
        assertTrue(result.diagnostic.contains("Risposta deterministica: NO"))
    }

    @Test fun emptySelectionNeverInvitesTechnicalMetaReply() {
        val selection = lab.select(ModularLabRequest("Come stai oggi?", 0, 0, 0))
        val instruction = lab.generationInstruction(selection).lowercase()
        assertTrue(selection.selectedIds.isEmpty())
        assertFalse(instruction.contains("nessun modulo"))
        assertFalse(instruction.contains("dato specifico richiesto"))
        assertTrue(instruction.contains("rispondi direttamente"))
        assertTrue(instruction.contains("non citare moduli"))
    }
}
