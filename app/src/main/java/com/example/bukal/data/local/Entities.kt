package com.example.bukal.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "materials",
    indices = [
        Index(
            value = ["imported_at_epoch_ms"],
            orders = [Index.Order.DESC],
            name = "idx_materials_imported_at",
        ),
    ],
)
data class MaterialEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "document_format")
    val documentFormat: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,
    @ColumnInfo(name = "retained_file_path")
    val retainedFilePath: String? = null,
    @ColumnInfo(name = "imported_at_epoch_ms")
    val importedAtEpochMs: Long,
)

@Entity(
    tableName = "passages",
    foreignKeys = [
        ForeignKey(
            entity = MaterialEntity::class,
            parentColumns = ["id"],
            childColumns = ["material_id"],
            onUpdate = ForeignKey.RESTRICT,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = ["material_id", "source_id"],
            unique = true,
            name = "index_passages_material_id_source_id",
        ),
        Index(
            value = ["material_id", "position"],
            unique = true,
            name = "index_passages_material_id_position",
        ),
    ],
)
data class PassageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "material_id")
    val materialId: Long,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    val position: Int,
    val title: String? = null,
    val content: String,
)

@Entity(
    tableName = "search_chunks",
    foreignKeys = [
        ForeignKey(
            entity = PassageEntity::class,
            parentColumns = ["id"],
            childColumns = ["passage_id"],
            onUpdate = ForeignKey.RESTRICT,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = ["passage_id", "chunk_index"],
            unique = true,
            name = "index_search_chunks_passage_id_chunk_index",
        ),
        Index(
            value = ["embedding_model_id"],
            name = "idx_search_chunks_embedding_model",
        ),
    ],
)
data class SearchChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "passage_id")
    val passageId: Long,
    @ColumnInfo(name = "chunk_index")
    val chunkIndex: Int,
    @ColumnInfo(name = "start_offset")
    val startOffset: Int,
    @ColumnInfo(name = "end_offset")
    val endOffset: Int,
    val content: String,
    @ColumnInfo(name = "embedding_model_id")
    val embeddingModelId: String? = null,
    @ColumnInfo(name = "embedding_dimensions")
    val embeddingDimensions: Int? = null,
    @ColumnInfo(name = "embedding_vector", typeAffinity = ColumnInfo.BLOB)
    val embeddingVector: ByteArray? = null,
)

@Entity(
    tableName = "attempts",
    foreignKeys = [
        ForeignKey(
            entity = PassageEntity::class,
            parentColumns = ["id"],
            childColumns = ["passage_id"],
            onUpdate = ForeignKey.RESTRICT,
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(
            value = ["completed_at_epoch_ms"],
            orders = [Index.Order.DESC],
            name = "idx_attempts_completed_at",
        ),
        Index(
            value = ["generated_at_epoch_ms"],
            orders = [Index.Order.DESC],
            name = "idx_attempts_generated_at",
        ),
        Index(
            value = ["completed_local_date"],
            name = "idx_attempts_completed_local_date",
        ),
        Index(
            value = ["passage_id"],
            name = "idx_attempts_passage_id",
        ),
    ],
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "passage_id")
    val passageId: Long,
    @ColumnInfo(name = "quiz_model_id")
    val quizModelId: String,
    @ColumnInfo(name = "generated_at_epoch_ms", defaultValue = "0")
    val generatedAtEpochMs: Long = 0,
    @ColumnInfo(defaultValue = "'completed'")
    val status: String = AttemptStatuses.COMPLETED,
    @ColumnInfo(name = "completed_at_epoch_ms")
    val completedAtEpochMs: Long,
    @ColumnInfo(name = "completed_local_date")
    val completedLocalDate: String,
    @ColumnInfo(name = "earned_points")
    val earnedPoints: Double,
    @ColumnInfo(name = "highest_earned_points", defaultValue = "0")
    val highestEarnedPoints: Double = earnedPoints,
    @ColumnInfo(name = "possible_points")
    val possiblePoints: Double,
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = AttemptEntity::class,
            parentColumns = ["id"],
            childColumns = ["attempt_id"],
            onUpdate = ForeignKey.RESTRICT,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = ["attempt_id", "position"],
            unique = true,
            name = "index_questions_attempt_id_position",
        ),
    ],
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "attempt_id")
    val attemptId: Long,
    val position: Int,
    @ColumnInfo(name = "quiz_type")
    val quizType: String,
    val prompt: String,
    val explanation: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    val evidence: String,
    @ColumnInfo(name = "option_0")
    val option0: String? = null,
    @ColumnInfo(name = "option_1")
    val option1: String? = null,
    @ColumnInfo(name = "option_2")
    val option2: String? = null,
    @ColumnInfo(name = "option_3")
    val option3: String? = null,
    @ColumnInfo(name = "correct_option_index")
    val correctOptionIndex: Int? = null,
    @ColumnInfo(name = "reference_answer")
    val referenceAnswer: String? = null,
    @ColumnInfo(name = "grading_criteria")
    val gradingCriteria: String? = null,
    @ColumnInfo(name = "selected_option_index")
    val selectedOptionIndex: Int? = null,
    @ColumnInfo(name = "text_response")
    val textResponse: String? = null,
    val result: String,
    @ColumnInfo(name = "ai_feedback")
    val aiFeedback: String? = null,
    @ColumnInfo(name = "evaluation_evidence")
    val evaluationEvidence: String? = null,
    @ColumnInfo(name = "earned_points")
    val earnedPoints: Double,
    @ColumnInfo(name = "possible_points")
    val possiblePoints: Double,
)

