package com.example.bukal.data.local

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EmbeddingVectorCodecTest {
    @Test
    fun encodeAndDecodePreserveValues() {
        val vector = floatArrayOf(0.25f, -1.5f, 3.75f)

        val decoded = EmbeddingVectorCodec.decode(
            bytes = EmbeddingVectorCodec.encode(vector),
            dimensions = vector.size,
        )

        assertArrayEquals(vector, decoded, 0f)
    }

    @Test
    fun decodeRejectsWrongByteLength() {
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingVectorCodec.decode(ByteArray(4), dimensions = 2)
        }
    }

    @Test
    fun cosineSimilarityRanksEquivalentVectorsAtOne() {
        val similarity = EmbeddingVectorCodec.cosineSimilarity(
            left = floatArrayOf(1f, 2f, 3f),
            right = floatArrayOf(2f, 4f, 6f),
        )

        assertEquals(1.0, similarity, 0.000_001)
    }
}
