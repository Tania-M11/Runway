package com.udenar.runway.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Base de datos SQLite del teléfono (enfoque offline-first): todo se guarda primero aquí
 * y funciona sin internet. En la parte 8 se sincroniza con Supabase.
 */
@Database(entities = [RutaEntidad::class], version = 1, exportSchema = true)
abstract class RunwayDatabase : RoomDatabase() {
    abstract fun rutas(): RutasDao
}

/**
 * Tabla `rutas`: catálogo personal. El trazado se guarda comprimido como "encoded polyline"
 * (unos pocos KB por ruta). El id es un UUID creado en el teléfono para que no choque
 * con otros dispositivos al sincronizar (plan, sección 11).
 */
@Entity(tableName = "rutas")
data class RutaEntidad(
    @PrimaryKey val id: String,
    val nombre: String,
    val visibilidad: String,
    val modalidad: String,
    val distanciaKm: Double,
    val subidaM: Double,
    val bajadaM: Double,
    val trazado: String,
    /** Altitudes separadas por ";" */
    val elevaciones: String,
    val intervaloElevacionM: Double,
    /** Calles principales separadas por "|" */
    val calles: String,
    val giros: Int,
    val inicioNombre: String,
    val finNombre: String?,
    val creadaEn: Long,
    val actualizadaEn: Long,
    /** true mientras el cambio no se haya subido al servidor. */
    val pendienteSync: Boolean = true,
    /** Borrado lógico: se marca para poder avisar al servidor antes de borrarla del todo. */
    val eliminada: Boolean = false,
)

@Dao
interface RutasDao {

    @Query("SELECT * FROM rutas WHERE eliminada = 0 ORDER BY creadaEn DESC")
    fun observarTodas(): Flow<List<RutaEntidad>>

    @Query("SELECT * FROM rutas WHERE id = :id AND eliminada = 0")
    fun observar(id: String): Flow<RutaEntidad?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(ruta: RutaEntidad)

    @Query("UPDATE rutas SET nombre = :nombre, actualizadaEn = :ahora, pendienteSync = 1 WHERE id = :id")
    suspend fun renombrar(id: String, nombre: String, ahora: Long)

    @Query("UPDATE rutas SET visibilidad = :visibilidad, actualizadaEn = :ahora, pendienteSync = 1 WHERE id = :id")
    suspend fun cambiarVisibilidad(id: String, visibilidad: String, ahora: Long)

    @Query("UPDATE rutas SET eliminada = 1, actualizadaEn = :ahora, pendienteSync = 1 WHERE id = :id")
    suspend fun marcarEliminada(id: String, ahora: Long)
}
