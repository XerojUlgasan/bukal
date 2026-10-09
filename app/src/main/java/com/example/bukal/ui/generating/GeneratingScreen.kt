package com.example.bukal.ui.generating

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.bukal.ui.theme.BukalSurface
import com.example.bukal.ui.theme.BukalText

data class GeneratingUiState(
    val modelName: String,
    val passageId: String,
    val passageTitle: String,
    val currentQuestionNumber: Int = 1,
    val totalQuestions: Int = 5,
    val errorMessage: String? = null,
) {
    companion object {
        val mock = GeneratingUiState(
            modelName = "Qwen 3 Compact",
            passageId = "TXT-P002",
            passageTitle = "The Malolos Constitution",
        )
    }
}

@Composable
fun GeneratingScreen(
    state: GeneratingUiState,
    onBackClick: () -> Unit,
    onCancelClick: () -> Unit,
    onRetryClick: () -> Unit,
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
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            GeneratingTopBar(onBackClick = onBackClick)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (state.errorMessage == null) ProcessingPencil()
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = if (state.errorMessage == null) {
                        stringResource(
                            R.string.generating_status,
                            state.currentQuestionNumber,
                            state.totalQuestions,
                        )
                    } else {
                        stringResource(R.string.generating_error_title)
                    },
                    color = BukalText,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.errorMessage ?: stringResource(R.string.generating_subtitle),
                    color = BukalMutedText,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(20.dp))
                ModelStatusChip(modelName = state.modelName)
                Spacer(modifier = Modifier.height(16.dp))
                GeneratingPassageCard(state = state)
                if (state.errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onRetryClick,
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BukalPrimary),
                    ) {
                        Text(stringResource(R.string.generating_retry_action))
                    }
                }
            }
            TextButton(
                onClick = onCancelClick,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .height(48.dp),
            ) {
                Text(
                    text = stringResource(R.string.cancel_action),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun GeneratingTopBar(onBackClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.generating_back_description),
                tint = BukalText,
            )
        }
        Text(
            text = stringResource(R.string.generating_title),
            color = BukalText,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun ProcessingPencil() {
    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.fillMaxSize(),
            color = BukalPrimary,
            trackColor = BukalPrimaryContainer,
            strokeWidth = 7.dp,
        )
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            tint = BukalAccent,
        )
    }
}

@Composable
private fun ModelStatusChip(modelName: String) {
    Surface(
        shape = CircleShape,
        color = BukalBackground,
        border = BorderStroke(1.dp, BukalOutline),
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
                tint = BukalMutedText,
            )
            Text(
                text = stringResource(R.string.generating_model_status, modelName),
                color = BukalMutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun GeneratingPassageCard(state: GeneratingUiState) {
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
                    .size(52.dp)
                    .background(BukalPrimaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = BukalMutedText,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.passageId,
                    color = BukalMutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = state.passageTitle,
                    color = BukalText,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview(
    name = "Generating",
    showBackground = true,
    backgroundColor = 0xFFF8F7F2,
    widthDp = 360,
    heightDp = 800,
)
@Composable
private fun GeneratingScreenPreview() {
    BukalTheme {
        GeneratingScreen(
            state = GeneratingUiState.mock,
            onBackClick = {},
            onCancelClick = {},
            onRetryClick = {},
        )
    }
}
