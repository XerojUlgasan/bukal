package com.example.bukal.data.local

import androidx.room.ColumnInfo

data class ImportedMaterial(
    val material: MaterialEntity,
    val passages: List<PassageEntity>,
)

data class QuestionRecord(
    val question: QuestionEntity,
    val matchingPairs: List<MatchingPairEntity> = emptyList(),
)

data class CompletedAttemptRecord(
    val attempt: AttemptEntity,
    val questions: List<QuestionRecord>,
)

data class SavedQuizRecord(
    val attempt: AttemptEntity,
    val passage: PassageEntity,
    val materialName: String,
    val questions: List<QuestionRecord>,
)

data class SearchCandidate(
    @ColumnInfo(name = "chunk_id")
    val chunkId: Long,
    @ColumnInfo(name = "passage_id")
    val passageId: Long,
    @ColumnInfo(name = "material_name")
    val materialName: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "chunk_content")
    val chunkContent: String,
    @ColumnInfo(name = "embedding_dimensions")
    val embeddingDimensions: Int,
    @ColumnInfo(name = "embedding_vector")
    val embeddingVector: ByteArray,
)

data class AttemptHistoryRow(
    @ColumnInfo(name = "attempt_id")
    val attemptId: Long,
    @ColumnInfo(name = "material_name")
    val materialName: String,
    @ColumnInfo(name = "passage_title")
    val passageTitle: String?,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "quiz_model_id")
    val quizModelId: String,
    @ColumnInfo(name = "generated_at_epoch_ms")
    val generatedAtEpochMs: Long,
    val status: String,
    @ColumnInfo(name = "completed_at_epoch_ms")
    val completedAtEpochMs: Long,
    @ColumnInfo(name = "completed_local_date")
    val completedLocalDate: String,
    @ColumnInfo(name = "earned_points")
    val earnedPoints: Double,
    @ColumnInfo(name = "highest_earned_points")
    val highestEarnedPoints: Double,
    @ColumnInfo(name = "possible_points")
    val possiblePoints: Double,
    @ColumnInfo(name = "question_count")
    val questionCount: Int,
    @ColumnInfo(name = "quiz_types")
    val quizTypes: String,
)

data class SavedQuizHeader(
    @ColumnInfo(name = "attempt_id")
    val attemptId: Long,
    @ColumnInfo(name = "passage_id")
    val passageId: Long,
    @ColumnInfo(name = "quiz_model_id")
    val quizModelId: String,
    @ColumnInfo(name = "generated_at_epoch_ms")
    val generatedAtEpochMs: Long,
    val status: String,
    @ColumnInfo(name = "completed_at_epoch_ms")
    val completedAtEpochMs: Long,
    @ColumnInfo(name = "completed_local_date")
    val completedLocalDate: String,
    @ColumnInfo(name = "earned_points")
    val earnedPoints: Double,
    @ColumnInfo(name = "highest_earned_points")
    val highestEarnedPoints: Double,
    @ColumnInfo(name = "possible_points")
    val possiblePoints: Double,
    @ColumnInfo(name = "material_name")
    val materialName: String,
)

data class DailyActivity(
    @ColumnInfo(name = "completed_local_date")
    val completedLocalDate: String,
    @ColumnInfo(name = "completed_count")
    val completedCount: Int,
)
