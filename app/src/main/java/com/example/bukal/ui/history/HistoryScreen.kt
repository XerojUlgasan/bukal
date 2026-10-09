package com.example.bukal.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.time.LocalDate
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

data class HistoryAttempt(
    val id: Long,
    val materialName: String,
    val passageTitle: String,
    val completedAtLabel: String,
    val typeSummary: String,
    val scoreLabel: String,
    val highestScoreLabel: String? = null,
    val date: LocalDate,
)

data class HistoryUiState(
    val thisWeek: List<HistoryAttempt> = emptyList(),
    val earlier: List<HistoryAttempt> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    companion object {
        val mock = HistoryUiState(
            thisWeek = listOf(
                HistoryAttempt(
                    id = 1,
                    materialName = "Philippine History",
                    passageTitle = "The Malolos Constitution",
                    completedAtLabel = "Today • 10:42 AM",
                    typeSummary = "5 question types",
                    scoreLabel = "4 / 5",
                    highestScoreLabel = "Highest 5 / 5",
                    date = LocalDate.now(),
                ),
                HistoryAttempt(
                    id = 2,
                    materialName = "Science Notes",
                    passageTitle = "Photosynthesis",
                    completedAtLabel = "Yesterday • 4:18 PM",
                    typeSummary = "Multiple choice",
                    scoreLabel = "5 / 5",
                    highestScoreLabel = "Highest 5 / 5",
                    date = LocalDate.now().minusDays(1),
                ),
            ),
            earlier = listOf(
                HistoryAttempt(
                    id = 3,
                    materialName = "Philippine History",
                    passageTitle = "The First Philippine Republic",
                    completedAtLabel = "Oct 6 • 8:05 PM",
                    typeSummary = "Multiple choice • Identification",
                    scoreLabel = "3.5 / 5",
                    highestScoreLabel = "Highest 4 / 5",
                    date = LocalDate.now().minusDays(8),
                ),
            ),
        )
    }
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onMenuClick: () -> Unit,
    onAttemptClick: (Long) -> Unit,
    onDestinationSelected: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BukalBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            BukalBottomNavigation(
                selectedDestination = MainDestination.HISTORY,
                onDestinationSelected = onDestinationSelected,
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                HistoryHeader(onMenuClick = onMenuClick)
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.history_this_week),
                    color = BukalText,
                    style = MaterialTheme.typography.headlineSmall,
                )
                state.errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (!state.isLoading && state.thisWeek.isEmpty() && state.earlier.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.history_empty),
                        color = BukalMutedText,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            items(
                items = state.thisWeek,
                key = HistoryAttempt::id,
            ) { attempt ->
                HistoryAttemptCard(
                    attempt = attempt,
                    onClick = { onAttemptClick(attempt.id) },
                )
            }
            if (state.earlier.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.history_earlier),
                        color = BukalText,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                items(
                    items = state.earlier,
                    key = HistoryAttempt::id,
                ) { attempt ->
                    HistoryAttemptCard(
                        attempt = attempt,
                        onClick = { onAttemptClick(attempt.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryHeader(onMenuClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.history_title),
                modifier = Modifier.weight(1f),
                color = BukalText,
                style = MaterialTheme.typography.displaySmall,
            )
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.history_menu_description),
                    tint = BukalText,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.history_subtitle),
                modifier = Modifier.weight(1f),
                color = BukalMutedText,
                style = MaterialTheme.typography.bodyLarge,
            )
            Icon(
                imageVector = Icons.Outlined.EventAvailable,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = BukalAccent,
            )
        }
    }
}

@Composable
private fun HistoryAttemptCard(
    attempt: HistoryAttempt,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
                    .background(BukalPrimaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = BukalMutedText,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attempt.materialName,
                    color = BukalText,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = attempt.passageTitle,
                    color = BukalMutedText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = attempt.completedAtLabel,
                    color = BukalMutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(7.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.FormatListBulleted,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = BukalMutedText,
                    )
                    Text(
                        text = attempt.typeSummary,
                        color = BukalMutedText,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = BukalPrimaryContainer,
                    shape = CircleShape,
                ) {
                    Text(
                        text = attempt.scoreLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        color = BukalPrimary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                attempt.highestScoreLabel?.let { highestScore ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = highestScore,
                        color = BukalMutedText,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = BukalMutedText,
                )
            }
        }
    }
}

@Preview(
    name = "History",
    showBackground = true,
    backgroundColor = 0xFFF8F7F2,
    widthDp = 360,
    heightDp = 800,
)
@Composable
private fun HistoryScreenPreview() {
    BukalTheme {
        HistoryScreen(
            state = HistoryUiState.mock,
            onMenuClick = {},
            onAttemptClick = {},
            onDestinationSelected = {},
        )
    }
}
