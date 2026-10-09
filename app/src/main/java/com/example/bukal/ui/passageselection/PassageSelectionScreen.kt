package com.example.bukal.ui.passageselection

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
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

data class PassagePreview(
    val id: String,
    val title: String,
    val excerpt: String,
)

data class PassageSelectionUiState(
    val materialName: String,
    val passages: List<PassagePreview>,
    val selectedPassageId: String?,
) {
    companion object {
        val mock = PassageSelectionUiState(
            materialName = "Philippine History.txt",
            passages = listOf(
                PassagePreview(
                    id = "TXT-P001",
                    title = "The First Philippine Republic",
                    excerpt = "The Malolos Congress drafted a constitution and established the new republic.",
                ),
                PassagePreview(
                    id = "TXT-P002",
                    title = "The Malolos Constitution",
                    excerpt = "The constitution defined the structure and powers of the new government.",
                ),
                PassagePreview(
                    id = "TXT-P003",
                    title = "Education and Reform",
                    excerpt = "Schools and civic institutions became important parts of the republic.",
                ),
                PassagePreview(
                    id = "TXT-P004",
                    title = "International Recognition",
                    excerpt = "Filipino leaders sought recognition for the country's independence abroad.",
                ),
                PassagePreview(
                    id = "TXT-P005",
                    title = "A Lasting Legacy",
                    excerpt = "The republic left a lasting example of constitutional government and national unity.",
                ),
            ),
            selectedPassageId = "TXT-P002",
        )
    }
}

@Composable
fun PassageSelectionScreen(
    state: PassageSelectionUiState,
    onBackClick: () -> Unit,
    onPassageSelected: (String) -> Unit,
    onContinueClick: () -> Unit,
    onDestinationSelected: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BukalBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            PassageSelectionBottomBar(
                continueEnabled = state.selectedPassageId != null,
                onContinueClick = onContinueClick,
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
                PassageSelectionTopBar(onBackClick = onBackClick)
                Spacer(modifier = Modifier.height(14.dp))
                MaterialSummary(
                    materialName = state.materialName,
                    passageCount = state.passages.size,
                )
                Spacer(modifier = Modifier.height(18.dp))
                PassageInstruction()
                Spacer(modifier = Modifier.height(6.dp))
            }
            items(
                items = state.passages,
                key = PassagePreview::id,
            ) { passage ->
                PassageCard(
                    passage = passage,
                    selected = passage.id == state.selectedPassageId,
                    onClick = { onPassageSelected(passage.id) },
                )
            }
        }
    }
}

@Composable
private fun PassageSelectionTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.passage_back_description),
                tint = BukalText,
            )
        }
        Text(
            text = stringResource(R.string.passage_title),
            color = BukalText,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun MaterialSummary(
    materialName: String,
    passageCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
                text = materialName,
                color = BukalText,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.passage_count, passageCount),
                color = BukalMutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PassageInstruction() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.passage_instruction),
            modifier = Modifier.weight(1f),
            color = BukalMutedText,
            style = MaterialTheme.typography.bodyLarge,
        )
        Icon(
            imageVector = Icons.Outlined.AutoStories,
            contentDescription = null,
            modifier = Modifier.size(38.dp),
            tint = BukalAccent,
        )
    }
}

@Composable
private fun PassageCard(
    passage: PassagePreview,
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
            containerColor = if (selected) {
                BukalPrimaryContainer.copy(alpha = 0.72f)
            } else {
                BukalSurface
            },
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) BukalPrimary else BukalOutline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = BukalPrimary,
                    unselectedColor = BukalMutedText,
                ),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 4.dp),
            ) {
                Text(
                    text = passage.id,
                    color = BukalMutedText,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = passage.title,
                    color = BukalText,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = passage.excerpt,
                    color = BukalMutedText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PassageSelectionBottomBar(
    continueEnabled: Boolean,
    onContinueClick: () -> Unit,
    onDestinationSelected: (MainDestination) -> Unit,
) {
    Column {
        Surface(
            color = BukalBackground,
            shadowElevation = 2.dp,
        ) {
            Button(
                onClick = onContinueClick,
                enabled = continueEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BukalPrimary),
            ) {
                Text(
                    text = stringResource(R.string.passage_continue_action),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        BukalBottomNavigation(
            selectedDestination = MainDestination.HOME,
            onDestinationSelected = onDestinationSelected,
        )
    }
}

@Preview(
    name = "Passage selection",
    showBackground = true,
    backgroundColor = 0xFFF8F7F2,
    widthDp = 360,
    heightDp = 800,
)
@Composable
private fun PassageSelectionScreenPreview() {
    BukalTheme {
        PassageSelectionScreen(
            state = PassageSelectionUiState.mock,
            onBackClick = {},
            onPassageSelected = {},
            onContinueClick = {},
            onDestinationSelected = {},
        )
    }
}
