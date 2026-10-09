package com.example.bukal.ui.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bukal.R
import com.example.bukal.ui.components.BukalBottomNavigation
import com.example.bukal.ui.components.MainDestination
import com.example.bukal.ui.theme.BukalAccent
import com.example.bukal.ui.theme.BukalBackground
import com.example.bukal.ui.theme.BukalError
import com.example.bukal.ui.theme.BukalTheme
import com.example.bukal.ui.theme.BukalMutedText
import com.example.bukal.ui.theme.BukalOutline
import com.example.bukal.ui.theme.BukalPrimary
import com.example.bukal.ui.theme.BukalPrimaryContainer
import com.example.bukal.ui.theme.BukalSuccess
import com.example.bukal.ui.theme.BukalSuccessContainer
import com.example.bukal.ui.theme.BukalSurface
import com.example.bukal.ui.theme.BukalText

data class HomeMaterial(
    val id: Long,
    val name: String,
    val details: String,
)

data class HomeUiState(
    val materials: List<HomeMaterial>,
    val streakLabel: String,
    val isImporting: Boolean = false,
    val importError: String? = null,
    val embeddingStatus: String? = null,
    val isEmbedding: Boolean = false,
    val embeddingError: Boolean = false,
) {
    companion object {
        val mock = HomeUiState(
            materials = listOf(
                HomeMaterial(
                    id = 1,
                    name = "Philippine History.txt",
                    details = "5 passages • Last opened today",
                ),
                HomeMaterial(
                    id = 2,
                    name = "Science Notes.pdf",
                    details = "3 passages • Stored locally",
                ),
            ),
            streakLabel = "3-day study streak",
        )
    }
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onImportClick: () -> Unit,
    onMaterialClick: (Long) -> Unit,
    onRetryEmbeddingClick: () -> Unit,
    onMenuClick: () -> Unit,
    onDestinationSelected: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BukalBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            BukalBottomNavigation(
                selectedDestination = MainDestination.HOME,
                onDestinationSelected = onDestinationSelected,
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            HomeTopBar(onMenuClick = onMenuClick)
            Spacer(modifier = Modifier.height(20.dp))
            HomeHeader()
            Spacer(modifier = Modifier.height(18.dp))
            ImportLessonCard(state = state, onImportClick = onImportClick)
            if (state.materials.isNotEmpty()) {
                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    text = stringResource(R.string.home_continue_title),
                    color = BukalText,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(12.dp))
                state.materials.forEach { material ->
                    RecentMaterialCard(
                        materialName = material.name,
                        materialDetails = material.details,
                        onContinueClick = { onMaterialClick(material.id) },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                state.embeddingStatus?.let { status ->
                    Spacer(modifier = Modifier.height(10.dp))
                    EmbeddingStatusRow(
                        status = status,
                        isEmbedding = state.isEmbedding,
                        isError = state.embeddingError,
                        onRetryClick = onRetryEmbeddingClick,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            StudyStreakChip(label = state.streakLabel)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EmbeddingStatusRow(
    status: String,
    isEmbedding: Boolean,
    isError: Boolean,
    onRetryClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isEmbedding) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = BukalPrimary,
                strokeWidth = 2.dp,
            )
        }
        Text(
            text = status,
            modifier = Modifier.weight(1f),
            color = if (isError) BukalError else BukalMutedText,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (isError) {
            OutlinedButton(onClick = onRetryClick) {
                Text(text = stringResource(R.string.home_embedding_retry_action))
            }
        }
    }
}

@Composable
private fun HomeTopBar(onMenuClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.weight(1f),
            color = BukalText,
            style = MaterialTheme.typography.titleLarge,
        )
        ModelReadyBadge()
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.home_menu_description),
                tint = BukalText,
            )
        }
    }
}

@Composable
private fun ModelReadyBadge() {
    Surface(
        color = BukalSuccessContainer,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(BukalSuccess, CircleShape),
            )
            Text(
                text = stringResource(R.string.home_model_ready),
                color = BukalSuccess,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_title),
                color = BukalText,
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.home_subtitle),
                color = BukalMutedText,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        val description = stringResource(R.string.home_doodle_description)
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .rotate(-12f)
                .semantics { contentDescription = description },
            tint = BukalAccent,
        )
    }
}

@Composable
private fun ImportLessonCard(
    state: HomeUiState,
    onImportClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = BukalSurface),
        border = BorderStroke(1.dp, BukalOutline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DocumentIconContainer()
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_import_title),
                        color = BukalText,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.home_import_subtitle),
                        color = BukalMutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onImportClick,
                enabled = !state.isImporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BukalPrimary),
            ) {
                if (state.isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = BukalSurface,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(
                    text = stringResource(
                        if (state.isImporting) R.string.home_importing_action else R.string.home_import_action,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            state.importError?.let { error ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = error,
                    color = BukalError,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
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
                    text = stringResource(R.string.home_privacy_note),
                    color = BukalMutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun RecentMaterialCard(
    materialName: String,
    materialDetails: String,
    onContinueClick: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = BukalSurface),
        border = BorderStroke(1.dp, BukalOutline),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DocumentIconContainer(modifier = Modifier.size(48.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = materialName,
                    color = BukalText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = materialDetails,
                    color = BukalMutedText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = onContinueClick,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BukalPrimary),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text(
                    text = stringResource(R.string.home_continue_action),
                    color = BukalPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun DocumentIconContainer(modifier: Modifier = Modifier.size(56.dp)) {
    Box(
        modifier = modifier.background(BukalPrimaryContainer, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Description,
            contentDescription = stringResource(R.string.home_material_description),
            modifier = Modifier.size(28.dp),
            tint = BukalMutedText,
        )
    }
}

@Composable
private fun StudyStreakChip(label: String) {
    Surface(
        color = BukalBackground,
        shape = CircleShape,
        border = BorderStroke(1.dp, BukalAccent.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalFireDepartment,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = BukalAccent,
            )
            Text(
                text = label,
                color = BukalMutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Preview(
    name = "Home",
    showBackground = true,
    backgroundColor = 0xFFF8F7F2,
    widthDp = 360,
    heightDp = 800,
)
@Composable
private fun HomeScreenPreview() {
    BukalTheme {
        HomeScreen(
            state = HomeUiState.mock,
            onImportClick = {},
            onMaterialClick = {},
            onRetryEmbeddingClick = {},
            onMenuClick = {},
            onDestinationSelected = {},
        )
    }
}
