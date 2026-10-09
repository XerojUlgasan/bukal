package com.example.bukal.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class SearchChunkDao {
    @Query("SELECT * FROM passages WHERE id = :passageId")
    protected abstract suspend fun getPassageRow(passageId: Long): PassageEntity?

    @Query("DELETE FROM search_chunks WHERE passage_id = :passageId")
    protected abstract suspend fun deleteForPassage(passageId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRows(chunks: List<SearchChunkEntity>)

    @Query(
        """
        UPDATE search_chunks
        SET embedding_model_id = :modelId,
            embedding_dimensions = :dimensions,
            embedding_vector = :vector
        WHERE id = :chunkId
        """,
    )
    protected abstract suspend fun updateEmbeddingRow(
        chunkId: Long,
        modelId: String,
        dimensions: Int,
        vector: ByteArray,
    ): Int

    @Transaction
    open suspend fun replaceForPassage(
        passageId: Long,
        chunks: List<SearchChunkEntity>,
    ) {
        val passage = requireNotNull(getPassageRow(passageId)) { "Passage $passageId does not exist" }
        DatabaseValidation.validateChunks(passage, chunks)
        deleteForPassage(passageId)
        insertRows(chunks.map { it.copy(id = 0, passageId = passageId) })
    }

    open suspend fun setEmbedding(
        chunkId: Long,
        modelId: String,
        vector: FloatArray,
    ) {
        require(modelId.isNotBlank()) { "Embedding model ID must not be blank" }
        val updated = updateEmbeddingRow(
            chunkId = chunkId,
            modelId = modelId,
            dimensions = vector.size,
            vector = EmbeddingVectorCodec.encode(vector),
        )
        require(updated == 1) { "Search chunk $chunkId does not exist" }
    }

    @Query(
        """
        UPDATE search_chunks
        SET embedding_model_id = NULL,
            embedding_dimensions = NULL,
            embedding_vector = NULL
        WHERE passage_id = :passageId
        """,
    )
    abstract suspend fun clearEmbeddings(passageId: Long)

    @Query(
        """
        SELECT * FROM search_chunks
        WHERE passage_id = :passageId
        ORDER BY chunk_index
        """,
    )
    abstract suspend fun getForPassage(passageId: Long): List<SearchChunkEntity>

    @Query(
        """
        SELECT * FROM search_chunks
        WHERE embedding_model_id IS NULL
        ORDER BY passage_id, chunk_index
        LIMIT :limit
        """,
    )
    abstract suspend fun getPending(limit: Int): List<SearchChunkEntity>

    @Query("SELECT COUNT(*) FROM search_chunks WHERE embedding_model_id IS NULL")
    abstract suspend fun getPendingCount(): Int

    @Query(
        """
        SELECT
            search_chunks.id AS chunk_id,
            passages.id AS passage_id,
            materials.display_name AS material_name,
            passages.source_id AS source_id,
            search_chunks.content AS chunk_content,
            search_chunks.embedding_dimensions AS embedding_dimensions,
            search_chunks.embedding_vector AS embedding_vector
        FROM search_chunks
        JOIN passages ON passages.id = search_chunks.passage_id
        JOIN materials ON materials.id = passages.material_id
        WHERE search_chunks.embedding_model_id = :modelId
          AND search_chunks.embedding_dimensions = :dimensions
          AND search_chunks.embedding_vector IS NOT NULL
        ORDER BY search_chunks.id
        """,
    )
    abstract suspend fun getSearchCandidates(
        modelId: String,
        dimensions: Int,
    ): List<SearchCandidate>

    @Query(
        """
        SELECT
            search_chunks.id AS chunk_id,
            passages.id AS passage_id,
            materials.display_name AS material_name,
            passages.source_id AS source_id,
            search_chunks.content AS chunk_content,
            search_chunks.embedding_dimensions AS embedding_dimensions,
            search_chunks.embedding_vector AS embedding_vector
        FROM search_chunks
        JOIN passages ON passages.id = search_chunks.passage_id
        JOIN materials ON materials.id = passages.material_id
        WHERE search_chunks.embedding_model_id = :modelId
          AND search_chunks.embedding_dimensions = :dimensions
          AND search_chunks.embedding_vector IS NOT NULL
          AND search_chunks.passage_id = :passageId
        ORDER BY search_chunks.id
        """,
    )
    abstract suspend fun getSearchCandidatesForPassage(
        modelId: String,
        dimensions: Int,
        passageId: Long,
    ): List<SearchCandidate>
}
