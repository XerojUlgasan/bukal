package com.example.bukal.ui.modelsetup

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bukal.R
import com.example.bukal.data.model.ModelInstallStatus
import com.example.bukal.data.model.ModelInstallation
import com.example.bukal.data.model.ModelInstallationSnapshot
import com.example.bukal.data.model.ModelCatalog
import com.example.bukal.data.model.ModelDownloadSpec
import com.example.bukal.data.model.ModelPurpose
import com.example.bukal.ui.theme.BukalBackground
import com.example.bukal.ui.theme.BukalError
import com.example.bukal.ui.theme.BukalErrorContainer
import com.example.bukal.ui.theme.BukalMutedText
import com.example.bukal.ui.theme.BukalOutline
import com.example.bukal.ui.theme.BukalPrimary
import com.example.bukal.ui.theme.BukalPrimaryContainer
import com.example.bukal.ui.theme.BukalSuccess
import com.example.bukal.ui.theme.BukalSurface
import com.example.bukal.ui.theme.BukalText
import com.example.bukal.ui.theme.BukalTheme

@Composable
fun ModelSetupScreen(
    state: ModelInstallationSnapshot,
    selectedQuizModelId: String?,
    actionError: String?,
    onInstallRequiredClick: () -> Unit,
    onInstallModel: (ModelDownloadSpec) -> Unit,
    onSelectQuizModel: (ModelDownloadSpec) -> Unit,
    onDeleteModel: (ModelDownloadSpec) -> Unit,
    onContinueClick: () -> Unit,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var termsModel by remember { mutableStateOf<ModelDownloadSpec?>(null) }
    val uriHandler = LocalUriHandler.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BukalBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                color = BukalText,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.model_setup_title),
                color = BukalText,
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = stringResource(R.string.model_setup_subtitle),
                color = BukalMutedText,
                style = MaterialTheme.typography.bodyLarge,
            )

            Text(
                text = stringResource(R.string.model_quiz_models_title),
                color = BukalText,
                style = MaterialTheme.typography.titleLarge,
            )
            state.models.filter { it.spec.purpose == ModelPurpose.QUIZ }.forEach { installation ->
                ModelCard(
                    installation = installation,
                    selected = installation.spec.id == selectedQuizModelId,
                    onInstall = {
                        if (installation.spec.termsUrl == null) {
                            onInstallModel(installation.spec)
                        } else {
                            termsModel = installation.spec
                        }
                    },
                    onSelect = { onSelectQuizModel(installation.spec) },
                    onDelete = { onDeleteModel(installation.spec) },
                )
            }

            Text(
                text = stringResource(R.string.model_embedding_title),
                color = BukalText,
                style = MaterialTheme.typography.titleLarge,
            )
            state.models.filter { it.spec.purpose == ModelPurpose.EMBEDDING }.forEach { installation ->
                ModelCard(
                    installation = installation,
                    selected = false,
                    onInstall = { onInstallModel(installation.spec) },
                    onSelect = {},
                    onDelete = { onDeleteModel(installation.spec) },
                )
            }

            actionError?.let { ErrorMessage(it) }

            Button(
                onClick = if (state.isReady) onContinueClick else onInstallRequiredClick,
                enabled = state.isReady ||
                    (state.needsRequiredInstallation && !state.hasBlockingDownloads),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BukalPrimary),
            ) {
                val icon = when {
                    state.isReady -> Icons.Outlined.CheckCircle
                    state.hasBlockingDownloads -> Icons.Outlined.HourglassTop
                    state.models.any { it.status == ModelInstallStatus.Corrupted } -> Icons.Outlined.Refresh
                    else -> Icons.Outlined.CloudDownload
                }
                Icon(icon, contentDescription = null)
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = when {
                        !state.checked -> stringResource(R.string.model_checking_action)
                        state.isReady -> stringResource(R.string.model_continue_action)
                        state.hasBlockingDownloads -> stringResource(R.string.model_downloading_action)
                        state.models.any { it.status == ModelInstallStatus.Corrupted } ->
                            stringResource(R.string.model_reinstall_action)
                        else -> stringResource(R.string.model_install_action)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            PrivacyNote()
            TextButton(
                onClick = onHelpClick,
                modifier = Modifier.height(48.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null)
                Spacer(modifier = Modifier.size(10.dp))
                Text(stringResource(R.string.model_help_action))
            }
        }
    }

    termsModel?.let { model ->
        AlertDialog(
            onDismissRequest = { termsModel = null },
            title = { Text(stringResource(R.string.model_terms_title, model.displayName)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.model_terms_message))
                    TextButton(onClick = { uriHandler.openUri(requireNotNull(model.termsUrl)) }) {
                        Text(stringResource(R.string.model_terms_review_action))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        termsModel = null
                        onInstallModel(model)
                    },
                ) {
                    Text(stringResource(R.string.model_terms_accept_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { termsModel = null }) {
                    Text(stringResource(R.string.cancel_action))
                }
            },
        )
    }
}

