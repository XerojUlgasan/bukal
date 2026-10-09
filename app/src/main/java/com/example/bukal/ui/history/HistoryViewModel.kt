package com.example.bukal.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bukal.data.local.AttemptHistoryRow
import com.example.bukal.data.local.AttemptStatuses
import com.example.bukal.data.local.BukalDatabase
import com.example.bukal.data.local.SavedQuizRecord
import com.example.bukal.ai.QuizPersistenceRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = BukalDatabase.create(application)
    private val repository = QuizPersistenceRepository(database)
    private val mutableUiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = mutableUiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(isLoading = true, errorMessage = null)
            try {
                mutableUiState.value = repository.getHistory().toHistoryUiState()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.value = HistoryUiState(
                    errorMessage = error.message ?: "Saved quizzes could not be loaded.",
                )
            }
        }
    }

    suspend fun getSavedQuiz(attemptId: Long): SavedQuizRecord = repository.getSavedQuiz(attemptId)

    fun showError(message: String) {
        mutableUiState.value = mutableUiState.value.copy(errorMessage = message)
    }

    override fun onCleared() {
        database.close()
    }
}

internal fun List<AttemptHistoryRow>.toHistoryUiState(
    now: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): HistoryUiState {
    val weekStart = now.minusDays((now.dayOfWeek.value - 1).toLong())
    val attempts = map { row -> row.toHistoryAttempt(now, zoneId) }
    return HistoryUiState(
        thisWeek = attempts.filter { it.date >= weekStart },
        earlier = attempts.filter { it.date < weekStart },
    )
}

private fun AttemptHistoryRow.toHistoryAttempt(now: LocalDate, zoneId: ZoneId): HistoryAttempt {
    val displayEpoch = if (status == AttemptStatuses.COMPLETED) {
        completedAtEpochMs
    } else {
        generatedAtEpochMs
    }
    val dateTime = Instant.ofEpochMilli(displayEpoch).atZone(zoneId)
    val dateLabel = when (dateTime.toLocalDate()) {
        now -> "Today"
        now.minusDays(1) -> "Yesterday"
        else -> dateTime.format(DateTimeFormatter.ofPattern("MMM d"))
    }
    val action = if (status == AttemptStatuses.COMPLETED) "Completed" else "Saved"
    val types = quizTypes.split('|').filter(String::isNotBlank).distinct()
    return HistoryAttempt(
        id = attemptId,
        materialName = materialName,
        passageTitle = passageTitle ?: sourceId,
        completedAtLabel = "$action $dateLabel • ${dateTime.format(DateTimeFormatter.ofPattern("h:mm a"))}",
        typeSummary = types.joinToString(" • ") { it.toDisplayName() },
        scoreLabel = if (status == AttemptStatuses.COMPLETED) {
            "Previous ${earnedPoints.formatPoints()} / ${possiblePoints.formatPoints()}"
        } else {
            "Ready"
        },
        highestScoreLabel = if (status == AttemptStatuses.COMPLETED) {
            "Highest ${highestEarnedPoints.formatPoints()} / ${possiblePoints.formatPoints()}"
        } else {
            null
        },
        date = dateTime.toLocalDate(),
    )
}

private fun String.toDisplayName(): String = when (this) {
    "multiple_choice" -> "Multiple choice"
    "fill_in_the_blank" -> "Fill in the blank"
    "identification" -> "Identification"
    "matching" -> "Matching"
    "explanation" -> "Explanation"
    else -> replace('_', ' ')
}

private fun Double.formatPoints(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()
