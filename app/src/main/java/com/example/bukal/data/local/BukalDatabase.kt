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
        QuestionEntity::class,
        MatchingPairEntity::class,
    ],
    version = 3,
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
    }
}
