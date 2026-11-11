package com.udenar.runway

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.udenar.runway.ui.RunwayRaiz
import com.udenar.runway.ui.theme.RunwayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tema siempre claro: íconos oscuros en las barras del sistema
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // Si la app se cerró a mitad de un entrenamiento, se recupera desde SQLite (pausado)
        lifecycleScope.launch { (application as RunwayApp).contenedor.sesion.recuperarSiHaceFalta() }
        setContent {
            RunwayTheme {
                RunwayRaiz()
            }
        }
    }
}
