package com.example.bukal.data.search

import com.example.bukal.data.local.BukalDatabase
import com.example.bukal.data.local.SearchChunkEntity
import com.example.bukal.data.model.EmbeddingModel
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EmbeddingIndexResult(
    val attempted: Int,
    val indexed: Int,
    val failed: Int,
    val pending: Int,
)

class GraniteEmbeddingIndexer(
    private val database: BukalDatabase,
    private val embeddingRunner: GraniteEmbeddingRunner,
) {
    private val model = EmbeddingModel

    suspend fun indexPending(): EmbeddingIndexResult = withContext(Dispatchers.Default) {
        val attemptedIds = mutableSetOf<Long>()
        var indexed = 0
        var failed = 0

        while (true) {
            val chunks = database.searchChunkDao()
                .getPending(Int.MAX_VALUE)
                .filterNot { it.id in attemptedIds }
            if (chunks.isEmpty()) break
            attemptedIds += chunks.map(SearchChunkEntity::id)

            val result = indexChunksWithRetry(
                chunks = chunks,
                embed = embeddingRunner::embed,
                persist = { chunk, vector ->
                    database.searchChunkDao().setEmbedding(chunk.id, model.id, vector)
                },
            )
            indexed += result.indexed
            failed += result.failed
        }

        EmbeddingIndexResult(
            attempted = attemptedIds.size,
            indexed = indexed,
            failed = failed,
            pending = database.searchChunkDao().getPendingCount(),
        )
    }

    companion object {
        const val EXPECTED_DIMENSIONS = 768
        const val MAX_ATTEMPTS_PER_CHUNK = 2
    }
}

internal data class ChunkIndexAttempt(
    val indexed: Int,
    val failed: Int,
)

internal suspend fun indexChunksWithRetry(
    chunks: List<SearchChunkEntity>,
    embed: suspend (String) -> FloatArray,
    persist: suspend (SearchChunkEntity, FloatArray) -> Unit,
    maxAttempts: Int = GraniteEmbeddingIndexer.MAX_ATTEMPTS_PER_CHUNK,
): ChunkIndexAttempt {
    require(maxAttempts > 0) { "Embedding attempts must be positive." }
    var indexed = 0
    var failed = 0

    chunks.forEach { chunk ->
        var success = false
        for (attempt in 0 until maxAttempts) {
            try {
                val vector = embed(chunk.content)
                validateGraniteEmbedding(vector)
                persist(chunk, vector)
                success = true
                break
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The row stays pending; the next attempt or a later manual retry can resume it.
            }
        }
        if (success) indexed += 1 else failed += 1
    }

    return ChunkIndexAttempt(indexed = indexed, failed = failed)
}

internal fun validateGraniteEmbedding(vector: FloatArray) {
    require(vector.size == GraniteEmbeddingIndexer.EXPECTED_DIMENSIONS) {
        "Expected ${GraniteEmbeddingIndexer.EXPECTED_DIMENSIONS} embedding dimensions but found ${vector.size}."
    }
    require(vector.all(Float::isFinite)) { "Embedding contains non-finite values." }
    val magnitude = sqrt(vector.sumOf { value -> value.toDouble() * value.toDouble() })
    require(magnitude > 0.0) { "Embedding has zero magnitude." }
    require(abs(magnitude - 1.0) <= NORMALIZATION_TOLERANCE) {
        "Embedding is not L2-normalized."
    }
}

private const val NORMALIZATION_TOLERANCE = 0.02
