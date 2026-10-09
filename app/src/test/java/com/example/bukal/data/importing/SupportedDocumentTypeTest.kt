package com.example.bukal.data.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupportedDocumentTypeTest {
    @Test
    fun resolvesEverySupportedCanonicalMimeType() {
        SupportedDocumentType.entries.forEach { type ->
            assertEquals(type, SupportedDocumentType.resolve(type.canonicalMimeType, "wrong.bin"))
        }
    }

    @Test
    fun fallsBackToExtensionOnlyForGenericProviderMimeTypes() {
        assertEquals(
            SupportedDocumentType.DOCX,
            SupportedDocumentType.resolve("application/octet-stream", "lesson.DOCX"),
        )
        assertEquals(
            SupportedDocumentType.PPTX,
            SupportedDocumentType.resolve("application/zip", "lesson.pptx"),
        )
        assertNull(SupportedDocumentType.resolve("image/png", "lesson.txt"))
    }
}
