package com.example.bukal.ai

import com.example.bukal.data.local.PassageEntity
import com.example.bukal.data.model.ModelDownloadSpec
import com.example.bukal.data.model.ModelPurpose
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizGenerationTest {
    @Test
    fun parsesEachTypeWithoutTypeIndexOrCriteriaFields() {
        val questions = QuestionType.entries.mapIndexed { index, type ->
            parseAndValidateQuestion(
                response = responseFor(type, index + 1),
                type = type,
                passage = passage,
                index = index,
            )
        }

        assertEquals(QuestionType.entries, questions.map(QuizQuestion::type))
        assertEquals(listOf("q1", "q2", "q3", "q4", "q5"), questions.map(QuizQuestion::id))
        assertTrue(questions.all { it.sourceId == passage.sourceId })
        assertEquals(0, (questions[0].answer as QuestionAnswer.MultipleChoice).answerIndex)
        assertEquals(3, (questions[3].answer as QuestionAnswer.Matching).pairs.size)
    }

    @Test
    fun acceptsOneOuterJsonCodeFence() {
        val question = parseAndValidateQuestion(
            response = "```json\n${responseFor(QuestionType.MULTIPLE_CHOICE, 1)}\n```",
            type = QuestionType.MULTIPLE_CHOICE,
            passage = passage,
            index = 0,
        )

        assertEquals("Question 1?", question.prompt)
    }

    @Test
    fun acceptsHarmlessExtraFields() {
        val question = parseAndValidateQuestion(
            response = responseFor(QuestionType.MULTIPLE_CHOICE, 1)
                .dropLast(1) + ",\"type\":\"multiple_choice\"}",
            type = QuestionType.MULTIPLE_CHOICE,
            passage = passage,
            index = 0,
        )

        assertEquals(QuestionType.MULTIPLE_CHOICE, question.type)
    }

    @Test
    fun derivesAnswerIndexFromReturnedAnswerText() {
        val question = parseAndValidateQuestion(
            response =
                """{"question":"Which is second?","options":["Alpha","Beta","Gamma","Delta"],"answer":"Beta"}""",
            type = QuestionType.MULTIPLE_CHOICE,
            passage = passage,
            index = 0,
        )

        assertEquals(1, (question.answer as QuestionAnswer.MultipleChoice).answerIndex)
    }

    @Test
    fun rejectsAnswerThatDoesNotMatchAnOption() {
        assertThrows(IllegalArgumentException::class.java) {
            parseAndValidateQuestion(
                response =
                    """{"question":"Which is first?","options":["Alpha","Beta","Gamma","Delta"],"answer":"Other"}""",
                type = QuestionType.MULTIPLE_CHOICE,
                passage = passage,
                index = 0,
            )
        }
    }

    @Test
    fun acceptsNegativeMultipleChoiceWording() {
        val question = parseAndValidateQuestion(
            response = responseFor(QuestionType.MULTIPLE_CHOICE, 1)
                .replace("Question 1?", "Which option is NOT supported?"),
            type = QuestionType.MULTIPLE_CHOICE,
            passage = passage,
            index = 0,
        )

        assertEquals("Which option is NOT supported?", question.prompt)
    }

    @Test
    fun acceptsFillInTheBlankWithOneUnderscoreOrNoBlank() {
        val responses = listOf(
            """{"question":"BenteHangin uses _ when the motor overheats.","answer":"automatic shutdown"}""",
            """{"question":"What does BenteHangin do when the motor overheats?","answer":"automatic shutdown"}""",
        )

        responses.forEach { response ->
            val question = parseAndValidateQuestion(
                response = response,
                type = QuestionType.FILL_IN_THE_BLANK,
                passage = passage,
                index = 0,
            )
            assertEquals(QuestionType.FILL_IN_THE_BLANK, question.type)
        }
    }

    @Test
    fun acceptsTwoUsableMatchingPairs() {
        val question = parseAndValidateQuestion(
            response =
                """{"question":"Match the items.","pairs":[{"left":"Alpha","right":"First"},{"left":"Beta","right":"Second"}]}""",
            type = QuestionType.MATCHING,
            passage = passage,
            index = 0,
        )

        assertEquals(2, (question.answer as QuestionAnswer.Matching).pairs.size)
    }

    @Test
    fun buildsDistinctMinimalPromptForEveryQuestionType() {
        val instructions = QuestionType.entries.map(::buildGenerationSystemInstruction)

        assertEquals(QuestionType.entries.size, instructions.toSet().size)
        instructions.forEach { instruction ->
            assertFalse(instruction.contains("\"type\""))
            assertFalse(instruction.contains("answerIndex"))
            assertFalse(instruction.contains("criteria", ignoreCase = true))
        }
        assertTrue(instructions.first().contains("\"answer\":\"option 1\""))
    }

    @Test
    fun generatesFiveQuestionsInFiveFreshTypeSpecificCalls() = runBlocking {
        val types = QuestionType.entries
        val sentInstructions = mutableListOf<String>()
        val sentRequests = mutableListOf<String>()
        val progress = mutableListOf<Pair<Int, Int>>()
        var call = 0
        val generator = QuizGenerator(
            onQuestionStarted = { current, total -> progress += current to total },
        ) { _, systemInstruction, request ->
            sentInstructions += systemInstruction
            sentRequests += request
            responseFor(types[call], ++call)
        }

        val result = generator.generate(
            QuizGenerationRequest(
                model = quizModel,
                passage = passage,
                typeCounts = types.associateTo(linkedMapOf()) { it to 1 },
            ),
        )

        assertEquals(types, result.questions.map(QuizQuestion::type))
        assertEquals(0, result.failedQuestionCount)
        assertEquals(5, sentInstructions.size)
        assertEquals((1..5).map { it to 5 }, progress)
        assertTrue(sentRequests.all { it == passage.content })
        assertTrue(sentInstructions.drop(1).all { it.contains("Do not repeat") })
    }

    @Test
    fun retriesOnlyTheInvalidQuestionTwice() = runBlocking {
        val responses = ArrayDeque(
            listOf("not json", "still not json") +
                (1..5).map { responseFor(QuestionType.MULTIPLE_CHOICE, it) },
        )
        val validationFailures = mutableListOf<String>()
        val generator = QuizGenerator(onValidationFailure = validationFailures::add) { _, _, _ ->
            responses.removeFirst()
        }

        val result = generator.generate(
            QuizGenerationRequest(
                model = quizModel,
                passage = passage,
                typeCounts = mapOf(QuestionType.MULTIPLE_CHOICE to 5),
            ),
        )

        assertEquals(5, result.questions.size)
        assertEquals(0, result.failedQuestionCount)
        assertEquals(2, validationFailures.size)
        assertTrue(validationFailures[0].contains("Question 1 rejected on attempt 1"))
        assertTrue(validationFailures[1].contains("Question 1 rejected on attempt 2"))
        assertTrue(responses.isEmpty())
    }

    @Test
    fun skipsQuestionAfterTwoRetriesAndContinuesGenerating() = runBlocking {
        val responses = ArrayDeque(
            listOf("not json", "still not json", "invalid again") +
                (2..5).map { responseFor(QuestionType.MULTIPLE_CHOICE, it) },
        )
        val generator = QuizGenerator { _, _, _ -> responses.removeFirst() }

        val result = generator.generate(
            QuizGenerationRequest(
                model = quizModel,
                passage = passage,
                typeCounts = mapOf(QuestionType.MULTIPLE_CHOICE to 5),
            ),
        )

        assertEquals(4, result.questions.size)
        assertEquals(1, result.failedQuestionCount)
        assertTrue(responses.isEmpty())
    }

    @Test
    fun reportsAnErrorOnlyWhenEveryQuestionFails() {
        var calls = 0
        val generator = QuizGenerator { _, _, _ ->
            calls += 1
            "not json"
        }

        assertThrows(QuizGenerationException::class.java) {
            runBlocking {
                generator.generate(
                    QuizGenerationRequest(
                        model = quizModel,
                        passage = passage,
                        typeCounts = mapOf(QuestionType.MULTIPLE_CHOICE to 5),
                    ),
                )
            }
        }
        assertEquals(15, calls)
    }

    private fun responseFor(type: QuestionType, number: Int): String = when (type) {
        QuestionType.MULTIPLE_CHOICE ->
            """{"question":"Question $number?","options":["Alpha","Beta","Gamma","Delta"],"answer":"Alpha"}"""
        QuestionType.FILL_IN_THE_BLANK ->
            """{"question":"___ is second in item $number.","answer":"Beta"}"""
        QuestionType.IDENTIFICATION ->
            """{"question":"Identify what is first in item $number.","answer":"Alpha"}"""
        QuestionType.MATCHING ->
            """{"question":"Match item $number.","pairs":[{"left":"Alpha","right":"First"},{"left":"Beta","right":"Second"},{"left":"Gamma","right":"Change"}]}"""
        QuestionType.EXPLANATION ->
            """{"question":"Explain item $number.","answer":"Gamma explains change."}"""
    }

    private val passage = PassageEntity(
        id = 1,
        materialId = 1,
        sourceId = "TXT-P001",
        position = 0,
        content = "Alpha is first. Beta is second. Gamma explains change.",
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
}
