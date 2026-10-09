package com.example.bukal.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bukal.data.local.BukalDatabase
import com.example.bukal.data.search.DocumentSearch
import com.example.bukal.data.search.GraniteEmbeddingRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QuizEvaluationUiState(
    val isChecking: Boolean = false,
    val result: QuizResultSummary? = null,
    val errorMessage: String? = null,
    val explanations: Map<String, String> = emptyMap(),
    val explainingQuestionId: String? = null,
    val explanationErrors: Map<String, String> = emptyMap(),
)

class QuizEvaluationViewModel(application: Application) : AndroidViewModel(application) {
    private val database = BukalDatabase.create(application)
    private val persistenceRepository = QuizPersistenceRepository(database)
    private val embeddingRunner = GraniteEmbeddingRunner(application)
    private val documentSearch = DocumentSearch(database, embeddingRunner)
    private val modelRunner = QuizModelRunner(application)
    private val evaluator = QuizEvaluator(
        search = { query, passageId, limit ->
            documentSearch.search(query, limit, passageId)
        },
        complete = modelRunner::complete,
    )
    private val mutableUiState = MutableStateFlow(QuizEvaluationUiState())
    private var evaluationJob: Job? = null
    private var explanationJob: Job? = null
    val uiState: StateFlow<QuizEvaluationUiState> = mutableUiState.asStateFlow()

    fun evaluate(request: QuizEvaluationRequest, savedQuizId: Long? = null) {
        if (evaluationJob?.isActive == true) return
        explanationJob?.cancel()
        evaluationJob = viewModelScope.launch {
            mutableUiState.value = QuizEvaluationUiState(isChecking = true)
            try {
                val result = evaluator.evaluate(request)
                try {
                    persistenceRepository.saveCompleted(request, result, savedQuizId)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    throw IllegalStateException(
                        "The answers were checked but the result could not be saved. Try again.",
                        error,
                    )
                }
                mutableUiState.value = QuizEvaluationUiState(result = result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.value = QuizEvaluationUiState(
                    errorMessage = error.message ?: "The quiz could not be checked.",
                )
            } finally {
                embeddingRunner.release()
                modelRunner.release()
            }
        }
    }

    fun explain(request: QuizExplanationRequest) {
        val questionId = request.result.question.id
        if (explanationJob?.isActive == true || questionId in mutableUiState.value.explanations) return

        explanationJob = viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                explainingQuestionId = questionId,
                explanationErrors = mutableUiState.value.explanationErrors - questionId,
            )
            try {
                val explanation = evaluator.explain(request)
                mutableUiState.value = mutableUiState.value.copy(
                    explanations = mutableUiState.value.explanations + (questionId to explanation),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    explanationErrors = mutableUiState.value.explanationErrors +
                        (questionId to (error.message ?: "The explanation could not be generated.")),
                )
            } finally {
                embeddingRunner.release()
                modelRunner.release()
                mutableUiState.value = mutableUiState.value.copy(explainingQuestionId = null)
            }
        }
    }

    fun clearResult() {
        evaluationJob?.cancel()
        explanationJob?.cancel()
        evaluationJob = null
        explanationJob = null
        mutableUiState.value = QuizEvaluationUiState()
    }

    override fun onCleared() {
        evaluationJob?.cancel()
        explanationJob?.cancel()
        embeddingRunner.close()
        modelRunner.close()
        database.close()
    }
}
