package com.stepnote.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DailyStepEntity::class, HourlyStepEntity::class],
    version = 1,
    exportSchema = false
)
abstract class StepDatabase : RoomDatabase() {
    abstract fun stepDao(): StepDao

    companion object {
        @Volatile
        private var instance: StepDatabase? = null

        fun getInstance(context: Context): StepDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StepDatabase::class.java,
                    "stepnote_cache.db"
                ).build().also { instance = it }
            }
    }
}
