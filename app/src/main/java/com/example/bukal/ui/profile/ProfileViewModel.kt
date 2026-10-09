package com.example.bukal.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bukal.ai.QuizPersistenceRepository
import com.example.bukal.data.local.AttemptHistoryRow
import com.example.bukal.data.local.AttemptStatuses
import com.example.bukal.data.local.BukalDatabase
import com.example.bukal.data.local.DailyActivity
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val database = BukalDatabase.create(application)
    private val repository = QuizPersistenceRepository(database)
    private val mutableUiState = MutableStateFlow(ProfileUiState.empty())
    private var loadJob: Job? = null

    val uiState: StateFlow<ProfileUiState> = mutableUiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        load(mutableUiState.value.selectedYear)
    }

    fun selectYear(year: Int) {
        if (year != mutableUiState.value.selectedYear) load(year)
    }

    private fun load(year: Int) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                selectedYear = year,
                isLoading = true,
                errorMessage = null,
            )
            try {
                val history = repository.getHistory()
                val dailyActivity = database.attemptDao().getDailyActivity(year.toString())
                mutableUiState.value = history.toProfileUiState(
                    selectedYear = year,
                    dailyActivity = dailyActivity,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Profile activity could not be loaded.",
                )
            }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        database.close()
    }
}

internal data class StreakSummary(
    val current: Int,
    val longest: Int,
)

internal fun List<AttemptHistoryRow>.toProfileUiState(
    selectedYear: Int,
    dailyActivity: List<DailyActivity>,
    today: LocalDate = LocalDate.now(),
): ProfileUiState {
    val completed = filter { it.status == AttemptStatuses.COMPLETED }
    val activeDates = completed.mapTo(mutableSetOf()) { LocalDate.parse(it.completedLocalDate) }
    val streaks = calculateStreaks(activeDates, today)
    val completedTypes = completed
        .flatMap { it.quizTypes.split('|') }
        .filter(String::isNotBlank)
        .toSet()
    val completedQuizCount = dailyActivity.sumOf(DailyActivity::completedCount)
    val availableYears = (activeDates.map { it.year } + today.year + selectedYear)
        .distinct()
        .sortedDescending()

    return ProfileUiState(
        selectedYear = selectedYear,
        availableYears = availableYears,
        completedQuizCount = completedQuizCount,
        currentStreakDays = streaks.current,
        longestStreakDays = streaks.longest,
        activityCounts = buildYearActivityCounts(selectedYear, dailyActivity),
        achievements = buildAchievements(
            completedQuizCount = completed.size,
            longestStreakDays = streaks.longest,
            completedTypeCount = completedTypes.size,
        ),
        milestones = buildMilestones(
            yearlyQuizCount = completedQuizCount,
            longestStreakDays = streaks.longest,
        ),
    )
}

internal fun calculateStreaks(
    activeDates: Set<LocalDate>,
    today: LocalDate = LocalDate.now(),
): StreakSummary {
    if (activeDates.isEmpty()) return StreakSummary(current = 0, longest = 0)

    var longest = 0
    var run = 0
    var previous: LocalDate? = null
    activeDates.sorted().forEach { date ->
        run = if (previous?.plusDays(1) == date) run + 1 else 1
        longest = maxOf(longest, run)
        previous = date
    }

    var cursor = when {
        today in activeDates -> today
        today.minusDays(1) in activeDates -> today.minusDays(1)
        else -> return StreakSummary(current = 0, longest = longest)
    }
    var current = 0
    while (cursor in activeDates) {
        current += 1
        cursor = cursor.minusDays(1)
    }
    return StreakSummary(current = current, longest = longest)
}

internal fun buildYearActivityCounts(
    year: Int,
    dailyActivity: List<DailyActivity>,
): List<Int> {
    val firstDay = LocalDate.of(year, 1, 1)
    val leadingEmptyDays = firstDay.dayOfWeek.value - 1
    val countsByDate = dailyActivity.associate { LocalDate.parse(it.completedLocalDate) to it.completedCount }
    val requiredCells = leadingEmptyDays + firstDay.lengthOfYear()
    val totalCells = ((requiredCells + 6) / 7) * 7

    return List(totalCells) { index ->
        val dayOffset = index - leadingEmptyDays
        if (dayOffset in 0 until firstDay.lengthOfYear()) {
            countsByDate[firstDay.plusDays(dayOffset.toLong())] ?: 0
        } else {
            0
        }
    }
}

private fun buildAchievements(
    completedQuizCount: Int,
    longestStreakDays: Int,
    completedTypeCount: Int,
): List<ProfileAchievement> = listOf(
    ProfileAchievement(
        title = "First Steps",
        description = "First quiz completed",
        isUnlocked = completedQuizCount >= 1,
        icon = ProfileProgressIcon.FIRST_STEPS,
    ),
    ProfileAchievement(
        title = "Week Builder",
        description = "7-day streak reached",
        isUnlocked = longestStreakDays >= 7,
        icon = ProfileProgressIcon.STREAK,
    ),
    ProfileAchievement(
        title = "Quiz Explorer",
        description = "Try all 5 quiz types",
        isUnlocked = completedTypeCount >= 5,
        icon = ProfileProgressIcon.QUIZ_EXPLORER,
    ),
)

private fun buildMilestones(
    yearlyQuizCount: Int,
    longestStreakDays: Int,
): List<ProfileMilestone> = listOf(
    ProfileMilestone(
        title = "Quiz Collector",
        description = "Complete 50 quizzes this year.",
        currentValue = yearlyQuizCount,
        targetValue = 50,
        unit = "quizzes",
        icon = ProfileProgressIcon.QUIZ_EXPLORER,
    ),
    ProfileMilestone(
        title = "Consistency Goal",
        description = "Reach a 14-day streak.",
        currentValue = longestStreakDays,
        targetValue = 14,
        unit = "days",
        icon = ProfileProgressIcon.STREAK,
    ),
)
