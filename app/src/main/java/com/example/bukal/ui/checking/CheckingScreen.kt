package com.example.bukal.ui.checking

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bukal.R
import com.example.bukal.ui.theme.BukalAccent
import com.example.bukal.ui.theme.BukalBackground
import com.example.bukal.ui.theme.BukalTheme
import com.example.bukal.ui.theme.BukalMutedText
import com.example.bukal.ui.theme.BukalOutline
import com.example.bukal.ui.theme.BukalPrimary
import com.example.bukal.ui.theme.BukalPrimaryContainer
import com.example.bukal.ui.theme.BukalSuccess
import com.example.bukal.ui.theme.BukalSuccessContainer
import com.example.bukal.ui.theme.BukalSurface
import com.example.bukal.ui.theme.BukalText

data class CheckingUiState(
    val deterministicAnswerCount: Int,
    val writtenAnswerCount: Int,
    val errorMessage: String? = null,
) {
    companion object {
        val mock = CheckingUiState(
            deterministicAnswerCount = 3,
            writtenAnswerCount = 2,
        )
    }
}

@Composable
fun CheckingScreen(
    state: CheckingUiState,
    modifier: Modifier = Modifier,
    onRetryClick: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BukalBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.checking_title),
                color = BukalText,
                style = MaterialTheme.typography.headlineSmall,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (state.errorMessage != null) {
                    Text(
                        text = state.errorMessage,
                        color = BukalText,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onRetryClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BukalPrimary),
                    ) {
                        Text(text = stringResource(R.string.checking_retry_action))
                    }
                } else {
                    CheckingIndicator()
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.checking_status, state.writtenAnswerCount),
                        color = BukalText,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.checking_subtitle),
                        color = BukalMutedText,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    LocalAiChip()
                    Spacer(modifier = Modifier.height(20.dp))
                    CheckingSummaryCard(state = state)
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = BukalMutedText,
                        )
                        Text(
                            text = stringResource(R.string.checking_privacy_note),
                            color = BukalMutedText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckingIndicator() {
    Box(
        modifier = Modifier.size(112.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.fillMaxSize(),
            color = BukalPrimary,
            trackColor = BukalPrimaryContainer,
            strokeWidth = 7.dp,
        )
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = BukalAccent,
        )
    }
}

@Composable
private fun LocalAiChip() {
    Surface(
        color = BukalSuccessContainer,
        shape = CircleShape,
        border = BorderStroke(1.dp, BukalSuccess.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Smartphone,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = BukalSuccess,
            )
            Text(
                text = stringResource(R.string.checking_local_ai_status),
                color = BukalSuccess,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CheckingSummaryCard(state: CheckingUiState) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = BukalSurface),
        border = BorderStroke(1.dp, BukalOutline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            StatusRow(
                completed = true,
                text = stringResource(
                    R.string.checking_instant_count,
                    state.deterministicAnswerCount,
                ),
            )
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = BukalOutline)
            Spacer(modifier = Modifier.height(14.dp))
            StatusRow(
                completed = false,
                text = stringResource(
                    R.string.checking_review_count,
                    state.writtenAnswerCount,
                ),
            )
        }
    }
}

@Composable
private fun StatusRow(
    completed: Boolean,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (completed) {
            Box(
                modifier = Modifier
                    .size(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    color = BukalSuccessContainer,
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.padding(6.dp),
                        tint = BukalSuccess,
                    )
                }
            }
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = BukalPrimary,
                trackColor = BukalPrimaryContainer,
                strokeWidth = 5.dp,
            )
        }
        Text(
            text = text,
            color = BukalText,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Preview(
    name = "AI checking",
    showBackground = true,
    backgroundColor = 0xFFF8F7F2,
    widthDp = 360,
    heightDp = 800,
)
@Composable
private fun CheckingScreenPreview() {
    BukalTheme {
        CheckingScreen(state = CheckingUiState.mock)
    }
}
