package com.neontides.nativeapp.ai

import com.neontides.nativeapp.model.CharacterProfile
import com.neontides.nativeapp.model.DialogueMessage
import com.neontides.nativeapp.model.Relationship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridDialogueRouterTest {
    private val router = HybridDialogueRouter()
    private val luna = CharacterProfile(
        id = "luna",
        name = "Luna Hayashi",
        age = 22,
        job = "Cantautrice",
        personality = "Spontanea e creativa.",
        likes = listOf("musica", "notte", "libertà"),
        dislikes = listOf("routine", "controllo"),
        gender = "Femmina",
        workFacts = listOf("Scrive e interpreta brani pop elettronici con influenze alternative."),
        personalFacts = mapOf(
            "musica" to listOf(
                "Ascolta synth-pop, indie rock e cantautrici che scrivono testi molto personali."
            )
        )
    )

    private fun instant(
        text: String,
        relationship: Relationship = Relationship(),
        history: List<DialogueMessage> = emptyList()
    ): String? {
        val route = router.route(luna, relationship, history, text)
        return router.instantGroundedReply(route, luna, relationship, history, text)
    }

    @Test
    fun onlyUnambiguousBiographySkipsTheModel() {
        assertEquals("Ho 22 anni.", instant("quanti anni hai?"))
        assertEquals("Ho 22 anni.", instant("qual è la tua età?"))
        assertEquals("Ho 22 anni.", instant("quanti hanno hai?"))
        assertEquals("Sono cantautrice.", instant("che lavoro fai?"))
        assertEquals("Sono cantautrice.", instant("che mestiere fai?"))
        assertNull(instant("ti piace la musica?"))
    }

    @Test
    fun isolatedGreetingsAndAcknowledgementsAreNeutralAndImmediate() {
        assertTrue(instant("ciao!").orEmpty().contains("Ciao", ignoreCase = true))
        assertTrue(router.isNeutralRelationshipTurn("ciao!"))
        assertTrue(router.isNeutralRelationshipTurn("no"))
        assertTrue(router.isNeutralRelationshipTurn("sì"))
        assertFalse(router.isNeutralRelationshipTurn("ti piace la musica?"))
    }

    @Test
    fun shortRepliesAreLeftToTheGgufForNaturalContinuity() {
        val history = listOf(
            DialogueMessage("Luna Hayashi", "Che ne dici di parlare di qualcos'altro?")
        )
        assertNull(instant("no", history = history))
    }

    @Test
    fun emotionalQuestionsAreLeftToTheCharacterVoice() {
        assertNull(instant("c'è qualche problema?"))
    }

    @Test
    fun mestiereIsNotMistakenForPreviousIdentityTopic() {
        val history = listOf(
            DialogueMessage("Tu", "qual è la tua età?"),
            DialogueMessage("Luna Hayashi", "Ho 22 anni.")
        )
        val text = "Il mio mestiere è creare l'applicazione di cui fai parte, tu che mestiere fai?"
        val route = router.route(luna, Relationship(), history, text)
        assertEquals(HybridDialogueRouter.Topic.WORK, route.topic)
        assertEquals("Sono cantautrice.", instant(text, history = history))
    }

    @Test
    fun correctionsAndOrdinaryQuestionsStayScoreNeutral() {
        assertTrue(router.isNeutralRelationshipTurn("ti ho chiesto che mestiere fai?"))
        assertTrue(router.isNeutralRelationshipTurn("quanti anni hai?"))
        assertTrue(router.isNeutralRelationshipTurn("che lavoro fai?"))
        assertFalse(router.isNeutralRelationshipTurn("io ho 44 anni e mi chiamo Alberto"))
    }

    @Test
    fun playerRecallUsesKnownPlayerFactsInsteadOfCharacterPrivacy() {
        val relationship = Relationship(
            knownPlayerName = "Alberto",
            playerFacts = listOf(
                "identita|Ha 44 anni.",
                "musica|Gli piace la musica rock."
            )
        )
        val text = "Ti ricordi come mi chiamo, quanti anni ho e cosa mi piace?"
        val route = router.route(luna, relationship, emptyList(), text)
        val reply = instant(text, relationship).orEmpty()

        assertEquals(HybridDialogueRouter.Target.PLAYER, route.target)
        assertFalse(route.privacyBoundary)
        assertTrue(reply.contains("ti chiami Alberto"))
        assertTrue(reply.contains("hai 44 anni"))
        assertTrue(reply.contains("ti piace la musica rock"))
    }
}
