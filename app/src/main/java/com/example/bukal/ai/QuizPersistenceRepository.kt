package com.example.bukal.ai

import com.example.bukal.data.local.AttemptEntity
import com.example.bukal.data.local.AttemptStatuses
import com.example.bukal.data.local.BukalDatabase
import com.example.bukal.data.local.CompletedAttemptRecord
import com.example.bukal.data.local.CompletedQuizSetAttempt
import com.example.bukal.data.local.CompletedQuizSetRecord
import com.example.bukal.data.local.MatchingPairEntity
import com.example.bukal.data.local.QuestionEntity
import com.example.bukal.data.local.QuestionRecord
import com.example.bukal.data.local.QuestionResults
import com.example.bukal.data.local.QuizSetHistoryRow
import com.example.bukal.data.local.SavedQuizRecord
import com.example.bukal.data.local.SavedQuizSetRecord
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QuizPersistenceRepository(
    private val database: BukalDatabase,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun saveGenerated(
        request: QuizGenerationRequest,
        result: QuizGenerationResult,
    ): Long = withContext(Dispatchers.IO) {
        val generatedAt = nowEpochMillis()
        database.attemptDao().insert(
            generatedRecord(
                request = request,
                result = result,
                generatedAtEpochMs = generatedAt,
                localDate = generatedAt.localDate(),
            ),
        )
    }

    suspend fun getOrCreateQuizSet(attemptIds: List<Long>): Long = withContext(Dispatchers.IO) {
        val createdAt = nowEpochMillis()
        database.attemptDao().getOrCreateQuizSet(
            attemptIds = attemptIds,
            createdAtEpochMs = createdAt,
            localDate = createdAt.localDate(),
        )
    }

    suspend fun saveCompleted(
        quizSetId: Long,
        completions: List<PassageQuizCompletion>,
    ): Long = withContext(Dispatchers.IO) {
        val savedSet = requireNotNull(database.attemptDao().getSavedQuizSet(quizSetId)) {
            "Saved quiz set $quizSetId no longer exists."
        }
        require(savedSet.quizzes.map { it.attempt.id } == completions.map { it.savedQuizId }) {
            "The saved quiz set changed before completion."
        }
        savedSet.quizzes.zip(completions).forEach { (savedQuiz, completion) ->
            require(savedQuiz.passage.id == completion.request.passage.id) {
                "The saved quiz passage changed."
            }
            require(savedQuiz.toQuizQuestions().sameQuestionsAs(completion.request.questions)) {
                "The saved questions changed before completion."
            }
        }

        val completedAt = nowEpochMillis()
        val completedLocalDate = completedAt.localDate()
        val attempts = savedSet.quizzes.zip(completions).map { (savedQuiz, completion) ->
            CompletedQuizSetAttempt(
                attemptId = completion.savedQuizId,
                record = completedRecord(
                    request = completion.request,
                    result = completion.result,
                    generatedAtEpochMs = savedQuiz.attempt.generatedAtEpochMs,
                    completedAtEpochMs = completedAt,
                    completedLocalDate = completedLocalDate,
                    highestEarnedPoints = maxOf(
                        savedQuiz.attempt.highestEarnedPoints,
                        completion.result.earnedPoints,
                    ),
                ),
            )
        }
        val earnedPoints = completions.sumOf { it.result.earnedPoints }
        val possiblePoints = completions.sumOf { it.result.possiblePoints }
        database.attemptDao().replaceQuizSetWithCompleted(
            CompletedQuizSetRecord(
                quizSet = savedSet.quizSet.copy(
                    status = AttemptStatuses.COMPLETED,
                    completedAtEpochMs = completedAt,
                    completedLocalDate = completedLocalDate,
                    earnedPoints = earnedPoints,
                    highestEarnedPoints = maxOf(savedSet.quizSet.highestEarnedPoints, earnedPoints),
                    possiblePoints = possiblePoints,
                ),
                attempts = attempts,
            ),
        )
    }

    suspend fun getHistory(): List<QuizSetHistoryRow> = withContext(Dispatchers.IO) {
        database.attemptDao().getHistory()
    }

    suspend fun getSavedQuizSet(quizSetId: Long): SavedQuizSetRecord = withContext(Dispatchers.IO) {
        requireNotNull(database.attemptDao().getSavedQuizSet(quizSetId)) {
            "Saved quiz set $quizSetId no longer exists."
        }
    }

    suspend fun getQuizForPassage(passageId: Long): SavedQuizRecord? = withContext(Dispatchers.IO) {
        database.attemptDao().getQuizForPassage(passageId)
    }

    suspend fun getQuestionPoolForPassage(passageId: Long): List<QuizQuestion> =
        withContext(Dispatchers.IO) {
            database.attemptDao().getQuizzesForPassage(passageId)
                .flatMap(SavedQuizRecord::toQuizQuestions)
                .distinctBy { question ->
                    question.type to question.prompt.trim().lowercase()
                }
        }

    private fun Long.localDate(): String = Instant.ofEpochMilli(this)
        .atZone(zoneId)
        .toLocalDate()
        .toString()
}

data class PassageQuizCompletion(
    val request: QuizEvaluationRequest,
    val result: QuizResultSummary,
    val savedQuizId: Long,
)

