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
import kotlin.random.Random

class QuizGenerationTest {
    @Test
    fun reusesOnlyMatchingQuestionsUpToTheRequestedTypeQuota() {
        val typeCounts = currentTypes.associateTo(linkedMapOf()) { it to 1 }
        val questionPool = listOf(
            question(QuestionType.MULTIPLE_CHOICE, 1),
            question(QuestionType.MULTIPLE_CHOICE, 2),
            question(QuestionType.MULTIPLE_CHOICE, 3),
            question(QuestionType.TRUE_FALSE, 4),
            question(QuestionType.TRUE_FALSE, 5),
        )

        val plan = planQuizReuse(typeCounts, questionPool, Random(1))

        assertEquals(
            mapOf(QuestionType.MULTIPLE_CHOICE to 1, QuestionType.TRUE_FALSE to 1),
            plan.reusedQuestions.groupingBy(QuizQuestion::type).eachCount(),
        )
        assertEquals(
            linkedMapOf(
                QuestionType.FILL_IN_THE_BLANK to 1,
                QuestionType.IDENTIFICATION to 1,
                QuestionType.EXPLANATION to 1,
            ),
            plan.missingTypeCounts,
        )
    }

    @Test
    fun fillsOnlyTheMissingSlotsAndKeepsTheFiveQuestionDistribution() {
        val typeCounts = linkedMapOf(
            QuestionType.MULTIPLE_CHOICE to 3,
            QuestionType.FILL_IN_THE_BLANK to 2,
        )
        val questionPool = listOf(
            question(QuestionType.MULTIPLE_CHOICE, 1),
            question(QuestionType.MULTIPLE_CHOICE, 2),
            question(QuestionType.TRUE_FALSE, 3),
            question(QuestionType.TRUE_FALSE, 4),
            question(QuestionType.TRUE_FALSE, 5),
        )
        val plan = planQuizReuse(typeCounts, questionPool, Random(1))
        val generated = listOf(
            question(QuestionType.MULTIPLE_CHOICE, 6),
            question(QuestionType.FILL_IN_THE_BLANK, 7),
            question(QuestionType.FILL_IN_THE_BLANK, 8),
        )

        val composed = composeQuizQuestions(typeCounts, plan.reusedQuestions, generated)

        assertEquals(
            linkedMapOf(
                QuestionType.MULTIPLE_CHOICE to 1,
                QuestionType.FILL_IN_THE_BLANK to 2,
            ),
            plan.missingTypeCounts,
        )
        assertEquals(
            listOf(
                QuestionType.MULTIPLE_CHOICE,
                QuestionType.MULTIPLE_CHOICE,
                QuestionType.MULTIPLE_CHOICE,
                QuestionType.FILL_IN_THE_BLANK,
                QuestionType.FILL_IN_THE_BLANK,
            ),
            composed.map(QuizQuestion::type),
        )
        assertEquals(listOf("q1", "q2", "q3", "q4", "q5"), composed.map(QuizQuestion::id))
    }

    @Test
    fun duplicateSavedQuestionsCountOnlyOnceInTheReusePool() {
        val saved = question(QuestionType.MULTIPLE_CHOICE, 1)

        val plan = planQuizReuse(
            typeCounts = linkedMapOf(
                QuestionType.MULTIPLE_CHOICE to 2,
                QuestionType.FILL_IN_THE_BLANK to 3,
            ),
            questionPool = listOf(saved, saved.copy(id = "another-row")),
            random = Random(1),
        )

        assertEquals(1, plan.reusedQuestions.size)
        assertEquals(
            linkedMapOf(
                QuestionType.MULTIPLE_CHOICE to 1,
                QuestionType.FILL_IN_THE_BLANK to 3,
            ),
            plan.missingTypeCounts,
        )
    }

