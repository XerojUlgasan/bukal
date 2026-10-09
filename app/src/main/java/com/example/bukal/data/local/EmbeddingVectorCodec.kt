package com.example.bukal.data.local

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object EmbeddingVectorCodec {
    private const val BYTES_PER_FLOAT = Float.SIZE_BYTES

    fun encode(vector: FloatArray): ByteArray {
        require(vector.isNotEmpty()) { "Embedding vector must not be empty" }
        require(vector.all(Float::isFinite)) { "Embedding vector must contain only finite values" }

        return ByteBuffer.allocate(vector.size * BYTES_PER_FLOAT)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { vector.forEach(::putFloat) }
            .array()
    }

    fun decode(bytes: ByteArray, dimensions: Int): FloatArray {
        require(dimensions > 0) { "Embedding dimensions must be positive" }
        require(bytes.size == dimensions * BYTES_PER_FLOAT) {
            "Expected ${dimensions * BYTES_PER_FLOAT} embedding bytes but found ${bytes.size}"
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(dimensions) { buffer.float }
    }

    fun cosineSimilarity(left: FloatArray, right: FloatArray): Double {
        require(left.isNotEmpty()) { "Embedding vectors must not be empty" }
        require(left.size == right.size) { "Embedding vectors must have matching dimensions" }

        var dot = 0.0
        var leftMagnitude = 0.0
        var rightMagnitude = 0.0
        for (index in left.indices) {
            val leftValue = left[index].toDouble()
            val rightValue = right[index].toDouble()
            require(leftValue.isFinite() && rightValue.isFinite()) {
                "Embedding vectors must contain only finite values"
            }
            dot += leftValue * rightValue
            leftMagnitude += leftValue * leftValue
            rightMagnitude += rightValue * rightValue
        }

        require(leftMagnitude > 0.0 && rightMagnitude > 0.0) {
            "Embedding vectors must have non-zero magnitude"
        }
        return dot / (sqrt(leftMagnitude) * sqrt(rightMagnitude))
    }
}
