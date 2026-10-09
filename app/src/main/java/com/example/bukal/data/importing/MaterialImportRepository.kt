package com.example.bukal.data.importing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.example.bukal.data.local.BukalDatabase
import com.example.bukal.data.local.ImportedMaterial
import com.example.bukal.data.local.MaterialEntity
import com.example.bukal.data.local.PassageEntity
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportedMaterialResult(
    val material: MaterialEntity,
    val passages: List<PassageEntity>,
)

class MaterialImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

class MaterialImportRepository(
    context: Context,
    private val database: BukalDatabase,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    private val applicationContext = context.applicationContext
    private val extractor = DocumentTextExtractor(applicationContext)
    private val passageBuilder = PassageBuilder()
    private val searchChunker = OverlappingSearchChunker()

    suspend fun import(uri: Uri): ImportedMaterialResult = withContext(Dispatchers.IO) {
        val resolver = applicationContext.contentResolver
        val metadata = resolver.queryMetadata(uri)
        if (metadata.size != null && metadata.size > MAX_IMPORT_BYTES) {
            throw MaterialImportException("Choose a file smaller than 50 MB.")
        }
        val reportedMimeType = resolver.getType(uri)
        val type = SupportedDocumentType.resolve(reportedMimeType, metadata.displayName)
            ?: throw MaterialImportException(
                "Choose a TXT, text-based PDF, DOCX, or PPTX file.",
            )
        val displayName = metadata.displayName
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: "Imported lesson.${type.extension}"

        val importDirectory = File(applicationContext.filesDir, IMPORT_DIRECTORY).apply { mkdirs() }
        if (!importDirectory.isDirectory) {
            throw MaterialImportException("Bukal could not prepare private storage for this file.")
        }
        val token = UUID.randomUUID().toString()
        val temporaryFile = File(importDirectory, ".$token.tmp")
        val retainedFile = File(importDirectory, "$token.${type.extension}")

        try {
            resolver.openInputStream(uri)?.use { input ->
                temporaryFile.outputStream().buffered().use { output ->
                    copyWithLimit(input, output)
                }
            } ?: throw MaterialImportException("The selected file could not be opened.")
            if (temporaryFile.length() == 0L) {
                throw MaterialImportException("The selected file is empty.")
            }

            val sections = extractor.extract(temporaryFile, type)
            if (sections.sumOf { it.text.length.toLong() } > MAX_EXTRACTED_CHARACTERS) {
                throw MaterialImportException("This file contains too much text. Choose a shorter lesson.")
            }
            val passageDrafts = passageBuilder.build(type, sections)
            if (!temporaryFile.renameTo(retainedFile)) {
                temporaryFile.copyTo(retainedFile, overwrite = false)
                temporaryFile.delete()
            }

            val relativePath = "$IMPORT_DIRECTORY/${retainedFile.name}"
            val materialId = try {
                database.withTransaction {
                    val id = database.materialDao().insert(
                        ImportedMaterial(
                            material = MaterialEntity(
                                displayName = displayName,
                                documentFormat = type.documentFormat,
                                mimeType = reportedMimeType ?: type.canonicalMimeType,
                                retainedFilePath = relativePath,
                                importedAtEpochMs = nowEpochMillis(),
                            ),
                            passages = passageDrafts,
                        ),
                    )
                    database.materialDao().getPassages(id).forEach { passage ->
                        database.searchChunkDao().replaceForPassage(
                            passageId = passage.id,
                            chunks = searchChunker.split(passage),
                        )
                    }
                    id
                }
            } catch (error: Exception) {
                retainedFile.delete()
                throw error
            }

            val material = requireNotNull(database.materialDao().getById(materialId))
            ImportedMaterialResult(
                material = material,
                passages = database.materialDao().getPassages(materialId),
            )
        } catch (error: MaterialImportException) {
            throw error
        } catch (error: DocumentExtractionException) {
            throw MaterialImportException(error.message ?: "Bukal could not extract text from this file.", error)
        } catch (error: SecurityException) {
            throw MaterialImportException("Bukal no longer has permission to read the selected file.", error)
        } catch (error: Exception) {
            throw MaterialImportException("The file could not be imported. Check that it is not damaged.", error)
        } finally {
            temporaryFile.delete()
        }
    }

    suspend fun getAll(): List<ImportedMaterialResult> = withContext(Dispatchers.IO) {
        database.materialDao().getAll().map { material ->
            ImportedMaterialResult(
                material = material,
                passages = database.materialDao().getPassages(material.id),
            )
        }
    }

    private fun android.content.ContentResolver.queryMetadata(uri: Uri): ImportMetadata {
        var displayName: String? = null
        var size: Long? = null
        query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) displayName = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        return ImportMetadata(displayName = displayName, size = size)
    }

    private fun copyWithLimit(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return
            copied += count
            if (copied > MAX_IMPORT_BYTES) throw MaterialImportException("Choose a file smaller than 50 MB.")
            output.write(buffer, 0, count)
        }
    }

    private data class ImportMetadata(
        val displayName: String?,
        val size: Long?,
    )

    private companion object {
        const val IMPORT_DIRECTORY = "imported-materials"
        const val MAX_IMPORT_BYTES = 50L * 1024L * 1024L
        const val MAX_EXTRACTED_CHARACTERS = 2_000_000L
    }
}
