package com.example.bukal.data.importing

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentTextExtractorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val extractor = DocumentTextExtractor()

    @Test
    fun rejectsEmptyTxt() {
        val file = temporaryFolder.newFile("empty.txt")

        val error = assertThrows(DocumentExtractionException::class.java) {
            extractor.extract(file, SupportedDocumentType.TXT)
        }

        assertEquals(
            "No readable text was found. Scanned or image-only files need OCR, which Bukal does not use.",
            error.message,
        )
    }

    @Test
    fun extractsUtf8TxtIncludingBom() {
        val file = temporaryFolder.newFile("lesson.txt")
        file.writeText("\uFEFFUnang aralin\n\nIkalawang talata")

        val sections = extractor.extract(file, SupportedDocumentType.TXT)

        assertEquals("Unang aralin\n\nIkalawang talata", sections.single().text)
    }

    @Test
    fun extractsDocxParagraphsWithoutApachePoi() {
        val file = temporaryFolder.newFile("lesson.docx")
        writeZip(
            file,
            mapOf(
                "word/document.xml" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        <w:p><w:r><w:t>First paragraph</w:t></w:r></w:p>
                        <w:p><w:r><w:t>Second</w:t><w:tab/><w:t>paragraph</w:t></w:r></w:p>
                      </w:body>
                    </w:document>
                """.trimIndent(),
            ),
        )

        val sections = extractor.extract(file, SupportedDocumentType.DOCX)

        assertEquals("First paragraph\n\nSecond\tparagraph", sections.single().text)
    }

    @Test
    fun extractsPptxSlidesInNumericOrder() {
        val file = temporaryFolder.newFile("lesson.pptx")
        writeZip(
            file,
            linkedMapOf(
                "ppt/slides/slide10.xml" to slideXml("Tenth slide"),
                "ppt/slides/slide2.xml" to slideXml("Second slide"),
                "ppt/slides/slide1.xml" to slideXml("First slide"),
            ),
        )

        val sections = extractor.extract(file, SupportedDocumentType.PPTX)

        assertEquals(listOf("First slide", "Second slide", "Tenth slide"), sections.map { it.text })
        assertEquals(listOf("Slide 1", "Slide 2", "Slide 10"), sections.map { it.title })
    }

    private fun slideXml(text: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
          <a:p><a:r><a:t>$text</a:t></a:r></a:p>
        </p:sld>
    """.trimIndent()

    private fun writeZip(file: File, entries: Map<String, String>) {
        ZipOutputStream(file.outputStream()).use { output ->
            entries.forEach { (path, content) ->
                output.putNextEntry(ZipEntry(path))
                output.write(content.toByteArray())
                output.closeEntry()
            }
        }
    }
}
