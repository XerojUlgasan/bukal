package com.example.bukal.ui.flashcards

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bukal.ai.QuestionAnswer
import com.example.bukal.ai.QuizPersistenceRepository
import com.example.bukal.ai.QuizQuestion
import com.example.bukal.ai.toQuizQuestions
import com.example.bukal.data.local.BukalDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Flashcard(
    val prompt: String,
    val answer: String,
    val quizTypeLabel: String,
    val sourceId: String,
)

data class FlashcardsUiState(
    val materialName: String = "",
    val cards: List<Flashcard> = emptyList(),
    val currentIndex: Int = 0,
    val isRevealed: Boolean = false,
) {
    val isEmpty: Boolean get() = cards.isEmpty()
}

class FlashcardsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = BukalDatabase.create(application)
    private val persistenceRepository = QuizPersistenceRepository(database)
    private val mutableUiState = MutableStateFlow(FlashcardsUiState())
    val uiState: StateFlow<FlashcardsUiState> = mutableUiState.asStateFlow()

    fun load(materialId: Long, materialName: String) {
        viewModelScope.launch {
            val passages = database.materialDao().getPassages(materialId)
            val cards = passages.flatMap { passage ->
                val savedQuiz = persistenceRepository.getQuizForPassage(passage.id)
                    ?: return@flatMap emptyList()
                val title = passage.title ?: passage.sourceId
                savedQuiz.toQuizQuestions().map { it.toFlashcard(title) }
            }
            mutableUiState.value = FlashcardsUiState(
                materialName = materialName,
                cards = cards,
            )
        }
    }

    fun toggleRevealed() {
        mutableUiState.update { it.copy(isRevealed = !it.isRevealed) }
    }

    fun previous() {
        mutableUiState.update {
            it.copy(currentIndex = (it.currentIndex - 1).coerceAtLeast(0), isRevealed = false)
        }
    }

    fun next() {
        mutableUiState.update {
            it.copy(
                currentIndex = (it.currentIndex + 1).coerceAtMost(it.cards.lastIndex),
                isRevealed = false,
            )
        }
    }

    override fun onCleared() {
        database.close()
        super.onCleared()
    }
}

internal fun QuizQuestion.toFlashcard(passageTitle: String): Flashcard = Flashcard(
    prompt = prompt,
    answer = answer.toFlashcardAnswer(),
    quizTypeLabel = type.toFlashcardLabel(),
    sourceId = passageTitle,
)

private fun QuestionAnswer.toFlashcardAnswer(): String = when (this) {
    is QuestionAnswer.MultipleChoice -> options[answerIndex]
    is QuestionAnswer.OpenResponse -> referenceAnswer
    is QuestionAnswer.Matching -> pairs.joinToString("\n") { "${it.leftText} = ${it.rightText}" }
}

private fun com.example.bukal.ai.QuestionType.toFlashcardLabel(): String = when (this) {
    com.example.bukal.ai.QuestionType.MULTIPLE_CHOICE -> "Multiple choice"
    com.example.bukal.ai.QuestionType.FILL_IN_THE_BLANK -> "Fill in the blank"
    com.example.bukal.ai.QuestionType.IDENTIFICATION -> "Identification"
    com.example.bukal.ai.QuestionType.TRUE_FALSE -> "True or false"
    com.example.bukal.ai.QuestionType.MATCHING -> "Matching"
    com.example.bukal.ai.QuestionType.EXPLANATION -> "Explanation"
}
