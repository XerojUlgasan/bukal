package com.example.bukal.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bukal.data.local.PassageEntity
import com.example.bukal.data.model.ModelFileVerification
import com.example.bukal.data.model.QuizModels
import com.example.bukal.data.model.modelFinalFile
import com.example.bukal.data.model.verifyModelFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GemmaQuizGenerationDeviceTest {
    @Test
    fun focusedPromptProducesOneValidMultipleChoiceQuestion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = QuizModels.single { it.id == GEMMA_MODEL_ID }
        assumeTrue(verifyModelFile(modelFinalFile(context, model), model) == ModelFileVerification.VALID)
        val runner = QuizModelRunner(context)

        try {
            val response = runner.complete(
                model = model,
                systemInstruction = buildGenerationSystemInstruction(QuestionType.MULTIPLE_CHOICE),
                request = buildGenerationRequest(PASSAGE, focusIndex = 0),
            )
            val question = parseAndValidateQuestion(
                response = response,
                type = QuestionType.MULTIPLE_CHOICE,
                passage = PassageEntity(
                    materialId = 1,
                    sourceId = "TXT-P001",
                    position = 0,
                    content = PASSAGE,
                ),
                index = 0,
            )

            assertEquals(4, (question.answer as QuestionAnswer.MultipleChoice).options.size)
        } finally {
            runner.close()
        }
    }

    private companion object {
        const val GEMMA_MODEL_ID = "gemma-4-e2b-it"
        val PASSAGE = """
            General Objective.
            To develop BakeWise, an AI-based demand forecasting and production decision-support
            system that assists bakeries in Quezon City in optimizing daily product production and
            ingredient purchasing based on predicted demand, available resources, inventory conditions,
            and unmet customer demand.

            Specific Objectives:
            1. To develop an AI-based demand forecasting component that predicts the expected
            demand for bakery products using historical sales and customer demand patterns.
            2. To design an AI-assisted production planning component that recommends the
            appropriate quantity of bakery products to prepare based on predicted demand and
            available production capacity.
            3. To develop an inventory and ingredient monitoring component that identifies potential
            ingredient shortages, excess inventory, slow-moving products, and products at risk of
            becoming unsold.
            4. To integrate customer product requests, availability searches, and restock requests to
            identify products with potential demand that may not be fully reflected in historical sales.
            5. To design an AI-powered production recommendation feature that provides
            recommendations such as Increase Production, Maintain Production, Reduce Production,
            or Do Not Produce, including the factors supporting each recommendation.
        """.trimIndent()
    }
}
