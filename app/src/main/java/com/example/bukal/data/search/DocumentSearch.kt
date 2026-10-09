package com.example.bukal.data.search

import com.example.bukal.data.local.BukalDatabase
import com.example.bukal.data.local.EmbeddingVectorCodec
import com.example.bukal.data.local.SearchCandidate
import com.example.bukal.data.model.EmbeddingModel

data class DocumentSearchResult(
    val chunkId: Long,
    val passageId: Long,
    val materialName: String,
    val sourceId: String,
    val text: String,
    val similarity: Double,
)

class DocumentSearch(
    private val database: BukalDatabase,
    private val embeddingRunner: GraniteEmbeddingRunner,
) {
    suspend fun search(
        query: String,
        limit: Int = DEFAULT_RESULT_LIMIT,
        passageId: Long? = null,
    ): List<DocumentSearchResult> {
        require(query.isNotBlank()) { "Enter a search phrase." }
        require(limit > 0) { "Search result limit must be positive." }
        val queryVector = embeddingRunner.embed(query)
        val candidates = if (passageId == null) {
            database.searchChunkDao().getSearchCandidates(
                modelId = EmbeddingModel.id,
                dimensions = queryVector.size,
            )
        } else {
            database.searchChunkDao().getSearchCandidatesForPassage(
                modelId = EmbeddingModel.id,
                dimensions = queryVector.size,
                passageId = passageId,
            )
        }
        return rankSearchCandidates(queryVector, candidates, limit)
    }

    companion object {
        const val DEFAULT_RESULT_LIMIT = 5
    }
}

internal fun rankSearchCandidates(
    queryVector: FloatArray,
    candidates: List<SearchCandidate>,
    limit: Int,
): List<DocumentSearchResult> {
    require(limit > 0) { "Search result limit must be positive." }
    return candidates
        .map { candidate ->
            val vector = EmbeddingVectorCodec.decode(
                candidate.embeddingVector,
                candidate.embeddingDimensions,
            )
            DocumentSearchResult(
                chunkId = candidate.chunkId,
                passageId = candidate.passageId,
                materialName = candidate.materialName,
                sourceId = candidate.sourceId,
                text = candidate.chunkContent,
                similarity = EmbeddingVectorCodec.cosineSimilarity(queryVector, vector),
            )
        }
        .sortedByDescending(DocumentSearchResult::similarity)
        .take(limit)
}
