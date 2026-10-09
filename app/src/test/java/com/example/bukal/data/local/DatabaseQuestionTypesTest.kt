package com.example.bukal.data.local

import org.junit.Test

class DatabaseQuestionTypesTest {
    @Test
    fun reviewedDatabaseShapeAcceptsAllFiveQuestionTypes() {
        val passage = PassageEntity(
            id = 1,
            materialId = 1,
            sourceId = "TXT-P001",
            position = 0,
            content = "Alpha is first. Beta is second.",
        )
        val questions = listOf(
            QuestionRecord(
                baseQuestion(0, QuizTypes.MULTIPLE_CHOICE).copy(
                    option0 = "Alpha",
                    option1 = "Beta",
                    option2 = "Gamma",
                    option3 = "Delta",
                    correctOptionIndex = 0,
                    selectedOptionIndex = 0,
                    result = QuestionResults.CORRECT,
                    earnedPoints = 1.0,
                ),
            ),
            QuestionRecord(openQuestion(1, QuizTypes.FILL_IN_THE_BLANK, QuestionResults.UNANSWERED)),
            QuestionRecord(
                openQuestion(2, QuizTypes.IDENTIFICATION, QuestionResults.CORRECT).copy(
                    textResponse = "Alpha",
                    earnedPoints = 1.0,
                ),
            ),
            QuestionRecord(
                question = baseQuestion(3, QuizTypes.MATCHING).copy(
                    result = QuestionResults.CORRECT,
                    earnedPoints = 1.0,
                ),
                matchingPairs = listOf(
                    matchingPair("l1", "Alpha", "r1", "First", 0),
                    matchingPair("l2", "Beta", "r2", "Second", 1),
                ),
            ),
            QuestionRecord(
                openQuestion(4, QuizTypes.EXPLANATION, QuestionResults.INCORRECT).copy(
                    textResponse = "They have an order.",
                ),
            ),
        )

        DatabaseValidation.validateAttempt(
            CompletedAttemptRecord(
                attempt = AttemptEntity(
                    passageId = passage.id,
                    quizModelId = "quiz-model",
                    completedAtEpochMs = 1,
                    completedLocalDate = "2026-10-09",
                    earnedPoints = 3.0,
                    possiblePoints = 5.0,
                ),
                questions = questions,
            ),
            passage,
        )
    }

    @Test
    fun reviewedDatabaseShapeAcceptsPartialGeneratedQuiz() {
        val passage = PassageEntity(
            id = 1,
            materialId = 1,
            sourceId = "TXT-P001",
            position = 0,
            content = "Alpha is first. Beta is second.",
        )
        val questions = (0..2).map { position ->
            QuestionRecord(
                baseQuestion(position, QuizTypes.MULTIPLE_CHOICE).copy(
                    option0 = "Alpha",
                    option1 = "Beta",
                    option2 = "Gamma",
                    option3 = "Delta",
                    correctOptionIndex = 0,
                ),
            )
        }

        DatabaseValidation.validateAttempt(
            CompletedAttemptRecord(
                attempt = AttemptEntity(
                    passageId = passage.id,
                    quizModelId = "quiz-model",
                    completedAtEpochMs = 1,
                    completedLocalDate = "2026-10-09",
                    earnedPoints = 0.0,
                    possiblePoints = 3.0,
                ),
                questions = questions,
            ),
            passage,
        )
    }

    private fun baseQuestion(position: Int, type: String) = QuestionEntity(
        attemptId = 0,
        position = position,
        quizType = type,
        prompt = "Question $position",
        explanation = "The passage supports the answer.",
        sourceId = "TXT-P001",
        evidence = "Alpha is first.",
        result = QuestionResults.UNANSWERED,
        earnedPoints = 0.0,
        possiblePoints = 1.0,
    )

    private fun openQuestion(position: Int, type: String, result: String) =
        baseQuestion(position, type).copy(
            referenceAnswer = "Alpha",
            result = result,
        )

    private fun matchingPair(
        leftId: String,
        leftText: String,
        rightId: String,
        rightText: String,
        position: Int,
    ) = MatchingPairEntity(
        questionId = 0,
        leftId = leftId,
        leftText = leftText,
        leftPosition = position,
        rightId = rightId,
        rightText = rightText,
        rightPosition = position,
        selectedRightId = rightId,
    )
}