@Entity(
    tableName = "matching_pairs",
    primaryKeys = ["question_id", "left_id"],
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["question_id"],
            onUpdate = ForeignKey.RESTRICT,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MatchingPairEntity::class,
            parentColumns = ["question_id", "right_id"],
            childColumns = ["question_id", "selected_right_id"],
            onUpdate = ForeignKey.RESTRICT,
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(
            value = ["question_id", "right_id"],
            unique = true,
            name = "index_matching_pairs_question_id_right_id",
        ),
        Index(
            value = ["question_id", "left_position"],
            unique = true,
            name = "index_matching_pairs_question_id_left_position",
        ),
        Index(
            value = ["question_id", "right_position"],
            unique = true,
            name = "index_matching_pairs_question_id_right_position",
        ),
        Index(
            value = ["question_id", "selected_right_id"],
            name = "index_matching_pairs_question_id_selected_right_id",
        ),
    ],
)
data class MatchingPairEntity(
    @ColumnInfo(name = "question_id")
    val questionId: Long,
    @ColumnInfo(name = "left_id")
    val leftId: String,
    @ColumnInfo(name = "left_text")
    val leftText: String,
    @ColumnInfo(name = "left_position")
    val leftPosition: Int,
    @ColumnInfo(name = "right_id")
    val rightId: String,
    @ColumnInfo(name = "right_text")
    val rightText: String,
    @ColumnInfo(name = "right_position")
    val rightPosition: Int,
    @ColumnInfo(name = "selected_right_id")
    val selectedRightId: String? = null,
)

object DocumentFormats {
    const val TXT = "txt"
    const val PDF = "pdf"
    const val DOCX = "docx"
    const val PPTX = "pptx"

    val all = setOf(TXT, PDF, DOCX, PPTX)
}

object QuizTypes {
    const val MULTIPLE_CHOICE = "multiple_choice"
    const val FILL_IN_THE_BLANK = "fill_in_the_blank"
    const val IDENTIFICATION = "identification"
    const val MATCHING = "matching"
    const val EXPLANATION = "explanation"

    val all = setOf(MULTIPLE_CHOICE, FILL_IN_THE_BLANK, IDENTIFICATION, MATCHING, EXPLANATION)
    val aiEvaluated = setOf(FILL_IN_THE_BLANK, IDENTIFICATION, EXPLANATION)
}

object QuestionResults {
    const val CORRECT = "correct"
    const val PARTIALLY_CORRECT = "partially_correct"
    const val INCORRECT = "incorrect"
    const val UNCERTAIN = "uncertain"
    const val EVALUATION_FAILED = "evaluation_failed"
    const val UNANSWERED = "unanswered"

    val all = setOf(CORRECT, PARTIALLY_CORRECT, INCORRECT, UNCERTAIN, EVALUATION_FAILED, UNANSWERED)
}

object AttemptStatuses {
    const val SAVED = "saved"
    const val COMPLETED = "completed"

    val all = setOf(SAVED, COMPLETED)
}