internal fun generatedRecord(
    request: QuizGenerationRequest,
    result: QuizGenerationResult,
    generatedAtEpochMs: Long,
    localDate: String,
): CompletedAttemptRecord = CompletedAttemptRecord(
    attempt = AttemptEntity(
        passageId = request.passage.id,
        quizModelId = request.model.id,
        generatedAtEpochMs = generatedAtEpochMs,
        status = AttemptStatuses.SAVED,
        // Version 1 required these fields. For a saved quiz, status is authoritative and
        // Profile/history completion queries ignore these generated-time placeholders.
        completedAtEpochMs = generatedAtEpochMs,
        completedLocalDate = localDate,
        earnedPoints = 0.0,
        highestEarnedPoints = 0.0,
        possiblePoints = result.questions.size.toDouble(),
    ),
    questions = result.questions.mapIndexed { index, question ->
        question.toQuestionRecord(
            passageContent = request.passage.content,
            position = index,
            result = null,
            request = null,
        )
    },
)

internal fun completedRecord(
    request: QuizEvaluationRequest,
    result: QuizResultSummary,
    generatedAtEpochMs: Long,
    completedAtEpochMs: Long,
    completedLocalDate: String,
    highestEarnedPoints: Double = result.earnedPoints,
): CompletedAttemptRecord = CompletedAttemptRecord(
    attempt = AttemptEntity(
        passageId = request.passage.id,
        quizModelId = request.model.id,
        generatedAtEpochMs = generatedAtEpochMs,
        status = AttemptStatuses.COMPLETED,
        completedAtEpochMs = completedAtEpochMs,
        completedLocalDate = completedLocalDate,
        earnedPoints = result.earnedPoints,
        highestEarnedPoints = highestEarnedPoints,
        possiblePoints = result.possiblePoints,
    ),
    questions = result.items.mapIndexed { index, item ->
        item.question.toQuestionRecord(
            passageContent = request.passage.content,
            position = index,
            result = item,
            request = request,
        )
    },
)

private fun QuizQuestion.toQuestionRecord(
    passageContent: String,
    position: Int,
    result: QuizQuestionResult?,
    request: QuizEvaluationRequest?,
): QuestionRecord {
    val multipleChoice = answer as? QuestionAnswer.MultipleChoice
    val openResponse = answer as? QuestionAnswer.OpenResponse
    val matching = answer as? QuestionAnswer.Matching
    val resultName = when (result?.verdict) {
        QuizResultVerdict.CORRECT -> QuestionResults.CORRECT
        QuizResultVerdict.INCORRECT -> QuestionResults.INCORRECT
        QuizResultVerdict.UNANSWERED, null -> QuestionResults.UNANSWERED
    }
    val question = QuestionEntity(
        attemptId = 0,
        position = position,
        quizType = type.wireName,
        prompt = prompt,
        explanation = SAVED_QUESTION_NOTE,
        sourceId = sourceId,
        evidence = passageContent,
        option0 = multipleChoice?.options?.getOrNull(0),
        option1 = multipleChoice?.options?.getOrNull(1),
        option2 = multipleChoice?.options?.getOrNull(2),
        option3 = multipleChoice?.options?.getOrNull(3),
        correctOptionIndex = multipleChoice?.answerIndex,
        referenceAnswer = openResponse?.referenceAnswer,
        selectedOptionIndex = request?.selectedOptions?.get(id),
        textResponse = if (openResponse == null) {
            null
        } else {
            request?.textResponses?.get(id)?.trim()?.takeIf(String::isNotEmpty)
        },
        result = resultName,
        earnedPoints = result?.earnedPoints ?: 0.0,
        possiblePoints = result?.possiblePoints ?: 1.0,
    )
    return QuestionRecord(
        question = question,
        matchingPairs = matching?.pairs.orEmpty().mapIndexed { index, pair ->
            MatchingPairEntity(
                questionId = 0,
                leftId = pair.leftId,
                leftText = pair.leftText,
                leftPosition = index,
                rightId = pair.rightId,
                rightText = pair.rightText,
                rightPosition = index,
                selectedRightId = request?.matchingSelections?.get("$id:${pair.leftId}"),
            )
        },
    )
}

fun SavedQuizRecord.toQuizQuestions(): List<QuizQuestion> = questions
    .sortedBy { it.question.position }
    .map { record ->
        val question = record.question
        val type = QuestionType.entries.first { it.wireName == question.quizType }
        QuizQuestion(
            id = "q${question.position + 1}",
            type = type,
            prompt = question.prompt,
            sourceId = question.sourceId,
            answer = when (type) {
                QuestionType.MULTIPLE_CHOICE -> QuestionAnswer.MultipleChoice(
                    options = listOf(
                        requireNotNull(question.option0),
                        requireNotNull(question.option1),
                        requireNotNull(question.option2),
                        requireNotNull(question.option3),
                    ),
                    answerIndex = requireNotNull(question.correctOptionIndex),
                )
                QuestionType.TRUE_FALSE -> QuestionAnswer.MultipleChoice(
                    options = listOf(
                        requireNotNull(question.option0),
                        requireNotNull(question.option1),
                    ),
                    answerIndex = requireNotNull(question.correctOptionIndex),
                )
                QuestionType.MATCHING -> QuestionAnswer.Matching(
                    record.matchingPairs.sortedBy(MatchingPairEntity::leftPosition).map { pair ->
                        MatchingPair(
                            leftId = pair.leftId,
                            leftText = pair.leftText,
                            rightId = pair.rightId,
                            rightText = pair.rightText,
                        )
                    },
                )
                QuestionType.FILL_IN_THE_BLANK,
                QuestionType.IDENTIFICATION,
                QuestionType.EXPLANATION,
                -> QuestionAnswer.OpenResponse(requireNotNull(question.referenceAnswer))
            },
        )
    }

private fun List<QuizQuestion>.sameQuestionsAs(other: List<QuizQuestion>): Boolean =
    map { it.copy(id = "") } == other.map { it.copy(id = "") }

private const val SAVED_QUESTION_NOTE = "Generated locally from the selected passage."
