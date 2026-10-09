package com.example.bukal.data.search

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bukal.data.local.BukalDatabase
import com.example.bukal.data.local.DocumentFormats
import com.example.bukal.data.local.ImportedMaterial
import com.example.bukal.data.local.MaterialEntity
import com.example.bukal.data.local.PassageEntity
import com.example.bukal.data.local.SearchChunkEntity
import com.example.bukal.data.model.EmbeddingModel
import com.example.bukal.data.model.ModelFileVerification
import com.example.bukal.data.model.modelFinalFile
import com.example.bukal.data.model.verifyModelFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GraniteEmbeddingIndexerDeviceTest {
    @Test
    fun verifiedGraniteModelEmbedsAndPersistsOneChunk() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = EmbeddingModel
        assumeTrue(verifyModelFile(modelFinalFile(context, model), model) == ModelFileVerification.VALID)

        val database = Room.inMemoryDatabaseBuilder(context, BukalDatabase::class.java).build()
        try {
            val materialId = database.materialDao().insert(
                ImportedMaterial(
                    material = MaterialEntity(
                        displayName = "Embedding device test",
                        documentFormat = DocumentFormats.TXT,
                        mimeType = "text/plain",
                        retainedFilePath = "device-test.txt",
                        importedAtEpochMs = 1,
                    ),
                    passages = listOf(
                        PassageEntity(
                            materialId = 0,
                            sourceId = "TXT-P001",
                            position = 0,
                            content = "The Philippines is an archipelago in Southeast Asia.",
                        ),
                    ),
                ),
            )
            val passage = database.materialDao().getPassages(materialId).single()
            database.searchChunkDao().replaceForPassage(
                passage.id,
                listOf(
                    SearchChunkEntity(
                        passageId = passage.id,
                        chunkIndex = 0,
                        startOffset = 0,
                        endOffset = passage.content.length,
                        content = passage.content,
                    ),
                ),
            )

            val embeddingRunner = GraniteEmbeddingRunner(context)
            val result = try {
                GraniteEmbeddingIndexer(database, embeddingRunner).indexPending()
            } finally {
                embeddingRunner.close()
            }

            assertEquals(1, result.indexed)
            assertEquals(0, result.failed)
            assertEquals(0, result.pending)
            val candidate = database.searchChunkDao()
                .getSearchCandidates(model.id, GraniteEmbeddingIndexer.EXPECTED_DIMENSIONS)
                .single()
            assertEquals(GraniteEmbeddingIndexer.EXPECTED_DIMENSIONS, candidate.embeddingDimensions)
            assertTrue(candidate.embeddingVector.isNotEmpty())
        } finally {
            database.close()
        }
    }
}
