package com.example.bukal.ui.results

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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bukal.R
import com.example.bukal.ai.QuestionType
import com.example.bukal.ui.theme.BukalAccent
import com.example.bukal.ui.theme.BukalBackground
import com.example.bukal.ui.theme.BukalError
import com.example.bukal.ui.theme.BukalErrorContainer
import com.example.bukal.ui.theme.BukalTheme
import com.example.bukal.ui.theme.BukalMutedText
import com.example.bukal.ui.theme.BukalOutline
import com.example.bukal.ui.theme.BukalPrimary
import com.example.bukal.ui.theme.BukalPrimaryContainer
import com.example.bukal.ui.theme.BukalSuccess
import com.example.bukal.ui.theme.BukalSuccessContainer
import com.example.bukal.ui.theme.BukalSurface
import com.example.bukal.ui.theme.BukalText

enum class ResultStatus {
    CORRECT,
    INCORRECT,
    UNANSWERED,
}

data class ResultItem(
    val id: String,
    val questionType: QuestionType,
    val question: String,
    val status: ResultStatus,
    val isAiEvaluated: Boolean = false,
    val learnerAnswer: String? = null,
    val expectedAnswer: String? = null,
    val sourceId: String? = null,
    val passageTitle: String? = null,
    val passageContent: String? = null,
    val expanded: Boolean = false,
)

data class ResultsUiState(
    val earnedPoints: Double,
    val possiblePoints: Double,
    val message: String,
    val materialName: String,
    val questionCount: Int,
    val items: List<ResultItem>,
) {
    companion object {
        val mock = ResultsUiState(
            earnedPoints = 4.0,
            possiblePoints = 5.0,
            message = "Great work",
            materialName = "Philippine History",
            questionCount = 5,
            items = listOf(
                ResultItem(
                    id = "q1",
                    questionType = QuestionType.MULTIPLE_CHOICE,
                    question = "What document established the new republic?",
                    status = ResultStatus.CORRECT,
                    learnerAnswer = "The Malolos Constitution",
                    expectedAnswer = "The Malolos Constitution",
                    sourceId = "TXT-P002",
                    passageTitle = "The Malolos Constitution",
                    passageContent = "The constitution defined the structure and powers of the new government.",
                ),
                ResultItem(
                    id = "q2",
                    questionType = QuestionType.EXPLANATION,
                    question = "Explain what the constitution established.",
                    status = ResultStatus.INCORRECT,
                    isAiEvaluated = true,
                    learnerAnswer = "It established rules for the new republic.",
                    expectedAnswer = "It defined the structure and powers of the government.",
                    sourceId = "TXT-P002",
                    expanded = true,
                ),
                ResultItem(
                    id = "q3",
                    questionType = QuestionType.IDENTIFICATION,
                    question = "Identify the constitution described in the passage.",
                    status = ResultStatus.INCORRECT,
                    isAiEvaluated = true,
                ),
            ),
        )
    }
}

@Composable
fun ResultsScreen(
    state: ResultsUiState,
    onCloseClick: () -> Unit,
    onResultToggle: (String) -> Unit,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier,
    explanations: Map<String, String> = emptyMap(),
    explainingQuestionId: String? = null,
    explanationErrors: Map<String, String> = emptyMap(),
    onExplainClick: (String) -> Unit = {},
) {
    var shownPassageItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val showPassageGroups = state.items.mapNotNull(ResultItem::sourceId).distinct().size > 1
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BukalBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = { ResultsDoneBar(onDoneClick = onDoneClick) },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ResultsTopBar(onCloseClick = onCloseClick)
                Spacer(modifier = Modifier.height(10.dp))
                ScoreSummary(state = state)
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.results_answers_title),
                    color = BukalText,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            itemsIndexed(
                items = state.items,
                key = { _, item -> item.id },
            ) { index, item ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showPassageGroups &&
                        (index == 0 || state.items[index - 1].sourceId != item.sourceId)
                    ) {
                        Text(
                            text = item.passageTitle ?: item.sourceId.orEmpty(),
                            color = BukalText,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = item.sourceId.orEmpty(),
                            color = BukalMutedText,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    ResultItemCard(
                        item = item,
                        onToggle = { onResultToggle(item.id) },
                        onViewPassageClick = { shownPassageItemId = item.id },
                        explanation = explanations[item.id],
                        isExplaining = explainingQuestionId == item.id,
                        explanationError = explanationErrors[item.id],
                        onExplainClick = { onExplainClick(item.id) },
                    )
                }
            }
        }
    }
    state.items.firstOrNull { it.id == shownPassageItemId }?.let { item ->
        PassageBottomSheet(
            item = item,
            onDismiss = { shownPassageItemId = null },
        )
    }
}

