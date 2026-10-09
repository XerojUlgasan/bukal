package com.example.bukal.data.importing

import com.example.bukal.data.local.DocumentFormats

enum class SupportedDocumentType(
    val documentFormat: String,
    val extension: String,
    val canonicalMimeType: String,
) {
    TXT(
        documentFormat = DocumentFormats.TXT,
        extension = "txt",
        canonicalMimeType = "text/plain",
    ),
    PDF(
        documentFormat = DocumentFormats.PDF,
        extension = "pdf",
        canonicalMimeType = "application/pdf",
    ),
    DOCX(
        documentFormat = DocumentFormats.DOCX,
        extension = "docx",
        canonicalMimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    ),
    PPTX(
        documentFormat = DocumentFormats.PPTX,
        extension = "pptx",
        canonicalMimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    ),
    ;

    companion object {
        val pickerMimeTypes = entries.map { it.canonicalMimeType }.toTypedArray()

        private val genericMimeTypes = setOf(
            "application/octet-stream",
            "application/zip",
            "application/x-zip-compressed",
        )

        fun resolve(mimeType: String?, displayName: String?): SupportedDocumentType? {
            val normalizedMime = mimeType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                ?.takeIf(String::isNotEmpty)
            val fromMime = entries.firstOrNull { it.canonicalMimeType == normalizedMime }
            if (fromMime != null) return fromMime

            val extension = displayName
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.lowercase()
                ?.takeIf(String::isNotEmpty)
            val fromExtension = entries.firstOrNull { it.extension == extension }

            return if (normalizedMime == null || normalizedMime in genericMimeTypes) {
                fromExtension
            } else {
                null
            }
        }
    }
}
