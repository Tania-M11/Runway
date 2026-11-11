package com.udenar.runway.data.red

import com.udenar.runway.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

val JsonRunway = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * Cliente HTTP único de la app. Los servicios públicos (Photon, Valhalla) piden
 * identificarse con un User-Agent propio y no saturarlos.
 */
fun crearClienteHttp(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = true
    install(ContentNegotiation) { json(JsonRunway) }
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 20_000
    }
    defaultRequest {
        headers.append(HttpHeaders.UserAgent, "RUNWAY/${BuildConfig.VERSION_NAME} (Android; proyecto academico)")
    }
}