@Composable
private fun ModelCard(
    installation: ModelInstallation,
    selected: Boolean,
    onInstall: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = BukalSurface),
        border = BorderStroke(1.dp, BukalOutline),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = BukalPrimaryContainer,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Storage,
                        contentDescription = null,
                        modifier = Modifier.padding(14.dp).size(26.dp),
                        tint = BukalPrimary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = installation.spec.displayName,
                        color = BukalText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (installation.spec.purpose == ModelPurpose.EMBEDDING) {
                            stringResource(R.string.model_fixed_embedding)
                        } else {
                            installation.spec.purpose.label
                        },
                        color = BukalMutedText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            HorizontalDivider(color = BukalOutline)
            Text(
                text = Formatter.formatShortFileSize(context, installation.spec.sizeBytes),
                color = BukalMutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            installation.spec.minimumRamGb?.let { minimumRamGb ->
                Text(
                    text = stringResource(R.string.model_minimum_ram, minimumRamGb),
                    color = BukalMutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ModelStatus(installation.status)
            ModelActions(
                installation = installation,
                selected = selected,
                onInstall = onInstall,
                onSelect = onSelect,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun ModelActions(
    installation: ModelInstallation,
    selected: Boolean,
    onInstall: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    when (installation.status) {
        ModelInstallStatus.Installed -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (installation.spec.purpose == ModelPurpose.QUIZ) {
                if (selected) {
                    Text(
                        text = stringResource(R.string.model_selected),
                        color = BukalSuccess,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else {
                    TextButton(onClick = onSelect) {
                        Text(stringResource(R.string.model_use_action))
                    }
                }
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                Spacer(modifier = Modifier.size(6.dp))
                Text(stringResource(R.string.model_remove_action))
            }
        }
        ModelInstallStatus.Missing,
        ModelInstallStatus.Corrupted,
        is ModelInstallStatus.Paused,
        is ModelInstallStatus.Failed,
        -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onInstall) {
                Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    stringResource(
                        if (installation.status == ModelInstallStatus.Missing) {
                            R.string.model_download_action
                        } else {
                            R.string.model_retry_action
                        },
                    ),
                )
            }
        }
        ModelInstallStatus.Checking,
        is ModelInstallStatus.Downloading,
        -> Unit
    }
}

@Composable
private fun ModelStatus(status: ModelInstallStatus) {
    val (icon, text, color) = when (status) {
        ModelInstallStatus.Checking -> Triple(
            Icons.Outlined.HourglassTop,
            stringResource(R.string.model_status_checking),
            BukalMutedText,
        )
        ModelInstallStatus.Missing -> Triple(
            Icons.Outlined.CloudDownload,
            stringResource(R.string.model_status_missing),
            BukalMutedText,
        )
        ModelInstallStatus.Installed -> Triple(
            Icons.Outlined.CheckCircle,
            stringResource(R.string.model_status_installed),
            BukalSuccess,
        )
        ModelInstallStatus.Corrupted -> Triple(
            Icons.Outlined.ErrorOutline,
            stringResource(R.string.model_status_corrupted),
            BukalError,
        )
        is ModelInstallStatus.Failed -> Triple(
            Icons.Outlined.ErrorOutline,
            status.message,
            BukalError,
        )
        is ModelInstallStatus.Downloading -> Triple(
            Icons.Outlined.CloudDownload,
            status.waitingMessage ?: stringResource(R.string.model_status_downloading),
            BukalPrimary,
        )
        is ModelInstallStatus.Paused -> Triple(
            Icons.Outlined.ErrorOutline,
            status.message,
            BukalError,
        )
    }

    val downloadProgress = when (status) {
        is ModelInstallStatus.Downloading -> status.downloadedBytes to status.totalBytes
        is ModelInstallStatus.Paused -> status.downloadedBytes to status.totalBytes
        else -> null
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status == ModelInstallStatus.Checking) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = color)
        }
        Text(
            text = text,
            color = color,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        downloadProgress?.let { (downloadedBytes, totalBytes) ->
            Text(
                text = stringResource(
                    R.string.model_download_percentage,
                    downloadPercent(downloadedBytes, totalBytes),
                ),
                color = color,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    downloadProgress?.let { (downloadedBytes, totalBytes) ->
        val progress = if (totalBytes > 0) {
            (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
        } else {
            null
        }
        if (progress == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Surface(color = BukalErrorContainer, shape = RoundedCornerShape(12.dp)) {
        Text(
            text = message,
            color = BukalError,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PrivacyNote() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = BukalMutedText)
        Text(
            text = stringResource(R.string.model_privacy_note),
            color = BukalMutedText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun downloadPercent(downloadedBytes: Long, totalBytes: Long): Int =
    if (totalBytes <= 0) 0 else {
        ((downloadedBytes * 100) / totalBytes).coerceIn(0, 100).toInt()
    }

@Preview(showBackground = true, backgroundColor = 0xFFF8F7F2)
@Composable
private fun ModelSetupScreenPreview() {
    BukalTheme {
        ModelSetupScreen(
            state = ModelInstallationSnapshot(
                models = ModelCatalog.mapIndexed { index, spec ->
                    ModelInstallation(
                        spec,
                        if (index == 0) ModelInstallStatus.Installed else ModelInstallStatus.Missing,
                    )
                },
                checked = true,
            ),
            selectedQuizModelId = "qwen3-compact",
            actionError = null,
            onInstallRequiredClick = {},
            onInstallModel = {},
            onSelectQuizModel = {},
            onDeleteModel = {},
            onContinueClick = {},
            onHelpClick = {},
        )
    }
}
