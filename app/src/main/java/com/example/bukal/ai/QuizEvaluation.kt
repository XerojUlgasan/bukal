package com.example.bukal.ai

import com.example.bukal.data.local.PassageEntity
import com.example.bukal.data.model.ModelDownloadSpec
import com.example.bukal.data.search.DocumentSearchResult
import kotlinx.coroutines.CancellationException

enum class QuizResultVerdict {
    CORRECT,
    INCORRECT,
    UNANSWERED,
}

data class QuizQuestionResult(
    val question: QuizQuestion,
    val learnerAnswer: String?,
    val expectedAnswer: String,
    val verdict: QuizResultVerdict,
    val earnedPoints: Double,
    val possiblePoints: Double,
    val wasAiEvaluated: Boolean = false,
)

data class QuizResultSummary(val items: List<QuizQuestionResult>) {
    val earnedPoints: Double = items.sumOf(QuizQuestionResult::earnedPoints)
    val possiblePoints: Double = items.sumOf(QuizQuestionResult::possiblePoints)
}

data class QuizEvaluationRequest(
    val model: ModelDownloadSpec,
    val passage: PassageEntity,
    val questions: List<QuizQuestion>,
    val selectedOptions: Map<String, Int>,
    val textResponses: Map<String, String>,
    val matchingSelections: Map<String, String>,
)

data class QuizExplanationRequest(
    val model: ModelDownloadSpec,
    val passage: PassageEntity,
    val result: QuizQuestionResult,
)

