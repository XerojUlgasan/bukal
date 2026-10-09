package com.example.bukal.data.local

import java.time.LocalDate
import kotlin.math.abs

internal object DatabaseValidation {
    private const val SCORE_EPSILON = 0.000_001

    fun validateMaterial(material: MaterialEntity, passages: List<PassageEntity>) {
        require(material.displayName.isNotBlank()) { "Material display name must not be blank" }
        require(material.documentFormat in DocumentFormats.all) { "Unsupported document format" }
        require(material.mimeType == null || material.mimeType.isNotBlank()) { "MIME type must not be blank" }
        require(material.retainedFilePath == null || material.retainedFilePath.isNotBlank()) {
            "Retained file path must not be blank"
        }
        require(material.importedAtEpochMs >= 0) { "Import time must not be negative" }
        require(passages.isNotEmpty()) { "An imported material must contain at least one passage" }
        require(passages.all { it.position >= 0 && it.sourceId.isNotBlank() && it.content.isNotBlank() }) {
            "Passages need a position, source ID, and content"
        }
        require(passages.map { it.position }.toSet().size == passages.size) {
            "Passage positions must be unique"
        }
        require(passages.map { it.sourceId }.toSet().size == passages.size) {
            "Passage source IDs must be unique"
        }
    }

    fun validateChunks(passage: PassageEntity, chunks: List<SearchChunkEntity>) {
        require(chunks.isNotEmpty()) { "A passage search index must contain at least one chunk" }
        require(chunks.map { it.chunkIndex }.toSet().size == chunks.size) {
            "Search chunk indexes must be unique"
        }
        require(chunks.map { it.chunkIndex }.sorted() == chunks.indices.toList()) {
            "Search chunk indexes must start at zero and be contiguous"
        }

        chunks.forEach { chunk ->
            require(chunk.startOffset >= 0 && chunk.endOffset > chunk.startOffset) {
                "Search chunk offsets are invalid"
            }
            require(chunk.endOffset <= passage.content.length) { "Search chunk exceeds its passage" }
            require(passage.content.substring(chunk.startOffset, chunk.endOffset) == chunk.content) {
                "Search chunk content must match its passage offsets"
            }
            validateEmbedding(chunk)
        }

        val ordered = chunks.sortedBy { it.chunkIndex }
        require(ordered.first().startOffset == 0 && ordered.last().endOffset == passage.content.length) {
            "Search chunks must cover the complete passage"
        }
        ordered.zipWithNext().forEach { (previous, next) ->
            require(next.startOffset > previous.startOffset && next.endOffset > previous.endOffset) {
                "Search chunks must advance through the passage"
            }
            require(next.startOffset < previous.endOffset) {
                "Adjacent search chunks must overlap"
            }
        }
    }

    fun validateEmbedding(chunk: SearchChunkEntity) {
        val fields = listOf(
            chunk.embeddingModelId,
            chunk.embeddingDimensions,
            chunk.embeddingVector,
        )
        require(fields.all { it == null } || fields.all { it != null }) {
            "Embedding model, dimensions, and vector must be set together"
        }
        if (chunk.embeddingVector != null) {
            require(chunk.embeddingModelId!!.isNotBlank()) { "Embedding model ID must not be blank" }
            require(chunk.embeddingDimensions!! > 0) { "Embedding dimensions must be positive" }
            EmbeddingVectorCodec.decode(chunk.embeddingVector, chunk.embeddingDimensions)
        }
    }

