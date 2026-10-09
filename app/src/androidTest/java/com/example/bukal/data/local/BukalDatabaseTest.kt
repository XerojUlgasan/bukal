package com.example.bukal.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BukalDatabaseTest {
    private lateinit var database: BukalDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BukalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun schemaContainsOnlyTheEightReviewedTables() {
        val tableNames = buildList {
            database.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY name",
            ).use { cursor ->
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

        assertEquals(
            listOf(
                "attempts",
                "matching_pairs",
                "materials",
                "passages",
                "questions",
                "quiz_set_items",
                "quiz_sets",
                "search_chunks",
            ),
            tableNames,
        )
    }

    @Test
    fun materialSummaryIsSavedOnlyOnce() = runBlocking {
        val materialId = database.materialDao().insert(
            ImportedMaterial(
                material = MaterialEntity(
                    displayName = "One-time summary",
                    documentFormat = DocumentFormats.TXT,
                    importedAtEpochMs = 1,
                ),
                passages = listOf(
                    PassageEntity(
                        materialId = 0,
                        sourceId = "TXT-P001",
                        position = 0,
                        content = "Alpha is first.",
                    ),
                ),
            ),
        )

        assertEquals(
            1,
            database.materialDao().saveSummaryOnce(
                materialId,
                "# Overall Summary\n\nFirst summary.",
                "quiz-model-a",
                100,
            ),
        )
        assertEquals(
            0,
            database.materialDao().saveSummaryOnce(
                materialId,
                "# Overall Summary\n\nReplacement summary.",
                "quiz-model-b",
                200,
            ),
        )

        val saved = requireNotNull(database.materialDao().getById(materialId))
        assertEquals("# Overall Summary\n\nFirst summary.", saved.summaryMarkdown)
        assertEquals("quiz-model-a", saved.summaryModelId)
        assertEquals(100L, saved.summarizedAtEpochMs)
    }

    @Test
    fun materialSearchIndexAttemptHistoryAndCascadeWorkTogether() = runBlocking {
        val materialId = database.materialDao().insert(
            ImportedMaterial(
                material = MaterialEntity(
                    displayName = "Local History",
                    documentFormat = DocumentFormats.TXT,
                    mimeType = "text/plain",
                    retainedFilePath = "imported-materials/local-history.txt",
                    importedAtEpochMs = 1_760_000_000_000,
                ),
                passages = listOf(
                    PassageEntity(
                        materialId = 0,
                        sourceId = "TXT-P001",
                        position = 0,
                        title = "Opening",
                        content = PASSAGE_CONTENT,
                    ),
                ),
            ),
        )
        val passage = database.materialDao().getPassages(materialId).single()

        database.searchChunkDao().replaceForPassage(
            passageId = passage.id,
            chunks = listOf(
                SearchChunkEntity(
                    passageId = passage.id,
                    chunkIndex = 0,
                    startOffset = 0,
                    endOffset = 16,
                    content = "Alpha beta gamma",
                ),
                SearchChunkEntity(
                    passageId = passage.id,
                    chunkIndex = 1,
                    startOffset = 11,
                    endOffset = PASSAGE_CONTENT.length,
                    content = "gamma delta",
                ),
            ),
        )
        val chunks = database.searchChunkDao().getForPassage(passage.id)
        assertEquals(2, chunks.size)
        assertEquals(2, database.searchChunkDao().getPendingCount())
        assertEquals("gamma", chunks[0].content.takeLast(5))
        assertEquals("gamma", chunks[1].content.take(5))

        val firstVector = floatArrayOf(1f, 0f, 0f)
        val secondVector = floatArrayOf(0f, 1f, 0f)
        database.searchChunkDao().setEmbedding(chunks[0].id, EMBEDDING_MODEL, firstVector)
        database.searchChunkDao().setEmbedding(chunks[1].id, EMBEDDING_MODEL, secondVector)
        assertEquals(0, database.searchChunkDao().getPendingCount())

        val candidates = database.searchChunkDao().getSearchCandidates(EMBEDDING_MODEL, 3)
        assertEquals(2, candidates.size)
        assertArrayEquals(
            firstVector,
            EmbeddingVectorCodec.decode(candidates[0].embeddingVector, candidates[0].embeddingDimensions),
            0f,
        )

        val questionRecords = listOf(
            QuestionRecord(correctMultipleChoice(position = 0)),
            QuestionRecord(unansweredOpenQuestion(position = 1, QuizTypes.FILL_IN_THE_BLANK)),
            QuestionRecord(correctOpenQuestion(position = 2, QuizTypes.IDENTIFICATION)),
            QuestionRecord(
                question = matchingQuestion(position = 3),
                matchingPairs = listOf(
                    MatchingPairEntity(
                        questionId = 0,
                        leftId = "left-1",
                        leftText = "Alpha",
                        leftPosition = 0,
                        rightId = "right-1",
                        rightText = "First",
                        rightPosition = 0,
                        selectedRightId = "right-2",
                    ),
                    MatchingPairEntity(
                        questionId = 0,
                        leftId = "left-2",
                        leftText = "Beta",
                        leftPosition = 1,
                        rightId = "right-2",
                        rightText = "Second",
                        rightPosition = 1,
                        selectedRightId = "right-1",
                    ),
                ),
            ),
            QuestionRecord(incorrectExplanation(position = 4)),
        )
        val completedRecord = CompletedAttemptRecord(
                attempt = AttemptEntity(
                    passageId = passage.id,
                    quizModelId = "quiz-model",
                    completedAtEpochMs = 1_760_000_000_000,
                    completedLocalDate = "2026-10-09",
                    earnedPoints = 2.0,
                    possiblePoints = 5.0,
                ),
                questions = questionRecords,
        )
        val attemptId = database.attemptDao().insert(completedRecord)
        val completedSetId = database.attemptDao().getOrCreateQuizSet(
            attemptIds = listOf(attemptId),
            createdAtEpochMs = 1_760_000_000_000,
            localDate = "2026-10-09",
        )
        val firstSet = requireNotNull(database.attemptDao().getSavedQuizSet(completedSetId))
        database.attemptDao().replaceQuizSetWithCompleted(
            CompletedQuizSetRecord(
                quizSet = firstSet.quizSet.copy(
                    status = AttemptStatuses.COMPLETED,
                    earnedPoints = 2.0,
                    highestEarnedPoints = 2.0,
                ),
                attempts = listOf(CompletedQuizSetAttempt(attemptId, completedRecord)),
            ),
        )

        val savedQuizId = database.attemptDao().insert(
            CompletedAttemptRecord(
                attempt = AttemptEntity(
                    passageId = passage.id,
                    quizModelId = "quiz-model",
                    generatedAtEpochMs = 1_760_000_100_000,
                    status = AttemptStatuses.SAVED,
                    completedAtEpochMs = 1_760_000_100_000,
                    completedLocalDate = "2026-10-09",
                    earnedPoints = 0.0,
                    possiblePoints = 1.0,
                ),
                questions = listOf(
                    QuestionRecord(
                        correctMultipleChoice(position = 0).copy(
                            selectedOptionIndex = null,
                            result = QuestionResults.UNANSWERED,
                            earnedPoints = 0.0,
                        ),
                    ),
                ),
            ),
        )
        val savedSetId = database.attemptDao().getOrCreateQuizSet(
            attemptIds = listOf(savedQuizId),
            createdAtEpochMs = 1_760_000_100_000,
            localDate = "2026-10-09",
        )

        val history = database.attemptDao().getHistory()
        assertEquals(listOf(savedSetId, completedSetId), history.map(QuizSetHistoryRow::quizSetId))
        assertEquals(AttemptStatuses.SAVED, history.first().status)
        assertEquals("Local History", history.first().materialName)
        assertEquals(1, database.attemptDao().getDailyActivity("2026").single().completedCount)
        assertEquals(2, database.attemptDao().getDailyCorrectAnswers().single().correctCount)

        val savedQuiz = requireNotNull(database.attemptDao().getSavedQuiz(savedQuizId))
        assertEquals(AttemptStatuses.SAVED, savedQuiz.attempt.status)
        assertEquals(QuestionResults.UNANSWERED, savedQuiz.questions.single().question.result)
        assertEquals(savedQuizId, database.attemptDao().getQuizForPassage(passage.id)?.attempt?.id)

        val completedSavedRecord = CompletedAttemptRecord(
                attempt = savedQuiz.attempt.copy(
                    status = AttemptStatuses.COMPLETED,
                    completedAtEpochMs = 1_760_000_200_000,
                    earnedPoints = 1.0,
                    highestEarnedPoints = 1.0,
                ),
                questions = listOf(QuestionRecord(correctMultipleChoice(position = 0))),
        )
        val savedSet = requireNotNull(database.attemptDao().getSavedQuizSet(savedSetId))
        database.attemptDao().replaceQuizSetWithCompleted(
            CompletedQuizSetRecord(
                quizSet = savedSet.quizSet.copy(
                    status = AttemptStatuses.COMPLETED,
                    completedAtEpochMs = 1_760_000_200_000,
                    earnedPoints = 1.0,
                    highestEarnedPoints = 1.0,
                ),
                attempts = listOf(CompletedQuizSetAttempt(savedQuizId, completedSavedRecord)),
            ),
        )
        assertEquals(AttemptStatuses.COMPLETED, database.attemptDao().getAttempt(savedQuizId)?.status)
        assertEquals(2, database.attemptDao().getDailyActivity("2026").single().completedCount)
        assertEquals(2, database.attemptDao().getDailyCorrectAnswers().single().correctCount)

        val retakenRecord = CompletedAttemptRecord(
                attempt = requireNotNull(database.attemptDao().getAttempt(savedQuizId)).copy(
                    completedAtEpochMs = 1_760_000_300_000,
                    earnedPoints = 0.0,
                ),
                questions = listOf(
                    QuestionRecord(
                        correctMultipleChoice(position = 0).copy(
                            selectedOptionIndex = 1,
                            result = QuestionResults.INCORRECT,
                            earnedPoints = 0.0,
                        ),
                    ),
                ),
        )
        val completedSet = requireNotNull(database.attemptDao().getSavedQuizSet(savedSetId))
        database.attemptDao().replaceQuizSetWithCompleted(
            CompletedQuizSetRecord(
                quizSet = completedSet.quizSet.copy(
                    completedAtEpochMs = 1_760_000_300_000,
                    earnedPoints = 0.0,
                ),
                attempts = listOf(CompletedQuizSetAttempt(savedQuizId, retakenRecord)),
            ),
        )
        assertEquals(savedQuizId, database.attemptDao().getQuizForPassage(passage.id)?.attempt?.id)
        assertEquals(2, database.attemptDao().getHistory().size)
        assertEquals(0.0, database.attemptDao().getAttempt(savedQuizId)?.earnedPoints ?: -1.0, 0.0)
        assertEquals(1.0, database.attemptDao().getAttempt(savedQuizId)?.highestEarnedPoints ?: -1.0, 0.0)
        assertEquals(1.0, database.attemptDao().getSavedQuizSet(savedSetId)?.quizSet?.highestEarnedPoints ?: -1.0, 0.0)
        assertEquals(2, database.attemptDao().getDailyCorrectAnswers().single().correctCount)

        val savedQuestions = database.attemptDao().getQuestions(attemptId)
        assertEquals(5, savedQuestions.size)
        assertEquals(QuizTypes.all, savedQuestions.map { it.quizType }.toSet())
        val savedMatchingPairs = database.attemptDao().getMatchingPairs(savedQuestions[3].id)
        assertEquals(listOf("right-2", "right-1"), savedMatchingPairs.map { it.selectedRightId })

        assertEquals(2, database.searchChunkDao().getForPassage(passage.id).size)
    }

    @Test
    fun questionPoolLoadsEveryPassageAttemptNewestFirst() = runBlocking {
        val materialId = database.materialDao().insert(
            ImportedMaterial(
                material = MaterialEntity(
                    displayName = "Question pool",
                    documentFormat = DocumentFormats.TXT,
                    importedAtEpochMs = 1,
                ),
                passages = listOf(
                    PassageEntity(
                        materialId = 0,
                        sourceId = "TXT-P001",
                        position = 0,
                        content = "Passage content for saved questions.",
                    ),
                ),
            ),
        )
        val passage = database.materialDao().getPassages(materialId).single()
        val olderAttemptId = database.attemptDao().insert(savedAttempt(passage))
        val newerAttempt = savedAttempt(passage)
        val newerAttemptId = database.attemptDao().insert(
            newerAttempt.copy(
                attempt = newerAttempt.attempt.copy(generatedAtEpochMs = 20),
            ),
        )

        assertEquals(
            listOf(newerAttemptId, olderAttemptId),
            database.attemptDao().getQuizzesForPassage(passage.id).map { it.attempt.id },
        )
    }

    @Test
    fun overlappingPassageSelectionsReuseAttemptsButCreateWholeHistoryItems() = runBlocking {
        val materialId = database.materialDao().insert(
            ImportedMaterial(
                material = MaterialEntity(
                    displayName = "Shared lesson",
                    documentFormat = DocumentFormats.TXT,
                    importedAtEpochMs = 1,
                ),
                passages = (1..3).map { index ->
                    PassageEntity(
                        materialId = 0,
                        sourceId = "TXT-P00$index",
                        position = index - 1,
                        content = "Passage $index has enough source content.",
                    )
                },
            ),
        )
        val passages = database.materialDao().getPassages(materialId)
        val attemptIds = passages.map { passage ->
            database.attemptDao().insert(savedAttempt(passage))
        }

        val firstSetId = database.attemptDao().getOrCreateQuizSet(
            attemptIds = attemptIds.take(2),
            createdAtEpochMs = 100,
            localDate = "2026-10-10",
        )
        val overlappingSetId = database.attemptDao().getOrCreateQuizSet(
            attemptIds = listOf(attemptIds[0], attemptIds[2]),
            createdAtEpochMs = 200,
            localDate = "2026-10-10",
        )
        val reusedSetId = database.attemptDao().getOrCreateQuizSet(
            attemptIds = attemptIds.take(2),
            createdAtEpochMs = 300,
            localDate = "2026-10-10",
        )
        val firstSet = requireNotNull(database.attemptDao().getSavedQuizSet(firstSetId))
        database.attemptDao().replaceQuizSetWithCompleted(
            CompletedQuizSetRecord(
                quizSet = firstSet.quizSet.copy(
                    status = AttemptStatuses.COMPLETED,
                    completedAtEpochMs = 400,
                    earnedPoints = 2.0,
                    highestEarnedPoints = 2.0,
                ),
                attempts = firstSet.quizzes.map { quiz ->
                    CompletedQuizSetAttempt(
                        attemptId = quiz.attempt.id,
                        record = CompletedAttemptRecord(
                            attempt = quiz.attempt.copy(
                                status = AttemptStatuses.COMPLETED,
                                completedAtEpochMs = 400,
                                earnedPoints = 1.0,
                                highestEarnedPoints = 1.0,
                            ),
                            questions = quiz.questions.map { question ->
                                question.copy(
                                    question = question.question.copy(
                                        selectedOptionIndex = 0,
                                        result = QuestionResults.CORRECT,
                                        earnedPoints = 1.0,
                                    ),
                                )
                            },
                        ),
                    )
                },
            ),
        )

        assertEquals(firstSetId, reusedSetId)
        assertEquals(2, database.attemptDao().getHistory().size)
        assertEquals(1, database.attemptDao().getDailyActivity("2026").single().completedCount)
        assertEquals(
            listOf(attemptIds[0], attemptIds[2]),
            database.attemptDao().getSavedQuizSet(overlappingSetId)?.quizzes?.map { it.attempt.id },
        )
    }

    private fun savedAttempt(passage: PassageEntity) = CompletedAttemptRecord(
        attempt = AttemptEntity(
            passageId = passage.id,
            quizModelId = "quiz-model",
            generatedAtEpochMs = 10,
            status = AttemptStatuses.SAVED,
            completedAtEpochMs = 10,
            completedLocalDate = "2026-10-10",
            earnedPoints = 0.0,
            possiblePoints = 1.0,
        ),
        questions = listOf(
            QuestionRecord(
                correctMultipleChoice(position = 0).copy(
                    sourceId = passage.sourceId,
                    evidence = passage.content,
                    selectedOptionIndex = null,
                    result = QuestionResults.UNANSWERED,
                    earnedPoints = 0.0,
                ),
            ),
        ),
    )

    private fun correctMultipleChoice(position: Int) = QuestionEntity(
        attemptId = 0,
        position = position,
        quizType = QuizTypes.MULTIPLE_CHOICE,
        prompt = "Question $position",
        explanation = "The passage contains the answer.",
        sourceId = "TXT-P001",
        evidence = "Alpha",
        option0 = "A",
        option1 = "B",
        option2 = "C",
        option3 = "D",
        correctOptionIndex = 0,
        selectedOptionIndex = 0,
        result = QuestionResults.CORRECT,
        earnedPoints = 1.0,
        possiblePoints = 1.0,
    )

    private fun matchingQuestion(position: Int) = QuestionEntity(
        attemptId = 0,
        position = position,
        quizType = QuizTypes.MATCHING,
        prompt = "Match the terms.",
        explanation = "The passage contains both terms.",
        sourceId = "TXT-P001",
        evidence = "Alpha beta",
        result = QuestionResults.INCORRECT,
        earnedPoints = 0.0,
        possiblePoints = 1.0,
    )

    private fun unansweredOpenQuestion(position: Int, type: String) = QuestionEntity(
        attemptId = 0,
        position = position,
        quizType = type,
        prompt = "Complete or identify Alpha.",
        explanation = "The passage starts with Alpha.",
        sourceId = "TXT-P001",
        evidence = "Alpha",
        referenceAnswer = "Alpha",
        result = QuestionResults.UNANSWERED,
        earnedPoints = 0.0,
        possiblePoints = 1.0,
    )

    private fun correctOpenQuestion(position: Int, type: String) =
        unansweredOpenQuestion(position, type).copy(
            textResponse = "Alpha",
            result = QuestionResults.CORRECT,
            earnedPoints = 1.0,
        )

    private fun incorrectExplanation(position: Int) =
        unansweredOpenQuestion(position, QuizTypes.EXPLANATION).copy(
            textResponse = "The terms are related.",
            result = QuestionResults.INCORRECT,
        )

    private companion object {
        const val PASSAGE_CONTENT = "Alpha beta gamma delta"
        const val EMBEDDING_MODEL = "ibm-granite/granite-embedding-311m-multilingual-r2"
    }
}
