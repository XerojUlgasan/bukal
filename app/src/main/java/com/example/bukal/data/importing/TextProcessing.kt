package com.example.bukal.data.importing

import com.example.bukal.data.local.PassageEntity
import com.example.bukal.data.local.SearchChunkEntity

object ExtractedTextNormalizer {
    fun normalize(text: String): String = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace('\u000C', '\n')
        .replace('\u00A0', ' ')
        .lines()
        .map { line -> line.trim().replace(INLINE_WHITESPACE, " ") }
        .joinToString("\n")
        .replace(EXCESS_BLANK_LINES, "\n\n")
        .trim()

    private val INLINE_WHITESPACE = Regex("[\\t ]+")
    private val EXCESS_BLANK_LINES = Regex("\\n{3,}")
}

class PassageBuilder(
    private val maxCharacters: Int = DEFAULT_MAX_PASSAGE_CHARACTERS,
) {
    init {
        require(maxCharacters > 0) { "Passage size must be positive" }
    }

    fun build(
        type: SupportedDocumentType,
        sections: List<ExtractedSection>,
    ): List<PassageEntity> {
        val drafts = sections.flatMap { section ->
            buildSection(section)
        }
        require(drafts.isNotEmpty()) { "The document must contain readable text" }

        return drafts.mapIndexed { index, draft ->
            PassageEntity(
                materialId = 0,
                sourceId = "${type.documentFormat.uppercase()}-P${(index + 1).toString().padStart(3, '0')}",
                position = index,
                title = draft.title ?: inferTitle(draft.content, index),
                content = draft.content,
            )
        }
    }

    private fun buildSection(section: ExtractedSection): List<PassageDraft> {
        val normalized = ExtractedTextNormalizer.normalize(section.text)
        if (normalized.isEmpty()) return emptyList()

        val pieces = normalized
            .split(BLANK_LINE)
            .filter(String::isNotBlank)
            .flatMap(::splitLongParagraph)
        val grouped = mutableListOf<String>()
        var current = StringBuilder()

        pieces.forEach { piece ->
            val separatorLength = if (current.isEmpty()) 0 else 2
            if (current.isNotEmpty() && current.length + separatorLength + piece.length > maxCharacters) {
                grouped += current.toString()
                current = StringBuilder()
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(piece)
        }
        if (current.isNotEmpty()) grouped += current.toString()

        return grouped.mapIndexed { index, content ->
            val title = when {
                section.title == null -> null
                grouped.size == 1 -> section.title
                else -> "${section.title} (${index + 1}/${grouped.size})"
            }
            PassageDraft(title = title, content = content)
        }
    }

    private fun splitLongParagraph(paragraph: String): List<String> {
        if (paragraph.length <= maxCharacters) return listOf(paragraph)

        val pieces = mutableListOf<String>()
        var start = 0
        while (start < paragraph.length) {
            var end = (start + maxCharacters).coerceAtMost(paragraph.length)
            if (end < paragraph.length) {
                end = findBreakBefore(paragraph, start, end)
            }
            pieces += paragraph.substring(start, end).trim()
            start = end
            while (start < paragraph.length && paragraph[start].isWhitespace()) start += 1
        }
        return pieces.filter(String::isNotEmpty)
    }

    private fun findBreakBefore(text: String, start: Int, preferredEnd: Int): Int {
        val minimumEnd = (start + maxCharacters / 2).coerceAtMost(preferredEnd)
        for (index in preferredEnd downTo minimumEnd) {
            if (index < text.length && text[index].isWhitespace()) return index
        }
        return preferredEnd
    }

    private fun inferTitle(content: String, index: Int): String {
        val firstLine = content.lineSequence().firstOrNull().orEmpty()
        val candidate = firstLine
            .substringBeforeLastSentenceBoundary()
            .trim()
            .take(MAX_INFERRED_TITLE_CHARACTERS)
            .trimEnd()
        return candidate.takeIf(String::isNotEmpty) ?: "Passage ${index + 1}"
    }

    private fun String.substringBeforeLastSentenceBoundary(): String {
        val boundary = indexOfAny(charArrayOf('.', '!', '?'))
        return if (boundary in 1 until MAX_INFERRED_TITLE_CHARACTERS) substring(0, boundary + 1) else this
    }

    private data class PassageDraft(val title: String?, val content: String)

    companion object {
        const val DEFAULT_MAX_PASSAGE_CHARACTERS = 3_500
        private const val MAX_INFERRED_TITLE_CHARACTERS = 80
        private val BLANK_LINE = Regex("\\n\\s*\\n")
    }
}

data class SearchChunkPolicy(
    val maxCharacters: Int = DEFAULT_MAX_CHUNK_CHARACTERS,
    val overlapCharacters: Int = DEFAULT_CHUNK_OVERLAP_CHARACTERS,
) {
    init {
        require(maxCharacters > 0) { "Chunk size must be positive" }
        require(overlapCharacters > 0 && overlapCharacters < maxCharacters) {
            "Chunk overlap must be positive and smaller than the chunk size"
        }
    }

    companion object {
        const val DEFAULT_MAX_CHUNK_CHARACTERS = 100
        const val DEFAULT_CHUNK_OVERLAP_CHARACTERS = 20
    }
}

class OverlappingSearchChunker(
    private val policy: SearchChunkPolicy = SearchChunkPolicy(),
) {
    fun split(passage: PassageEntity): List<SearchChunkEntity> {
        require(passage.content.isNotBlank()) { "Cannot split an empty passage" }
        val content = passage.content
        val chunks = mutableListOf<SearchChunkEntity>()
        var start = 0

        while (start < content.length) {
            val preferredEnd = (start + policy.maxCharacters).coerceAtMost(content.length)
            val end = if (preferredEnd == content.length) {
                content.length
            } else {
                findNaturalEnd(content, start, preferredEnd)
            }
            chunks += SearchChunkEntity(
                passageId = passage.id,
                chunkIndex = chunks.size,
                startOffset = start,
                endOffset = end,
                content = content.substring(start, end),
            )
            if (end == content.length) break

            val targetStart = (end - policy.overlapCharacters).coerceAtLeast(start + 1)
            var nextStart = targetStart
            val earliestWordBoundary = (nextStart - policy.overlapCharacters)
                .coerceAtLeast(start + 1)
            while (
                nextStart > earliestWordBoundary &&
                content[nextStart - 1].isLetterOrDigit() &&
                content[nextStart].isLetterOrDigit()
            ) {
                nextStart -= 1
            }
            while (nextStart < targetStart && content[nextStart].isWhitespace()) nextStart += 1
            start = nextStart.coerceAtMost(end - 1)
        }

        return chunks
    }

    private fun findNaturalEnd(content: String, start: Int, preferredEnd: Int): Int {
        val minimumEnd = (start + policy.maxCharacters / 2).coerceAtMost(preferredEnd)
        for (index in preferredEnd downTo minimumEnd) {
            if (content[index - 1].isWhitespace()) return index
        }
        return preferredEnd
    }
}
