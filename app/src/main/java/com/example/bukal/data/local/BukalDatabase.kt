package com.example.bukal.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MaterialEntity::class,
        PassageEntity::class,
        SearchChunkEntity::class,
        AttemptEntity::class,
        QuizSetEntity::class,
        QuizSetItemEntity::class,
        QuestionEntity::class,
        MatchingPairEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class BukalDatabase : RoomDatabase() {
    abstract fun materialDao(): MaterialDao

    abstract fun searchChunkDao(): SearchChunkDao

    abstract fun attemptDao(): AttemptDao

    companion object {
        const val DATABASE_NAME = "bukal.db"

        fun create(context: Context): BukalDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                BukalDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE attempts ADD COLUMN generated_at_epoch_ms INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE attempts ADD COLUMN status TEXT NOT NULL DEFAULT 'completed'",
                )
                db.execSQL(
                    "UPDATE attempts SET generated_at_epoch_ms = completed_at_epoch_ms",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_attempts_generated_at " +
                        "ON attempts(generated_at_epoch_ms DESC)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE attempts ADD COLUMN highest_earned_points REAL NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "UPDATE attempts SET highest_earned_points = earned_points",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS quiz_sets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        selection_key TEXT NOT NULL,
                        created_at_epoch_ms INTEGER NOT NULL,
                        status TEXT NOT NULL DEFAULT 'saved',
                        completed_at_epoch_ms INTEGER NOT NULL,
                        completed_local_date TEXT NOT NULL,
                        earned_points REAL NOT NULL,
                        highest_earned_points REAL NOT NULL DEFAULT 0,
                        possible_points REAL NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_quiz_sets_selection_key " +
                        "ON quiz_sets(selection_key)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_quiz_sets_created_at " +
                        "ON quiz_sets(created_at_epoch_ms DESC)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_quiz_sets_completed_at " +
                        "ON quiz_sets(completed_at_epoch_ms DESC)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_quiz_sets_completed_local_date " +
                        "ON quiz_sets(completed_local_date)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS quiz_set_items (
                        quiz_set_id INTEGER NOT NULL,
                        attempt_id INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        PRIMARY KEY(quiz_set_id, attempt_id),
                        FOREIGN KEY(quiz_set_id) REFERENCES quiz_sets(id)
                            ON UPDATE RESTRICT ON DELETE CASCADE,
                        FOREIGN KEY(attempt_id) REFERENCES attempts(id)
                            ON UPDATE RESTRICT ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_quiz_set_items_quiz_set_id_position " +
                        "ON quiz_set_items(quiz_set_id, position)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_quiz_set_items_attempt_id " +
                        "ON quiz_set_items(attempt_id)",
                )
                db.execSQL(
                    """
                    INSERT INTO quiz_sets (
                        id, selection_key, created_at_epoch_ms, status,
                        completed_at_epoch_ms, completed_local_date,
                        earned_points, highest_earned_points, possible_points
                    )
                    SELECT
                        id, CAST(id AS TEXT), generated_at_epoch_ms, status,
                        completed_at_epoch_ms, completed_local_date,
                        earned_points, highest_earned_points, possible_points
                    FROM attempts
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO quiz_set_items (quiz_set_id, attempt_id, position)
                    SELECT id, id, 0 FROM attempts
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE materials ADD COLUMN summary_markdown TEXT")
                db.execSQL("ALTER TABLE materials ADD COLUMN summary_model_id TEXT")
                db.execSQL("ALTER TABLE materials ADD COLUMN summarized_at_epoch_ms INTEGER")
            }
        }
    }
}
