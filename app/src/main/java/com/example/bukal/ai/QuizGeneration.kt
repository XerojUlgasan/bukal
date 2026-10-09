package com.example.bukal.ai

import com.example.bukal.data.local.PassageEntity
import com.example.bukal.data.model.ModelDownloadSpec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class QuestionType(val wireName: String) {
    @SerialName("multiple_choice")
    MULTIPLE_CHOICE("multiple_choice"),

    @SerialName("fill_in_the_blank")
    FILL_IN_THE_BLANK("fill_in_the_blank"),

    @SerialName("identification")
    IDENTIFICATION("identification"),

    @SerialName("matching")
    MATCHING("matching"),

    @SerialName("explanation")
    EXPLANATION("explanation"),
}

sealed interface QuestionAnswer {
    data class MultipleChoice(
        val options: List<String>,
        val answerIndex: Int,
    ) : QuestionAnswer

    data class OpenResponse(
        val referenceAnswer: String,
    ) : QuestionAnswer

    data class Matching(val pairs: List<MatchingPair>) : QuestionAnswer
}

data class MatchingPair(
    val leftId: String,
    val leftText: String,
    val rightId: String,
    val rightText: String,
)

data class QuizQuestion(
    val id: String,
    val type: QuestionType,
    val prompt: String,
    val sourceId: String,
    val answer: QuestionAnswer,
)

data class QuizGenerationRequest(
    val model: ModelDownloadSpec,
    val passage: PassageEntity,
    val typeCounts: Map<QuestionType, Int>,
)

data class QuizGenerationResult(
    val questions: List<QuizQuestion>,
    val failedQuestionCount: Int,
)

class QuizGenerationException(message: String, cause: Throwable? = null) : Exception(message, cause)

class QuizGenerator(
    private val onValidationFailure: (String) -> Unit = {},
    private val onQuestionStarted: (current: Int, total: Int) -> Unit = { _, _ -> },
    private val complete: suspend (
        model: ModelDownloadSpec,
        systemInstruction: String,
        request: String,
    ) -> String,
) {
    suspend fun generate(request: QuizGenerationRequest): QuizGenerationResult {
        require(request.typeCounts.isNotEmpty()) { "Select at least one question type." }
        require(request.typeCounts.values.all { it > 0 }) { "Question counts must be positive." }
        require(request.typeCounts.values.sum() == QUESTION_COUNT) {
            "A quiz must request exactly $QUESTION_COUNT questions."
        }

        val requestedTypes = request.typeCounts.entries.flatMap { (type, count) -> List(count) { type } }
        val questions = mutableListOf<QuizQuestion>()
        var failedQuestionCount = 0
        requestedTypes.forEachIndexed { index, type ->
            onQuestionStarted(index + 1, QUESTION_COUNT)
            try {
                questions += generateQuestion(request, type, index, questions.map(QuizQuestion::prompt))
            } catch (_: QuizGenerationException) {
                failedQuestionCount += 1
            }
        }
        if (questions.isEmpty()) {
            throw QuizGenerationException("The local model could not generate any valid questions.")
        }
        return QuizGenerationResult(questions, failedQuestionCount)
    }

    private suspend fun generateQuestion(
        request: QuizGenerationRequest,
        type: QuestionType,
        index: Int,
        existingQuestions: List<String>,
    ): QuizQuestion {
        var validationMessage: String? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            val response = complete(
                request.model,
                buildGenerationSystemInstruction(type, existingQuestions, validationMessage),
                request.passage.content,
            )
            try {
                return parseAndValidateQuestion(
                    response = response,
                    type = type,
                    passage = request.passage,
                    index = index,
                    existingQuestions = existingQuestions,
                )
            } catch (error: SerializationException) {
                validationMessage = "The response was not valid JSON."
                onValidationFailure(
                    "Question ${index + 1} rejected on attempt ${attempt + 1}: ${error.message.orEmpty()}",
                )
            } catch (error: IllegalArgumentException) {
                validationMessage = error.message.orEmpty().take(MAX_VALIDATION_MESSAGE_CHARACTERS)
                onValidationFailure(
                    "Question ${index + 1} rejected on attempt ${attempt + 1}: $validationMessage",
                )
            }
        }
        throw QuizGenerationException(
            "The local model returned invalid ${type.wireName.replace('_', ' ')} data after " +
                "$MAX_RETRIES retries. " +
                "Try another passage or model.",
        )
    }

    companion object {
        const val QUESTION_COUNT = 5
        const val MAX_RETRIES = 2
        private const val MAX_ATTEMPTS = MAX_RETRIES + 1
        private const val MAX_VALIDATION_MESSAGE_CHARACTERS = 300
    }
}

@Serializable
private data class GeneratedMultipleChoicePayload(
    val question: String,
    val options: List<String>,
    val answer: String,
)

@Serializable
private data class GeneratedOpenResponsePayload(
    val question: String,
    val answer: String,
)

@Serializable
private data class GeneratedMatchingPayload(
    val question: String,
    val pairs: List<GeneratedMatchingPairPayload>,
)

@Serializable
private data class GeneratedMatchingPairPayload(
    val left: String,
    val right: String,
)

internal val QuizJson = Json {
    ignoreUnknownKeys = true
    isLenient = false
}

