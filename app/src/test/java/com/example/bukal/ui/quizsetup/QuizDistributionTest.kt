package com.example.bukal.ui.quizsetup

import org.junit.Assert.assertEquals
import org.junit.Test

class QuizDistributionTest {
    @Test
    fun noSelectedTypesProducesNoQuestions() {
        assertEquals(emptyList<QuestionTypeCount>(), calculateQuestionDistribution(emptyList()))
    }

    @Test
    fun oneSelectedTypeReceivesAllFiveQuestions() {
        val result = calculateQuestionDistribution(listOf(QuizType.MULTIPLE_CHOICE))

        assertEquals(
            listOf(QuestionTypeCount(QuizType.MULTIPLE_CHOICE, 5)),
            result,
        )
    }

    @Test
    fun remainderFollowsSelectedTypeOrder() {
        val result = calculateQuestionDistribution(
            listOf(
                QuizType.TRUE_FALSE,
                QuizType.EXPLANATION,
                QuizType.IDENTIFICATION,
            ),
        )

        assertEquals(
            listOf(
                QuestionTypeCount(QuizType.TRUE_FALSE, 2),
                QuestionTypeCount(QuizType.EXPLANATION, 2),
                QuestionTypeCount(QuizType.IDENTIFICATION, 1),
            ),
            result,
        )
    }

    @Test
    fun allFiveTypesReceiveOneQuestionEach() {
        val result = calculateQuestionDistribution(QuizType.entries)

        assertEquals(QuizQuestionCountForTest, result.sumOf(QuestionTypeCount::count))
        assertEquals(List(QuizType.entries.size) { 1 }, result.map(QuestionTypeCount::count))
    }

    private companion object {
        const val QuizQuestionCountForTest = 5
    }
}
