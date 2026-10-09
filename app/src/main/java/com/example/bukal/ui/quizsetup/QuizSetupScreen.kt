package com.example.bukal.ui.quizsetup

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.example.bukal.R
import com.example.bukal.ai.QuestionType
import com.example.bukal.ui.theme.BukalAccent
import com.example.bukal.ui.theme.BukalBackground
import com.example.bukal.ui.theme.BukalTheme
import com.example.bukal.ui.theme.BukalMutedText
import com.example.bukal.ui.theme.BukalOutline
import com.example.bukal.ui.theme.BukalPrimary
import com.example.bukal.ui.theme.BukalPrimaryContainer
import com.example.bukal.ui.theme.BukalSurface
import com.example.bukal.ui.theme.BukalText

private const val QuizQuestionCount = 5

enum class QuizType(
    val questionType: QuestionType,
    @param:StringRes val labelRes: Int,
) {
    MULTIPLE_CHOICE(QuestionType.MULTIPLE_CHOICE, R.string.quiz_type_multiple_choice),
    FILL_IN_THE_BLANK(QuestionType.FILL_IN_THE_BLANK, R.string.quiz_type_fill_blank),
    IDENTIFICATION(QuestionType.IDENTIFICATION, R.string.quiz_type_identification),
    TRUE_FALSE(QuestionType.TRUE_FALSE, R.string.quiz_type_true_false),
    EXPLANATION(QuestionType.EXPLANATION, R.string.quiz_type_explanation),
}

data class QuestionTypeCount(
    val type: QuizType,
    val count: Int,
)

data class QuizSetupUiState(
    val materialName: String,
    val passageId: String,
    val passageTitle: String,
    val selectedTypes: List<QuizType>,
) {
    companion object {
        val mock = QuizSetupUiState(
            materialName = "Philippine History.txt",
            passageId = "TXT-P002",
            passageTitle = "The Malolos Constitution",
            selectedTypes = QuizType.entries,
        )
    }
}

internal fun calculateQuestionDistribution(
    selectedTypes: List<QuizType>,
): List<QuestionTypeCount> {
    val uniqueTypes = selectedTypes.distinct()
    if (uniqueTypes.isEmpty()) return emptyList()

    val baseCount = QuizQuestionCount / uniqueTypes.size
    val remainder = QuizQuestionCount % uniqueTypes.size
    return uniqueTypes.mapIndexed { index, type ->
        QuestionTypeCount(
            type = type,
            count = baseCount + if (index < remainder) 1 else 0,
        )
    }
}

@Composable
fun QuizSetupScreen(
    state: QuizSetupUiState,
    onBackClick: () -> Unit,
    onQuizTypeToggled: (QuizType) -> Unit,
    onGenerateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BukalBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            GenerateQuizBar(
                enabled = state.selectedTypes.isNotEmpty(),
                onGenerateClick = onGenerateClick,
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item {
                QuizSetupTopBar(onBackClick = onBackClick)
                Spacer(modifier = Modifier.height(14.dp))
                SelectedPassageCard(state = state)
                Spacer(modifier = Modifier.height(22.dp))
                QuestionTypeHeader()
                Spacer(modifier = Modifier.height(14.dp))
                QuizTypeGrid(
                    selectedTypes = state.selectedTypes,
                    onQuizTypeToggled = onQuizTypeToggled,
                )
                Spacer(modifier = Modifier.height(16.dp))
                QuestionMixCard(selectedTypes = state.selectedTypes)
                Spacer(modifier = Modifier.height(12.dp))
                LocalAiInformationCard()
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun QuizSetupTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.quiz_setup_back_description),
                tint = BukalText,
            )
        }
        Text(
            text = stringResource(R.string.quiz_setup_title),
            color = BukalText,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun SelectedPassageCard(state: QuizSetupUiState) {
    OutlinedCard(
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
                    .size(56.dp)
                    .background(BukalPrimaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = BukalMutedText,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = BukalPrimaryContainer,
                    shape = CircleShape,
                ) {
                    Text(
                        text = state.passageId,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = BukalPrimary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.passageTitle,
                    color = BukalText,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.materialName,
                    color = BukalMutedText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QuestionTypeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.quiz_setup_types_title),
                color = BukalText,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.quiz_setup_types_subtitle),
                color = BukalMutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.FactCheck,
            contentDescription = null,
            modifier = Modifier.size(38.dp),
            tint = BukalAccent,
        )
    }
}

@Composable
private fun QuizTypeGrid(
    selectedTypes: List<QuizType>,
    onQuizTypeToggled: (QuizType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        QuizType.entries.chunked(2).forEach { rowTypes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowTypes.forEach { quizType ->
                    QuizTypeChip(
                        quizType = quizType,
                        selected = quizType in selectedTypes,
                        onClick = { onQuizTypeToggled(quizType) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowTypes.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuizTypeChip(
    quizType: QuizType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(48.dp),
        label = {
            Text(
                text = stringResource(quizType.labelRes),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .then(
                        if (selected) {
                            Modifier.background(BukalPrimary, CircleShape)
                        } else {
                            Modifier.border(1.dp, BukalOutline, CircleShape)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = BukalSurface,
                    )
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = BukalSurface,
            labelColor = BukalMutedText,
            iconColor = BukalMutedText,
            selectedContainerColor = BukalPrimaryContainer,
            selectedLabelColor = BukalPrimary,
            selectedLeadingIconColor = BukalPrimary,
        ),
    )
}

@Composable
private fun QuestionMixCard(selectedTypes: List<QuizType>) {
    val distribution = calculateQuestionDistribution(selectedTypes)
    val description = questionMixDescription(distribution)

    OutlinedCard(
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
                    imageVector = Icons.Outlined.BarChart,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = BukalMutedText,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.quiz_mix_title),
                    color = BukalText,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = BukalMutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun questionMixDescription(distribution: List<QuestionTypeCount>): String {
    return when {
        distribution.isEmpty() -> stringResource(R.string.quiz_mix_empty)
        distribution.size == QuizType.entries.size -> stringResource(R.string.quiz_mix_each)
        else -> {
            val parts = mutableListOf<String>()
            for (item in distribution) {
                parts += "${item.count} ${stringResource(item.type.labelRes)}"
            }
            parts.joinToString(separator = " • ")
        }
    }
}

@Composable
private fun LocalAiInformationCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BukalAccent.copy(alpha = 0.08f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BukalAccent.copy(alpha = 0.48f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = BukalAccent,
            )
            Text(
                text = stringResource(R.string.quiz_setup_ai_note),
                color = BukalMutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun GenerateQuizBar(
    enabled: Boolean,
    onGenerateClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        color = BukalBackground,
        shadowElevation = 2.dp,
    ) {
        Button(
            onClick = onGenerateClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BukalPrimary),
        ) {
            Text(
                text = stringResource(R.string.quiz_setup_generate_action),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Preview(
    name = "Quiz setup",
    showBackground = true,
    backgroundColor = 0xFFF8F7F2,
    widthDp = 360,
    heightDp = 800,
)
@Composable
private fun QuizSetupScreenPreview() {
    BukalTheme {
        QuizSetupScreen(
            state = QuizSetupUiState.mock,
            onBackClick = {},
            onQuizTypeToggled = {},
            onGenerateClick = {},
        )
    }
}
