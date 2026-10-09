package com.example.bukal.data.model

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.StatFs
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

private val SafeModelId = Regex("[a-z0-9][a-z0-9._-]*")
private val Sha256 = Regex("[0-9a-f]{64}")
private const val StorageBufferBytes = 256L * 1024L * 1024L
const val DefaultQuizModelId = "qwen3-compact"

enum class ModelPurpose(val label: String) {
    QUIZ("Quiz and answer model"),
    EMBEDDING("Document search model"),
}

data class ModelDownloadSpec(
    val id: String,
    val displayName: String,
    val purpose: ModelPurpose,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val minimumRamGb: Int? = null,
    val termsUrl: String? = null,
) {
    init {
        require(SafeModelId.matches(id)) { "Model ID contains unsupported characters." }
        require(displayName.isNotBlank()) { "Model display name must not be blank." }
        require(fileName.endsWith(".litertlm", ignoreCase = true)) {
            "Model file must use the .litertlm extension."
        }
        require(fileName == File(fileName).name) { "Model filename must not contain a path." }
        require(sizeBytes > 0) { "Model size must be greater than zero." }
        require(minimumRamGb == null || minimumRamGb > 0) {
            "Minimum device RAM must be greater than zero."
        }
        termsUrl?.let { requireHttpsUrl(it, "Model terms URL") }
        require(Sha256.matches(sha256)) {
            "Model SHA-256 must be 64 lowercase hexadecimal characters."
        }

        requireHttpsUrl(downloadUrl, "Model download URL")
    }
}

val ModelCatalog = listOf(
    ModelDownloadSpec(
        id = "qwen3-compact",
        displayName = "Qwen 3 Compact",
        purpose = ModelPurpose.QUIZ,
        fileName = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/" +
            "a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76/" +
            "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm?download=true",
        sizeBytes = 344_671_744,
        sha256 = "03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856",
    ),
    ModelDownloadSpec(
        id = "gemma-3-1b-it",
        displayName = "Gemma 3 1B IT",
        purpose = ModelPurpose.QUIZ,
        fileName = "gemma3-1b-it-int4.litertlm",
        downloadUrl = "https://huggingface.co/prathameshchougale/" +
            "saley-gemma-3-1b-it-litertlm/resolve/" +
            "b8a15be0bb0c7eba428f7e5830a40ccb1b662751/" +
            "gemma3-1b-it-int4.litertlm?download=true",
        sizeBytes = 584_417_280,
        sha256 = "1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be",
        minimumRamGb = 6,
        termsUrl = "https://ai.google.dev/gemma/terms",
    ),
    ModelDownloadSpec(
        id = "gemma-4-e2b-it",
        displayName = "Gemma 4 E2B IT",
        purpose = ModelPurpose.QUIZ,
        fileName = "gemma-4-E2B-it.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/" +
            "gemma-4-E2B-it-litert-lm/resolve/" +
            "7fa1d78473894f7e736a21d920c3aa80f950c0db/" +
            "gemma-4-E2B-it.litertlm?download=true",
        sizeBytes = 2_583_085_056,
        sha256 = "ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42",
        minimumRamGb = 8,
    ),
    ModelDownloadSpec(
        id = "granite-embedding-311m-r2",
        displayName = "Granite Embedding 311M R2",
        purpose = ModelPurpose.EMBEDDING,
        fileName = "granite-embedding-311m-r2_wi8fc.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/" +
            "granite-embedding-311m-multilingual-r2/resolve/" +
            "1b10683d630c0d2877bf0430af89668cda7f26c4/" +
            "granite-embedding-311m-r2_wi8fc.litertlm?download=true",
        sizeBytes = 332_365_313,
        sha256 = "beb2be205abc766a670522e651be5713cecb4e4c33e5ef5c30f6a710d4226db5",
    ),
)

val QuizModels = ModelCatalog.filter { it.purpose == ModelPurpose.QUIZ }
val EmbeddingModel = ModelCatalog.single { it.purpose == ModelPurpose.EMBEDDING }
val DefaultQuizModel = QuizModels.single { it.id == DefaultQuizModelId }

