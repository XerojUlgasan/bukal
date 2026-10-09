package com.example.bukal.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
abstract class AttemptDao {
    @Query("SELECT * FROM passages WHERE id = :passageId")
    protected abstract suspend fun getPassageRow(passageId: Long): PassageEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAttemptRow(attempt: AttemptEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertQuestionRow(question: QuestionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertMatchingPairRows(pairs: List<MatchingPairEntity>)

    @Update
    protected abstract suspend fun updateAttemptRow(attempt: AttemptEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertQuizSetRow(quizSet: QuizSetEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertQuizSetItems(items: List<QuizSetItemEntity>)

    @Update
    protected abstract suspend fun updateQuizSetRow(quizSet: QuizSetEntity): Int

    @Query("SELECT * FROM quiz_sets WHERE selection_key = :selectionKey")
    protected abstract suspend fun getQuizSetBySelectionKey(selectionKey: String): QuizSetEntity?

    @Query("SELECT * FROM quiz_sets WHERE id = :quizSetId")
    protected abstract suspend fun getQuizSetRow(quizSetId: Long): QuizSetEntity?

    @Query(
        "SELECT attempt_id FROM quiz_set_items WHERE quiz_set_id = :quizSetId ORDER BY position",
    )
    protected abstract suspend fun getQuizSetAttemptIds(quizSetId: Long): List<Long>

    @Query(
        """
        UPDATE matching_pairs
        SET selected_right_id = :selectedRightId
        WHERE question_id = :questionId AND left_id = :leftId
        """,
    )
    protected abstract suspend fun updateMatchingSelection(
        questionId: Long,
        leftId: String,
        selectedRightId: String,
    ): Int

    @Transaction
    open suspend fun insert(record: CompletedAttemptRecord): Long {
        val passage = requireNotNull(getPassageRow(record.attempt.passageId)) {
            "Passage ${record.attempt.passageId} does not exist"
        }
        DatabaseValidation.validateAttempt(record, passage)

        val attemptId = insertAttemptRow(record.attempt.copy(id = 0))
        insertQuestionGraph(attemptId, record.questions)
        return attemptId
    }

    private suspend fun insertQuestionGraph(
        attemptId: Long,
        questions: List<QuestionRecord>,
    ) {
        questions.sortedBy { it.question.position }.forEach { questionRecord ->
            val questionId = insertQuestionRow(
                questionRecord.question.copy(id = 0, attemptId = attemptId),
            )
            if (questionRecord.matchingPairs.isNotEmpty()) {
                insertMatchingPairRows(
                    questionRecord.matchingPairs.map { pair ->
                        pair.copy(questionId = questionId, selectedRightId = null)
                    },
                )
                questionRecord.matchingPairs.forEach { pair ->
                    pair.selectedRightId?.let { selectedRightId ->
                        check(
                            updateMatchingSelection(
                                questionId = questionId,
                                leftId = pair.leftId,
                                selectedRightId = selectedRightId,
                            ) == 1,
                        ) { "Could not save matching selection ${pair.leftId}" }
                    }
                }
            }
        }
    }

    @Query(
        """
        SELECT
            quiz_sets.id AS quiz_set_id,
            MIN(materials.display_name) AS material_name,
            CASE WHEN COUNT(DISTINCT attempts.id) = 1
                THEN MAX(COALESCE(passages.title, passages.source_id))
                ELSE NULL
            END AS passage_title,
            COUNT(DISTINCT attempts.id) AS passage_count,
            quiz_sets.created_at_epoch_ms AS created_at_epoch_ms,
            quiz_sets.status AS status,
            quiz_sets.completed_at_epoch_ms AS completed_at_epoch_ms,
            quiz_sets.completed_local_date AS completed_local_date,
            quiz_sets.earned_points AS earned_points,
            quiz_sets.highest_earned_points AS highest_earned_points,
            quiz_sets.possible_points AS possible_points,
            COUNT(questions.id) AS question_count,
            GROUP_CONCAT(questions.quiz_type, '|') AS quiz_types
        FROM quiz_sets
        JOIN quiz_set_items ON quiz_set_items.quiz_set_id = quiz_sets.id
        JOIN attempts ON attempts.id = quiz_set_items.attempt_id
        JOIN passages ON passages.id = attempts.passage_id
        JOIN materials ON materials.id = passages.material_id
        JOIN questions ON questions.attempt_id = attempts.id
        GROUP BY quiz_sets.id
        ORDER BY quiz_sets.created_at_epoch_ms DESC, quiz_sets.id DESC
        """,
    )
    abstract suspend fun getHistory(): List<QuizSetHistoryRow>

    @Query(
        """
        SELECT
            attempts.id AS attempt_id,
            attempts.passage_id AS passage_id,
            attempts.quiz_model_id AS quiz_model_id,
            attempts.generated_at_epoch_ms AS generated_at_epoch_ms,
            attempts.status AS status,
            attempts.completed_at_epoch_ms AS completed_at_epoch_ms,
            attempts.completed_local_date AS completed_local_date,
            attempts.earned_points AS earned_points,
            attempts.highest_earned_points AS highest_earned_points,
            attempts.possible_points AS possible_points,
            materials.display_name AS material_name
        FROM attempts
        JOIN passages ON passages.id = attempts.passage_id
        JOIN materials ON materials.id = passages.material_id
        WHERE attempts.id = :attemptId
        """,
    )
    protected abstract suspend fun getSavedQuizHeader(attemptId: Long): SavedQuizHeader?

    @Query("SELECT * FROM attempts WHERE id = :attemptId")
    abstract suspend fun getAttempt(attemptId: Long): AttemptEntity?

    @Query("SELECT * FROM questions WHERE attempt_id = :attemptId ORDER BY position")
    abstract suspend fun getQuestions(attemptId: Long): List<QuestionEntity>

    @Query("SELECT * FROM matching_pairs WHERE question_id = :questionId ORDER BY left_position")
    abstract suspend fun getMatchingPairs(questionId: Long): List<MatchingPairEntity>

    @Transaction
    open suspend fun getSavedQuiz(attemptId: Long): SavedQuizRecord? {
        val header = getSavedQuizHeader(attemptId) ?: return null
        val passage = getPassageRow(header.passageId) ?: return null
        val questions = getQuestions(attemptId).map { question ->
            QuestionRecord(
                question = question,
                matchingPairs = getMatchingPairs(question.id),
            )
        }
        return SavedQuizRecord(
            attempt = AttemptEntity(
                id = header.attemptId,
                passageId = header.passageId,
                quizModelId = header.quizModelId,
                generatedAtEpochMs = header.generatedAtEpochMs,
                status = header.status,
                completedAtEpochMs = header.completedAtEpochMs,
                completedLocalDate = header.completedLocalDate,
                earnedPoints = header.earnedPoints,
                highestEarnedPoints = header.highestEarnedPoints,
                possiblePoints = header.possiblePoints,
            ),
            passage = passage,
            materialName = header.materialName,
            questions = questions,
        )
    }

    @Transaction
    open suspend fun getSavedQuizSet(quizSetId: Long): SavedQuizSetRecord? {
        val quizSet = getQuizSetRow(quizSetId) ?: return null
        val quizzes = getQuizSetAttemptIds(quizSetId).map { attemptId ->
            getSavedQuiz(attemptId) ?: return null
        }
        return SavedQuizSetRecord(quizSet = quizSet, quizzes = quizzes)
    }

    @Transaction
    open suspend fun getOrCreateQuizSet(
        attemptIds: List<Long>,
        createdAtEpochMs: Long,
        localDate: String,
    ): Long {
        require(attemptIds.isNotEmpty()) { "A quiz set needs at least one passage quiz" }
        require(attemptIds.distinct().size == attemptIds.size) { "A quiz set cannot repeat a passage quiz" }
        val selectionKey = attemptIds.joinToString(",")
        getQuizSetBySelectionKey(selectionKey)?.let { return it.id }
        val attempts = attemptIds.map { attemptId ->
            requireNotNull(getAttempt(attemptId)) { "Quiz $attemptId does not exist" }
        }
        val quizSetId = insertQuizSetRow(
            QuizSetEntity(
                selectionKey = selectionKey,
                createdAtEpochMs = createdAtEpochMs,
                completedAtEpochMs = createdAtEpochMs,
                completedLocalDate = localDate,
                earnedPoints = 0.0,
                highestEarnedPoints = 0.0,
                possiblePoints = attempts.sumOf(AttemptEntity::possiblePoints),
            ),
        )
        insertQuizSetItems(
            attemptIds.mapIndexed { position, attemptId ->
                QuizSetItemEntity(quizSetId = quizSetId, attemptId = attemptId, position = position)
            },
        )
        return quizSetId
    }

    @Transaction
    open suspend fun replaceQuizWithCompleted(
        quizId: Long,
        completed: CompletedAttemptRecord,
    ): Long {
        val existing = requireNotNull(getAttempt(quizId)) { "Quiz $quizId does not exist" }
        require(existing.passageId == completed.attempt.passageId) { "Quiz passage changed" }
        require(existing.generatedAtEpochMs == completed.attempt.generatedAtEpochMs) {
            "Quiz generation time changed"
        }
        val passage = requireNotNull(getPassageRow(existing.passageId)) {
            "Passage ${existing.passageId} does not exist"
        }
        val replacement = completed.copy(
            attempt = completed.attempt.copy(
                id = quizId,
                highestEarnedPoints = maxOf(
                    existing.highestEarnedPoints,
                    completed.attempt.highestEarnedPoints,
                ),
            ),
        )
        DatabaseValidation.validateAttempt(replacement, passage)
        clearMatchingSelectionsForAttempt(quizId)
        deleteMatchingPairsForAttempt(quizId)
        deleteQuestionsForAttempt(quizId)
        check(updateAttemptRow(replacement.attempt) == 1) { "Could not update quiz $quizId" }
        insertQuestionGraph(quizId, replacement.questions)
        return quizId
    }

    @Transaction
    open suspend fun replaceQuizSetWithCompleted(completed: CompletedQuizSetRecord): Long {
        val existing = requireNotNull(getQuizSetRow(completed.quizSet.id)) {
            "Quiz set ${completed.quizSet.id} does not exist"
        }
        val memberIds = getQuizSetAttemptIds(existing.id)
        require(memberIds == completed.attempts.map(CompletedQuizSetAttempt::attemptId)) {
            "Quiz set members changed"
        }
        require(completed.quizSet.status == AttemptStatuses.COMPLETED) {
            "A completed quiz set must use completed status"
        }
        completed.attempts.forEach { attempt ->
            replaceQuizWithCompleted(attempt.attemptId, attempt.record)
        }
        check(updateQuizSetRow(completed.quizSet) == 1) {
            "Could not update quiz set ${completed.quizSet.id}"
        }
        return completed.quizSet.id
    }

    @Query(
        "SELECT id FROM attempts WHERE passage_id = :passageId " +
            "ORDER BY generated_at_epoch_ms DESC, id DESC",
    )
    protected abstract suspend fun findQuizIdsForPassage(passageId: Long): List<Long>

    @Transaction
    open suspend fun getQuizForPassage(passageId: Long): SavedQuizRecord? =
        findQuizIdsForPassage(passageId).firstOrNull()?.let { getSavedQuiz(it) }

    @Transaction
    open suspend fun getQuizzesForPassage(passageId: Long): List<SavedQuizRecord> =
        findQuizIdsForPassage(passageId).mapNotNull { getSavedQuiz(it) }

    @Query(
        """
        SELECT completed_local_date, COUNT(*) AS completed_count
        FROM quiz_sets
        WHERE status = 'completed'
          AND completed_local_date BETWEEN :year || '-01-01' AND :year || '-12-31'
        GROUP BY completed_local_date
        ORDER BY completed_local_date
        """,
    )
    abstract suspend fun getDailyActivity(year: String): List<DailyActivity>

    @Query(
        """
        SELECT
            quiz_sets.completed_local_date,
            COUNT(
                DISTINCT CASE WHEN questions.result = 'correct' THEN
                    attempts.passage_id || CHAR(31) || questions.quiz_type ||
                    CHAR(31) || LOWER(TRIM(questions.prompt))
                END
            ) AS correct_count
        FROM quiz_sets
        JOIN quiz_set_items ON quiz_set_items.quiz_set_id = quiz_sets.id
        JOIN attempts ON attempts.id = quiz_set_items.attempt_id
        JOIN questions ON questions.attempt_id = quiz_set_items.attempt_id
        WHERE quiz_sets.status = 'completed'
        GROUP BY quiz_sets.completed_local_date
        ORDER BY quiz_sets.completed_local_date
        """,
    )
    abstract suspend fun getDailyCorrectAnswers(): List<DailyCorrectAnswers>

    @Query(
        """
        UPDATE matching_pairs
        SET selected_right_id = NULL
        WHERE question_id IN (SELECT id FROM questions WHERE attempt_id = :attemptId)
        """,
    )
    protected abstract suspend fun clearMatchingSelectionsForAttempt(attemptId: Long)

    @Query(
        """
        DELETE FROM matching_pairs
        WHERE question_id IN (SELECT id FROM questions WHERE attempt_id = :attemptId)
        """,
    )
    protected abstract suspend fun deleteMatchingPairsForAttempt(attemptId: Long)

    @Query("DELETE FROM questions WHERE attempt_id = :attemptId")
    protected abstract suspend fun deleteQuestionsForAttempt(attemptId: Long)

    @Query("DELETE FROM attempts WHERE id = :attemptId")
    protected abstract suspend fun deleteAttemptRow(attemptId: Long): Int

    @Transaction
    open suspend fun deleteAttempt(attemptId: Long): Int {
        // Clear the composite self-reference before the question cascade runs. SQLite's
        // SET_NULL action would otherwise also try to null the non-null question_id column.
        clearMatchingSelectionsForAttempt(attemptId)
        deleteMatchingPairsForAttempt(attemptId)
        return deleteAttemptRow(attemptId)
    }
}
