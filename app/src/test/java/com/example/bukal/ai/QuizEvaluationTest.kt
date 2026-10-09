package com.example.bukal.ai

import com.example.bukal.data.local.PassageEntity
import com.example.bukal.data.model.ModelDownloadSpec
import com.example.bukal.data.model.ModelPurpose
import com.example.bukal.data.search.DocumentSearchResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizEvaluationTest {
    @Test
    fun evaluatesAQuizWithFewerThanFiveGeneratedQuestions() = runBlocking {
        val evaluator = QuizEvaluator(
            search = { _, _, _ -> error("The partial fixture contains deterministic questions only") },
            complete = { _, _, _ -> error("The partial fixture must not call the model") },
        )

        val summary = evaluator.evaluate(
            evaluationRequest(
                questions = questions.take(3),
                selectedOptions = mapOf("q1" to 0, "q2" to 1),
                matchingSelections = mapOf(
                    "q3:left-1" to "right-1",
                    "q3:left-2" to "right-2",
                ),
            ),
        )

        assertEquals(3, summary.items.size)
    }

    @Test
    fun gradesWrittenAnswerWithBooleanAndOnlyTopFiveMatches() = runBlocking {
        var aiRequest = ""
        var systemInstruction = ""
        val evaluator = QuizEvaluator(
            search = { _, passageId, limit ->
                assertEquals(passage.id, passageId)
                assertEquals(5, limit)
                (1..6).map(::searchResult)
            },
            complete = { _, system, request ->
                systemInstruction = system
                aiRequest = request
                "  TrUe  "
            },
        )

        val summary = evaluator.evaluate(
            evaluationRequest(
                questions = listOf(questions[3]),
                textResponses = mapOf("q4" to "Alpha"),
            ),
        )

        assertEquals(QuizResultVerdict.CORRECT, summary.items.single().verdict)
        assertEquals(1.0, summary.earnedPoints, 0.0)
        assertEquals(1.0, summary.possiblePoints, 0.0)
        assertTrue(systemInstruction.contains("Return only true"))
        assertFalse(aiRequest.contains("{"))
        assertTrue(aiRequest.contains("LEARNER ANSWER:\nAlpha"))
        assertTrue(aiRequest.endsWith("Return only true or false."))
        assertTrue(aiRequest.contains("Match 5"))
        assertFalse(aiRequest.contains("Match 6"))
    }

    @Test
    fun obviousNonAnswerIsIncorrectWithoutCallingAi() = runBlocking {
        val evaluator = QuizEvaluator(
            search = { _, _, _ -> error("An obvious non-answer must not search") },
            complete = { _, _, _ -> error("An obvious non-answer must not call the model") },
        )

        val summary = evaluator.evaluate(
            evaluationRequest(
                questions = listOf(questions[3]),
                textResponses = mapOf("q4" to "idk"),
            ),
        )

        assertEquals(QuizResultVerdict.INCORRECT, summary.items.single().verdict)
        assertEquals(0.0, summary.earnedPoints, 0.0)
        assertEquals(1.0, summary.possiblePoints, 0.0)
    }

    @Test
    fun invalidBooleanRetriesOnceThenDefaultsToFalse() = runBlocking {
        var calls = 0
        var retryInstruction = ""
        val evaluator = QuizEvaluator(
            search = { _, _, _ -> listOf(searchResult(1)) },
            complete = { _, system, _ ->
                calls += 1
                retryInstruction = system
                "not a boolean"
            },
        )

        val summary = evaluator.evaluate(
            evaluationRequest(
                questions = listOf(questions[3]),
                textResponses = mapOf("q4" to "Alpha"),
            ),
        )

        assertEquals(2, calls)
        assertTrue(retryInstruction.contains("previous response was invalid"))
        assertEquals(QuizResultVerdict.INCORRECT, summary.items.single().verdict)
    }

    @Test
    fun explanationIsGeneratedOnlyWhenExplicitlyRequested() = runBlocking {
        var calls = 0
        val evaluator = QuizEvaluator(
            search = { _, _, limit ->
                assertEquals(5, limit)
                (1..6).map(::searchResult)
            },
            complete = { _, system, request ->
                calls += 1
                if (system == SystemPrompts.ANSWER_EXPLANATION) {
                    assertFalse(request.contains("{"))
                    assertTrue(request.contains("RESULT:\ncorrect"))
                    assertTrue(request.contains("Match 5"))
                    assertFalse(request.contains("Match 6"))
                    "Your answer matches the lesson because Alpha is identified as first."
                } else {
                    "true"
                }
            },
        )

        val result = evaluator.evaluate(
            evaluationRequest(
                questions = listOf(questions[3]),
                textResponses = mapOf("q4" to "Alpha"),
            ),
        ).items.single()

        assertEquals(1, calls)
        val explanation = evaluator.explain(
            QuizExplanationRequest(
                model = quizModel,
                passage = passage,
                result = result,
            ),
        )

        assertEquals(2, calls)
        assertEquals(
            "Your answer matches the lesson because Alpha is identified as first.",
            explanation,
        )
    }

    @Test
    fun deterministicAnswersDoNotCallAi() = runBlocking {
        var aiCalls = 0
        val evaluator = QuizEvaluator(
            search = { _, _, _ -> error("Deterministic answers must not search") },
            complete = { _, _, _ ->
                aiCalls += 1
                error("Deterministic answers must not load the quiz model")
            },
        )

        val summary = evaluator.evaluate(
            evaluationRequest(
                questions = questions.take(3),
                selectedOptions = mapOf("q1" to 0),
                matchingSelections = mapOf(
                    "q3:left-1" to "right-1",
                    "q3:left-2" to "right-2",
                ),
            ),
        )

        assertEquals(QuizResultVerdict.CORRECT, summary.items[0].verdict)
        assertEquals(QuizResultVerdict.UNANSWERED, summary.items[1].verdict)
        assertEquals(QuizResultVerdict.CORRECT, summary.items[2].verdict)
        assertEquals(0, aiCalls)
    }

    private fun evaluationRequest(
        questions: List<QuizQuestion>,
        selectedOptions: Map<String, Int> = emptyMap(),
        textResponses: Map<String, String> = emptyMap(),
        matchingSelections: Map<String, String> = emptyMap(),
    ) = QuizEvaluationRequest(
        model = quizModel,
        passage = passage,
        questions = questions,
        selectedOptions = selectedOptions,
        textResponses = textResponses,
        matchingSelections = matchingSelections,
    )

    private fun searchResult(index: Int) = DocumentSearchResult(
        chunkId = index.toLong(),
        passageId = passage.id,
        materialName = "Lesson",
        sourceId = passage.sourceId,
        text = "Match $index supports Alpha.",
        similarity = 1.0 - index / 10.0,
    )

    private val passage = PassageEntity(
        id = 7,
        materialId = 1,
        sourceId = "TXT-P001",
        position = 0,
        content = "Alpha is first. Beta is second.",
    )

    private val quizModel = ModelDownloadSpec(
        id = "test-model",
        displayName = "Test model",
        purpose = ModelPurpose.QUIZ,
        fileName = "test.litertlm",
        downloadUrl = "https://example.com/test.litertlm",
        sizeBytes = 1,
        sha256 = "0".repeat(64),
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
            type = QuestionType.MULTIPLE_CHOICE,
            prompt = "Which is second?",
            sourceId = passage.sourceId,
            answer = QuestionAnswer.MultipleChoice(
                options = listOf("Alpha", "Beta", "Gamma", "Delta"),
                answerIndex = 1,
            ),
        ),
        QuizQuestion(
            id = "q3",
            type = QuestionType.MATCHING,
            prompt = "Match the items.",
            sourceId = passage.sourceId,
            answer = QuestionAnswer.Matching(
                pairs = listOf(
                    MatchingPair("left-1", "Alpha", "right-1", "First"),
                    MatchingPair("left-2", "Beta", "right-2", "Second"),
                ),
            ),
        ),
        QuizQuestion(
            id = "q4",
            type = QuestionType.IDENTIFICATION,
            prompt = "Identify what is first.",
            sourceId = passage.sourceId,
            answer = QuestionAnswer.OpenResponse(referenceAnswer = "Alpha"),
        ),
    )
}