sealed interface ModelInstallStatus {
    data object Checking : ModelInstallStatus
    data object Missing : ModelInstallStatus
    data object Installed : ModelInstallStatus
    data object Corrupted : ModelInstallStatus

    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val waitingMessage: String? = null,
    ) : ModelInstallStatus

    data class Paused(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val message: String,
    ) : ModelInstallStatus

    data class Failed(val message: String) : ModelInstallStatus
}

data class ModelInstallation(
    val spec: ModelDownloadSpec,
    val status: ModelInstallStatus,
)

data class ModelInstallationSnapshot(
    val models: List<ModelInstallation>,
    val checked: Boolean,
) {
    val isReady: Boolean
        get() = checked &&
            models.any {
                it.spec.purpose == ModelPurpose.QUIZ && it.status == ModelInstallStatus.Installed
            } &&
            models.any {
                it.spec.purpose == ModelPurpose.EMBEDDING && it.status == ModelInstallStatus.Installed
            }

    val hasActiveDownloads: Boolean
        get() = models.any { it.status is ModelInstallStatus.Downloading }

    val hasBlockingDownloads: Boolean
        get() {
            val quizInstalled = models.any {
                it.spec.purpose == ModelPurpose.QUIZ && it.status == ModelInstallStatus.Installed
            }
            return models.any {
                it.status is ModelInstallStatus.Downloading &&
                    (it.spec.purpose == ModelPurpose.EMBEDDING || !quizInstalled)
            }
        }

    val needsRequiredInstallation: Boolean
        get() = requiredModelsToInstall(this).isNotEmpty()

    companion object {
        fun checking(models: List<ModelDownloadSpec> = ModelCatalog) =
            ModelInstallationSnapshot(
                models = models.map { ModelInstallation(it, ModelInstallStatus.Checking) },
                checked = false,
            )
    }
}

fun requiredModelsToInstall(snapshot: ModelInstallationSnapshot): List<ModelDownloadSpec> {
    if (!snapshot.checked) return emptyList()

    val required = mutableListOf<ModelDownloadSpec>()
    snapshot.models
        .singleOrNull { it.spec.purpose == ModelPurpose.EMBEDDING }
        ?.takeIf { it.status.needsInstall() }
        ?.let { required += it.spec }

    val quizModels = snapshot.models.filter { it.spec.purpose == ModelPurpose.QUIZ }
    val hasInstalledQuiz = quizModels.any { it.status == ModelInstallStatus.Installed }
    val hasDownloadingQuiz = quizModels.any { it.status is ModelInstallStatus.Downloading }
    if (!hasInstalledQuiz && !hasDownloadingQuiz) {
        (quizModels.firstOrNull { it.spec.id == DefaultQuizModel.id } ?: quizModels.firstOrNull())
            ?.takeIf { it.status.needsInstall() }
            ?.let { required += it.spec }
    }
    return required
}

fun resolveSelectedQuizModelId(
    snapshot: ModelInstallationSnapshot,
    preferredModelId: String?,
): String? {
    val installed = snapshot.models.filter {
        it.spec.purpose == ModelPurpose.QUIZ && it.status == ModelInstallStatus.Installed
    }
    return installed.firstOrNull { it.spec.id == preferredModelId }?.spec?.id
        ?: installed.firstOrNull { it.spec.id == DefaultQuizModelId }?.spec?.id
        ?: installed.firstOrNull()?.spec?.id
}

private fun ModelInstallStatus.needsInstall(): Boolean =
    this == ModelInstallStatus.Missing ||
        this == ModelInstallStatus.Corrupted ||
        this is ModelInstallStatus.Paused ||
        this is ModelInstallStatus.Failed

internal fun installationsToStart(
    snapshot: ModelInstallationSnapshot,
    requestedModelIds: Set<String>,
    enqueuedModelIds: Set<String> = emptySet(),
): List<ModelInstallation> = snapshot.models.filter {
    it.spec.id in requestedModelIds &&
        it.status.needsInstall() &&
        (it.spec.id !in enqueuedModelIds || it.status is ModelInstallStatus.Paused)
}

enum class ModelFileVerification {
    VALID,
    MISSING,
    WRONG_SIZE,
    WRONG_SHA256,
    UNREADABLE,
}

