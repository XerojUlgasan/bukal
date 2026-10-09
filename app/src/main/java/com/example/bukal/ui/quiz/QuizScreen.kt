package com.example.bukal.ui.quiz

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bukal.R
import com.example.bukal.ai.MatchingPair
import com.example.bukal.ai.QuestionType
import com.example.bukal.ui.theme.BukalBackground
import com.example.bukal.ui.theme.BukalTheme
import com.example.bukal.ui.theme.BukalMutedText
import com.example.bukal.ui.theme.BukalOutline
import com.example.bukal.ui.theme.BukalPrimary
import com.example.bukal.ui.theme.BukalPrimaryContainer
import com.example.bukal.ui.theme.BukalSurface
import com.example.bukal.ui.theme.BukalText

data class QuizUiState(
    val materialName: String,
    val passageId: String,
    val currentQuestion: Int,
    val totalQuestions: Int,
    val currentPassage: Int = 1,
    val totalPassages: Int = 1,
    val currentPassageQuestion: Int = currentQuestion,
    val totalPassageQuestions: Int = totalQuestions,
    val questionType: QuestionType,
    val prompt: String,
    val options: List<String>,
    val selectedOptionIndex: Int?,
    val textResponse: String = "",
    val matchingPairs: List<MatchingPair> = emptyList(),
    val matchingSelections: Map<String, String> = emptyMap(),
    val failedQuestionCount: Int = 0,
) {
    companion object {
        val mock = QuizUiState(
            materialName = "Philippine History",
            passageId = "TXT-P002",
            currentQuestion = 2,
            totalQuestions = 5,
            questionType = QuestionType.MULTIPLE_CHOICE,
            prompt = "What document established the structure of the First Philippine Republic?",
            options = listOf(
                "The Treaty of Paris",
                "The Malolos Constitution",
                "The Jones Law",
                "The Biak-na-Bato Constitution",
            ),
            selectedOptionIndex = 1,
        )
    }
}

@Composable
fun QuizScreen(
    state: QuizUiState,
    onCloseClick: () -> Unit,
    onMenuClick: () -> Unit,
    onOptionSelected: (Int) -> Unit,
    onTextResponseChanged: (String) -> Unit,
    onMatchingSelected: (leftId: String, rightId: String) -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BukalBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            QuizNavigationBar(
                onPreviousClick = onPreviousClick,
                onNextClick = onNextClick,
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
                QuizTopBar(
                    materialName = state.materialName,
                    onCloseClick = onCloseClick,
                    onMenuClick = onMenuClick,
                )
                if (state.currentQuestion == 1 && state.failedQuestionCount > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = BukalPrimaryContainer,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.quiz_generation_failed_notice,
                                state.failedQuestionCount,
                                state.failedQuestionCount,
                                state.totalQuestions,
                                state.totalQuestions + state.failedQuestionCount,
                            ),
                            modifier = Modifier.padding(12.dp),
                            color = BukalText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { state.currentQuestion.toFloat() / state.totalQuestions },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = BukalPrimary,
                    trackColor = BukalOutline,
                )
                Spacer(modifier = Modifier.height(8.dp))
                QuestionMeta(state = state)
                Spacer(modifier = Modifier.height(16.dp))
                QuestionTypeChip(label = questionTypeLabel(state.questionType))
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = state.prompt,
                    color = BukalText,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            when (state.questionType) {
                QuestionType.MULTIPLE_CHOICE,
                QuestionType.TRUE_FALSE,
                -> itemsIndexed(state.options) { index, option ->
                    AnswerOptionCard(
                        label = option,
                        selected = index == state.selectedOptionIndex,
                        onClick = { onOptionSelected(index) },
                    )
                }
                QuestionType.FILL_IN_THE_BLANK,
                QuestionType.IDENTIFICATION,
                QuestionType.EXPLANATION,
                -> item {
                    OutlinedTextField(
                        value = state.textResponse,
                        onValueChange = onTextResponseChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(textResponseLabel(state.questionType)) },
                        singleLine = state.questionType != QuestionType.EXPLANATION,
                        minLines = if (state.questionType == QuestionType.EXPLANATION) 4 else 1,
                    )
                }
                QuestionType.MATCHING -> itemsIndexed(state.matchingPairs) { _, pair ->
                    MatchingAnswerCard(
                        pair = pair,
                        rightOptions = state.matchingPairs,
                        selectedRightId = state.matchingSelections[pair.leftId],
                        onSelected = { rightId -> onMatchingSelected(pair.leftId, rightId) },
                    )
                }
            }
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
private fun textResponseLabel(type: QuestionType): String = when (type) {
    QuestionType.FILL_IN_THE_BLANK -> stringResource(R.string.quiz_answer_fill_blank)
    QuestionType.IDENTIFICATION -> stringResource(R.string.quiz_answer_identification)
    QuestionType.EXPLANATION -> stringResource(R.string.quiz_answer_explanation)
    else -> ""
}

