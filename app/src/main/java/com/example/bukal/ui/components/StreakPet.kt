package com.example.bukal.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.bukal.R
import com.example.bukal.ui.theme.BukalAccent
import com.example.bukal.ui.theme.BukalMutedText
import com.example.bukal.ui.theme.BukalOutline
import com.example.bukal.ui.theme.BukalSurface
import com.example.bukal.ui.theme.BukalText
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val AutoMinimizeDelayMs = 7_000L
private val ExpandedEdgeMargin = 8.dp

@Composable
fun StreakPet(
    streakDays: Int,
    correctAnswersToday: Int,
    dailyGoal: Int,
    modifier: Modifier = Modifier,
) {
    if (streakDays <= 0) return

    var minimized by rememberSaveable { mutableStateOf(false) }
    var showMessage by rememberSaveable { mutableStateOf(false) }
    var interactionVersion by rememberSaveable { mutableIntStateOf(0) }
    var dockedRight by rememberSaveable { mutableStateOf(true) }
    var positionInitialized by rememberSaveable { mutableStateOf(false) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
    var petBounds by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(minimized, interactionVersion, streakDays) {
        if (!minimized) {
            delay(AutoMinimizeDelayMs)
            showMessage = false
            minimized = true
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availableWidth = constraints.maxWidth.toFloat()
        val availableHeight = constraints.maxHeight.toFloat()
        val expandedMargin = with(LocalDensity.current) { ExpandedEdgeMargin.toPx() }

        fun dockedX(width: Int, toRight: Boolean, isMinimized: Boolean): Float {
            val maximumX = (availableWidth - width).coerceAtLeast(0f)
            val margin = if (isMinimized) 0f else expandedMargin.coerceAtMost(maximumX)
            return if (toRight) maximumX - margin else margin
        }

        LaunchedEffect(
            availableWidth,
            availableHeight,
            petBounds,
            minimized,
            dockedRight,
        ) {
            if (petBounds == IntSize.Zero) return@LaunchedEffect
            offsetX = dockedX(petBounds.width, dockedRight, minimized)
            val maximumY = (availableHeight - petBounds.height).coerceAtLeast(0f)
            offsetY = if (positionInitialized) {
                offsetY.coerceIn(0f, maximumY)
            } else {
                positionInitialized = true
                maximumY / 2f
            }
        }

        val movableModifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .onSizeChanged { petBounds = it }
            .pointerInput(availableWidth, availableHeight, petBounds) {
                detectDragGestures(
                    onDragStart = {
                        showMessage = false
                        interactionVersion += 1
                    },
                    onDragEnd = {
                        val snapRight = offsetX + petBounds.width / 2f >= availableWidth / 2f
                        dockedRight = snapRight
                        offsetX = dockedX(petBounds.width, snapRight, isMinimized = true)
                        minimized = true
                    },
                    onDragCancel = {
                        offsetX = dockedX(petBounds.width, dockedRight, minimized)
                    },
                ) { change, dragAmount ->
                    change.consume()
                    val maximumX = (availableWidth - petBounds.width).coerceAtLeast(0f)
                    val maximumY = (availableHeight - petBounds.height).coerceAtLeast(0f)
                    offsetX = (offsetX + dragAmount.x).coerceIn(0f, maximumX)
                    offsetY = (offsetY + dragAmount.y).coerceIn(0f, maximumY)
                }
            }

        if (minimized) {
            MinimizedStreakPet(
                dockedRight = dockedRight,
                onClick = {
                    minimized = false
                    interactionVersion += 1
                },
                modifier = movableModifier,
            )
        } else {
            ExpandedStreakPet(
                streakDays = streakDays,
                correctAnswersToday = correctAnswersToday,
                dailyGoal = dailyGoal,
                showMessage = showMessage,
                onClick = {
                    showMessage = !showMessage
                    interactionVersion += 1
                },
                modifier = movableModifier,
            )
        }
    }
}

@Composable
private fun ExpandedStreakPet(
    streakDays: Int,
    correctAnswersToday: Int,
    dailyGoal: Int,
    showMessage: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeGoal = dailyGoal.coerceAtLeast(1)
    val progress = correctAnswersToday.coerceIn(0, safeGoal)
    val remaining = (safeGoal - progress).coerceAtLeast(0)
    val description = pluralStringResource(
        R.plurals.streak_pet_description,
        streakDays,
        streakDays,
        progress,
        safeGoal,
    )

    Column(
        modifier = modifier.width(132.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showMessage) {
            Surface(
                color = BukalSurface,
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 4.dp,
                tonalElevation = 1.dp,
                border = BorderStroke(1.dp, BukalOutline),
            ) {
                Text(
                    text = if (remaining == 0) {
                        stringResource(R.string.streak_pet_goal_complete)
                    } else {
                        pluralStringResource(
                            R.plurals.streak_pet_answers_remaining,
                            remaining,
                            remaining,
                        )
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    color = BukalText,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        Image(
            painter = painterResource(R.drawable.streak_pet_fire),
            contentDescription = null,
            modifier = Modifier
                .size(petSize(streakDays))
                .clip(RoundedCornerShape(28.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        )
        Surface(
            color = BukalSurface,
            shape = RoundedCornerShape(50),
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, BukalOutline),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.streak_pet_progress, progress, safeGoal),
                    color = BukalText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.streak_pet_today),
                    color = BukalMutedText,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun MinimizedStreakPet(
    dockedRight: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.streak_pet_open_description)
    val shape = if (dockedRight) {
        RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
    } else {
        RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    }
    Surface(
        modifier = modifier
            .offset(x = if (dockedRight) 18.dp else (-18).dp)
            .width(64.dp)
            .height(76.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        color = BukalSurface,
        shape = shape,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, BukalOutline),
    ) {
        Box(contentAlignment = if (dockedRight) Alignment.CenterStart else Alignment.CenterEnd) {
            Image(
                painter = painterResource(R.drawable.streak_pet_fire),
                contentDescription = null,
                modifier = Modifier
                    .offset(x = if (dockedRight) (-5).dp else 5.dp)
                    .size(66.dp),
            )
        }
    }
}

private fun petSize(streakDays: Int): Dp = when {
    streakDays >= 14 -> 104.dp
    streakDays >= 7 -> 96.dp
    streakDays >= 3 -> 88.dp
    else -> 80.dp
}