    @Test
    fun parsesEachTypeWithoutTypeIndexOrCriteriaFields() {
        val questions = currentTypes.mapIndexed { index, type ->
            parseAndValidateQuestion(
                response = responseFor(type, index + 1),
                type = type,
                passage = passage,
                index = index,
            )
        }

        assertEquals(currentTypes, questions.map(QuizQuestion::type))
        assertEquals(listOf("q1", "q2", "q3", "q4", "q5"), questions.map(QuizQuestion::id))
        assertTrue(questions.all { it.sourceId == passage.sourceId })
        assertEquals(0, (questions[0].answer as QuestionAnswer.MultipleChoice).answerIndex)
        assertEquals(listOf("True", "False"), (questions[3].answer as QuestionAnswer.MultipleChoice).options)
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
    fun keepsTheAnswerAndThreeDistractorsWhenTheModelReturnsExtraOptions() {
        val question = parseAndValidateQuestion(
            response =
                """{"question":"What is the forecasting goal?","options":["Recommend quantity","Identify shortages","Customer availability","Production recommendation","Predict demand"],"answer":"Predict demand"}""",
            type = QuestionType.MULTIPLE_CHOICE,
            passage = passage,
            index = 0,
        )

        val answer = question.answer as QuestionAnswer.MultipleChoice
        assertEquals(
            listOf("Recommend quantity", "Identify shortages", "Customer availability", "Predict demand"),
            answer.options,
        )
        assertEquals(3, answer.answerIndex)
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
    fun mapsFalseBooleanToTheFalseChoice() {
        val question = parseAndValidateQuestion(
            response = """{"question":"Is Beta first?","answer":false}""",
            type = QuestionType.TRUE_FALSE,
            passage = passage,
            index = 0,
        )

        val answer = question.answer as QuestionAnswer.MultipleChoice
        assertEquals(listOf("True", "False"), answer.options)
        assertEquals(1, answer.answerIndex)
    }

    @Test
    fun rejectsStringInsteadOfBooleanForTrueFalse() {
        assertThrows(IllegalArgumentException::class.java) {
            parseAndValidateQuestion(
                response = """{"question":"Is Alpha first?","answer":"true"}""",
                type = QuestionType.TRUE_FALSE,
                passage = passage,
                index = 0,
            )
        }
    }

    @Test
    fun rejectsStatementOrOpenEndedTrueFalsePrompt() {
        listOf(
            "Alpha is first.",
            "What is first?",
        ).forEach { prompt ->
            assertThrows(IllegalArgumentException::class.java) {
                parseAndValidateQuestion(
                    response = """{"question":"$prompt","answer":true}""",
                    type = QuestionType.TRUE_FALSE,
                    passage = passage,
                    index = 0,
                )
            }
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
        val instructions = currentTypes.map(::buildGenerationSystemInstruction)

        assertEquals(currentTypes.size, instructions.toSet().size)
        instructions.forEach { instruction ->
            assertFalse(instruction.contains("\"type\""))
            assertFalse(instruction.contains("answerIndex"))
            assertFalse(instruction.contains("criteria", ignoreCase = true))
        }
        assertTrue(instructions.first().contains("\"answer\":\"option 1\""))
    }

    @Test
    fun generatesFiveQuestionsInFiveFreshTypeSpecificCalls() = runBlocking {
        val types = currentTypes
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
        assertTrue(sentRequests.all { it.contains(passage.content) })
        assertTrue(sentRequests.all { it.contains("FOCUS EXCERPT FOR THIS QUESTION") })
        assertEquals(5, sentRequests.map { it.substringAfterLast("copied from the source):\n") }.toSet().size)
        assertTrue(sentInstructions.drop(1).all { it.contains("Do not repeat") })
    }

    @Test
    fun generatesOnlyTheMissingSlotsAndAvoidsAReusedPrompt() = runBlocking {
        val reused = question(QuestionType.MULTIPLE_CHOICE, 1)
        val generatedTypes = listOf(
            QuestionType.MULTIPLE_CHOICE,
            QuestionType.FILL_IN_THE_BLANK,
            QuestionType.FILL_IN_THE_BLANK,
        )
        val sentInstructions = mutableListOf<String>()
        val progress = mutableListOf<Pair<Int, Int>>()
        var call = 0
        val generator = QuizGenerator(
            onQuestionStarted = { current, total -> progress += current to total },
        ) { _, instruction, _ ->
            sentInstructions += instruction
            val type = generatedTypes[call]
            responseFor(type, ++call + 5)
        }

        val result = generator.generate(
            request = QuizGenerationRequest(
                model = quizModel,
                passage = passage,
                typeCounts = linkedMapOf(
                    QuestionType.MULTIPLE_CHOICE to 1,
                    QuestionType.FILL_IN_THE_BLANK to 2,
                ),
            ),
            existingQuestions = listOf(reused),
        )

        assertEquals(generatedTypes, result.questions.map(QuizQuestion::type))
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progress)
        assertTrue(sentInstructions.first().contains(reused.prompt))
    }

    @Test
    fun focusesNumberedPassagesOnOneItemAtATime() {
        val passage = """
            General objective

            1. Predict demand from historical sales.
            2. Recommend production from available capacity.
            3. Identify possible ingredient shortages.
        """.trimIndent()

        val request = buildGenerationRequest(passage, focusIndex = 1)

        assertTrue(request.contains(passage))
        assertTrue(
            request.endsWith(
                "FOCUS EXCERPT FOR THIS QUESTION (copied from the source):\n" +
                    "2. Recommend production from available capacity.",
            ),
        )
    }

    @Test
    fun duplicateRetryMovesToAnotherFocusSection() = runBlocking {
        val requests = mutableListOf<String>()
        val instructions = mutableListOf<String>()
        val responses = ArrayDeque(
            listOf(
                """{"question":"Identify the first term.","answer":"Alpha"}""",
                """{"question":"Identify the first term.","answer":"Alpha"}""",
                """{"question":"Identify the third term.","answer":"Gamma"}""",
                """{"question":"Identify the fourth term.","answer":"Delta"}""",
                """{"question":"Identify the fifth term.","answer":"Epsilon"}""",
                """{"question":"Identify what Beta represents.","answer":"second"}""",
            ),
        )
        val generator = QuizGenerator { _, instruction, request ->
            instructions += instruction
            requests += request
            responses.removeFirst()
        }

        val result = generator.generate(
            QuizGenerationRequest(
                model = quizModel,
                passage = passage,
                typeCounts = mapOf(QuestionType.IDENTIFICATION to 5),
            ),
        )

        assertEquals(5, result.questions.size)
        assertTrue(instructions[2].contains("different from the existing questions"))
        assertTrue(requests[1].endsWith("Beta is second."))
        assertTrue(requests[2].endsWith("Gamma explains change."))
    }

    @Test
    fun missingFieldRetryNamesTheRequiredField() = runBlocking {
        val instructions = mutableListOf<String>()
        val responses = ArrayDeque(
            listOf(
                """{"question":"Which is first?","options":["Alpha","Beta","Gamma","Delta"]}""",
            ) + (1..5).map { responseFor(QuestionType.MULTIPLE_CHOICE, it) },
        )
        val generator = QuizGenerator { _, instruction, _ ->
            instructions += instruction
            responses.removeFirst()
        }

        generator.generate(
            QuizGenerationRequest(
                model = quizModel,
                passage = passage,
                typeCounts = mapOf(QuestionType.MULTIPLE_CHOICE to 5),
            ),
        )

        assertTrue(instructions[1].contains("Required JSON field 'answer' was missing."))
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
        QuestionType.TRUE_FALSE ->
            """{"question":"Is Alpha first in item $number?","answer":true}"""
        QuestionType.MATCHING ->
            """{"question":"Match item $number.","pairs":[{"left":"Alpha","right":"First"},{"left":"Beta","right":"Second"},{"left":"Gamma","right":"Change"}]}"""
        QuestionType.EXPLANATION ->
            """{"question":"Explain item $number.","answer":"Gamma explains change."}"""
    }

    private fun question(type: QuestionType, number: Int): QuizQuestion =
        parseAndValidateQuestion(
            response = responseFor(type, number),
            type = type,
            passage = passage,
            index = number - 1,
        )

    private val passage = PassageEntity(
        id = 1,
        materialId = 1,
        sourceId = "TXT-P001",
        position = 0,
        content =
            "Alpha is first. Beta is second. Gamma explains change. " +
                "Delta is fourth. Epsilon is fifth.",
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

    private val currentTypes = listOf(
        QuestionType.MULTIPLE_CHOICE,
        QuestionType.FILL_IN_THE_BLANK,
        QuestionType.IDENTIFICATION,
        QuestionType.TRUE_FALSE,
        QuestionType.EXPLANATION,
    )
}