internal fun parseAndValidateQuestion(
    response: String,
    type: QuestionType,
    passage: PassageEntity,
    index: Int,
    existingQuestions: List<String> = emptyList(),
): QuizQuestion {
    val json = unwrapJsonCodeFence(response)
    val (question, answer) = when (type) {
        QuestionType.MULTIPLE_CHOICE -> validateMultipleChoice(
            QuizJson.decodeFromString<GeneratedMultipleChoicePayload>(json),
        )
        QuestionType.FILL_IN_THE_BLANK ->
            validateOpenResponse(QuizJson.decodeFromString<GeneratedOpenResponsePayload>(json))
        QuestionType.IDENTIFICATION,
        QuestionType.EXPLANATION,
        -> validateOpenResponse(QuizJson.decodeFromString<GeneratedOpenResponsePayload>(json))
        QuestionType.MATCHING -> validateMatching(
            QuizJson.decodeFromString<GeneratedMatchingPayload>(json),
        )
    }
    val normalizedQuestion = normalizeForDuplicateCheck(question)
    require(question.isNotBlank() && existingQuestions.none {
        normalizeForDuplicateCheck(it) == normalizedQuestion
    }) {
        "Question must be non-empty and different from the existing questions."
    }
    return QuizQuestion(
        id = "q${index + 1}",
        type = type,
        prompt = question.trim(),
        sourceId = passage.sourceId,
        answer = answer,
    )
}

private fun validateMultipleChoice(
    payload: GeneratedMultipleChoicePayload,
): Pair<String, QuestionAnswer.MultipleChoice> {
    val options = payload.options.map(String::trim)
    require(options.size == 4 && options.all { it.isNotBlank() }) {
        "Multiple choice requires four non-empty options."
    }
    require(options.map(String::lowercase).toSet().size == 4) {
        "Multiple-choice options must be unique."
    }
    val answerIndex = options.indexOfFirst { it.equals(payload.answer.trim(), ignoreCase = true) }
    require(answerIndex >= 0) {
        "Multiple-choice answer must exactly match one option."
    }
    return payload.question to QuestionAnswer.MultipleChoice(options, answerIndex)
}

private fun validateOpenResponse(
    payload: GeneratedOpenResponsePayload,
): Pair<String, QuestionAnswer.OpenResponse> {
    require(payload.answer.isNotBlank()) {
        "Open-response questions require a reference answer."
    }
    return payload.question to QuestionAnswer.OpenResponse(
        referenceAnswer = payload.answer.trim(),
    )
}

private fun validateMatching(
    payload: GeneratedMatchingPayload,
): Pair<String, QuestionAnswer.Matching> {
    require(payload.pairs.size >= 2) { "Matching questions require at least two pairs." }
    require(payload.pairs.all { it.left.isNotBlank() && it.right.isNotBlank() }) {
        "Matching pair fields must not be empty."
    }
    require(payload.pairs.map { it.left.trim().lowercase() }.toSet().size == payload.pairs.size) {
        "Matching left text must be unique."
    }
    require(payload.pairs.map { it.right.trim().lowercase() }.toSet().size == payload.pairs.size) {
        "Matching right text must be unique."
    }
    return payload.question to QuestionAnswer.Matching(
        payload.pairs.mapIndexed { index, pair ->
            MatchingPair(
                leftId = "left-${index + 1}",
                leftText = pair.left.trim(),
                rightId = "right-${index + 1}",
                rightText = pair.right.trim(),
            )
        },
    )
}

internal fun buildGenerationSystemInstruction(
    type: QuestionType,
    existingQuestions: List<String> = emptyList(),
    validationMessage: String? = null,
): String = buildString {
    append(SystemPrompts.QUIZ_GENERATION)
    append(' ')
    append(typeInstruction(type))
    if (existingQuestions.isNotEmpty()) {
        append(" Do not repeat these existing questions: ")
        append(QuizJson.encodeToString(existingQuestions))
        append('.')
    }
    validationMessage?.let {
        append(" Previous response was invalid: ")
        append(it)
        append(" Return one corrected JSON object.")
    }
}

private fun typeInstruction(type: QuestionType): String = when (type) {
    QuestionType.MULTIPLE_CHOICE ->
        "Create exactly one multiple-choice question. Provide four unique options, one supported " +
            "correct answer, and no NOT or EXCEPT wording. The answer must exactly match one option. " +
            "Return only: {\"question\":\"question\",\"options\":[\"option 1\",\"option 2\"," +
            "\"option 3\",\"option 4\"],\"answer\":\"option 1\"}"
    QuestionType.FILL_IN_THE_BLANK ->
        "Create exactly one fill-in-the-blank or short-answer question. Prefer one blank written " +
            "with one or more underscores, but a direct question is acceptable. The answer must " +
            "be one supported word or short phrase. Return only: " +
            "{\"question\":\"question with _\",\"answer\":\"missing text\"}"
    QuestionType.IDENTIFICATION ->
        "Create exactly one identification question for a supported term, person, place, object, " +
            "or concept. Keep the answer short and do not reveal it in the question. Return only: " +
            "{\"question\":\"question\",\"answer\":\"answer\"}"
    QuestionType.MATCHING ->
        "Create exactly one matching question with exactly three unique one-to-one pairs. Keep " +
            "every item short. Return only: {\"question\":\"question\",\"pairs\":[" +
            "{\"left\":\"item 1\",\"right\":\"match 1\"}," +
            "{\"left\":\"item 2\",\"right\":\"match 2\"}," +
            "{\"left\":\"item 3\",\"right\":\"match 3\"}]}"
    QuestionType.EXPLANATION ->
        "Create exactly one short explanation question about a supported why, how, cause, effect, " +
            "or relationship. Return only: " +
            "{\"question\":\"question\",\"answer\":\"brief reference answer\"}"
}

internal fun unwrapJsonCodeFence(response: String): String {
    val trimmed = response.trim()
    return JSON_CODE_FENCE.matchEntire(trimmed)?.groupValues?.get(1)?.trim() ?: trimmed
}

private fun normalizeForDuplicateCheck(value: String): String =
    value.trim().lowercase().replace(WHITESPACE, " ")

private val WHITESPACE = Regex("\\s+")
private val JSON_CODE_FENCE = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
