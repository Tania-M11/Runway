package com.udenar.runway.data.local

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Base de datos SQLite del teléfono (enfoque offline-first): todo se guarda primero aquí
 * y funciona sin internet. En la parte 8 se sincroniza con Supabase.
 *
 * Versión 2 agrega los entrenamientos y sus puntos GPS; Room migra sola desde la 1
 * (las rutas guardadas se conservan).
 */
@Database(
    entities = [RutaEntidad::class, EntrenamientoEntidad::class, PuntoGpsEntidad::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class RunwayDatabase : RoomDatabase() {
    abstract fun rutas(): RutasDao
    abstract fun entrenamientos(): EntrenamientosDao
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

/**
 * Tabla `entrenamientos`. Se actualiza con cada punto GPS aceptado, así que si la app
 * se cierra de golpe la sesión se puede recuperar (plan, sección 10).
 */
@Entity(tableName = "entrenamientos")
data class EntrenamientoEntidad(
    @PrimaryKey val id: String,
    val rutaId: String?,
    val rutaNombre: String?,
    /** Trazado de la ruta de referencia (polyline6), si se entrenó siguiendo una. */
    val rutaTrazado: String?,
    val inicioEn: Long,
    val finEn: Long?,
    /** ACTIVO, PAUSADO o FINALIZADO */
    val estado: String,
    val distanciaM: Double,
    /** Tiempo en movimiento acumulado hasta el inicio del tramo actual. */
    val duracionMs: Long,
    /** Inicio del tramo activo actual; null si está pausado o finalizado. */
    val tramoInicioEn: Long?,
    /** Número del tramo actual (aumenta con cada reanudación). */
    val segmento: Int,
    val subidaM: Double,
    /** Trazado completo comprimido (polyline6), se llena al finalizar. */
    val trazado: String?,
    val actualizadoEn: Long,
    val pendienteSync: Boolean = true,
    val eliminada: Boolean = false,
)

/** Tabla `puntos_gps`: cada posición aceptada, guardada en el momento (no se pierde nada). */
@Entity(
    tableName = "puntos_gps",
    foreignKeys = [
        ForeignKey(
            entity = EntrenamientoEntidad::class,
            parentColumns = ["id"],
            childColumns = ["entrenamientoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entrenamientoId")],
)
data class PuntoGpsEntidad(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entrenamientoId: String,
    val segmento: Int,
    val lat: Double,
    val lon: Double,
    val altitudM: Double?,
    val precisionM: Float,
    val velocidadMs: Float?,
    val tiempoMs: Long,
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

    // --- Sincronización (parte 9) ---

    @Query("SELECT * FROM rutas WHERE pendienteSync = 1")
    suspend fun pendientes(): List<RutaEntidad>

    @Query("SELECT COUNT(*) FROM rutas WHERE pendienteSync = 1")
    fun contarPendientes(): Flow<Int>

    /** Solo si no cambió mientras se subía (si cambió, queda pendiente para la próxima vez). */
    @Query("UPDATE rutas SET pendienteSync = 0 WHERE id = :id AND actualizadaEn = :version")
    suspend fun marcarSincronizada(id: String, version: Long)

    @Query("DELETE FROM rutas WHERE id = :id AND eliminada = 1")
    suspend fun borrarEliminada(id: String)

    @Query("SELECT COUNT(*) FROM rutas")
    suspend fun total(): Int
}

@Dao
interface EntrenamientosDao {

    @Insert
    suspend fun insertar(e: EntrenamientoEntidad)

    @Update
    suspend fun actualizar(e: EntrenamientoEntidad)

    @Insert
    suspend fun insertarPunto(p: PuntoGpsEntidad)

    /** Punto + métricas en una sola transacción: o se guardan ambos o ninguno. */
    @Transaction
    suspend fun registrarPunto(p: PuntoGpsEntidad, e: EntrenamientoEntidad) {
        insertarPunto(p)
        actualizar(e)
    }

    @Query("SELECT * FROM entrenamientos WHERE id = :id")
    suspend fun obtener(id: String): EntrenamientoEntidad?

    @Query("SELECT * FROM puntos_gps WHERE entrenamientoId = :id ORDER BY tiempoMs, id")
    suspend fun puntos(id: String): List<PuntoGpsEntidad>

    /** Sesión que quedó abierta (la app se cerró a mitad de un entrenamiento). */
    @Query("SELECT * FROM entrenamientos WHERE estado IN ('ACTIVO', 'PAUSADO') AND eliminada = 0 ORDER BY inicioEn DESC LIMIT 1")
    suspend fun sinTerminar(): EntrenamientoEntidad?

    @Query("SELECT * FROM entrenamientos WHERE estado = 'FINALIZADO' AND eliminada = 0 ORDER BY inicioEn DESC")
    fun observarFinalizados(): Flow<List<EntrenamientoEntidad>>

    /** Descartar: borrado real (nunca se sincronizó); los puntos se borran en cascada. */
    @Query("DELETE FROM entrenamientos WHERE id = :id")
    suspend fun borrar(id: String)

    // --- Sincronización (parte 9): solo se suben los entrenamientos terminados ---

    @Query("SELECT * FROM entrenamientos WHERE pendienteSync = 1 AND estado = 'FINALIZADO'")
    suspend fun pendientes(): List<EntrenamientoEntidad>

    @Query("SELECT COUNT(*) FROM entrenamientos WHERE pendienteSync = 1 AND estado = 'FINALIZADO'")
    fun contarPendientes(): Flow<Int>

    @Query("UPDATE entrenamientos SET pendienteSync = 0 WHERE id = :id")
    suspend fun marcarSincronizado(id: String)

    @Query("SELECT COUNT(*) FROM entrenamientos")
    suspend fun total(): Int

    /** Entrenamiento descargado del servidor con sus puntos reconstruidos. */
    @Transaction
    suspend fun insertarDescargado(e: EntrenamientoEntidad, puntos: List<PuntoGpsEntidad>) {
        insertar(e)
        puntos.forEach { insertarPunto(it) }
    }
}
