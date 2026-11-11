package com.udenar.runway.data.lugares

import com.udenar.runway.data.red.JsonRunway
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotonTest {

    private fun leer(json: String) = JsonRunway.decodeFromString<RespuestaPhoton>(json).aLugares()

    @Test
    fun lugarConNombreUsaCiudadYPaisComoDetalle() {
        val lugares = leer(
            """{"features":[{"geometry":{"coordinates":[-77.6390149,0.8256698]},
            "properties":{"name":"Plaza La Pola","city":"Ipiales","state":"Nariño","country":"Colombia","osm_key":"place"}}]}""",
        )
        assertEquals(1, lugares.size)
        assertEquals("Plaza La Pola", lugares[0].nombre)
        assertEquals("Ipiales, Nariño, Colombia", lugares[0].detalle)
        assertEquals(0.8256698, lugares[0].coordenada.lat, 1e-9)
        assertEquals(-77.6390149, lugares[0].coordenada.lon, 1e-9)
    }

    @Test
    fun sinNombreUsaLaCalleYNoLaRepiteEnElDetalle() {
        val lugares = leer(
            """{"features":[{"geometry":{"coordinates":[-74.07,4.6]},
            "properties":{"street":"Carrera 7","housenumber":"12-30","city":"Bogotá","country":"Colombia"}}]}""",
        )
        assertEquals("Carrera 7 12-30", lugares[0].nombre)
        assertEquals("Bogotá, Colombia", lugares[0].detalle)
    }

    @Test
    fun resultadosRepetidosSeMuestranUnaVez() {
        val elemento = """{"geometry":{"coordinates":[1.0,2.0]},"properties":{"name":"Parque","city":"Pasto"}}"""
        assertEquals(1, leer("""{"features":[$elemento,$elemento]}""").size)
    }

    @Test
    fun ciudadSinDetalleNoRepiteSuNombre() {
        val lugares = leer(
            """{"features":[{"geometry":{"coordinates":[-77.28,1.21]},
            "properties":{"name":"Pasto","city":"Pasto","state":"Nariño","country":"Colombia"}}]}""",
        )
        assertEquals("Nariño, Colombia", lugares[0].detalle)
    }
}