    fun validateAttempt(record: CompletedAttemptRecord, passage: PassageEntity) {
        val attempt = record.attempt
        require(attempt.passageId == passage.id) { "Attempt passage does not exist" }
        require(attempt.quizModelId.isNotBlank()) { "Quiz model ID must not be blank" }
        require(attempt.generatedAtEpochMs >= 0) { "Generation time must not be negative" }
        require(attempt.status in AttemptStatuses.all) { "Unsupported attempt status" }
        require(attempt.completedAtEpochMs >= 0) { "Completion time must not be negative" }
        LocalDate.parse(attempt.completedLocalDate)
        require(record.questions.size in 1..5) { "A completed attempt must contain one to five questions" }
        require(record.questions.map { it.question.position }.toSet() == record.questions.indices.toSet()) {
            "Question positions must start at zero and be contiguous"
        }

        record.questions.forEach { validateQuestion(it, passage) }
        val earned = record.questions.sumOf { it.question.earnedPoints }
        val possible = record.questions.sumOf { it.question.possiblePoints }
        require(closeEnough(earned, attempt.earnedPoints)) { "Attempt earned points do not match questions" }
        require(closeEnough(possible, attempt.possiblePoints)) { "Attempt possible points do not match questions" }
        require(attempt.highestEarnedPoints >= attempt.earnedPoints) {
            "Highest earned points must include the latest score"
        }
        require(attempt.highestEarnedPoints <= attempt.possiblePoints) {
            "Highest earned points must not exceed possible points"
        }
        if (attempt.status == AttemptStatuses.SAVED) {
            require(attempt.earnedPoints == 0.0) { "Saved quiz must not have earned points" }
            require(attempt.highestEarnedPoints == 0.0) { "Saved quiz must not have a highest score" }
            require(record.questions.all { it.question.result == QuestionResults.UNANSWERED }) {
                "Saved quiz questions must be unanswered"
            }
            require(record.questions.all {
                it.question.selectedOptionIndex == null &&
                    it.question.textResponse == null &&
                    it.matchingPairs.all { pair -> pair.selectedRightId == null }
            }) { "Saved quiz must not contain learner answers" }
        }
    }

    private fun validateQuestion(record: QuestionRecord, passage: PassageEntity) {
        val question = record.question
        require(question.quizType in QuizTypes.all) { "Unsupported quiz type" }
        require(question.prompt.isNotBlank() && question.explanation.isNotBlank()) {
            "Question prompt and explanation must not be blank"
        }
        require(question.sourceId == passage.sourceId) { "Question source ID does not match its passage" }
        require(question.evidence.isNotBlank() && passage.content.contains(question.evidence)) {
            "Question evidence must be exact passage text"
        }
        require(question.result in QuestionResults.all) { "Unsupported question result" }
        validateScore(question)

        when (question.quizType) {
            QuizTypes.MULTIPLE_CHOICE -> validateMultipleChoice(question, record.matchingPairs)
            QuizTypes.MATCHING -> validateMatching(question, record.matchingPairs)
            else -> validateAiEvaluated(question, record.matchingPairs)
        }
    }

    private fun validateMultipleChoice(
        question: QuestionEntity,
        matchingPairs: List<MatchingPairEntity>,
    ) {
        val options = listOf(question.option0, question.option1, question.option2, question.option3)
        require(options.all { !it.isNullOrBlank() }) { "Multiple choice requires four options" }
        require(options.map { it!!.trim() }.toSet().size == 4) { "Multiple-choice options must be unique" }
        require(question.correctOptionIndex in 0..3) { "Multiple-choice answer index is invalid" }
        require(question.selectedOptionIndex == null || question.selectedOptionIndex in 0..3) {
            "Selected option index is invalid"
        }
        require(question.referenceAnswer == null && question.gradingCriteria == null) {
            "Multiple choice must not store AI grading data"
        }
        require(question.textResponse == null && question.aiFeedback == null && question.evaluationEvidence == null) {
            "Multiple choice must not store text or AI evaluation data"
        }
        require(matchingPairs.isEmpty()) { "Multiple choice must not contain matching pairs" }

        val expectedResult = when (question.selectedOptionIndex) {
            null -> QuestionResults.UNANSWERED
            question.correctOptionIndex -> QuestionResults.CORRECT
            else -> QuestionResults.INCORRECT
        }
        require(question.result == expectedResult) { "Multiple-choice result does not match the selected option" }
    }

