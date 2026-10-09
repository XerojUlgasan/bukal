package com.example.bukal.ai

import com.example.bukal.data.local.AttemptStatuses
import com.example.bukal.data.local.PassageEntity
import com.example.bukal.data.local.QuestionResults
import com.example.bukal.data.model.ModelDownloadSpec
import com.example.bukal.data.model.ModelPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuizPersistenceTest {
    @Test
    fun successfulPartialGenerationBecomesAnUnansweredSavedQuiz() {
        val record = generatedRecord(
            request = generationRequest,
            result = QuizGenerationResult(questions = questions, failedQuestionCount = 3),
            generatedAtEpochMs = 100,
            localDate = "2026-10-09",
        )

        assertEquals(AttemptStatuses.SAVED, record.attempt.status)
        assertEquals(100L, record.attempt.generatedAtEpochMs)
        assertEquals(0.0, record.attempt.earnedPoints, 0.0)
        assertEquals(2.0, record.attempt.possiblePoints, 0.0)
        assertEquals(listOf(QuestionResults.UNANSWERED, QuestionResults.UNANSWERED), record.questions.map {
            it.question.result
        })
        assertNull(record.questions.first().question.selectedOptionIndex)
        assertEquals(passage.content, record.questions.first().question.evidence)
    }

    @Test
    fun completedRetakeStoresFreshAnswersAndScore() {
        val evaluationRequest = QuizEvaluationRequest(
            model = model,
            passage = passage,
            questions = questions,
            selectedOptions = mapOf("q1" to 0),
            textResponses = mapOf("q2" to "Alpha"),
            matchingSelections = emptyMap(),
        )
        val summary = QuizResultSummary(
            listOf(
                QuizQuestionResult(
                    question = questions[0],
                    learnerAnswer = "Alpha",
                    expectedAnswer = "Alpha",
                    verdict = QuizResultVerdict.CORRECT,
                    earnedPoints = 1.0,
                    possiblePoints = 1.0,
                ),
                QuizQuestionResult(
                    question = questions[1],
                    learnerAnswer = "Alpha",
                    expectedAnswer = "Alpha",
                    verdict = QuizResultVerdict.CORRECT,
                    earnedPoints = 1.0,
                    possiblePoints = 1.0,
                    wasAiEvaluated = true,
                ),
            ),
        )

        val record = completedRecord(
            request = evaluationRequest,
            result = summary,
            generatedAtEpochMs = 100,
            completedAtEpochMs = 200,
            completedLocalDate = "2026-10-09",
        )

        assertEquals(AttemptStatuses.COMPLETED, record.attempt.status)
        assertEquals(2.0, record.attempt.earnedPoints, 0.0)
        assertEquals(0, record.questions[0].question.selectedOptionIndex)
        assertEquals("Alpha", record.questions[1].question.textResponse)
        assertEquals(QuestionResults.CORRECT, record.questions[1].question.result)
    }

    private val passage = PassageEntity(
        id = 7,
        materialId = 1,
        sourceId = "TXT-P001",
        position = 0,
        content = "Alpha is first. Beta is second.",
    )
    private val model = ModelDownloadSpec(
        id = "test-model",
        displayName = "Test model",
        purpose = ModelPurpose.QUIZ,
        fileName = "test.litertlm",
        downloadUrl = "https://example.com/test.litertlm",
        sizeBytes = 1,
        sha256 = "0".repeat(64),
    )
    private val generationRequest = QuizGenerationRequest(
        model = model,
        passage = passage,
        typeCounts = mapOf(QuestionType.MULTIPLE_CHOICE to 3, QuestionType.IDENTIFICATION to 2),
    )
    private val questions = listOf(
        QuizQuestion(
            id = "q1",
            type = QuestionType.MULTIPLE_CHOICE,
            prompt = "Which is first?",
            sourceId = passage.sourceId,
            answer = QuestionAnswer.MultipleChoice(
                options = listOf("Alpha", "Beta", "Gamma", "Delta"),
                answerIndex = 0,
            ),
        ),
        QuizQuestion(
            id = "q2",
            type = QuestionType.IDENTIFICATION,
            prompt = "Identify the first term.",
            sourceId = passage.sourceId,
            answer = QuestionAnswer.OpenResponse("Alpha"),
        ),
    )
}
