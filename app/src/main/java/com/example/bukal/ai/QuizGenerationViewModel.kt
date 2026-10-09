package com.example.bukal.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bukal.data.local.BukalDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuizGenerationUiState(
    val isGenerating: Boolean = false,
    val currentQuestionNumber: Int = 1,
    val totalQuestions: Int = QuizGenerator.QUESTION_COUNT,
    val questions: List<QuizQuestion> = emptyList(),
    val failedQuestionCount: Int = 0,
    val savedQuizId: Long? = null,
    val errorMessage: String? = null,
)

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

    fun generate(request: QuizGenerationRequest) {
        if (generationJob?.isActive == true) return
        generationJob = viewModelScope.launch {
            mutableUiState.value = QuizGenerationUiState(isGenerating = true)
            try {
                val existingQuiz = persistenceRepository.getQuizForPassage(request.passage.id)
                if (existingQuiz != null) {
                    val questions = existingQuiz.toQuizQuestions()
                    mutableUiState.value = QuizGenerationUiState(
                        currentQuestionNumber = questions.size,
                        totalQuestions = questions.size,
                        questions = questions,
                        savedQuizId = existingQuiz.attempt.id,
                    )
                    return@launch
                }
                val result = generator.generate(request)
                val savedQuizId = try {
                    persistenceRepository.saveGenerated(request, result)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    throw QuizGenerationException(
                        "The quiz was generated but could not be saved. Try again.",
                        error,
                    )
                }
                mutableUiState.value = QuizGenerationUiState(
                    currentQuestionNumber = QuizGenerator.QUESTION_COUNT,
                    questions = result.questions,
                    failedQuestionCount = result.failedQuestionCount,
                    savedQuizId = savedQuizId,
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

    fun useSavedQuiz(questions: List<QuizQuestion>, savedDraftId: Long?) {
        cancel()
        mutableUiState.value = QuizGenerationUiState(
            currentQuestionNumber = questions.size,
            totalQuestions = questions.size,
            questions = questions,
            savedQuizId = savedDraftId,
        )
    }

    override fun onCleared() {
        generationJob?.cancel()
        modelRunner.close()
        database.close()
    }
}
