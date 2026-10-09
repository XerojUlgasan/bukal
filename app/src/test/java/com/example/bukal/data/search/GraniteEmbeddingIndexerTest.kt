package com.example.bukal.data.search

import com.example.bukal.data.local.SearchChunkEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GraniteEmbeddingIndexerTest {
    @Test
    fun acceptsExpectedNormalizedEmbedding() {
        validateGraniteEmbedding(validVector())
    }

    @Test
    fun rejectsWrongDimensionsNonFiniteAndNonNormalizedEmbeddings() {
        assertThrows(IllegalArgumentException::class.java) {
            validateGraniteEmbedding(floatArrayOf(1f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateGraniteEmbedding(validVector().apply { this[0] = Float.NaN })
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateGraniteEmbedding(FloatArray(GraniteEmbeddingIndexer.EXPECTED_DIMENSIONS))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateGraniteEmbedding(FloatArray(GraniteEmbeddingIndexer.EXPECTED_DIMENSIONS) { 1f })
        }
    }

    @Test
    fun retriesOnceThenPersistsAValidEmbedding() = runBlocking {
        var embeddingAttempts = 0
        var persisted = 0

        val result = indexChunksWithRetry(
            chunks = listOf(chunk()),
            embed = {
                embeddingAttempts += 1
                if (embeddingAttempts == 1) error("Temporary model failure")
                validVector()
            },
            persist = { _, _ -> persisted += 1 },
        )

        assertEquals(2, embeddingAttempts)
        assertEquals(1, persisted)
        assertEquals(1, result.indexed)
        assertEquals(0, result.failed)
    }

    @Test
    fun leavesChunkUnpersistedAfterRetryIsExhausted() = runBlocking {
        var embeddingAttempts = 0
        var persisted = 0

        val result = indexChunksWithRetry(
            chunks = listOf(chunk()),
            embed = {
                embeddingAttempts += 1
                error("Model failure")
            },
            persist = { _, _ -> persisted += 1 },
        )

        assertEquals(GraniteEmbeddingIndexer.MAX_ATTEMPTS_PER_CHUNK, embeddingAttempts)
        assertEquals(0, persisted)
        assertEquals(0, result.indexed)
        assertEquals(1, result.failed)
    }

    private fun validVector() = FloatArray(GraniteEmbeddingIndexer.EXPECTED_DIMENSIONS).apply {
        this[0] = 1f
    }

    private fun chunk() = SearchChunkEntity(
        id = 1,
        passageId = 1,
        chunkIndex = 0,
        startOffset = 0,
        endOffset = 4,
        content = "Text",
    )
}
