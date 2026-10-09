package com.example.bukal.ai

import com.example.bukal.data.local.PassageEntity
import com.example.bukal.data.model.DefaultQuizModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSummaryTest {
    @Test
    fun summarizesEveryPassageAndKeepsTheModelsMarkdown() = runBlocking {
        val responses = ArrayDeque(
            listOf(
                "- Alpha is the first idea.",
                """```markdown
                    - Beta is the second idea.
                ```""".trimIndent(),
                """# Overall Summary

                    The lesson explains Alpha and Beta.

                    ## Key Points

                    - Alpha comes first. *(Source: TXT-P001)*
                    - Beta comes second. *(Source: TXT-P002)*
                """.trimIndent(),
            ),
        )
        val progress = mutableListOf<Pair<Int, Int>>()
        val summarizer = FileSummarizer(
            onPassageStarted = { current, total -> progress += current to total },
            complete = { _, _, _ -> responses.removeFirst() },
        )

        val markdown = summarizer.summarize(
            FileSummaryRequest(
                model = DefaultQuizModel,
                passages = listOf(passage(1, "TXT-P001"), passage(2, "TXT-P002")),
            ),
        )

        assertEquals(listOf(1 to 2, 2 to 2), progress)
        assertTrue(markdown.startsWith("# Overall Summary"))
        assertTrue(markdown.contains("## Key Points"))
        assertTrue(markdown.contains("Alpha comes first. *(Source: TXT-P001)*"))
        assertTrue(markdown.contains("Beta comes second. *(Source: TXT-P002)*"))
    }

    @Test
    fun acceptsFourUsefulPassageBulletsWithoutRetrying() = runBlocking {
        var callCount = 0
        val summarizer = FileSummarizer { _, _, _ ->
            callCount += 1
            if (callCount == 1) {
                "- First rule\n- Second rule\n- Third rule\n- Fourth rule"
            } else {
                "# Overall Summary\n\n## Rules\n\n- Four important rules. *(Source: TXT-P001)*"
            }
        }

        summarizer.summarize(FileSummaryRequest(DefaultQuizModel, listOf(passage(1, "TXT-P001"))))

        assertEquals(2, callCount)
    }

    @Test
    fun retriesWhenFinalMarkdownCitesAnUnknownSource() = runBlocking {
        var callCount = 0
        val summarizer = FileSummarizer { _, _, _ ->
            callCount += 1
            when (callCount) {
                1 -> "- Supported note."
                2 -> "# Summary\n\n- Point. *(Source: TXT-P999)*"
                else -> "# Summary\n\n- Point. *(Source: TXT-P001)*"
            }
        }

        val markdown = summarizer.summarize(
            FileSummaryRequest(DefaultQuizModel, listOf(passage(1, "TXT-P001"))),
        )

        assertEquals(3, callCount)
        assertTrue(markdown.contains("TXT-P001"))
    }

    @Test
    fun addsAStableSourceFooterWhenFinalMarkdownOmitsSources() = runBlocking {
        val responses = ArrayDeque(
            listOf(
                "- Supported note.",
                "# Summary\n\nA supported overview.",
            ),
        )
        val summarizer = FileSummarizer { _, _, _ -> responses.removeFirst() }

        val markdown = summarizer.summarize(
            FileSummaryRequest(DefaultQuizModel, listOf(passage(1, "TXT-P001"))),
        )

        assertTrue(markdown.endsWith("## Sources\n\n- TXT-P001"))
    }

    private fun passage(id: Long, sourceId: String) = PassageEntity(
        id = id,
        materialId = 1,
        sourceId = sourceId,
        position = id.toInt() - 1,
        content = "Source content for $sourceId.",
    )
}
