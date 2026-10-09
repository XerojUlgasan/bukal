package com.example.bukal.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BukalMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BukalDatabase::class.java,
    )

    @Test
    fun migrationOneToTwoKeepsCompletedAttemptsAndAddsSavedQuizFields() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                "INSERT INTO materials " +
                    "(id, display_name, document_format, mime_type, retained_file_path, imported_at_epoch_ms) " +
                    "VALUES (1, 'Lesson', 'txt', 'text/plain', NULL, 1)",
            )
            execSQL(
                "INSERT INTO passages (id, material_id, source_id, position, title, content) " +
                    "VALUES (1, 1, 'TXT-P001', 0, NULL, 'Alpha is first.')",
            )
            execSQL(
                "INSERT INTO attempts " +
                    "(id, passage_id, quiz_model_id, completed_at_epoch_ms, completed_local_date, " +
                    "earned_points, possible_points) " +
                    "VALUES (1, 1, 'quiz-model', 1234, '2026-10-09', 1, 1)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            BukalDatabase.MIGRATION_1_2,
        ).use { database ->
            database.query(
                "SELECT generated_at_epoch_ms, status FROM attempts WHERE id = 1",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1234L, cursor.getLong(0))
                assertEquals(AttemptStatuses.COMPLETED, cursor.getString(1))
            }
        }
    }

    @Test
    fun migrationTwoToThreeUsesTheExistingScoreAsTheHighestScore() {
        helper.createDatabase(TEST_DATABASE, 2).apply {
            execSQL(
                "INSERT INTO materials " +
                    "(id, display_name, document_format, mime_type, retained_file_path, imported_at_epoch_ms) " +
                    "VALUES (1, 'Lesson', 'txt', 'text/plain', NULL, 1)",
            )
            execSQL(
                "INSERT INTO passages (id, material_id, source_id, position, title, content) " +
                    "VALUES (1, 1, 'TXT-P001', 0, NULL, 'Alpha is first.')",
            )
            execSQL(
                "INSERT INTO attempts " +
                    "(id, passage_id, quiz_model_id, generated_at_epoch_ms, status, " +
                    "completed_at_epoch_ms, completed_local_date, earned_points, possible_points) " +
                    "VALUES (1, 1, 'quiz-model', 1200, 'completed', 1234, '2026-10-09', 4, 5)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            3,
            true,
            BukalDatabase.MIGRATION_2_3,
        ).use { database ->
            database.query(
                "SELECT earned_points, highest_earned_points FROM attempts WHERE id = 1",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(4.0, cursor.getDouble(0), 0.0)
                assertEquals(4.0, cursor.getDouble(1), 0.0)
            }
        }
    }

    @Test
    fun migrationThreeToFourBackfillsOneQuizSetPerExistingAttempt() {
        helper.createDatabase(TEST_DATABASE, 3).apply {
            execSQL(
                "INSERT INTO materials " +
                    "(id, display_name, document_format, mime_type, retained_file_path, imported_at_epoch_ms) " +
                    "VALUES (1, 'Lesson', 'txt', 'text/plain', NULL, 1)",
            )
            execSQL(
                "INSERT INTO passages (id, material_id, source_id, position, title, content) " +
                    "VALUES (1, 1, 'TXT-P001', 0, NULL, 'Alpha is first.')",
            )
            execSQL(
                "INSERT INTO attempts " +
                    "(id, passage_id, quiz_model_id, generated_at_epoch_ms, status, " +
                    "completed_at_epoch_ms, completed_local_date, earned_points, " +
                    "highest_earned_points, possible_points) " +
                    "VALUES (7, 1, 'quiz-model', 1200, 'completed', 1234, '2026-10-09', 4, 4, 5)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            4,
            true,
            BukalDatabase.MIGRATION_3_4,
        ).use { database ->
            database.query(
                "SELECT id, selection_key, status, earned_points FROM quiz_sets",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(7L, cursor.getLong(0))
                assertEquals("7", cursor.getString(1))
                assertEquals(AttemptStatuses.COMPLETED, cursor.getString(2))
                assertEquals(4.0, cursor.getDouble(3), 0.0)
            }
            database.query(
                "SELECT quiz_set_id, attempt_id, position FROM quiz_set_items",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(7L, cursor.getLong(0))
                assertEquals(7L, cursor.getLong(1))
                assertEquals(0, cursor.getInt(2))
            }
        }
    }

    @Test
    fun migrationFourToFiveAddsOptionalOneTimeSummaryFields() {
        helper.createDatabase(TEST_DATABASE, 4).apply {
            execSQL(
                "INSERT INTO materials " +
                    "(id, display_name, document_format, mime_type, retained_file_path, imported_at_epoch_ms) " +
                    "VALUES (1, 'Lesson', 'txt', 'text/plain', NULL, 1)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            5,
            true,
            BukalDatabase.MIGRATION_4_5,
        ).use { database ->
            database.query(
                "SELECT summary_markdown, summary_model_id, summarized_at_epoch_ms " +
                    "FROM materials WHERE id = 1",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(true, cursor.isNull(0))
                assertEquals(true, cursor.isNull(1))
                assertEquals(true, cursor.isNull(2))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE = "bukal-migration-test"
    }
}
