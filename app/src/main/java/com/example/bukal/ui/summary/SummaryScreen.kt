package com.example.bukal.ui.summary

import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.example.bukal.R
import com.example.bukal.ai.FileSummaryUiState
import com.example.bukal.ui.theme.BukalBackground
import com.example.bukal.ui.theme.BukalError
import com.example.bukal.ui.theme.BukalMutedText
import com.example.bukal.ui.theme.BukalPrimary
import com.example.bukal.ui.theme.BukalSurface
import com.example.bukal.ui.theme.BukalText
import io.noties.markwon.Markwon

@Composable
fun SummaryScreen(
    state: FileSummaryUiState,
    onBackClick: () -> Unit,
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
                .padding(contentPadding),
        ) {
            SummaryTopBar(onBackClick)
            when {
                state.isLoading || state.isGenerating -> SummaryProgress(state)
                state.errorMessage != null -> SummaryError(state.errorMessage, onRetryClick)
                state.markdown != null -> SummaryContent(state.materialName, state.markdown)
            }
        }
    }
}

@Composable
private fun SummaryTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.summary_back_description),
                tint = BukalText,
            )
        }
        Text(
            text = stringResource(R.string.summary_title),
            color = BukalText,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun SummaryProgress(state: FileSummaryUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = BukalPrimary)
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = if (state.currentPassage > 0) {
                stringResource(
                    R.string.summary_progress,
                    state.currentPassage,
                    state.totalPassages,
                )
            } else {
                stringResource(R.string.summary_loading)
            },
            color = BukalText,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.summary_once_note),
            color = BukalMutedText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SummaryError(message: String, onRetryClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.summary_error_title),
            color = BukalError,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = message, color = BukalMutedText)
        Spacer(modifier = Modifier.height(18.dp))
        Button(onClick = onRetryClick) {
            Text(stringResource(R.string.summary_retry_action))
        }
    }
}

@Composable
private fun SummaryContent(materialName: String, markdown: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = materialName,
            color = BukalMutedText,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = BukalSurface),
        ) {
            MarkdownSummary(
                markdown = markdown,
                modifier = Modifier.padding(18.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.summary_source_note),
            color = BukalMutedText,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MarkdownSummary(markdown: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val textColor = BukalText.toArgb()
    val markwon = remember(context) { Markwon.create(context) }
    AndroidView(
        factory = { viewContext ->
            TextView(viewContext).apply {
                setTextColor(textColor)
                textSize = 16f
                setLineSpacing(0f, 1.2f)
                linksClickable = false
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            markwon.setMarkdown(textView, markdown)
        },
        modifier = modifier.fillMaxWidth(),
    )
}
