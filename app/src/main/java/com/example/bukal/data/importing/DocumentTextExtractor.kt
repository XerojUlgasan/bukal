package com.example.bukal.data.importing

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

data class ExtractedSection(
    val title: String? = null,
    val text: String,
)

class DocumentExtractionException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class DocumentTextExtractor(context: Context? = null) {
    private val applicationContext = context?.applicationContext

    fun extract(file: File, type: SupportedDocumentType): List<ExtractedSection> =
        try {
            when (type) {
                SupportedDocumentType.TXT -> extractTxt(file)
                SupportedDocumentType.PDF -> extractPdf(file)
                SupportedDocumentType.DOCX -> extractDocx(file)
                SupportedDocumentType.PPTX -> extractPptx(file)
            }.filter { it.text.isNotBlank() }
                .ifEmpty {
                    throw DocumentExtractionException(
                        "No readable text was found. Scanned or image-only files need OCR, which Bukal does not use.",
                    )
                }
        } catch (error: DocumentExtractionException) {
            throw error
        } catch (error: Exception) {
            throw DocumentExtractionException("Bukal could not read this ${type.extension.uppercase()} file.", error)
        }

    private fun extractTxt(file: File): List<ExtractedSection> {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = InputStreamReader(file.inputStream(), decoder).use { it.readText() }
            .removePrefix("\uFEFF")
        return listOf(ExtractedSection(text = text))
    }

    private fun extractPdf(file: File): List<ExtractedSection> {
        PDFBoxResourceLoader.init(
            requireNotNull(applicationContext) { "Android context is required for PDF extraction" },
        )
        return PDDocument.load(file).use { document ->
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            (1..document.numberOfPages).mapNotNull { pageNumber ->
                stripper.startPage = pageNumber
                stripper.endPage = pageNumber
                stripper.getText(document)
                    .takeIf(String::isNotBlank)
                    ?.let { ExtractedSection(title = "Page $pageNumber", text = it) }
            }
        }
    }

    private fun extractDocx(file: File): List<ExtractedSection> =
        ZipFile(file).use { archive ->
            val document = archive.getEntry(DOCX_DOCUMENT_PATH)
                ?: throw DocumentExtractionException("This DOCX file has no readable document body.")
            archive.getInputStream(document).use { input ->
                listOf(ExtractedSection(text = extractParagraphs(input)))
            }
        }

    private fun extractPptx(file: File): List<ExtractedSection> =
        ZipFile(file).use { archive ->
            archive.entries().asSequence()
                .filter { !it.isDirectory && PPTX_SLIDE_PATH.matches(it.name) }
                .sortedBy { entry ->
                    PPTX_SLIDE_PATH.matchEntire(entry.name)?.groupValues?.get(1)?.toIntOrNull()
                }
                .mapNotNull { entry ->
                    val slideNumber = PPTX_SLIDE_PATH.matchEntire(entry.name)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull()
                        ?: return@mapNotNull null
                    archive.getInputStream(entry).use { input ->
                        extractParagraphs(input)
                            .takeIf(String::isNotBlank)
                            ?.let { ExtractedSection(title = "Slide $slideNumber", text = it) }
                    }
                }
                .toList()
        }

    private fun extractParagraphs(input: java.io.InputStream): String {
        val paragraphs = mutableListOf<String>()
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            secureFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            secureFeature("http://xml.org/sax/features/external-general-entities", false)
            secureFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        factory.newSAXParser().parse(
            input,
            object : DefaultHandler() {
                private var paragraph: StringBuilder? = null
                private var collectingText = false

                override fun startElement(
                    uri: String?,
                    localName: String?,
                    qName: String?,
                    attributes: Attributes?,
                ) {
                    when (localName.orEmpty().ifEmpty { qName.orEmpty().substringAfter(':') }) {
                        "p" -> paragraph = StringBuilder()
                        "t" -> collectingText = true
                        "tab" -> paragraph?.append('\t')
                        "br", "cr" -> paragraph?.append('\n')
                    }
                }

                override fun characters(characters: CharArray, start: Int, length: Int) {
                    if (collectingText) paragraph?.append(characters, start, length)
                }

                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    when (localName.orEmpty().ifEmpty { qName.orEmpty().substringAfter(':') }) {
                        "t" -> collectingText = false
                        "p" -> {
                            paragraph?.toString()?.trim()?.takeIf(String::isNotEmpty)?.let(paragraphs::add)
                            paragraph = null
                        }
                    }
                }
            },
        )
        return paragraphs.joinToString("\n\n")
    }

    private fun SAXParserFactory.secureFeature(name: String, enabled: Boolean) {
        runCatching { setFeature(name, enabled) }
    }

    private companion object {
        const val DOCX_DOCUMENT_PATH = "word/document.xml"
        val PPTX_SLIDE_PATH = Regex("ppt/slides/slide(\\d+)\\.xml")
    }
}
