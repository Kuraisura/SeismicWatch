package com.gising.data.repository

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.gising.data.model.Typhoon
import com.gising.data.model.TyphoonAlert

/**
 * On-disk copy of a tropical cyclone, so the Typhoons feed still works offline / on weak signal.
 * Kept separate from the domain [Typhoon] (which has computed fields + enums) so the model stays a
 * plain data class and Room only ever sees primitives.
 */
@Entity(tableName = "typhoons")
data class TyphoonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val windKph: Double?,
    val alertLevel: String,
    val fromMs: Long,
    val toMs: Long,
    val isCurrent: Boolean,
    /** When this row was last written, for retention purging. */
    val savedAt: Long,
)

@Dao
interface TyphoonDao {

    @Query("SELECT * FROM typhoons ORDER BY isCurrent DESC, fromMs DESC")
    suspend fun getAll(): List<TyphoonEntity>

    @Upsert
    suspend fun upsertAll(typhoons: List<TyphoonEntity>)

    @Query("DELETE FROM typhoons WHERE savedAt < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long)

    @Query("SELECT COUNT(*) FROM typhoons")
    suspend fun count(): Int
}

fun Typhoon.toEntity(now: Long): TyphoonEntity = TyphoonEntity(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    windKph = windKph,
    alertLevel = alertLevel.name,
    fromMs = fromMs,
    toMs = toMs,
    isCurrent = isCurrent,
    savedAt = now,
)

fun TyphoonEntity.toDomain(): Typhoon = Typhoon(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    windKph = windKph,
    alertLevel = runCatching { TyphoonAlert.valueOf(alertLevel) }.getOrDefault(TyphoonAlert.GREEN),
    fromMs = fromMs,
    toMs = toMs,
    isCurrent = isCurrent,
)