@Composable
private fun ResultsTopBar(onCloseClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.results_title),
            color = BukalText,
            style = MaterialTheme.typography.headlineSmall,
        )
        IconButton(onClick = onCloseClick) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.results_close_description),
                tint = BukalText,
            )
        }
    }
}

@Composable
private fun ScoreSummary(state: ResultsUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.results_score,
                    formatPoints(state.earnedPoints),
                    formatPoints(state.possiblePoints),
                ),
                color = BukalText,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
            )
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = BukalAccent,
            )
        }
        Text(
            text = state.message,
            color = BukalText,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(
                R.string.results_material_summary,
                state.materialName,
                state.questionCount,
            ),
            color = BukalMutedText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatPoints(points: Double): String =
    if (points % 1.0 == 0.0) points.toInt().toString() else points.toString()

@Composable
private fun ResultItemCard(
    item: ResultItem,
    onToggle: () -> Unit,
    onViewPassageClick: () -> Unit,
    explanation: String?,
    isExplaining: Boolean,
    explanationError: String?,
    onExplainClick: () -> Unit,
) {
    val statusColors = resultStatusColors(item.status)
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = BukalSurface),
        border = BorderStroke(1.dp, BukalOutline),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusIcon(
                    status = item.status,
                    foreground = statusColors.foreground,
                    container = statusColors.container,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = questionTypeLabel(item.questionType),
                        color = BukalText,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = statusColors.container,
                        shape = CircleShape,
                    ) {
                        Text(
                            text = stringResource(statusLabel(item.status)),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = statusColors.foreground,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (item.expanded) {
                            Icons.Outlined.ExpandLess
                        } else {
                            Icons.Outlined.ExpandMore
                        },
                        contentDescription = stringResource(
                            if (item.expanded) {
                                R.string.results_collapse_description
                            } else {
                                R.string.results_expand_description
                            },
                        ),
                        tint = BukalMutedText,
                    )
                }
            }
            if (item.expanded) {
                ResultDetails(
                    item = item,
                    onViewPassageClick = onViewPassageClick,
                    explanation = explanation,
                    isExplaining = isExplaining,
                    explanationError = explanationError,
                    onExplainClick = onExplainClick,
                )
            }
        }
    }
}

private data class ResultStatusColors(
    val foreground: Color,
    val container: Color,
)

@Composable
private fun resultStatusColors(status: ResultStatus): ResultStatusColors {
    return when (status) {
        ResultStatus.CORRECT -> ResultStatusColors(BukalSuccess, BukalSuccessContainer)
        ResultStatus.INCORRECT -> ResultStatusColors(BukalError, BukalErrorContainer)
        ResultStatus.UNANSWERED -> ResultStatusColors(BukalMutedText, BukalPrimaryContainer)
    }
}

private fun statusLabel(status: ResultStatus): Int {
    return when (status) {
        ResultStatus.CORRECT -> R.string.result_status_correct
        ResultStatus.INCORRECT -> R.string.result_status_incorrect
        ResultStatus.UNANSWERED -> R.string.result_status_unanswered
    }
}