@Composable
private fun QuizTopBar(
    materialName: String,
    onCloseClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCloseClick) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.quiz_close_description),
                tint = BukalText,
            )
        }
        Text(
            text = materialName,
            modifier = Modifier.weight(1f),
            color = BukalText,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.quiz_menu_description),
                tint = BukalText,
            )
        }
    }
}

@Composable
private fun QuestionMeta(state: QuizUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (state.totalPassages > 1) {
                stringResource(
                    R.string.quiz_passage_question_position,
                    state.currentPassage,
                    state.totalPassages,
                    state.currentPassageQuestion,
                    state.totalPassageQuestions,
                )
            } else {
                stringResource(
                    R.string.quiz_question_position,
                    state.currentQuestion,
                    state.totalQuestions,
                )
            },
            color = BukalMutedText,
            style = MaterialTheme.typography.bodyLarge,
        )
        Surface(
            color = BukalPrimaryContainer,
            shape = CircleShape,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = BukalMutedText,
                )
                Text(
                    text = state.passageId,
                    color = BukalMutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun QuestionTypeChip(label: String) {
    Surface(
        color = BukalBackground,
        shape = CircleShape,
        border = BorderStroke(1.dp, BukalPrimary.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.FormatListBulleted,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = BukalMutedText,
            )
            Text(
                text = label,
                color = BukalMutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AnswerOptionCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) BukalPrimaryContainer else BukalSurface,
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) BukalPrimary else BukalOutline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = BukalPrimary,
                    unselectedColor = BukalMutedText,
                ),
            )
            Text(
                text = label,
                color = BukalText,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun MatchingAnswerCard(
    pair: MatchingPair,
    rightOptions: List<MatchingPair>,
    selectedRightId: String?,
    onSelected: (String) -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = BukalSurface),
        border = BorderStroke(1.dp, BukalOutline),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = pair.leftText,
                color = BukalText,
                style = MaterialTheme.typography.titleMedium,
            )
            rightOptions.forEach { option ->
                AnswerOptionCard(
                    label = option.rightText,
                    selected = selectedRightId == option.rightId,
                    onClick = { onSelected(option.rightId) },
                )
            }
        }
    }
}

@Composable
private fun QuizNavigationBar(
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        color = BukalSurface,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onPreviousClick,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BukalPrimary),
            ) {
                Text(
                    text = stringResource(R.string.previous_action),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Button(
                onClick = onNextClick,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BukalPrimary),
            ) {
                Text(
                    text = stringResource(R.string.next_action),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Preview(
    name = "Quiz answering",
    showBackground = true,
    backgroundColor = 0xFFF8F7F2,
    widthDp = 360,
    heightDp = 800,
)
@Composable
private fun QuizScreenPreview() {
    BukalTheme {
        QuizScreen(
            state = QuizUiState.mock,
            onCloseClick = {},
            onMenuClick = {},
            onOptionSelected = {},
            onTextResponseChanged = {},
            onMatchingSelected = { _, _ -> },
            onPreviousClick = {},
            onNextClick = {},
        )
    }
}
