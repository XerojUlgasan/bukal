package com.example.bukal.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bukal.R
import com.example.bukal.ui.components.BukalBottomNavigation
import com.example.bukal.ui.components.MainDestination
import com.example.bukal.ui.theme.BukalAccent
import com.example.bukal.ui.theme.BukalBackground
import com.example.bukal.ui.theme.BukalTheme
import com.example.bukal.ui.theme.BukalMutedText
import com.example.bukal.ui.theme.BukalOutline
import com.example.bukal.ui.theme.BukalPrimary
import com.example.bukal.ui.theme.BukalPrimaryContainer
import com.example.bukal.ui.theme.BukalSurface
import com.example.bukal.ui.theme.BukalText

private const val HeatmapWeekCount = 53
private const val HeatmapDayCount = 7

enum class ProfileProgressIcon {
    FIRST_STEPS,
    STREAK,
    QUIZ_EXPLORER,
}

data class ProfileAchievement(
    val title: String,
    val description: String,
    val isUnlocked: Boolean,
    val icon: ProfileProgressIcon,
)

data class ProfileMilestone(
    val title: String,
    val description: String,
    val currentValue: Int,
    val targetValue: Int,
    val unit: String,
    val icon: ProfileProgressIcon,
)

data class ProfileUiState(
    val selectedYear: Int,
    val availableYears: List<Int>,
    val completedQuizCount: Int,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val activityCounts: List<Int>,
    val achievements: List<ProfileAchievement>,
    val milestones: List<ProfileMilestone>,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    companion object {
        fun empty(today: java.time.LocalDate = java.time.LocalDate.now()) = ProfileUiState(
            selectedYear = today.year,
            availableYears = listOf(today.year),
            completedQuizCount = 0,
            currentStreakDays = 0,
            longestStreakDays = 0,
            activityCounts = buildYearActivityCounts(today.year, emptyList()),
            achievements = emptyList(),
            milestones = emptyList(),
            isLoading = true,
        )

        val mock = ProfileUiState(
            selectedYear = 2026,
            availableYears = listOf(2026, 2025),
            completedQuizCount = 34,
            currentStreakDays = 3,
            longestStreakDays = 8,
            activityCounts = List(HeatmapWeekCount * HeatmapDayCount) { index ->
                when {
                    index % 31 == 0 -> 4
                    index % 17 == 0 -> 3
                    index % 11 == 0 -> 2
                    index % 7 == 0 -> 1
                    else -> 0
                }
            },
            achievements = listOf(
                ProfileAchievement(
                    title = "First Steps",
                    description = "First quiz completed",
                    isUnlocked = true,
                    icon = ProfileProgressIcon.FIRST_STEPS,
                ),
                ProfileAchievement(
                    title = "Week Builder",
                    description = "7-day streak reached",
                    isUnlocked = true,
                    icon = ProfileProgressIcon.STREAK,
                ),
                ProfileAchievement(
                    title = "Quiz Explorer",
                    description = "Try all 5 quiz types",
                    isUnlocked = false,
                    icon = ProfileProgressIcon.QUIZ_EXPLORER,
                ),
            ),
            milestones = listOf(
                ProfileMilestone(
                    title = "Quiz Collector",
                    description = "Complete 50 quizzes this year.",
                    currentValue = 34,
                    targetValue = 50,
                    unit = "quizzes",
                    icon = ProfileProgressIcon.QUIZ_EXPLORER,
                ),
                ProfileMilestone(
                    title = "Consistency Goal",
                    description = "Reach a 14-day streak.",
                    currentValue = 8,
                    targetValue = 14,
                    unit = "days",
                    icon = ProfileProgressIcon.STREAK,
                ),
            ),
        )
    }
}

