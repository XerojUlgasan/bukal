package com.example.bukal.ui.flashcards

import com.example.bukal.ai.MatchingPair
import com.example.bukal.ai.QuestionAnswer
import com.example.bukal.ai.QuestionType
import com.example.bukal.ai.QuizQuestion
import org.junit.Assert.assertEquals
import org.junit.Test

class FlashcardsViewModelTest {
    @Test
    fun everySavedQuestionTypeUsesItsAnswerKey() {
        val cards = listOf(
            question(
                QuestionType.MULTIPLE_CHOICE,
                QuestionAnswer.MultipleChoice(listOf("A", "B", "C", "D"), 1),
            ),
            question(QuestionType.FILL_IN_THE_BLANK, QuestionAnswer.OpenResponse("Blank")),
            question(QuestionType.IDENTIFICATION, QuestionAnswer.OpenResponse("Person")),
            question(
                QuestionType.TRUE_FALSE,
                QuestionAnswer.MultipleChoice(listOf("True", "False"), 0),
            ),
            question(QuestionType.EXPLANATION, QuestionAnswer.OpenResponse("Because")),
            question(
                QuestionType.MATCHING,
                QuestionAnswer.Matching(listOf(MatchingPair("l1", "Left", "r1", "Right"))),
            ),
        ).map { it.toFlashcard("Passage 1") }

        assertEquals(
            listOf("B", "Blank", "Person", "True", "Because", "Left = Right"),
            cards.map(Flashcard::answer),
        )
        assertEquals(List(cards.size) { "Passage 1" }, cards.map(Flashcard::sourceId))
    }

    private fun question(type: QuestionType, answer: QuestionAnswer) = QuizQuestion(
        id = type.wireName,
        type = type,
        prompt = "Question",
        sourceId = "TXT-P001",
        answer = answer,
    )
}
