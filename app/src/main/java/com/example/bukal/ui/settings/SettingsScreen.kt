package com.example.bukal.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bukal.R
import com.example.bukal.data.search.DocumentSearchResult
import com.example.bukal.ui.library.VectorSearchStatus
import com.example.bukal.ui.theme.BukalBackground
import com.example.bukal.ui.theme.BukalMutedText
import com.example.bukal.ui.theme.BukalOutline
import com.example.bukal.ui.theme.BukalPrimary
import com.example.bukal.ui.theme.BukalSurface
import com.example.bukal.ui.theme.BukalText
import com.example.bukal.ui.theme.BukalTheme
import java.util.Locale

data class SettingsUiState(
    val query: String = "",
    val searchStatus: VectorSearchStatus = VectorSearchStatus.IDLE,
    val results: List<DocumentSearchResult> = emptyList(),
    val errorMessage: String? = null,
)

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onManageModelsClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BukalBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsHeader(onBackClick)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onManageModelsClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BukalPrimary),
                ) {
                    Icon(imageVector = Icons.Outlined.Memory, contentDescription = null)
                    Text(
                        text = stringResource(R.string.settings_manage_models),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.settings_vector_search_title),
                    color = BukalText,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.settings_vector_search_subtitle),
                    color = BukalMutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_vector_search_input)) },
                    minLines = 2,
                    enabled = state.searchStatus != VectorSearchStatus.SEARCHING,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onSearchClick,
                    enabled = state.query.isNotBlank() && state.searchStatus != VectorSearchStatus.SEARCHING,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BukalPrimary),
                ) {
                    if (state.searchStatus == VectorSearchStatus.SEARCHING) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(22.dp),
                            color = BukalSurface,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.settings_vector_search_action))
                    }
                }
                state.errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.searchStatus == VectorSearchStatus.READY && state.results.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_vector_search_empty),
                        color = BukalMutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(state.results, key = DocumentSearchResult::chunkId) { result ->
                SearchResultCard(result)
            }
        }
    }
}

@Composable
private fun SettingsHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.settings_back_description),
                tint = BukalText,
            )
        }
        Text(
            text = stringResource(R.string.settings_title),
            color = BukalText,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun SearchResultCard(result: DocumentSearchResult) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = BukalSurface),
        border = BorderStroke(1.dp, BukalOutline),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = result.sourceId,
                    color = BukalPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = String.format(Locale.ROOT, "%.3f", result.similarity),
                    color = BukalMutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = result.materialName,
                color = BukalText,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = result.text,
                color = BukalMutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun SettingsScreenPreview() {
    BukalTheme {
        SettingsScreen(
            state = SettingsUiState(query = "Philippine history"),
            onQueryChange = {},
            onSearchClick = {},
            onManageModelsClick = {},
            onBackClick = {},
        )
    }
}
