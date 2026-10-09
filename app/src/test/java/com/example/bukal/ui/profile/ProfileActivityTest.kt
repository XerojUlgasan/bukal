package com.example.bukal.ui.profile

import com.example.bukal.data.local.DailyActivity
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
    fun leapYearHeatmapAlignsSundayAndKeepsDailyCount() {
        val counts = buildYearActivityCounts(
            year = 2012,
            dailyActivity = listOf(DailyActivity("2012-01-01", 2)),
        )

        assertEquals(54 * 7, counts.size)
        assertEquals(2, counts[6])
    }
}
