package com.example.bukal.ui.profile

import com.example.bukal.data.local.DailyActivity
import com.example.bukal.data.local.DailyCorrectAnswers
import com.example.bukal.data.local.AttemptStatuses
import com.example.bukal.data.local.QuizSetHistoryRow
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileActivityTest {
    @Test
    fun intensityUsesZeroThroughFourPlusLevels() {
        assertEquals(0, activityIntensityLevel(-1))
        assertEquals(0, activityIntensityLevel(0))
        assertEquals(1, activityIntensityLevel(1))
        assertEquals(2, activityIntensityLevel(2))
        assertEquals(3, activityIntensityLevel(3))
        assertEquals(4, activityIntensityLevel(4))
        assertEquals(4, activityIntensityLevel(12))
    }

    @Test
    fun streaksCrossYearBoundaryAndAllowYesterdayAsCurrent() {
        val activeDates = setOf(
            LocalDate.of(2025, 12, 30),
            LocalDate.of(2025, 12, 31),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 2),
            LocalDate.of(2026, 1, 5),
        )

        assertEquals(
            StreakSummary(current = 4, longest = 4),
            calculateStreaks(activeDates, today = LocalDate.of(2026, 1, 3)),
        )
        assertEquals(
            StreakSummary(current = 0, longest = 4),
            calculateStreaks(activeDates, today = LocalDate.of(2026, 1, 7)),
        )
    }

    @Test
    fun onlyDaysWithTenCorrectAnswersQualifyForThePetStreak() {
        val qualified = qualifiedStreakDates(
            listOf(
                DailyCorrectAnswers("2026-10-08", 9),
                DailyCorrectAnswers("2026-10-09", 10),
                DailyCorrectAnswers("2026-10-10", 14),
            ),
        )

        assertEquals(
            setOf(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 10)),
            qualified,
        )
        assertEquals(
            StreakSummary(current = 2, longest = 2),
            calculateStreaks(qualified, today = LocalDate.of(2026, 10, 10)),
        )
    }

    @Test
    fun leapYearHeatmapAlignsSundayAndKeepsDailyCount() {
        val counts = buildYearActivityCounts(
            year = 2012,
            dailyActivity = listOf(DailyActivity("2012-01-01", 2)),
        )

        assertEquals(54 * 7, counts.size)
        assertEquals(2, counts[6])
    }

    @Test
    fun aMultiPassageQuizSetCountsAsOneCompletedQuiz() {
        val history = listOf(
            QuizSetHistoryRow(
                quizSetId = 1,
                materialName = "Lesson",
                passageTitle = null,
                passageCount = 3,
                createdAtEpochMs = 1,
                status = AttemptStatuses.COMPLETED,
                completedAtEpochMs = 1,
                completedLocalDate = "2026-10-10",
                earnedPoints = 12.0,
                highestEarnedPoints = 12.0,
                possiblePoints = 15.0,
                questionCount = 15,
                quizTypes = "multiple_choice|identification|explanation",
            ),
        )

        val state = history.toProfileUiState(
            selectedYear = 2026,
            dailyActivity = listOf(DailyActivity("2026-10-10", 1)),
            dailyCorrectAnswers = listOf(DailyCorrectAnswers("2026-10-10", 12)),
            today = LocalDate.of(2026, 10, 10),
        )

        assertEquals(1, state.completedQuizCount)
        assertEquals(1, state.activityCounts.max())
        assertEquals(1, state.currentStreakDays)
        assertEquals(12, state.todayCorrectAnswers)
    }
}