class ModelInstallationManager(
    context: Context,
    private val models: List<ModelDownloadSpec> = ModelCatalog,
) {
    private val appContext = context.applicationContext
    private val downloadManager = requireNotNull(
        appContext.getSystemService(DownloadManager::class.java),
    ) { "Android DownloadManager is unavailable." }
    private val verifiedFiles = mutableMapOf<String, Pair<Long, Long>>()
    private val corruptedModels = mutableSetOf<String>()
    private val enqueuedModelIds = mutableSetOf<String>()

    @Synchronized
    fun inspect(): ModelInstallationSnapshot {
        val downloads = queryDownloads()
        return ModelInstallationSnapshot(
            models = models.map { spec -> inspectModel(spec, downloads[spec.downloadUrl]) },
            checked = true,
        )
    }

    @Synchronized
    fun installRequired(): String? = installModels(requiredModelsToInstall(inspect()))

    @Synchronized
    fun install(spec: ModelDownloadSpec): String? = installModels(listOf(spec))

    @Synchronized
    fun delete(spec: ModelDownloadSpec): String? {
        if (models.none { it.id == spec.id }) return "This model is not in the supported catalog."

        queryDownloads()[spec.downloadUrl]?.let { downloadManager.remove(it.id) }
        val files = listOf(modelPendingFile(appContext, spec), modelFinalFile(appContext, spec))
        val failed = files.map { it.exists() && !it.delete() }.any { it }
        verifiedFiles.remove(spec.id)
        corruptedModels.remove(spec.id)
        enqueuedModelIds.remove(spec.id)
        return if (failed) "Could not remove ${spec.displayName}." else null
    }

    private fun installModels(requestedModels: List<ModelDownloadSpec>): String? {
        val requestedIds = requestedModels.map { it.id }.toSet()
        if (requestedIds.any { requestedId -> models.none { it.id == requestedId } }) {
            return "One or more requested models are not in the supported catalog."
        }

        val snapshot = inspect()
        val toInstall = installationsToStart(snapshot, requestedIds, enqueuedModelIds)
        if (toInstall.isEmpty()) return null

        val memoryConstrainedModel = toInstall.firstOrNull { installation ->
            installation.spec.minimumRamGb?.let { minimumRamGb ->
                val activityManager = appContext.getSystemService(ActivityManager::class.java)
                    ?: return "Could not check device memory."
                val memoryInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                !hasRequiredDeviceMemory(memoryInfo.totalMem, minimumRamGb)
            } ?: false
        }
        if (memoryConstrainedModel != null) {
            return "${memoryConstrainedModel.spec.displayName} requires at least " +
                "${memoryConstrainedModel.spec.minimumRamGb} GB of device RAM."
        }

        val externalFilesDir = appContext.getExternalFilesDir(null)
            ?: return "App-specific storage is unavailable."
        val requiredBytes = toInstall.sumOf { it.spec.sizeBytes } + StorageBufferBytes
        val availableBytes = runCatching { StatFs(externalFilesDir.absolutePath).availableBytes }
            .getOrElse { return "Could not check available storage." }
        if (availableBytes < requiredBytes) {
            return "Not enough storage. Free at least ${formatMegabytes(requiredBytes)} and try again."
        }

        val downloads = queryDownloads()
        return runCatching {
            toInstall.forEach { installation ->
                val spec = installation.spec
                downloads[spec.downloadUrl]?.let { downloadManager.remove(it.id) }
                modelPendingFile(appContext, spec).delete()
                modelFinalFile(appContext, spec).delete()
                verifiedFiles.remove(spec.id)
                corruptedModels.remove(spec.id)
                enqueueModelDownload(appContext, spec)
                enqueuedModelIds.add(spec.id)
            }
        }.exceptionOrNull()?.let { "Could not start the model download: ${it.message ?: "unknown error"}" }
    }

    private fun inspectModel(
        spec: ModelDownloadSpec,
        download: DownloadRecord?,
    ): ModelInstallation {
        val finalFile = modelFinalFile(appContext, spec)
        if (finalFile.exists()) {
            val cached = verifiedFiles[spec.id]
            if (cached == (finalFile.length() to finalFile.lastModified())) {
                enqueuedModelIds.remove(spec.id)
                return ModelInstallation(spec, ModelInstallStatus.Installed)
            }

            if (verifyModelFile(finalFile, spec) == ModelFileVerification.VALID) {
                verifiedFiles[spec.id] = finalFile.length() to finalFile.lastModified()
                corruptedModels.remove(spec.id)
                enqueuedModelIds.remove(spec.id)
                return ModelInstallation(spec, ModelInstallStatus.Installed)
            }

            finalFile.delete()
            verifiedFiles.remove(spec.id)
            corruptedModels.add(spec.id)
            enqueuedModelIds.remove(spec.id)
            return ModelInstallation(spec, ModelInstallStatus.Corrupted)
        }

        if (spec.id in corruptedModels) {
            return ModelInstallation(spec, ModelInstallStatus.Corrupted)
        }

        val pendingFile = modelPendingFile(appContext, spec)
        if (download == null) {
            if (spec.id in enqueuedModelIds) {
                return ModelInstallation(
                    spec,
                    ModelInstallStatus.Downloading(
                        downloadedBytes = 0,
                        totalBytes = spec.sizeBytes,
                        waitingMessage = "Starting download",
                    ),
                )
            }
            pendingFile.delete()
            return ModelInstallation(spec, ModelInstallStatus.Missing)
        }

        return when (download.status) {
            DownloadManager.STATUS_PENDING,
            DownloadManager.STATUS_RUNNING,
            -> ModelInstallation(
                spec,
                ModelInstallStatus.Downloading(
                    downloadedBytes = download.downloadedBytes.coerceAtLeast(0),
                    totalBytes = if (download.totalBytes > 0) download.totalBytes else spec.sizeBytes,
                ),
            )

            DownloadManager.STATUS_PAUSED -> ModelInstallation(
                spec,
                ModelInstallStatus.Paused(
                    downloadedBytes = download.downloadedBytes.coerceAtLeast(0),
                    totalBytes = if (download.totalBytes > 0) download.totalBytes else spec.sizeBytes,
                    message = pausedMessage(download.reason),
                ),
            )

            DownloadManager.STATUS_SUCCESSFUL -> finishDownload(spec, pendingFile)
            DownloadManager.STATUS_FAILED -> {
                pendingFile.delete()
                enqueuedModelIds.remove(spec.id)
                ModelInstallation(spec, ModelInstallStatus.Failed(downloadFailureMessage(download.reason)))
            }

            else -> ModelInstallation(spec, ModelInstallStatus.Failed("Unknown download state."))
        }
    }

    private fun finishDownload(
        spec: ModelDownloadSpec,
        pendingFile: File,
    ): ModelInstallation {
        if (verifyModelFile(pendingFile, spec) != ModelFileVerification.VALID) {
            pendingFile.delete()
            corruptedModels.add(spec.id)
            enqueuedModelIds.remove(spec.id)
            return ModelInstallation(spec, ModelInstallStatus.Corrupted)
        }

        val finalFile = modelFinalFile(appContext, spec)
        val finalDirectory = requireNotNull(finalFile.parentFile)
        if (!finalDirectory.isDirectory && !finalDirectory.mkdirs()) {
            return ModelInstallation(spec, ModelInstallStatus.Failed("Could not create the model folder."))
        }

        val moved = runCatching {
            Files.move(
                pendingFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.recoverCatching {
            Files.move(
                pendingFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.isSuccess

        if (!moved) {
            return ModelInstallation(spec, ModelInstallStatus.Failed("Could not finish installing the model."))
        }

        verifiedFiles[spec.id] = finalFile.length() to finalFile.lastModified()
        corruptedModels.remove(spec.id)
        enqueuedModelIds.remove(spec.id)
        return ModelInstallation(spec, ModelInstallStatus.Installed)
    }

    private fun queryDownloads(): Map<String, DownloadRecord> {
        val downloads = mutableMapOf<String, DownloadRecord>()
        downloadManager.query(DownloadManager.Query()).use { cursor ->
            while (cursor.moveToNext()) {
                val record = cursor.toDownloadRecord()
                val previous = downloads[record.url]
                if (previous == null || record.id > previous.id) downloads[record.url] = record
            }
        }
        return downloads
    }
}

data class EnqueuedModelDownload(
    val downloadId: Long,
    val pendingFile: File,
)

fun enqueueModelDownload(
    context: Context,
    spec: ModelDownloadSpec,
    allowMetered: Boolean = false,
): EnqueuedModelDownload {
    val pendingRelativePath = modelPendingRelativePath(spec)
    val externalFilesDir = requireNotNull(context.getExternalFilesDir(null)) {
        "App-specific external storage is unavailable."
    }
    val pendingFile = File(externalFilesDir, pendingRelativePath)
    val pendingDirectory = requireNotNull(pendingFile.parentFile)

    require(pendingDirectory.isDirectory || pendingDirectory.mkdirs()) {
        "Could not create the model download directory."
    }
    require(!pendingFile.exists()) {
        "A pending download already exists for model ${spec.id}."
    }

    val request = DownloadManager.Request(Uri.parse(spec.downloadUrl))
        .setTitle(spec.displayName)
        .setDescription("Downloading offline AI model")
        .setMimeType("application/octet-stream")
        .setAllowedOverMetered(allowMetered)
        .setAllowedOverRoaming(false)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        .setDestinationInExternalFilesDir(context, null, pendingRelativePath)

    val downloadManager = requireNotNull(context.getSystemService(DownloadManager::class.java)) {
        "Android DownloadManager is unavailable."
    }
    return EnqueuedModelDownload(downloadManager.enqueue(request), pendingFile)
}

fun verifyModelFile(file: File, spec: ModelDownloadSpec): ModelFileVerification {
    if (!file.isFile) return ModelFileVerification.MISSING
    if (file.length() != spec.sizeBytes) return ModelFileVerification.WRONG_SIZE

    return runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        if (actual == spec.sha256) {
            ModelFileVerification.VALID
        } else {
            ModelFileVerification.WRONG_SHA256
        }
    }.getOrDefault(ModelFileVerification.UNREADABLE)
}

fun modelFinalFile(context: Context, spec: ModelDownloadSpec): File =
    File(requireNotNull(context.getExternalFilesDir(null)), modelFinalRelativePath(spec))

internal fun modelPendingFile(context: Context, spec: ModelDownloadSpec): File =
    File(requireNotNull(context.getExternalFilesDir(null)), modelPendingRelativePath(spec))

internal fun modelPendingRelativePath(spec: ModelDownloadSpec): String =
    "models/pending/${spec.id}/${spec.fileName}.download"

internal fun modelFinalRelativePath(spec: ModelDownloadSpec): String =
    "models/${spec.id}/${spec.fileName}"

private data class DownloadRecord(
    val id: Long,
    val url: String,
    val status: Int,
    val reason: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
)

private fun Cursor.toDownloadRecord() = DownloadRecord(
    id = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_ID)),
    url = getString(getColumnIndexOrThrow(DownloadManager.COLUMN_URI)),
    status = getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
    reason = getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
    downloadedBytes = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
    totalBytes = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
)

private fun pausedMessage(reason: Int): String =
    when (reason) {
        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Waiting for Wi-Fi"
        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for network"
        DownloadManager.PAUSED_WAITING_TO_RETRY -> "Waiting to retry"
        else -> "Download paused"
    }

private fun downloadFailureMessage(reason: Int): String = when (reason) {
    DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage. Free space and retry."
    DownloadManager.ERROR_CANNOT_RESUME -> "Download could not resume. Retry to start again."
    DownloadManager.ERROR_HTTP_DATA_ERROR,
    DownloadManager.ERROR_UNHANDLED_HTTP_CODE,
    -> "The model host returned a download error. Retry when online."
    DownloadManager.ERROR_DEVICE_NOT_FOUND -> "App-specific storage is unavailable."
    DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "A stale model file already exists. Retry installation."
    else -> "Download failed. Retry when the connection is stable."
}

private fun formatMegabytes(bytes: Long): String = "${(bytes + 999_999) / 1_000_000} MB"

internal fun hasRequiredDeviceMemory(totalMemoryBytes: Long, minimumRamGb: Int): Boolean =
    totalMemoryBytes >= minimumRamGb * 1_000_000_000L

private fun requireHttpsUrl(url: String, label: String) {
    val uri = runCatching { URI(url) }
        .getOrElse { throw IllegalArgumentException("$label is invalid.", it) }
    require(uri.scheme == "https" && !uri.host.isNullOrBlank()) {
        "$label must be an absolute HTTPS URL."
    }
}
