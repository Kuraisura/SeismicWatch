package com.gising.data.repository

import androidx.room.*
import com.gising.data.model.Earthquake
import com.gising.data.model.EmergencyContact
import com.gising.data.security.DatabaseKeyManager
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Dao
interface EarthquakeDao {

    @Query("SELECT * FROM earthquakes ORDER BY timeMs DESC LIMIT 200")
    fun observeAll(): Flow<List<Earthquake>>

    @Query("SELECT * FROM earthquakes ORDER BY timeMs DESC LIMIT 200")
    suspend fun getAll(): List<Earthquake>

    @Query("SELECT * FROM earthquakes WHERE id = :id")
    suspend fun getById(id: String): Earthquake?

    @Query("SELECT * FROM earthquakes WHERE magnitude >= :minMag ORDER BY timeMs DESC")
    fun observeByMinMagnitude(minMag: Double): Flow<List<Earthquake>>

    @Query("SELECT * FROM earthquakes WHERE isAlerted = 0 ORDER BY timeMs DESC")
    suspend fun getUnalerted(): List<Earthquake>

    @Upsert
    suspend fun upsertAll(earthquakes: List<Earthquake>)

    @Query("UPDATE earthquakes SET isAlerted = 1 WHERE id = :id")
    suspend fun markAlerted(id: String)

    @Query("DELETE FROM earthquakes WHERE savedAt < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long)

    @Query("SELECT COUNT(*) FROM earthquakes")
    suspend fun count(): Int
}

@Dao
interface ContactDao {

    @Query("SELECT * FROM emergency_contacts ORDER BY isPrimary DESC, name ASC")
    fun observeAll(): Flow<List<EmergencyContact>>

    @Query("SELECT * FROM emergency_contacts ORDER BY isPrimary DESC, name ASC")
    suspend fun getAll(): List<EmergencyContact>

    @Query("SELECT * FROM emergency_contacts WHERE isPrimary = 1 LIMIT 1")
    suspend fun getPrimary(): EmergencyContact?

    @Insert
    suspend fun insert(contact: EmergencyContact): Long

    @Update
    suspend fun update(contact: EmergencyContact)

    @Delete
    suspend fun delete(contact: EmergencyContact)

    /** Ensures only one contact is flagged as the primary (auto-call) target. */
    @Query("UPDATE emergency_contacts SET isPrimary = 0 WHERE id != :keepId")
    suspend fun clearPrimaryExcept(keepId: Long)

    @Query("SELECT COUNT(*) FROM emergency_contacts")
    suspend fun count(): Int
}

@Database(
    entities = [Earthquake::class, EmergencyContact::class, TyphoonEntity::class],
    version = 3,
    exportSchema = false
)
abstract class SeismicDatabase : RoomDatabase() {
    abstract fun earthquakeDao(): EarthquakeDao
    abstract fun contactDao(): ContactDao
    abstract fun typhoonDao(): TyphoonDao

    companion object {
        @Volatile private var INSTANCE: SeismicDatabase? = null

        fun getInstance(context: android.content.Context): SeismicDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext

                    // Load SQLCipher's native library before opening the database.
                    System.loadLibrary("sqlcipher")

                    // Open Room through SQLCipher so the on-disk DB is AES-256 encrypted.
                    // The passphrase lives in the Keystore-backed secure store, not in code.
                    val passphrase = DatabaseKeyManager.getOrCreatePassphrase(appContext)
                    val factory = SupportOpenHelperFactory(passphrase)

                    Room.databaseBuilder(
                        appContext,
                        SeismicDatabase::class.java,
                        "seismic_watch.db"
                    )
                        .openHelperFactory(factory)
                        .fallbackToDestructiveMigration()
                        .build()
                        .also { INSTANCE = it }
                }
            }
        }
    }
}
