package com.example.bukal.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class MaterialDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertMaterialRow(material: MaterialEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertPassageRows(passages: List<PassageEntity>)

    @Transaction
    open suspend fun insert(record: ImportedMaterial): Long {
        DatabaseValidation.validateMaterial(record.material, record.passages)
        val materialId = insertMaterialRow(record.material.copy(id = 0))
        insertPassageRows(
            record.passages.map { passage ->
                passage.copy(id = 0, materialId = materialId)
            },
        )
        return materialId
    }

    @Query("SELECT * FROM materials ORDER BY imported_at_epoch_ms DESC")
    abstract suspend fun getAll(): List<MaterialEntity>

    @Query("SELECT * FROM materials WHERE id = :materialId")
    abstract suspend fun getById(materialId: Long): MaterialEntity?

    @Query("SELECT * FROM passages WHERE material_id = :materialId ORDER BY position")
    abstract suspend fun getPassages(materialId: Long): List<PassageEntity>

    @Query("SELECT * FROM passages WHERE id = :passageId")
    abstract suspend fun getPassage(passageId: Long): PassageEntity?

    @Query(
        """
        UPDATE materials
        SET summary_markdown = :markdown,
            summary_model_id = :modelId,
            summarized_at_epoch_ms = :summarizedAtEpochMs
        WHERE id = :materialId AND summary_markdown IS NULL
        """,
    )
    abstract suspend fun saveSummaryOnce(
        materialId: Long,
        markdown: String,
        modelId: String,
        summarizedAtEpochMs: Long,
    ): Int

    @Delete
    abstract suspend fun delete(material: MaterialEntity)
}