    private fun validateAiEvaluated(
        question: QuestionEntity,
        matchingPairs: List<MatchingPairEntity>,
    ) {
        require(question.referenceAnswer?.isNotBlank() == true) { "AI-evaluated question needs a reference answer" }
        require(question.gradingCriteria == null) { "AI-evaluated question does not use grading criteria" }
        require(question.option0 == null && question.option1 == null && question.option2 == null && question.option3 == null) {
            "AI-evaluated question must not contain options"
        }
        require(question.correctOptionIndex == null && question.selectedOptionIndex == null) {
            "AI-evaluated question must not contain option indexes"
        }
        require(matchingPairs.isEmpty()) { "AI-evaluated question must not contain matching pairs" }

        when (question.result) {
            QuestionResults.CORRECT,
            QuestionResults.INCORRECT,
            -> {
                require(question.textResponse?.isNotBlank() == true) { "Evaluated answer must not be blank" }
                require(question.aiFeedback == null && question.evaluationEvidence == null) {
                    "Boolean grading must not store generated feedback or evidence"
                }
            }
            QuestionResults.UNANSWERED -> require(question.textResponse.isNullOrBlank()) {
                "Unanswered question must not contain a response"
            }
            else -> error("AI-evaluated questions support only correct, incorrect, or unanswered results")
        }
    }

    private fun validateMatching(
        question: QuestionEntity,
        matchingPairs: List<MatchingPairEntity>,
    ) {
        require(question.option0 == null && question.option1 == null && question.option2 == null && question.option3 == null) {
            "Matching question must not contain options"
        }
        require(question.correctOptionIndex == null && question.selectedOptionIndex == null) {
            "Matching question must not contain option indexes"
        }
        require(question.referenceAnswer == null && question.gradingCriteria == null) {
            "Matching question must not store AI grading data"
        }
        require(question.textResponse == null && question.aiFeedback == null && question.evaluationEvidence == null) {
            "Matching question must not store text or AI evaluation data"
        }
        require(matchingPairs.size >= 2) { "Matching question must contain at least two pairs" }
        require(matchingPairs.map { it.leftId }.toSet().size == matchingPairs.size) {
            "Matching left IDs must be unique"
        }
        require(matchingPairs.map { it.rightId }.toSet().size == matchingPairs.size) {
            "Matching right IDs must be unique"
        }
        require(matchingPairs.map { it.leftPosition }.toSet().size == matchingPairs.size) {
            "Matching left positions must be unique"
        }
        require(matchingPairs.map { it.rightPosition }.toSet().size == matchingPairs.size) {
            "Matching right positions must be unique"
        }
        val rightIds = matchingPairs.map { it.rightId }.toSet()
        require(matchingPairs.all { it.selectedRightId == null || it.selectedRightId in rightIds }) {
            "Selected matching ID does not belong to this question"
        }

        when (question.result) {
            QuestionResults.UNANSWERED -> require(matchingPairs.all { it.selectedRightId == null }) {
                "Unanswered matching question must not contain selections"
            }
            QuestionResults.CORRECT -> require(matchingPairs.all { it.selectedRightId == it.rightId }) {
                "Correct matching result must contain only expected pairs"
            }
            QuestionResults.INCORRECT -> require(
                matchingPairs.all { it.selectedRightId != null } &&
                    matchingPairs.any { it.selectedRightId != it.rightId },
            ) { "Incorrect matching result must contain a complete incorrect mapping" }
            else -> error("Matching supports only correct, incorrect, or unanswered results")
        }
    }

    private fun validateScore(question: QuestionEntity) {
        val expected = when (question.result) {
            QuestionResults.CORRECT -> 1.0 to 1.0
            QuestionResults.PARTIALLY_CORRECT -> 0.5 to 1.0
            QuestionResults.INCORRECT -> 0.0 to 1.0
            QuestionResults.UNCERTAIN,
            QuestionResults.EVALUATION_FAILED,
            -> 0.0 to 0.0
            QuestionResults.UNANSWERED -> 0.0 to 1.0
            else -> error("Unsupported question result")
        }
        require(closeEnough(question.earnedPoints, expected.first)) { "Question earned points are invalid" }
        require(closeEnough(question.possiblePoints, expected.second)) { "Question possible points are invalid" }
    }

    private fun closeEnough(left: Double, right: Double): Boolean = abs(left - right) < SCORE_EPSILON
}
