package com.example.bukal.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bukal.data.local.BukalDatabase
import com.example.bukal.data.model.ModelDownloadSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FileSummaryUiState(
    val materialName: String = "",
    val markdown: String? = null,
    val isLoading: Boolean = true,
    val isGenerating: Boolean = false,
    val currentPassage: Int = 0,
    val totalPassages: Int = 0,
    val errorMessage: String? = null,
)

class FileSummaryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = BukalDatabase.create(application)
    private val modelRunner = QuizModelRunner(application)
    private val mutableUiState = MutableStateFlow(FileSummaryUiState())
    private var activeRequest: Pair<Long, ModelDownloadSpec>? = null
    private var summaryJob: Job? = null
    val uiState: StateFlow<FileSummaryUiState> = mutableUiState.asStateFlow()

    fun open(materialId: Long, model: ModelDownloadSpec) {
        if (summaryJob?.isActive == true) return
        activeRequest = materialId to model
        summaryJob = viewModelScope.launch {
            try {
                val material = requireNotNull(database.materialDao().getById(materialId)) {
                    "This lesson is no longer available."
                }
                material.summaryMarkdown?.let { saved ->
                    mutableUiState.value = FileSummaryUiState(
                        materialName = material.displayName,
                        markdown = saved,
                        isLoading = false,
                    )
                    return@launch
                }

                val passages = database.materialDao().getPassages(materialId)
                mutableUiState.value = FileSummaryUiState(
                    materialName = material.displayName,
                    isLoading = false,
                    isGenerating = true,
                    totalPassages = passages.size,
                )
                val summarizer = FileSummarizer(
                    onPassageStarted = { current, total ->
                        mutableUiState.update {
                            it.copy(currentPassage = current, totalPassages = total)
                        }
                    },
                    complete = modelRunner::complete,
                )
                val markdown = summarizer.summarize(FileSummaryRequest(model, passages))
                database.materialDao().saveSummaryOnce(
                    materialId = materialId,
                    markdown = markdown,
                    modelId = model.id,
                    summarizedAtEpochMs = System.currentTimeMillis(),
                )
                val saved = requireNotNull(database.materialDao().getById(materialId)?.summaryMarkdown) {
                    "The summary was created but could not be saved."
                }
                mutableUiState.value = FileSummaryUiState(
                    materialName = material.displayName,
                    markdown = saved,
                    isLoading = false,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        isGenerating = false,
                        errorMessage = error.message ?: "The file could not be summarized.",
                    )
                }
            } finally {
                modelRunner.release()
            }
        }
    }

    fun retry() {
        val (materialId, model) = activeRequest ?: return
        open(materialId, model)
    }

    fun cancel() {
        summaryJob?.cancel()
        summaryJob = null
    }

    override fun onCleared() {
        summaryJob?.cancel()
        modelRunner.close()
        database.close()
        super.onCleared()
    }
}
