package com.neontides.nativeapp.ai

import com.neontides.nativeapp.model.CharacterProfile
import com.neontides.nativeapp.model.Relationship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticMemorySelectorTest {
    private val router = HybridDialogueRouter()
    private val selector = SemanticMemorySelector()
    private val luna = CharacterProfile(
        id = "luna",
        name = "Luna Hayashi",
        age = 22,
        job = "Cantautrice",
        personality = "Spontanea e creativa.",
        likes = listOf("musica", "notte"),
        dislikes = listOf("controllo"),
        workFacts = listOf("Scrive e interpreta brani pop elettronici."),
        personalFacts = mapOf(
            "musica" to listOf("Ascolta synth-pop e indie rock."),
            "paure" to listOf("Teme di perdere la propria voce.")
        ),
        innerConflict = "Una registrazione contiene un suono inspiegabile."
    )

    private fun selection(text: String, relationship: Relationship = Relationship()): SemanticTurnSelection {
        val route = router.route(luna, relationship, emptyList(), text)
        return selector.select(luna, relationship, route, emptyList(), text)
    }

    @Test
    fun mixedPlayerDisclosureAndNpcQuestionKeepBothOwners() {
        val result = selection("Il mio mestiere è creare applicazioni, tu che mestiere fai?")
        assertTrue(result.selected.any { it.owner == SemanticOwner.PLAYER && "applicazioni" in it.text })
        assertTrue(result.selected.any { it.owner == SemanticOwner.NPC && "Cantautrice" in it.text })
        assertTrue("GIOCATORE:" in result.promptKnowledge)
        assertTrue("PERSONAGGIO:" in result.promptKnowledge)
    }

    @Test
    fun playerRecallNeverInjectsNpcBiography() {
        val relationship = Relationship(
            knownPlayerName = "Alberto",
            playerFacts = listOf("identita|Ha 44 anni.", "hobby|Gli piace il rock.")
        )
        val result = selection("Ti ricordi come mi chiamo, quanti anni ho e cosa mi piace?", relationship)
        assertTrue(result.selected.isNotEmpty())
        assertTrue(result.selected.all { it.owner == SemanticOwner.PLAYER })
        assertFalse(result.promptKnowledge.contains("Luna Hayashi ha 22 anni"))
    }

    @Test
    fun lowTrustBlocksSensitiveFactWithoutLeakingItsText() {
        val result = selection("Qual è la tua paura più grande?", Relationship(trust = 0))
        assertTrue(result.blockedIds.isNotEmpty())
        assertFalse(result.promptKnowledge.contains("perdere la propria voce"))
        assertFalse(result.promptKnowledge.contains("suono inspiegabile"))
    }

    @Test
    fun genericConversationDoesNotInjectRandomBiography() {
        val result = selection("Come stai oggi?")
        assertTrue(result.selected.isEmpty())
        assertTrue(result.promptKnowledge.isBlank())
    }

    @Test
    fun extractorStopsBeforeQuestionAddressedToNpc() {
        val facts = PlayerFactExtractor.extract("Mi piace il rock, tu che musica ascolti?")
        assertEquals(listOf("hobby|Gli piace il rock."), facts)
    }

    @Test
    fun mergeReplacesSingularFactsAndKeepsPreferences() {
        val merged = PlayerFactExtractor.merge(
            existing = listOf("identita|Ha 43 anni.", "hobby|Gli piace il rock."),
            incoming = listOf("identita|Ha 44 anni.", "hobby|Gli piace il jazz.")
        )
        assertFalse("identita|Ha 43 anni." in merged)
        assertTrue("identita|Ha 44 anni." in merged)
        assertTrue("hobby|Gli piace il rock." in merged)
        assertTrue("hobby|Gli piace il jazz." in merged)
    }
}
