package com.example.bukal.ai

import com.example.bukal.ui.passageselection.togglePassageSelection
import org.junit.Assert.assertEquals
import org.junit.Test

class MultiPassageQuizTest {
    @Test
    fun passageSelectionStopsAtFiveAndStillAllowsDeselection() {
        val selected = (1..5).fold(emptySet<String>()) { ids, index ->
            togglePassageSelection(ids, "TXT-P00$index")
        }

        assertEquals(selected, togglePassageSelection(selected, "TXT-P006"))
        assertEquals(selected - "TXT-P003", togglePassageSelection(selected, "TXT-P003"))
    }

    @Test
    fun questionIdsAreUniqueAcrossPassages() {
        val question = QuizQuestion(
            id = "q1",
            type = QuestionType.TRUE_FALSE,
            prompt = "Is this supported by the passage?",
            sourceId = "TXT-P001",
            answer = QuestionAnswer.MultipleChoice(listOf("True", "False"), 0),
        )

        val first = listOf(question).withPassageScopedIds(10).single()
        val second = listOf(question.copy(sourceId = "TXT-P002")).withPassageScopedIds(11).single()

        assertEquals("10:q1", first.id)
        assertEquals("11:q1", second.id)
    }
}
