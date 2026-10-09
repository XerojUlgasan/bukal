package com.example.bukal.data.search

import android.content.Context
import com.example.bukal.data.model.EmbeddingModel
import com.example.bukal.data.model.ModelFileVerification
import com.example.bukal.data.model.modelFinalFile
import com.example.bukal.data.model.verifyModelFile
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.InputData
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class GraniteEmbeddingRunner(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var engine: EmbeddingEngine? = null

    suspend fun embed(text: String): FloatArray {
        require(text.isNotBlank()) { "Text to embed must not be blank." }
        return mutex.withLock {
            withContext(Dispatchers.Default) {
                val vector = requireEngine()
                    .computeEmbeddingAsync(listOf(InputData.Text(text.trim())))
                    .embedding
                validateGraniteEmbedding(vector)
                vector
            }
        }
    }

    suspend fun release() = mutex.withLock {
        withContext(Dispatchers.Default) {
            closeEngine()
        }
    }

    private fun requireEngine(): EmbeddingEngine {
        engine?.takeIf { it.isInitialized() }?.let { return it }
        val modelFile = modelFinalFile(appContext, EmbeddingModel)
        require(verifyModelFile(modelFile, EmbeddingModel) == ModelFileVerification.VALID) {
            "The embedding model is missing or failed verification. Reinstall it and retry."
        }
        val cacheDirectory = File(appContext.cacheDir, "litertlm").apply { mkdirs() }
        require(cacheDirectory.isDirectory) { "Could not prepare the embedding model cache." }
        return EmbeddingEngine(
            EmbeddingEngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = cacheDirectory.absolutePath,
            ),
        ).also {
            it.initialize()
            engine = it
        }
    }

    override fun close() {
        closeEngine()
    }

    private fun closeEngine() {
        engine?.let { active ->
            if (active.isInitialized()) runCatching { active.close() }
        }
        engine = null
    }
}