internal fun activityIntensityLevel(completedQuizCount: Int): Int {
    return completedQuizCount.coerceIn(0, 4)
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onMenuClick: () -> Unit,
    onYearSelected: (Int) -> Unit,
    onDestinationSelected: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BukalBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            BukalBottomNavigation(
                selectedDestination = MainDestination.PROFILE,
                onDestinationSelected = onDestinationSelected,
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            ProfileHeader(onMenuClick = onMenuClick)
            Spacer(modifier = Modifier.height(18.dp))
            ProfileYearSummary(
                state = state,
                onYearSelected = onYearSelected,
            )
            state.errorMessage?.let { errorMessage ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            StreakCards(state = state)
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.profile_yearly_activity),
                color = BukalText,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(10.dp))
            ActivityHeatmapCard(
                activityCounts = state.activityCounts,
                selectedYear = state.selectedYear,
            )
            Spacer(modifier = Modifier.height(14.dp))
            AchievementsSection(achievements = state.achievements)
            Spacer(modifier = Modifier.height(22.dp))
            MilestonesSection(milestones = state.milestones)
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.profile_activity_explanation),
                color = BukalMutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProfileHeader(onMenuClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.profile_title),
                modifier = Modifier.weight(1f),
                color = BukalText,
                style = MaterialTheme.typography.displaySmall,
            )
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.profile_menu_description),
                    tint = BukalText,
                )
            }
        }
        Text(
            text = stringResource(R.string.profile_subtitle),
            color = BukalMutedText,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ProfileYearSummary(
    state: ProfileUiState,
    onYearSelected: (Int) -> Unit,
) {
    var yearMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            OutlinedButton(
                onClick = { yearMenuExpanded = true },
                modifier = Modifier.height(48.dp),
                enabled = !state.isLoading,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BukalPrimary),
            ) {
                Text(
                    text = state.selectedYear.toString(),
                    color = BukalText,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = BukalText,
                )
            }
            DropdownMenu(
                expanded = yearMenuExpanded,
                onDismissRequest = { yearMenuExpanded = false },
            ) {
                state.availableYears.forEach { year ->
                    DropdownMenuItem(
                        text = { Text(year.toString()) },
                        onClick = {
                            yearMenuExpanded = false
                            onYearSelected(year)
                        },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = state.completedQuizCount.toString(),
                color = BukalText,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = stringResource(R.string.profile_quizzes_completed),
                color = BukalMutedText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun StreakCards(state: ProfileUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StreakCard(
            days = state.currentStreakDays,
            label = stringResource(R.string.profile_current_streak),
            iconColor = BukalAccent,
            iconContainerColor = BukalAccent.copy(alpha = 0.12f),
            longest = false,
            modifier = Modifier.weight(1f),
        )
        StreakCard(
            days = state.longestStreakDays,
            label = stringResource(R.string.profile_longest_streak),
            iconColor = BukalPrimary,
            iconContainerColor = BukalPrimaryContainer,
            longest = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StreakCard(
    days: Int,
    label: String,
    iconColor: Color,
    iconContainerColor: Color,
    longest: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = BukalSurface),
        border = BorderStroke(1.dp, BukalOutline),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconContainerColor, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (longest) {
                        Icons.Outlined.CalendarMonth
                    } else {
                        Icons.Outlined.LocalFireDepartment
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = iconColor,
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.profile_days, days),
                    color = BukalText,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = label,
                    color = BukalMutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun ActivityHeatmapCard(
    activityCounts: List<Int>,
    selectedYear: Int,
) {
    val monthNames = stringArrayResource(R.array.profile_months)
    val weekCounts = activityCounts
        .chunked(HeatmapDayCount)
    val monthWeekCounts = monthWeekCounts(selectedYear, weekCounts.size)
    val heatmapScrollState = rememberScrollState()

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = BukalSurface),
        border = BorderStroke(1.dp, BukalOutline),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                WeekdayLabels()
                Column(modifier = Modifier.horizontalScroll(heatmapScrollState)) {
                    Row {
                        monthNames.zip(monthWeekCounts).forEach { (month, weekCount) ->
                            Text(
                                text = month,
                                modifier = Modifier.width((weekCount * 13).dp),
                                color = BukalMutedText,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        weekCounts.forEach { week ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                week.forEach { count ->
                                    ActivityCell(count = count)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.profile_swipe_hint),
                modifier = Modifier.fillMaxWidth(),
                color = BukalMutedText,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            ActivityLegend()
        }
    }
}

@Composable
private fun WeekdayLabels() {
    val labels = listOf("", "Mon", "", "Wed", "", "Fri", "")
    Column(
        modifier = Modifier
            .padding(top = 22.dp, end = 6.dp)
            .width(28.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.height(10.dp),
                color = BukalMutedText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ActivityCell(count: Int) {
    val description = stringResource(R.string.profile_day_activity_description, count)
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(activityColor(count), RoundedCornerShape(2.dp))
            .semantics { contentDescription = description },
    )
}

private fun activityColor(count: Int): Color {
    return when (activityIntensityLevel(count)) {
        0 -> BukalOutline.copy(alpha = 0.48f)
        1 -> BukalPrimary.copy(alpha = 0.24f)
        2 -> BukalPrimary.copy(alpha = 0.45f)
        3 -> BukalPrimary.copy(alpha = 0.70f)
        else -> BukalPrimary
    }
}

@Composable
private fun ActivityLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.profile_less),
            color = BukalMutedText,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(modifier = Modifier.width(8.dp))
        repeat(5) { level ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(14.dp)
                    .background(activityColor(level), RoundedCornerShape(3.dp)),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.profile_more),
            color = BukalMutedText,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun AchievementsSection(achievements: List<ProfileAchievement>) {
    Text(
        text = stringResource(R.string.profile_achievements),
        color = BukalText,
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        achievements.forEach { achievement ->
            AchievementCard(achievement = achievement)
        }
    }
}

@Composable
private fun AchievementCard(achievement: ProfileAchievement) {
    val status = if (achievement.isUnlocked) {
        stringResource(R.string.profile_unlocked)
    } else {
        stringResource(R.string.profile_locked)
    }
    val accentColor = if (achievement.isUnlocked) BukalPrimary else BukalMutedText
    val statusColor = if (achievement.isUnlocked) Color(0xFF176B4B) else BukalMutedText
    val statusBackground = if (achievement.isUnlocked) {
        Color(0xFFE4F3EC)
    } else {
        BukalOutline.copy(alpha = 0.55f)
    }

    OutlinedCard(
        modifier = Modifier
            .width(148.dp)
            .height(184.dp)
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = BukalSurface),
        border = BorderStroke(1.dp, BukalOutline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(accentColor.copy(alpha = 0.10f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = achievement.icon.imageVector(),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = accentColor,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = achievement.title,
                color = BukalText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = achievement.description,
                color = BukalMutedText,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.background(statusBackground, RoundedCornerShape(50)),
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    color = statusColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun MilestonesSection(milestones: List<ProfileMilestone>) {
    Text(
        text = stringResource(R.string.profile_next_milestones),
        color = BukalText,
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(modifier = Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        milestones.forEach { milestone ->
            MilestoneCard(milestone = milestone)
        }
    }
}

@Composable
private fun MilestoneCard(milestone: ProfileMilestone) {
    val progress = if (milestone.targetValue <= 0) {
        0f
    } else {
        (milestone.currentValue.toFloat() / milestone.targetValue).coerceIn(0f, 1f)
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = BukalSurface),
        border = BorderStroke(1.dp, BukalOutline),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(BukalPrimaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = milestone.icon.imageVector(),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = BukalPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = milestone.title,
                        color = BukalText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            R.string.profile_milestone_progress,
                            milestone.currentValue,
                            milestone.targetValue,
                            milestone.unit,
                        ),
                        color = BukalPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = milestone.description,
                    color = BukalMutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(9.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = BukalPrimary,
                    trackColor = BukalOutline.copy(alpha = 0.65f),
                )
            }
        }
    }
}

private fun ProfileProgressIcon.imageVector(): ImageVector = when (this) {
    ProfileProgressIcon.FIRST_STEPS -> Icons.Outlined.CheckCircle
    ProfileProgressIcon.STREAK -> Icons.Outlined.CalendarMonth
    ProfileProgressIcon.QUIZ_EXPLORER -> Icons.Outlined.MenuBook
}

private fun monthWeekCounts(year: Int, totalWeeks: Int): List<Int> {
    val firstDay = java.time.LocalDate.of(year, 1, 1)
    val leadingEmptyDays = firstDay.dayOfWeek.value - 1
    val monthStartWeeks = (1..12).map { month ->
        val dayOfYear = java.time.LocalDate.of(year, month, 1).dayOfYear
        (leadingEmptyDays + dayOfYear - 1) / HeatmapDayCount
    }
    return monthStartWeeks.mapIndexed { index, startWeek ->
        val endWeek = monthStartWeeks.getOrElse(index + 1) { totalWeeks }
        endWeek - startWeek
    }
}

@Preview(
    name = "Profile",
    showBackground = true,
    backgroundColor = 0xFFF8F7F2,
    widthDp = 360,
    heightDp = 800,
)
@Composable
private fun ProfileScreenPreview() {
    BukalTheme {
        ProfileScreen(
            state = ProfileUiState.mock,
            onMenuClick = {},
            onYearSelected = {},
            onDestinationSelected = {},
        )
    }
}
