package com.example.bukal.data.importing

import com.example.bukal.data.local.PassageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextProcessingTest {
    @Test
    fun normalizesLineEndingsWhitespaceAndRepeatedBlankLines() {
        val input = "  First   line\r\n\r\n\r\nSecond\tline  \rThird"

        assertEquals("First line\n\nSecond line\nThird", ExtractedTextNormalizer.normalize(input))
    }

    @Test
    fun passagesAreBoundedAndHaveStableSequentialSourceIds() {
        val paragraph = (1..900).joinToString(" ") { "word$it" }
        val sections = listOf(
            ExtractedSection(text = "Short heading\n\nA short paragraph."),
            ExtractedSection(title = "Page 2", text = paragraph),
        )

        val passages = PassageBuilder(maxCharacters = 500).build(SupportedDocumentType.PDF, sections)

        assertTrue(passages.size > 2)
        assertEquals(passages.indices.toList(), passages.map { it.position })
        assertEquals("PDF-P001", passages.first().sourceId)
        assertEquals("PDF-P${passages.size.toString().padStart(3, '0')}", passages.last().sourceId)
        assertTrue(passages.all { it.content.length <= 500 && it.content.isNotBlank() })
    }

    @Test
    fun everyAdjacentSearchChunkOverlapsAndOffsetsReproduceContent() {
        val content = (1..600).joinToString(" ") { "token$it" }
        val passage = PassageEntity(
            id = 42,
            materialId = 7,
            sourceId = "TXT-P001",
            position = 0,
            content = content,
        )

        val chunks = OverlappingSearchChunker().split(passage)

        assertTrue(chunks.size > 2)
        assertEquals(0, chunks.first().startOffset)
        assertEquals(content.length, chunks.last().endOffset)
        chunks.forEachIndexed { index, chunk ->
            assertEquals(index, chunk.chunkIndex)
            assertEquals(content.substring(chunk.startOffset, chunk.endOffset), chunk.content)
            assertTrue(chunk.content.length <= SearchChunkPolicy.DEFAULT_MAX_CHUNK_CHARACTERS)
        }
        chunks.zipWithNext().forEach { (previous, next) ->
            assertTrue(next.startOffset < previous.endOffset)
            assertTrue(
                previous.endOffset - next.startOffset >=
                    SearchChunkPolicy.DEFAULT_CHUNK_OVERLAP_CHARACTERS,
            )
            assertTrue(next.startOffset > previous.startOffset)
            assertTrue(next.endOffset > previous.endOffset)
        }
    }

    @Test
    fun longUnbrokenTextStillAdvancesByMostOfTheChunkSize() {
        val passage = PassageEntity(
            id = 1,
            materialId = 1,
            sourceId = "TXT-P001",
            position = 0,
            content = "a".repeat(2_000),
        )

        val chunks = OverlappingSearchChunker(
            SearchChunkPolicy(maxCharacters = 500, overlapCharacters = 100),
        ).split(passage)

        assertTrue(chunks.size < 10)
        chunks.zipWithNext().forEach { (previous, next) ->
            assertTrue(next.startOffset in (previous.startOffset + 300)..<previous.endOffset)
        }
    }
}
