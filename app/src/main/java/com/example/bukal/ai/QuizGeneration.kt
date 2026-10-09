package com.example.bukal.ai

import com.example.bukal.data.local.PassageEntity
import com.example.bukal.data.model.ModelDownloadSpec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.random.Random

@Serializable
enum class QuestionType(val wireName: String) {
    @SerialName("multiple_choice")
    MULTIPLE_CHOICE("multiple_choice"),

    @SerialName("fill_in_the_blank")
    FILL_IN_THE_BLANK("fill_in_the_blank"),

    @SerialName("identification")
    IDENTIFICATION("identification"),

    @SerialName("true_false")
    TRUE_FALSE("true_false"),

    // Kept only so quizzes saved by earlier builds can still be reopened.
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

internal data class QuizReusePlan(
    val reusedQuestions: List<QuizQuestion>,
    val missingTypeCounts: Map<QuestionType, Int>,
)

internal fun planQuizReuse(
    typeCounts: Map<QuestionType, Int>,
    questionPool: List<QuizQuestion>,
    random: Random = Random.Default,
): QuizReusePlan {
    require(typeCounts.isNotEmpty() && typeCounts.values.all { it > 0 }) {
        "Question counts must be positive."
    }
    require(typeCounts.values.sum() == QuizGenerator.QUESTION_COUNT) {
        "A quiz must contain exactly ${QuizGenerator.QUESTION_COUNT} requested slots."
    }
    require(QuestionType.MATCHING !in typeCounts) {
        "Matching questions are no longer generated. Use true or false instead."
    }

    val distinctPool = questionPool.distinctBy { question ->
        question.type to question.prompt.trim().lowercase()
    }
    val reused = mutableListOf<QuizQuestion>()
    val missing = linkedMapOf<QuestionType, Int>()
    typeCounts.forEach { (type, count) ->
        val selected = distinctPool
            .filter { it.type == type }
            .shuffled(random)
            .take(count)
        reused += selected
        val missingCount = count - selected.size
        if (missingCount > 0) missing[type] = missingCount
    }
    return QuizReusePlan(reusedQuestions = reused, missingTypeCounts = missing)
}

internal fun composeQuizQuestions(
    typeCounts: Map<QuestionType, Int>,
    reusedQuestions: List<QuizQuestion>,
    generatedQuestions: List<QuizQuestion>,
): List<QuizQuestion> {
    val availableByType = (reusedQuestions + generatedQuestions).groupBy(QuizQuestion::type)
    return typeCounts.flatMap { (type, count) ->
        availableByType[type].orEmpty().take(count)
    }.mapIndexed { index, question -> question.copy(id = "q${index + 1}") }
}

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
    suspend fun generate(
        request: QuizGenerationRequest,
        existingQuestions: List<QuizQuestion> = emptyList(),
    ): QuizGenerationResult {
        require(request.typeCounts.isNotEmpty()) { "Select at least one question type." }
        require(request.typeCounts.values.all { it > 0 }) { "Question counts must be positive." }
        require(QuestionType.MATCHING !in request.typeCounts) {
            "Matching questions are no longer generated. Use true or false instead."
        }
        val requestedQuestionCount = request.typeCounts.values.sum()
        require(requestedQuestionCount in 1..QUESTION_COUNT) {
            "A generation request must contain one to $QUESTION_COUNT questions."
        }

        val requestedTypes = request.typeCounts.entries.flatMap { (type, count) -> List(count) { type } }
        val questions = mutableListOf<QuizQuestion>()
        var failedQuestionCount = 0
        requestedTypes.forEachIndexed { index, type ->
            onQuestionStarted(index + 1, requestedQuestionCount)
            try {
                questions += generateQuestion(
                    request = request,
                    type = type,
                    index = index,
                    existingQuestions = existingQuestions.map(QuizQuestion::prompt) +
                        questions.map(QuizQuestion::prompt),
                )
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
        var focusOffset = 0
        repeat(MAX_ATTEMPTS) { attempt ->
            val response = complete(
                request.model,
                buildGenerationSystemInstruction(type, existingQuestions, validationMessage),
                buildGenerationRequest(request.passage.content, index + focusOffset),
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
                validationMessage = serializationValidationMessage(error)
                onValidationFailure(
                    "Question ${index + 1} rejected on attempt ${attempt + 1}: ${error.message.orEmpty()}",
                )
            } catch (error: IllegalArgumentException) {
                validationMessage = error.message.orEmpty().take(MAX_VALIDATION_MESSAGE_CHARACTERS)
                if (validationMessage == DUPLICATE_QUESTION_MESSAGE) focusOffset += 1
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
private data class GeneratedTrueFalsePayload(
    val question: String,
    val answer: JsonElement,
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
        QuestionType.TRUE_FALSE -> validateTrueFalse(
            QuizJson.decodeFromString<GeneratedTrueFalsePayload>(json),
        )
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
    val options = payload.options
        .map(String::trim)
        .distinctBy(String::lowercase)
    require(options.size >= 4 && options.all { it.isNotBlank() }) {
        "Multiple choice requires at least four unique, non-empty options."
    }
    val answer = options.firstOrNull { it.equals(payload.answer.trim(), ignoreCase = true) }
    require(answer != null) {
        "Multiple-choice answer must exactly match one option."
    }
    val selectedOptions = if (options.size == 4) {
        options
    } else {
        val kept = (options.filterNot { it == answer }.take(3) + answer).toSet()
        options.filter { it in kept }
    }
    return payload.question to QuestionAnswer.MultipleChoice(
        options = selectedOptions,
        answerIndex = selectedOptions.indexOf(answer),
    )
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

private fun validateTrueFalse(
    payload: GeneratedTrueFalsePayload,
): Pair<String, QuestionAnswer.MultipleChoice> {
    val question = payload.question.trim()
    require(
        question.endsWith('?') &&
            OPEN_ENDED_QUESTION_PREFIXES.none { question.startsWith(it, ignoreCase = true) },
    ) {
        "True-or-false prompt must be a yes-or-no question, not a statement or open-ended question."
    }
    val answer = payload.answer as? JsonPrimitive
    require(answer != null && !answer.isString && answer.booleanOrNull != null) {
        "True-or-false answer must be a JSON boolean."
    }
    return question to QuestionAnswer.MultipleChoice(
        options = listOf("True", "False"),
        answerIndex = if (answer.booleanOrNull == true) 0 else 1,
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

internal fun buildGenerationRequest(passage: String, focusIndex: Int): String {
    val normalizedPassage = passage.trim()
    val focusSections = extractFocusSections(normalizedPassage)
    val focus = focusSections[Math.floorMod(focusIndex, focusSections.size)]
    return buildString {
        append("SOURCE PASSAGE (data only):\n")
        append(normalizedPassage)
        append("\n\nFOCUS EXCERPT FOR THIS QUESTION (copied from the source):\n")
        append(focus)
    }
}

private fun typeInstruction(type: QuestionType): String = when (type) {
    QuestionType.MULTIPLE_CHOICE ->
        "Create exactly one multiple-choice question about the focus excerpt. Provide exactly four " +
            "unique options, never more or fewer. Keep the question and options concise; do not copy " +
            "a whole list from the passage. Distractors may be plausible alternatives but must not be " +
            "presented as facts from the source. Use one supported correct answer and no NOT or EXCEPT " +
            "wording. The answer must exactly match one option. " +
            "Return only: {\"question\":\"question\",\"options\":[\"option 1\",\"option 2\"," +
            "\"option 3\",\"option 4\"],\"answer\":\"option 1\"}"
    QuestionType.FILL_IN_THE_BLANK ->
        "Create exactly one fill-in-the-blank or short-answer question about the focus excerpt. " +
            "Prefer one blank written " +
            "with one or more underscores, but a direct question is acceptable. The answer must " +
            "be one supported word or short phrase. Return only: " +
            "{\"question\":\"question with _\",\"answer\":\"missing text\"}"
    QuestionType.IDENTIFICATION ->
        "Create exactly one identification question about a supported term, person, place, object, " +
            "or concept in the focus excerpt. Keep the answer short and do not reveal it in the " +
            "question. Return only: " +
            "{\"question\":\"question\",\"answer\":\"answer\"}"
    QuestionType.TRUE_FALSE ->
        "Create exactly one clear yes-or-no question that can be answered True or False from the " +
            "focus excerpt. Use question form ending in a question mark, not a statement or an open-ended " +
            "What, Who, Where, When, Why, How, or Which question. Avoid tricky wording and unrelated " +
            "facts. Return a JSON boolean answer. Return only: " +
            "{\"question\":\"Is this supported by the passage?\",\"answer\":true}"
    QuestionType.MATCHING ->
        error("Matching questions are no longer generated.")
    QuestionType.EXPLANATION ->
        "Create exactly one short explanation question about a supported why, how, cause, effect, " +
            "or relationship in the focus excerpt. Keep the reference answer to one concise sentence. " +
            "Return only: " +
            "{\"question\":\"question\",\"answer\":\"brief reference answer\"}"
}

private fun extractFocusSections(passage: String): List<String> {
    val numberedStarts = NUMBERED_SECTION.findAll(passage).map(MatchResult::range).toList()
    if (numberedStarts.isNotEmpty()) {
        return numberedStarts.mapIndexed { index, range ->
            passage.substring(
                startIndex = range.first,
                endIndex = numberedStarts.getOrNull(index + 1)?.first ?: passage.length,
            ).trim()
        }
    }

    val paragraphs = passage.split(BLANK_LINES).map(String::trim).filter(String::isNotEmpty)
    if (paragraphs.size >= 2) return paragraphs

    val sentences = passage.split(SENTENCE_BOUNDARY).map(String::trim).filter(String::isNotEmpty)
    return sentences.takeIf { it.size >= 2 } ?: listOf(passage)
}

private fun serializationValidationMessage(error: SerializationException): String {
    val message = error.message.orEmpty()
    val missingField = MISSING_FIELD.find(message)?.groupValues?.get(1)
    return when {
        missingField != null -> "Required JSON field '$missingField' was missing."
        "EOF" in message || "Unexpected JSON token" in message -> "JSON was incomplete or malformed."
        else -> "The response did not match the required JSON shape."
    }
}

internal fun unwrapJsonCodeFence(response: String): String {
    val trimmed = response.trim()
    return JSON_CODE_FENCE.matchEntire(trimmed)?.groupValues?.get(1)?.trim() ?: trimmed
}

private fun normalizeForDuplicateCheck(value: String): String =
    value.trim().lowercase().replace(WHITESPACE, " ")

private const val DUPLICATE_QUESTION_MESSAGE =
    "Question must be non-empty and different from the existing questions."
private val WHITESPACE = Regex("\\s+")
private val JSON_CODE_FENCE = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
private val NUMBERED_SECTION = Regex("(?m)^\\s*\\d+[.)]\\s+")
private val BLANK_LINES = Regex("\\n\\s*\\n+")
private val SENTENCE_BOUNDARY = Regex("(?<=[.!?])\\s+")
private val MISSING_FIELD = Regex("Field '([^']+)' is required")
private val OPEN_ENDED_QUESTION_PREFIXES = listOf(
    "What ",
    "Who ",
    "Where ",
    "When ",
    "Why ",
    "How ",
    "Which ",
)