class QuizEvaluator(
    private val search: suspend (
        query: String,
        passageId: Long,
        limit: Int,
    ) -> List<DocumentSearchResult>,
    private val complete: suspend (
        model: ModelDownloadSpec,
        systemInstruction: String,
        request: String,
    ) -> String,
) {
    suspend fun evaluate(request: QuizEvaluationRequest): QuizResultSummary {
        require(request.questions.size in 1..QuizGenerator.QUESTION_COUNT) {
            "A completed quiz must contain between 1 and ${QuizGenerator.QUESTION_COUNT} questions."
        }
        return QuizResultSummary(
            request.questions.map { question ->
                when (question.answer) {
                    is QuestionAnswer.MultipleChoice -> evaluateMultipleChoice(question, request)
                    is QuestionAnswer.Matching -> evaluateMatching(question, request)
                    is QuestionAnswer.OpenResponse -> evaluateOpenResponse(question, request)
                }
            },
        )
    }

    suspend fun explain(request: QuizExplanationRequest): String {
        val answer = request.result.question.answer as? QuestionAnswer.OpenResponse
            ?: error("Only written answers can be explained by local AI.")
        val learnerAnswer = request.result.learnerAnswer
            ?.takeIf(String::isNotBlank)
            ?: error("An unanswered question cannot be explained.")
        val matches = search(
            buildSearchQuery(request.result.question, learnerAnswer, answer),
            request.passage.id,
            MATCH_LIMIT,
        ).take(MATCH_LIMIT)
        require(matches.isNotEmpty()) {
            "No embedded lesson sections are available for an explanation."
        }
        val explanationRequest = buildExplanationRequest(
            question = request.result.question.prompt,
            learnerAnswer = learnerAnswer,
            referenceAnswer = answer.referenceAnswer,
            correct = request.result.verdict == QuizResultVerdict.CORRECT,
            matches = matches,
        )
        return complete(
            request.model,
            SystemPrompts.ANSWER_EXPLANATION,
            explanationRequest,
        ).trim().ifEmpty { error("The local model returned no explanation.") }
    }

    private fun evaluateMultipleChoice(
        question: QuizQuestion,
        request: QuizEvaluationRequest,
    ): QuizQuestionResult {
        val answer = question.answer as QuestionAnswer.MultipleChoice
        val selectedIndex = request.selectedOptions[question.id]
        val verdict = when (selectedIndex) {
            null -> QuizResultVerdict.UNANSWERED
            answer.answerIndex -> QuizResultVerdict.CORRECT
            else -> QuizResultVerdict.INCORRECT
        }
        return deterministicResult(
            question = question,
            learnerAnswer = selectedIndex?.let(answer.options::getOrNull),
            expectedAnswer = answer.options[answer.answerIndex],
            verdict = verdict,
        )
    }

    private fun evaluateMatching(
        question: QuizQuestion,
        request: QuizEvaluationRequest,
    ): QuizQuestionResult {
        val answer = question.answer as QuestionAnswer.Matching
        val selections = answer.pairs.associate { pair ->
            pair.leftId to request.matchingSelections["${question.id}:${pair.leftId}"]
        }
        val complete = selections.values.all { it != null }
        val verdict = when {
            !complete -> QuizResultVerdict.UNANSWERED
            answer.pairs.all { selections[it.leftId] == it.rightId } -> QuizResultVerdict.CORRECT
            else -> QuizResultVerdict.INCORRECT
        }
        val rightTextById = answer.pairs.associate { it.rightId to it.rightText }
        val learnerAnswer = selections.entries
            .mapNotNull { (leftId, rightId) ->
                val left = answer.pairs.first { it.leftId == leftId }.leftText
                rightId?.let { "$left → ${rightTextById[it]}" }
            }
            .joinToString("; ")
            .ifBlank { null }
        return deterministicResult(
            question = question,
            learnerAnswer = learnerAnswer,
            expectedAnswer = answer.pairs.joinToString("; ") { "${it.leftText} → ${it.rightText}" },
            verdict = verdict,
        )
    }

    private suspend fun evaluateOpenResponse(
        question: QuizQuestion,
        request: QuizEvaluationRequest,
    ): QuizQuestionResult {
        val answer = question.answer as QuestionAnswer.OpenResponse
        val learnerAnswer = request.textResponses[question.id]?.trim().orEmpty()
        if (learnerAnswer.isBlank()) {
            return deterministicResult(
                question = question,
                learnerAnswer = null,
                expectedAnswer = answer.referenceAnswer,
                verdict = QuizResultVerdict.UNANSWERED,
            )
        }

        val isCorrect = if (learnerAnswer.isObviousNonAnswer()) {
            false
        } else {
            try {
                val matches = search(
                    buildSearchQuery(question, learnerAnswer, answer),
                    request.passage.id,
                    MATCH_LIMIT,
                ).take(MATCH_LIMIT)
                matches.isNotEmpty() && requestAiEvaluation(
                    model = request.model,
                    question = question,
                    learnerAnswer = learnerAnswer,
                    answer = answer,
                    matches = matches,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
        }
        return QuizQuestionResult(
            question = question,
            learnerAnswer = learnerAnswer,
            expectedAnswer = answer.referenceAnswer,
            verdict = if (isCorrect) QuizResultVerdict.CORRECT else QuizResultVerdict.INCORRECT,
            earnedPoints = if (isCorrect) 1.0 else 0.0,
            possiblePoints = 1.0,
            wasAiEvaluated = true,
        )
    }

    private suspend fun requestAiEvaluation(
        model: ModelDownloadSpec,
        question: QuizQuestion,
        learnerAnswer: String,
        answer: QuestionAnswer.OpenResponse,
        matches: List<DocumentSearchResult>,
    ): Boolean {
        val requestText = buildEvaluationRequest(
            question = question.prompt,
            learnerAnswer = learnerAnswer,
            referenceAnswer = answer.referenceAnswer,
            matches = matches,
        )
        var retry = false
        repeat(MAX_EVALUATION_ATTEMPTS) { attempt ->
            val response = complete(
                model,
                buildEvaluationSystemInstruction(retry),
                requestText,
            )
            when (unwrapJsonCodeFence(response).trim().lowercase()) {
                "true" -> return true
                "false" -> return false
            }
            retry = true
            if (attempt == MAX_EVALUATION_ATTEMPTS - 1) return false
        }
        return false
    }

    private fun deterministicResult(
        question: QuizQuestion,
        learnerAnswer: String?,
        expectedAnswer: String,
        verdict: QuizResultVerdict,
    ) = QuizQuestionResult(
        question = question,
        learnerAnswer = learnerAnswer,
        expectedAnswer = expectedAnswer,
        verdict = verdict,
        earnedPoints = if (verdict == QuizResultVerdict.CORRECT) 1.0 else 0.0,
        possiblePoints = 1.0,
    )

    companion object {
        const val MATCH_LIMIT = 5
        private const val MAX_EVALUATION_ATTEMPTS = 2
    }
}

private fun buildSearchQuery(
    question: QuizQuestion,
    learnerAnswer: String,
    answer: QuestionAnswer.OpenResponse,
): String = listOf(
    question.prompt,
    answer.referenceAnswer,
    learnerAnswer,
).joinToString("\n")

private fun String.isObviousNonAnswer(): Boolean = lowercase().trim() in setOf(
    "idk",
    "i don't know",
    "i dont know",
    "don't know",
    "dont know",
    "no idea",
    "hindi ko alam",
    "di ko alam",
)

internal fun buildEvaluationSystemInstruction(retry: Boolean = false): String = buildString {
    append(SystemPrompts.ANSWER_EVALUATION)
    if (retry) {
        append(" Your previous response was invalid. Return only true or false.")
    }
}

private fun buildEvaluationRequest(
    question: String,
    learnerAnswer: String,
    referenceAnswer: String,
    matches: List<DocumentSearchResult>,
): String = buildString {
    appendLine("QUESTION:")
    appendLine(question)
    appendLine()
    appendLine("REFERENCE ANSWER:")
    appendLine(referenceAnswer)
    appendLine()
    appendMatches(matches)
    appendLine()
    appendLine("LEARNER ANSWER:")
    appendLine(learnerAnswer)
    appendLine()
    append("DECISION: Is the learner answer itself correct? Return only true or false.")
}

private fun buildExplanationRequest(
    question: String,
    learnerAnswer: String,
    referenceAnswer: String,
    correct: Boolean,
    matches: List<DocumentSearchResult>,
): String = buildString {
    appendLine("QUESTION:")
    appendLine(question)
    appendLine()
    appendLine("REFERENCE ANSWER:")
    appendLine(referenceAnswer)
    appendLine()
    appendMatches(matches)
    appendLine()
    appendLine("LEARNER ANSWER:")
    appendLine(learnerAnswer)
    appendLine()
    appendLine("RESULT:")
    appendLine(if (correct) "correct" else "incorrect")
    appendLine()
    append("TASK: Briefly explain why the learner answer received this result.")
}

private fun StringBuilder.appendMatches(matches: List<DocumentSearchResult>) {
    appendLine("RETRIEVED SOURCE MATCHES:")
    matches.forEachIndexed { index, match ->
        appendLine("[${index + 1}] ${match.sourceId}: ${match.text}")
    }
}
