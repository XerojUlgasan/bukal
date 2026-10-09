package com.example.bukal.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ModelDownloadSpecTest {
    @Test
    fun validSpecProducesStablePendingPath() {
        val spec = validSpec()

        assertEquals(
            "models/pending/qwen3-compact/Qwen3-0.6B.litertlm.download",
            modelPendingRelativePath(spec),
        )
    }

    @Test
    fun rejectsNonHttpsDownloadUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            validSpec(downloadUrl = "http://example.com/model.litertlm")
        }
    }

    @Test
    fun rejectsFilenameContainingAPath() {
        assertThrows(IllegalArgumentException::class.java) {
            validSpec(fileName = "../model.litertlm")
        }
    }

    @Test
    fun rejectsUnsafeModelId() {
        assertThrows(IllegalArgumentException::class.java) {
            validSpec(id = "../model")
        }
    }

    @Test
    fun rejectsInvalidSha256() {
        assertThrows(IllegalArgumentException::class.java) {
            validSpec(sha256 = "not-a-sha256")
        }
    }

    @Test
    fun catalogContainsOneEmbeddingAndAtLeastOneQuizModel() {
        assertEquals(1, ModelCatalog.count { it.purpose == ModelPurpose.EMBEDDING })
        assertTrue(ModelCatalog.any { it.purpose == ModelPurpose.QUIZ })
        assertEquals(ModelCatalog.size, ModelCatalog.map { it.id }.toSet().size)
    }

    @Test
    fun catalogPinsGemma4E2BArtifactAndMemoryRequirement() {
        val gemma = ModelCatalog.single { it.id == "gemma-4-e2b-it" }

        assertEquals(ModelPurpose.QUIZ, gemma.purpose)
        assertEquals("gemma-4-E2B-it.litertlm", gemma.fileName)
        assertEquals(2_583_085_056, gemma.sizeBytes)
        assertEquals("ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42", gemma.sha256)
        assertEquals(8, gemma.minimumRamGb)
    }

    @Test
    fun catalogPinsGemma3ArtifactTermsAndMemoryRequirement() {
        val gemma = ModelCatalog.single { it.id == "gemma-3-1b-it" }

        assertEquals(ModelPurpose.QUIZ, gemma.purpose)
        assertEquals("gemma3-1b-it-int4.litertlm", gemma.fileName)
        assertEquals(584_417_280, gemma.sizeBytes)
        assertEquals("1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be", gemma.sha256)
        assertEquals(6, gemma.minimumRamGb)
        assertEquals("https://ai.google.dev/gemma/terms", gemma.termsUrl)
    }

    @Test
    fun deviceMemoryRequirementUsesDecimalGigabytes() {
        assertTrue(hasRequiredDeviceMemory(8_000_000_000, 8))
        assertFalse(hasRequiredDeviceMemory(7_999_999_999, 8))
    }

    @Test
    fun gateRequiresEmbeddingAndAnyInstalledQuizModel() {
        val secondQuiz = validSpec(
            id = "second-quiz-model",
            fileName = "second.litertlm",
            downloadUrl = "https://example.com/second.litertlm",
        )
        val ready = ModelInstallationSnapshot(
            models = ModelCatalog.map { ModelInstallation(it, ModelInstallStatus.Installed) } +
                ModelInstallation(secondQuiz, ModelInstallStatus.Missing),
            checked = true,
        )
        val embeddingMissing = ready.copy(
            models = ready.models.map { model ->
                if (model.spec.purpose == ModelPurpose.EMBEDDING) {
                    model.copy(status = ModelInstallStatus.Missing)
                } else {
                    model
                }
            },
        )
        val everyQuizMissing = ready.copy(
            models = ready.models.map { model ->
                if (model.spec.purpose == ModelPurpose.QUIZ) {
                    model.copy(status = ModelInstallStatus.Missing)
                } else {
                    model
                }
            },
        )

        assertTrue(ready.isReady)
        assertFalse(ready.copy(checked = false).isReady)
        assertFalse(embeddingMissing.isReady)
        assertFalse(everyQuizMissing.isReady)
    }

    @Test
    fun optionalMissingQuizModelDoesNotBecomeRequired() {
        val optionalQuiz = validSpec(
            id = "optional-quiz",
            fileName = "optional.litertlm",
            downloadUrl = "https://example.com/optional.litertlm",
        )
        val snapshot = ModelInstallationSnapshot(
            models = ModelCatalog.map { ModelInstallation(it, ModelInstallStatus.Installed) } +
                ModelInstallation(optionalQuiz, ModelInstallStatus.Missing),
            checked = true,
        )

        assertTrue(snapshot.isReady)
        assertTrue(requiredModelsToInstall(snapshot).isEmpty())
    }

    @Test
    fun activeAndAlreadyEnqueuedModelsAreSkippedButPausedDownloadsCanRestart() {
        val missing = validSpec(
            id = "missing-quiz",
            fileName = "missing.litertlm",
            downloadUrl = "https://example.com/missing.litertlm",
        )
        val downloading = validSpec(
            id = "downloading-quiz",
            fileName = "downloading.litertlm",
            downloadUrl = "https://example.com/downloading.litertlm",
        )
        val paused = validSpec(
            id = "paused-quiz",
            fileName = "paused.litertlm",
            downloadUrl = "https://example.com/paused.litertlm",
        )
        val snapshot = ModelInstallationSnapshot(
            models = listOf(
                ModelInstallation(DefaultQuizModel, ModelInstallStatus.Installed),
                ModelInstallation(
                    downloading,
                    ModelInstallStatus.Downloading(downloadedBytes = 10, totalBytes = 100),
                ),
                ModelInstallation(
                    paused,
                    ModelInstallStatus.Paused(
                        downloadedBytes = 20,
                        totalBytes = 100,
                        message = "Waiting to retry",
                    ),
                ),
                ModelInstallation(missing, ModelInstallStatus.Missing),
            ),
            checked = true,
        )
        val requested = snapshot.models.map { it.spec.id }.toSet()

        assertEquals(
            listOf(paused.id, missing.id),
            installationsToStart(snapshot, requested).map { it.spec.id },
        )
        assertEquals(
            listOf(paused.id),
            installationsToStart(
                snapshot,
                requested,
                enqueuedModelIds = setOf(paused.id, missing.id),
            ).map { it.spec.id },
        )
    }

    @Test
    fun selectionUsesPreferredInstalledQuizThenFallsBackPredictably() {
        val alternate = validSpec(
            id = "alternate-quiz",
            fileName = "alternate.litertlm",
            downloadUrl = "https://example.com/alternate.litertlm",
        )
        val bothInstalled = ModelInstallationSnapshot(
            models = listOf(
                ModelInstallation(DefaultQuizModel, ModelInstallStatus.Installed),
                ModelInstallation(alternate, ModelInstallStatus.Installed),
                ModelInstallation(EmbeddingModel, ModelInstallStatus.Installed),
            ),
            checked = true,
        )

        assertEquals(alternate.id, resolveSelectedQuizModelId(bothInstalled, alternate.id))
        assertEquals(DefaultQuizModel.id, resolveSelectedQuizModelId(bothInstalled, "unknown"))

        val onlyAlternate = bothInstalled.copy(
            models = bothInstalled.models.map { installation ->
                if (installation.spec.id == DefaultQuizModel.id) {
                    installation.copy(status = ModelInstallStatus.Missing)
                } else {
                    installation
                }
            },
        )
        assertEquals(alternate.id, resolveSelectedQuizModelId(onlyAlternate, DefaultQuizModel.id))
    }

    @Test
    fun downloadedFileMustMatchSizeAndSha256() {
        val file = Files.createTempFile("bukal-model", ".litertlm").toFile()
        try {
            file.writeText("hello")
            val spec = ModelDownloadSpec(
                id = "test-model",
                displayName = "Test Model",
                purpose = ModelPurpose.QUIZ,
                fileName = "test.litertlm",
                downloadUrl = "https://example.com/test.litertlm",
                sizeBytes = 5,
                sha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            )

            assertEquals(ModelFileVerification.VALID, verifyModelFile(file, spec))
            assertEquals(
                ModelFileVerification.WRONG_SIZE,
                verifyModelFile(file, spec.copy(sizeBytes = 6)),
            )
            assertEquals(
                ModelFileVerification.WRONG_SHA256,
                verifyModelFile(file, spec.copy(sha256 = "0".repeat(64))),
            )
        } finally {
            file.delete()
        }
    }

    private fun validSpec(
        id: String = "qwen3-compact",
        fileName: String = "Qwen3-0.6B.litertlm",
        downloadUrl: String = "https://huggingface.co/example/model.litertlm",
        sha256: String = "a".repeat(64),
    ) = ModelDownloadSpec(
        id = id,
        displayName = "Qwen 3 Compact",
        purpose = ModelPurpose.QUIZ,
        fileName = fileName,
        downloadUrl = downloadUrl,
        sizeBytes = 344_671_744,
        sha256 = sha256,
    )
}
