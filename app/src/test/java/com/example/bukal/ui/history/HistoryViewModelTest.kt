package com.example.bukal.ui.history

import com.example.bukal.data.local.AttemptStatuses
import com.example.bukal.data.local.QuizSetHistoryRow
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryViewModelTest {
    @Test
    fun savedAndCompletedQuizzesAreBothShownWithRetakeLabels() {
        val zone = ZoneId.of("Asia/Manila")
        val rows = listOf(
            historyRow(1, AttemptStatuses.SAVED, "2026-10-09T10:00:00+08:00[Asia/Manila]", 0.0),
            historyRow(2, AttemptStatuses.COMPLETED, "2026-10-08T10:00:00+08:00[Asia/Manila]", 4.0),
        )

        val state = rows.toHistoryUiState(LocalDate.of(2026, 10, 9), zone)

        assertEquals(2, state.thisWeek.size)
        assertEquals("Ready", state.thisWeek[0].scoreLabel)
        assertEquals("Saved Today • 10:00 AM", state.thisWeek[0].completedAtLabel)
        assertEquals("Previous 4 / 5", state.thisWeek[1].scoreLabel)
        assertEquals("Highest 4 / 5", state.thisWeek[1].highestScoreLabel)
        assertEquals("5 questions • Multiple choice • Identification", state.thisWeek[0].typeSummary)
    }

    @Test
    fun multiplePassagesRenderAsOneHistoryObject() {
        val row = historyRow(
            id = 1,
            status = AttemptStatuses.COMPLETED,
            dateTime = "2026-10-09T10:00:00+08:00[Asia/Manila]",
            earned = 12.0,
        ).copy(passageTitle = null, passageCount = 3, possiblePoints = 15.0, questionCount = 15)

        val item = listOf(row).toHistoryUiState(
            now = LocalDate.of(2026, 10, 9),
            zoneId = ZoneId.of("Asia/Manila"),
        ).thisWeek.single()

        assertEquals("3 passages", item.passageTitle)
        assertEquals("Previous 12 / 15", item.scoreLabel)
        assertEquals("15 questions • Multiple choice • Identification", item.typeSummary)
    }

    private fun historyRow(
        id: Long,
        status: String,
        dateTime: String,
        earned: Double,
    ): QuizSetHistoryRow {
        val epoch = java.time.ZonedDateTime.parse(dateTime).toInstant().toEpochMilli()
        return QuizSetHistoryRow(
            quizSetId = id,
            materialName = "Lesson",
            passageTitle = "Passage",
            passageCount = 1,
            createdAtEpochMs = epoch,
            status = status,
            completedAtEpochMs = epoch,
            completedLocalDate = "2026-10-09",
            earnedPoints = earned,
            highestEarnedPoints = earned,
            possiblePoints = 5.0,
            questionCount = 5,
            quizTypes = "multiple_choice|identification|multiple_choice",
        )
    }
}
