package com.example.bukal.data.importing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfTextExtractorTest {
    @Test
    fun extractsTextByPage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(context)
        val file = File(context.cacheDir, "extractor-test.pdf")
        try {
            PDDocument().use { document ->
                document.addPage(PDPage())
                PDPageContentStream(document, document.getPage(0)).use { content ->
                    content.beginText()
                    content.setFont(PDType1Font.HELVETICA, 12f)
                    content.newLineAtOffset(72f, 720f)
                    content.showText("Bukal readable PDF lesson")
                    content.endText()
                }
                document.save(file)
            }

            val sections = DocumentTextExtractor(context).extract(file, SupportedDocumentType.PDF)

            assertEquals("Page 1", sections.single().title)
            assertTrue(sections.single().text.contains("Bukal readable PDF lesson"))
        } finally {
            file.delete()
        }
    }
}
