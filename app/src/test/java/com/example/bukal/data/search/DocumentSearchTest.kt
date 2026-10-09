package com.example.bukal.data.search

import com.example.bukal.data.local.EmbeddingVectorCodec
import com.example.bukal.data.local.SearchCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentSearchTest {
    @Test
    fun ranksClosestEmbeddingFirstAndHonorsLimit() {
        val results = rankSearchCandidates(
            queryVector = floatArrayOf(1f, 0f),
            candidates = listOf(
                candidate(id = 1, vector = floatArrayOf(0f, 1f)),
                candidate(id = 2, vector = floatArrayOf(1f, 0f)),
                candidate(id = 3, vector = floatArrayOf(0.8f, 0.2f)),
            ),
            limit = 2,
        )

        assertEquals(listOf(2L, 3L), results.map(DocumentSearchResult::passageId))
    }

    private fun candidate(id: Long, vector: FloatArray) = SearchCandidate(
        chunkId = id,
        passageId = id,
        materialName = "Material $id",
        sourceId = "TXT-P00$id",
        chunkContent = "Chunk $id",
        embeddingDimensions = vector.size,
        embeddingVector = EmbeddingVectorCodec.encode(vector),
    )
}
