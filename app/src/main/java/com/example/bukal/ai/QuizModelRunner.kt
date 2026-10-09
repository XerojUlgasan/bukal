package com.example.bukal.ai

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.example.bukal.data.model.ModelDownloadSpec
import com.example.bukal.data.model.ModelFileVerification
import com.example.bukal.data.model.ModelPurpose
import com.example.bukal.data.model.modelFinalFile
import com.example.bukal.data.model.verifyModelFile
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class QuizModelRunner(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val isDebuggable =
        appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var loadedModelId: String? = null

    suspend fun complete(
        model: ModelDownloadSpec,
        systemInstruction: String,
        request: String,
    ): String = mutex.withLock {
        withContext(Dispatchers.Default) {
            logDebug("Quiz model", "${model.id} (${model.fileName})")
            logDebug("System instruction sent to AI", systemInstruction)
            logDebug("Request sent to AI", request)

            val activeEngine = requireEngine(model)
            activeEngine.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(systemInstruction),
                    maxOutputToken = MAX_OUTPUT_TOKENS,
                    thinkingConfig = ThinkingConfig(enableThinking = false),
                ),
            ).use { conversation ->
                val rawResponse = conversation.sendMessage(request)
                    .contents
                    .contents
                    .filterIsInstance<Content.Text>()
                    .joinToString(separator = "") { it.text }
                logDebug("Raw response returned by AI", rawResponse)
                rawResponse.trim()
                    .ifEmpty { error("The local model returned no text.") }
            }
        }
    }

    private fun logDebug(label: String, value: String) {
        if (!isDebuggable) return

        val chunks = value.chunked(LOG_CHUNK_SIZE).ifEmpty { listOf("<empty>") }
        chunks.forEachIndexed { index, chunk ->
            Log.d(LOG_TAG, "$label [${index + 1}/${chunks.size}]\n$chunk")
        }
    }

    internal fun logValidationFailure(message: String) {
        logDebug("Quiz response rejected", message)
    }

    suspend fun release() = mutex.withLock {
        withContext(Dispatchers.Default) {
            closeEngine()
        }
    }

    private fun requireEngine(model: ModelDownloadSpec): Engine {
        require(model.purpose == ModelPurpose.QUIZ) { "Only quiz models can generate questions." }
        if (loadedModelId == model.id && engine?.isInitialized() == true) return requireNotNull(engine)

        closeEngine()
        val modelFile = requireVerifiedModel(model)
        val cacheDirectory = File(appContext.cacheDir, "litertlm").apply { mkdirs() }
        require(cacheDirectory.isDirectory) { "Could not prepare the quiz model cache." }
        return Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = cacheDirectory.absolutePath,
            ),
        ).also {
            it.initialize()
            engine = it
            loadedModelId = model.id
        }
    }

    private fun requireVerifiedModel(model: ModelDownloadSpec): File {
        val file = modelFinalFile(appContext, model)
        require(verifyModelFile(file, model) == ModelFileVerification.VALID) {
            "${model.displayName} is missing or failed verification. Reinstall it and retry."
        }
        return file
    }

    override fun close() {
        closeEngine()
    }

    private fun closeEngine() {
        engine?.let { active ->
            if (active.isInitialized()) runCatching { active.close() }
        }
        engine = null
        loadedModelId = null
    }

    companion object {
        private const val MAX_OUTPUT_TOKENS = 2_048
        private const val LOG_TAG = "BukalQuizAI"
        private const val LOG_CHUNK_SIZE = 3_000
    }
}