@Composable
private fun StatusIcon(
    status: ResultStatus,
    foreground: Color,
    container: Color,
) {
    Surface(
        modifier = Modifier.size(44.dp),
        color = container,
        shape = CircleShape,
    ) {
        Icon(
            imageVector = when (status) {
                ResultStatus.CORRECT -> Icons.Outlined.Check
                ResultStatus.INCORRECT -> Icons.Outlined.Close
                ResultStatus.UNANSWERED -> Icons.Outlined.Remove
            },
            contentDescription = null,
            modifier = Modifier.padding(10.dp),
            tint = foreground,
        )
    }
}

@Composable
private fun ResultDetails(
    item: ResultItem,
    onViewPassageClick: () -> Unit,
    explanation: String?,
    isExplaining: Boolean,
    explanationError: String?,
    onExplainClick: () -> Unit,
) {
    Spacer(modifier = Modifier.height(12.dp))
    DetailText(
        label = stringResource(R.string.results_question),
        value = item.question,
    )
    Spacer(modifier = Modifier.height(10.dp))
    item.learnerAnswer?.let { learnerAnswer ->
        DetailText(
            label = stringResource(R.string.results_your_answer),
            value = learnerAnswer,
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
    item.expectedAnswer?.let { expectedAnswer ->
        DetailText(
            label = stringResource(R.string.results_expected_answer),
            value = expectedAnswer,
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
    if (item.isAiEvaluated) {
        explanation?.let { generatedExplanation ->
            DetailText(
                label = stringResource(R.string.results_ai_explanation),
                value = generatedExplanation,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        explanationError?.let { error ->
            Text(
                text = error,
                color = BukalError,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (explanation == null) {
            OutlinedButton(
                onClick = onExplainClick,
                enabled = !isExplaining,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BukalPrimary),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(
                        if (isExplaining) {
                            R.string.results_explaining_action
                        } else {
                            R.string.results_explain_action
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
    if (item.sourceId != null) {
        HorizontalDivider(color = BukalOutline)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(BukalPrimaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = BukalMutedText,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.sourceId,
                    color = BukalText,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onViewPassageClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, BukalPrimary),
        ) {
            Text(
                text = stringResource(R.string.results_view_passage_action),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun questionTypeLabel(type: QuestionType): String = when (type) {
    QuestionType.MULTIPLE_CHOICE -> stringResource(R.string.quiz_type_multiple_choice)
    QuestionType.FILL_IN_THE_BLANK -> stringResource(R.string.quiz_type_fill_blank)
    QuestionType.IDENTIFICATION -> stringResource(R.string.quiz_type_identification)
    QuestionType.TRUE_FALSE -> stringResource(R.string.quiz_type_true_false)
    QuestionType.MATCHING -> stringResource(R.string.quiz_type_matching)
    QuestionType.EXPLANATION -> stringResource(R.string.quiz_type_explanation)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PassageBottomSheet(
    item: ResultItem,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BukalSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.passageTitle.orEmpty(),
                color = BukalText,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = item.sourceId.orEmpty(),
                color = BukalMutedText,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = item.passageContent.orEmpty(),
                color = BukalText,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun DetailText(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            color = BukalMutedText,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = BukalText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ResultsDoneBar(onDoneClick: () -> Unit) {
    Surface(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        color = BukalSurface,
        shadowElevation = 2.dp,
    ) {
        Button(
            onClick = onDoneClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BukalPrimary),
        ) {
            Text(
                text = stringResource(R.string.done_action),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Preview(
    name = "Results",
    showBackground = true,
    backgroundColor = 0xFFF8F7F2,
    widthDp = 360,
    heightDp = 800,
)
@Composable
private fun ResultsScreenPreview() {
    BukalTheme {
        ResultsScreen(
            state = ResultsUiState.mock,
            onCloseClick = {},
            onResultToggle = {},
            onDoneClick = {},
        )
    }
}
