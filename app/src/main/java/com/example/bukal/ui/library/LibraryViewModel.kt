package com.example.bukal.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bukal.data.importing.ImportedMaterialResult
import com.example.bukal.data.importing.MaterialImportRepository
import com.example.bukal.data.local.BukalDatabase
import com.example.bukal.data.search.GraniteEmbeddingIndexer
import com.example.bukal.data.search.DocumentSearch
import com.example.bukal.data.search.DocumentSearchResult
import com.example.bukal.data.search.GraniteEmbeddingRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ImportStatus {
    IDLE,
    IMPORTING,
    ERROR,
}

enum class EmbeddingIndexStatus {
    IDLE,
    INDEXING,
    READY,
    ERROR,
}

enum class VectorSearchStatus {
    IDLE,
    SEARCHING,
    READY,
    ERROR,
}

data class LibraryUiState(
    val materials: List<ImportedMaterialResult> = emptyList(),
    val importStatus: ImportStatus = ImportStatus.IDLE,
    val importError: String? = null,
    val newlyImportedMaterialId: Long? = null,
    val embeddingIndexStatus: EmbeddingIndexStatus = EmbeddingIndexStatus.IDLE,
    val embeddingIndexed: Int = 0,
    val embeddingFailed: Int = 0,
    val embeddingPending: Int = 0,
    val embeddingError: String? = null,
    val vectorSearchStatus: VectorSearchStatus = VectorSearchStatus.IDLE,
    val vectorSearchResults: List<DocumentSearchResult> = emptyList(),
    val vectorSearchError: String? = null,
) {
    val recentMaterial: ImportedMaterialResult?
        get() = materials.firstOrNull()
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = BukalDatabase.create(application)
    private val repository = MaterialImportRepository(application, database)
    private val embeddingRunner = GraniteEmbeddingRunner(application)
    private val embeddingIndexer = GraniteEmbeddingIndexer(database, embeddingRunner)
    private val documentSearch = DocumentSearch(database, embeddingRunner)
    private val mutableUiState = MutableStateFlow(LibraryUiState())
    private var indexingJob: Job? = null
    val uiState: StateFlow<LibraryUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            val materials = repository.getAll()
            mutableUiState.update { state ->
                if (state.materials.isEmpty() && state.importStatus != ImportStatus.IMPORTING) {
                    state.copy(materials = materials)
                } else {
                    state
                }
            }
        }
    }

    fun import(uri: Uri) {
        if (mutableUiState.value.importStatus == ImportStatus.IMPORTING) return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(importStatus = ImportStatus.IMPORTING, importError = null)
            }
            runCatching { repository.import(uri) }
                .onSuccess { result ->
                    val materials = repository.getAll()
                    mutableUiState.update {
                        it.copy(
                            materials = materials,
                            importStatus = ImportStatus.IDLE,
                            importError = null,
                            newlyImportedMaterialId = result.material.id,
                        )
                    }
                    indexPendingChunks()
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    mutableUiState.update {
                        it.copy(
                            importStatus = ImportStatus.ERROR,
                            importError = error.message ?: "The file could not be imported.",
                        )
                    }
                }
        }
    }

    fun indexPendingChunks() {
        if (indexingJob?.isActive == true) return
        indexingJob = viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    embeddingIndexStatus = EmbeddingIndexStatus.INDEXING,
                    embeddingIndexed = 0,
                    embeddingFailed = 0,
                    embeddingError = null,
                )
            }
            try {
                val result = embeddingIndexer.indexPending()
                val completed = result.failed == 0 && result.pending == 0
                mutableUiState.update {
                    it.copy(
                        embeddingIndexStatus = if (completed) {
                            EmbeddingIndexStatus.READY
                        } else {
                            EmbeddingIndexStatus.ERROR
                        },
                        embeddingIndexed = result.indexed,
                        embeddingFailed = result.failed,
                        embeddingPending = result.pending,
                        embeddingError = if (completed) {
                            null
                        } else {
                            "Some lesson sections could not be prepared for search."
                        },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(
                        embeddingIndexStatus = EmbeddingIndexStatus.ERROR,
                        embeddingError = error.message
                            ?: "Lesson sections could not be prepared for search.",
                    )
                }
            } finally {
                embeddingRunner.release()
            }
        }
    }

    fun consumeNewImport() {
        mutableUiState.update { it.copy(newlyImportedMaterialId = null) }
    }

    fun searchByMeaning(query: String) {
        if (mutableUiState.value.vectorSearchStatus == VectorSearchStatus.SEARCHING) return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    vectorSearchStatus = VectorSearchStatus.SEARCHING,
                    vectorSearchResults = emptyList(),
                    vectorSearchError = null,
                )
            }
            try {
                val results = documentSearch.search(query)
                mutableUiState.update {
                    it.copy(
                        vectorSearchStatus = VectorSearchStatus.READY,
                        vectorSearchResults = results,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(
                        vectorSearchStatus = VectorSearchStatus.ERROR,
                        vectorSearchError = error.message ?: "Vector search failed.",
                    )
                }
            } finally {
                embeddingRunner.release()
            }
        }
    }

    override fun onCleared() {
        embeddingRunner.close()
        database.close()
    }
}
