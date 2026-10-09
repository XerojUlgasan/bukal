package com.example.bukal.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bukal.data.local.BukalDatabase
import com.example.bukal.data.local.PassageEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuizGenerationUiState(
    val isGenerating: Boolean = false,
    val currentPassageNumber: Int = 1,
    val totalPassages: Int = 1,
    val currentQuestionNumber: Int = 1,
    val totalQuestions: Int = QuizGenerator.QUESTION_COUNT,
    val quizSetId: Long? = null,
    val passageQuizzes: List<PassageQuiz> = emptyList(),
    val errorMessage: String? = null,
) {
    val questions: List<QuizQuestion>
        get() = passageQuizzes.flatMap(PassageQuiz::questions)

    val failedQuestionCount: Int
        get() = passageQuizzes.sumOf(PassageQuiz::failedQuestionCount)
}

data class PassageQuiz(
    val request: QuizGenerationRequest,
    val questions: List<QuizQuestion>,
    val failedQuestionCount: Int,
    val savedQuizId: Long,
)

internal fun List<QuizQuestion>.withPassageScopedIds(passageId: Long): List<QuizQuestion> =
    map { question -> question.copy(id = "$passageId:${question.id}") }

class QuizGenerationViewModel(application: Application) : AndroidViewModel(application) {
    private val database = BukalDatabase.create(application)
    private val persistenceRepository = QuizPersistenceRepository(database)
    private val modelRunner = QuizModelRunner(application)
    private val mutableUiState = MutableStateFlow(QuizGenerationUiState())
    private val generator = QuizGenerator(
        onValidationFailure = modelRunner::logValidationFailure,
        onQuestionStarted = { current, total ->
            mutableUiState.update {
                it.copy(currentQuestionNumber = current, totalQuestions = total)
            }
        },
        complete = modelRunner::complete,
    )
    private var generationJob: Job? = null
    val uiState: StateFlow<QuizGenerationUiState> = mutableUiState.asStateFlow()
    private val mutableCachedQuizQuestionCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val cachedQuizQuestionCounts: StateFlow<Map<Long, Int>> =
        mutableCachedQuizQuestionCounts.asStateFlow()

    fun loadCachedQuizQuestionCounts(passages: List<PassageEntity>) {
        viewModelScope.launch {
            mutableCachedQuizQuestionCounts.value = passages.mapNotNull { passage ->
                persistenceRepository.getQuestionPoolForPassage(passage.id)
                    .size
                    .takeIf { it > 0 }
                    ?.let { count -> passage.id to count }
            }.toMap()
        }
    }

    fun generate(requests: List<QuizGenerationRequest>) {
        if (generationJob?.isActive == true) return
        require(requests.isNotEmpty()) { "Select at least one passage." }
        generationJob = viewModelScope.launch {
            mutableUiState.value = QuizGenerationUiState(
                isGenerating = true,
                totalPassages = requests.size,
            )
            try {
                val passageQuizzes = mutableListOf<PassageQuiz>()
                requests.forEachIndexed { index, request ->
                    mutableUiState.update {
                        it.copy(
                            currentPassageNumber = index + 1,
                            currentQuestionNumber = 1,
                        )
                    }
                    val reusePlan = planQuizReuse(
                        typeCounts = request.typeCounts,
                        questionPool = persistenceRepository.getQuestionPoolForPassage(
                            request.passage.id,
                        ),
                    )
                    val generated = if (reusePlan.missingTypeCounts.isEmpty()) {
                        QuizGenerationResult(emptyList(), 0)
                    } else {
                        try {
                            generator.generate(
                                request.copy(typeCounts = reusePlan.missingTypeCounts),
                                existingQuestions = reusePlan.reusedQuestions,
                            )
                        } catch (error: QuizGenerationException) {
                            if (reusePlan.reusedQuestions.isEmpty()) throw error
                            QuizGenerationResult(
                                questions = emptyList(),
                                failedQuestionCount = reusePlan.missingTypeCounts.values.sum(),
                            )
                        }
                    }
                    val questions = composeQuizQuestions(
                        typeCounts = request.typeCounts,
                        reusedQuestions = reusePlan.reusedQuestions,
                        generatedQuestions = generated.questions,
                    )
                    val result = QuizGenerationResult(
                        questions = questions,
                        failedQuestionCount = QuizGenerator.QUESTION_COUNT - questions.size,
                    )
                    val savedQuizId = try {
                        persistenceRepository.saveGenerated(request, result)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        throw QuizGenerationException(
                            "The quiz was prepared but could not be saved. Try again.",
                            error,
                        )
                    }
                    passageQuizzes += PassageQuiz(
                        request = request,
                        questions = questions.withPassageScopedIds(request.passage.id),
                        failedQuestionCount = result.failedQuestionCount,
                        savedQuizId = savedQuizId,
                    )
                    mutableUiState.update { it.copy(passageQuizzes = passageQuizzes.toList()) }
                }
                val quizSetId = persistenceRepository.getOrCreateQuizSet(
                    passageQuizzes.map(PassageQuiz::savedQuizId),
                )
                mutableUiState.value = QuizGenerationUiState(
                    currentPassageNumber = requests.size,
                    totalPassages = requests.size,
                    currentQuestionNumber = QuizGenerator.QUESTION_COUNT,
                    quizSetId = quizSetId,
                    passageQuizzes = passageQuizzes,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(
                        isGenerating = false,
                        errorMessage = error.message ?: "The quiz could not be generated.",
                    )
                }
            } finally {
                modelRunner.release()
            }
        }
    }

    fun cancel() {
        generationJob?.cancel()
        generationJob = null
        mutableUiState.update { it.copy(isGenerating = false) }
    }

    fun clearQuiz() {
        cancel()
        mutableUiState.value = QuizGenerationUiState()
    }

    fun useSavedQuizSet(
        quizSetId: Long,
        passageQuizzes: List<PassageQuiz>,
    ) {
        cancel()
        mutableUiState.value = QuizGenerationUiState(
            currentPassageNumber = passageQuizzes.size,
            totalPassages = passageQuizzes.size,
            currentQuestionNumber = passageQuizzes.sumOf { it.questions.size },
            totalQuestions = passageQuizzes.sumOf { it.questions.size },
            quizSetId = quizSetId,
            passageQuizzes = passageQuizzes,
        )
    }

    override fun onCleared() {
        generationJob?.cancel()
        modelRunner.close()
        database.close()
    }
}
