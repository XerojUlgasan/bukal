package com.example.bukal.ui.flashcards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bukal.R
import com.example.bukal.ui.theme.BukalAccent
import com.example.bukal.ui.theme.BukalBackground
import com.example.bukal.ui.theme.BukalErrorContainer
import com.example.bukal.ui.theme.BukalMutedText
import com.example.bukal.ui.theme.BukalOutline
import com.example.bukal.ui.theme.BukalPrimary
import com.example.bukal.ui.theme.BukalPrimaryContainer
import com.example.bukal.ui.theme.BukalSuccess
import com.example.bukal.ui.theme.BukalSuccessContainer
import com.example.bukal.ui.theme.BukalSurface
import com.example.bukal.ui.theme.BukalText
import com.example.bukal.ui.theme.BukalTheme

@Composable
fun FlashcardsScreen(
    state: FlashcardsUiState,
    onBackClick: () -> Unit,
    onCardClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BukalBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            FlashcardsTopBar(onBackClick = onBackClick)
            if (state.isEmpty) {
                FlashcardsEmptyState()
            } else {
                FlashcardsContent(
                    state = state,
                    onCardClick = onCardClick,
                    onPreviousClick = onPreviousClick,
                    onNextClick = onNextClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FlashcardsTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.flashcards_back_description),
                tint = BukalText,
            )
        }
        Text(
            text = stringResource(R.string.flashcards_title),
            color = BukalText,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun FlashcardsContent(
    state: FlashcardsUiState,
    onCardClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val card = state.cards[state.currentIndex]
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.materialName,
            color = BukalText,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(
                R.string.flashcards_position,
                state.currentIndex + 1,
                state.cards.size,
            ),
            color = BukalMutedText,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = card.sourceId,
            color = BukalMutedText,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(16.dp))
        key(state.currentIndex) {
            FlashcardCard(
                card = card,
                isRevealed = state.isRevealed,
                onClick = onCardClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onPreviousClick,
                enabled = state.currentIndex > 0,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.previous_action),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Button(
                onClick = onNextClick,
                enabled = state.currentIndex < state.cards.lastIndex,
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

@Composable
private fun FlashcardCard(
    card: Flashcard,
    isRevealed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation = animateFloatAsState(
        targetValue = if (isRevealed) 180f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "flashcardFlip",
    ).value
    val showAnswer = rotation > 90f
    OutlinedCard(
        modifier = modifier
            .height(320.dp)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (showAnswer) {
                BukalSuccessContainer
            } else {
                BukalSurface
            },
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (showAnswer) BukalSuccess else BukalOutline,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationY = if (showAnswer) 180f else 0f }
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = if (showAnswer) BukalSuccess else BukalPrimaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (showAnswer) {
                                R.string.flashcards_answer_label
                            } else {
                                R.string.flashcards_question_label
                            },
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = if (showAnswer) BukalSurface else BukalPrimary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    text = card.quizTypeLabel,
                    color = BukalMutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (showAnswer) card.answer else card.prompt,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    color = BukalText,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoStories,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = BukalAccent,
                )
                Text(
                    text = stringResource(
                        if (showAnswer) {
                            R.string.flashcards_hide_hint
                        } else {
                            R.string.flashcards_reveal_hint
                        },
                    ),
                    color = BukalMutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun FlashcardsEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(BukalErrorContainer, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoStories,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = BukalMutedText,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.flashcards_empty_title),
            color = BukalText,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.flashcards_empty_subtitle),
            color = BukalMutedText,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(
    name = "Flashcards",
    showBackground = true,
    backgroundColor = 0xFFF8F7F2,
    widthDp = 360,
    heightDp = 800,
)
@Composable
private fun FlashcardsScreenPreview() {
    BukalTheme {
        FlashcardsScreen(
            state = FlashcardsUiState(
                materialName = "Philippine History.txt",
                cards = listOf(
                    Flashcard(
                        prompt = "Who drafted the Malolos Constitution?",
                        answer = "The Malolos Congress",
                        quizTypeLabel = "Identification",
                        sourceId = "TXT-P001",
                    ),
                    Flashcard(
                        prompt = "The Malolos Constitution defined the structure of the new government.",
                        answer = "True",
                        quizTypeLabel = "True or false",
                        sourceId = "TXT-P002",
                    ),
                ),
                currentIndex = 0,
                isRevealed = false,
            ),
            onBackClick = {},
            onCardClick = {},
            onPreviousClick = {},
            onNextClick = {},
        )
    }
}

@Preview(
    name = "Flashcards empty",
    showBackground = true,
    backgroundColor = 0xFFF8F7F2,
    widthDp = 360,
    heightDp = 800,
)
@Composable
private fun FlashcardsScreenEmptyPreview() {
    BukalTheme {
        FlashcardsScreen(
            state = FlashcardsUiState(materialName = "Philippine History.txt"),
            onBackClick = {},
            onCardClick = {},
            onPreviousClick = {},
            onNextClick = {},
        )
    }
}
