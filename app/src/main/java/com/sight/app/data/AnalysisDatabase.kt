package com.sight.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "analyses")
data class AnalysisRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val sourceType: String, // IMAGE, VIDEO, CAMERA, SIMULATION
    val sourceName: String,
    val frameCount: Int,
    val detectionCount: Int,
    val trackCount: Int,
    val ocrCount: Int,
    val avgInferenceMs: Long,
    val avgFps: Double,
    val summaryJson: String
)

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM analyses ORDER BY timestampMs DESC LIMIT 50")
    fun recent(): Flow<List<AnalysisRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AnalysisRecord): Long

    @Query("DELETE FROM analyses")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM analyses")
    suspend fun count(): Int
}

@Database(entities = [AnalysisRecord::class], version = 1, exportSchema = false)
abstract class SightDatabase : RoomDatabase() {
    abstract fun analysisDao(): AnalysisDao

    companion object {
        @Volatile private var INSTANCE: SightDatabase? = null
        fun getInstance(context: Context): SightDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, SightDatabase::class.java, "sight.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
