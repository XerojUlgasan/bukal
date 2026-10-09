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
            attempts.id AS attempt_id,
            materials.display_name AS material_name,
            passages.title AS passage_title,
            passages.source_id AS source_id,
            attempts.quiz_model_id AS quiz_model_id,
            attempts.generated_at_epoch_ms AS generated_at_epoch_ms,
            attempts.status AS status,
            attempts.completed_at_epoch_ms AS completed_at_epoch_ms,
            attempts.completed_local_date AS completed_local_date,
            attempts.earned_points AS earned_points,
            attempts.highest_earned_points AS highest_earned_points,
            attempts.possible_points AS possible_points,
            COUNT(questions.id) AS question_count,
            GROUP_CONCAT(questions.quiz_type, '|') AS quiz_types
        FROM attempts
        JOIN passages ON passages.id = attempts.passage_id
        JOIN materials ON materials.id = passages.material_id
        JOIN questions ON questions.attempt_id = attempts.id
        GROUP BY attempts.id
        ORDER BY attempts.generated_at_epoch_ms DESC, attempts.id DESC
        """,
    )
    abstract suspend fun getHistory(): List<AttemptHistoryRow>

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

    @Query(
        "SELECT id FROM attempts WHERE passage_id = :passageId " +
            "ORDER BY generated_at_epoch_ms DESC, id DESC LIMIT 1",
    )
    protected abstract suspend fun findQuizIdForPassage(passageId: Long): Long?

    @Transaction
    open suspend fun getQuizForPassage(passageId: Long): SavedQuizRecord? =
        findQuizIdForPassage(passageId)?.let { getSavedQuiz(it) }

    @Query(
        """
        SELECT completed_local_date, COUNT(*) AS completed_count
        FROM attempts
        WHERE status = 'completed'
          AND completed_local_date BETWEEN :year || '-01-01' AND :year || '-12-31'
        GROUP BY completed_local_date
        ORDER BY completed_local_date
        """,
    )
    abstract suspend fun getDailyActivity(year: String): List<DailyActivity>

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
