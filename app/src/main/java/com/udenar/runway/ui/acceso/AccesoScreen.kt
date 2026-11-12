package com.udenar.runway.ui.acceso

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udenar.runway.R
import com.udenar.runway.ui.components.AvisoEstado
import com.udenar.runway.ui.components.BotonCircular
import com.udenar.runway.ui.components.BotonRunway
import com.udenar.runway.ui.components.CampoTexto
import com.udenar.runway.ui.components.EstiloBoton
import com.udenar.runway.ui.components.SelectorAvatar
import com.udenar.runway.ui.components.TipoAviso
import com.udenar.runway.ui.components.TipoCampo
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway

/** Acceso sin sesión: bienvenida → registro o inicio de sesión. */
@Composable
fun AccesoScreen(vm: AccesoViewModel = viewModel(factory = AccesoViewModel.Factory)) {
    val e by vm.estado.collectAsStateWithLifecycle()
    BackHandler(enabled = e.pantalla != PantallaAcceso.Bienvenida) { vm.ir(PantallaAcceso.Bienvenida) }

    AnimatedContent(e.pantalla, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "acceso") { pantalla ->
        when (pantalla) {
            PantallaAcceso.Bienvenida -> Bienvenida(
                onComenzar = { vm.ir(PantallaAcceso.Registro) },
                onIngresar = { vm.ir(PantallaAcceso.Ingreso) },
            )
            else -> Formulario(e, vm)
        }
    }
}

/** Pantalla de inicio con la ilustración de RUNWAY y el botón "Comenzar". */
@Composable
private fun Bienvenida(onComenzar: () -> Unit, onIngresar: () -> Unit) {
    // La imagen es 9:16 y los teléfonos suelen ser más altos: se ajusta al ancho (sin cortar "RUNWAY")
    // y el fondo continúa con los tonos de la parte baja de la ilustración
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFB58CF2), Color(0xFF9C7CEB), Runway.MoradoOscuro))),
    ) {
        Image(
            painterResource(R.drawable.bienvenida),
            contentDescription = "RUNWAY: personaje con ropa deportiva",
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        )
        // Degradado inferior para que los botones se lean sobre la imagen
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(260.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Runway.MoradoOscuro.copy(alpha = 0.85f)))),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(Espacio.margen),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Planifica, corre y comparte tus recorridos",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Espacio.l))
            BotonRunway(
                "Comenzar",
                onClick = onComenzar,
                estilo = EstiloBoton.Lima,
                icono = Icons.AutoMirrored.Outlined.ArrowForward,
                alto = 60.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onIngresar, modifier = Modifier.padding(top = Espacio.s)) {
                Text("Ya tengo cuenta · Iniciar sesión", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun Formulario(e: AccesoEstado, vm: AccesoViewModel) {
    val foco = LocalFocusManager.current
    val registro = e.pantalla == PantallaAcceso.Registro
    Column(
        Modifier
            .fillMaxSize()
            .background(Runway.Fondo)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Espacio.margen),
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Espacio.l)) {
            BotonCircular(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", onClick = { vm.ir(PantallaAcceso.Bienvenida) })
        }
        Image(
            painterResource(R.drawable.logo_runway),
            contentDescription = "RUNWAY",
            modifier = Modifier.fillMaxWidth(0.62f).align(Alignment.CenterHorizontally),
        )
        Text(if (registro) "Crea tu cuenta" else "Hola de nuevo", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (registro) "Así guardamos tus entrenamientos y podrás correr con tus amigos."
            else "Inicia sesión para seguir donde lo dejaste.",
            style = MaterialTheme.typography.bodyLarge,
            color = Runway.TextoSecundario,
        )

        if (registro) {
            Text("Elige tu avatar", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Espacio.s))
            SelectorAvatar(e.avatar, vm::onAvatar)
            CampoTexto(e.nombre, vm::onNombre, "Nombre o alias", error = e.errorNombre, accion = ImeAction.Next)
        }
        CampoTexto(e.correo, vm::onCorreo, "Correo", error = e.errorCorreo, tipo = TipoCampo.Correo, accion = ImeAction.Next)
        CampoTexto(
            e.contrasena, vm::onContrasena, "Contraseña",
            error = e.errorContrasena,
            ayuda = if (registro) "Mínimo 8 caracteres, con letras y números" else null,
            tipo = TipoCampo.Contrasena,
            onListo = {
                foco.clearFocus()
                vm.enviar()
            },
        )
        e.mensaje?.let {
            AvisoEstado(
                if (it.startsWith("Te enviamos")) TipoAviso.PendienteSincronizar else TipoAviso.Error,
                it,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BotonRunway(
            if (registro) "Crear cuenta" else "Iniciar sesión",
            onClick = {
                foco.clearFocus()
                vm.enviar()
            },
            cargando = e.enviando,
            modifier = Modifier.fillMaxWidth().padding(top = Espacio.s),
        )
        TextButton(
            onClick = { vm.ir(if (registro) PantallaAcceso.Ingreso else PantallaAcceso.Registro) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                if (registro) "¿Ya tienes cuenta? Inicia sesión" else "¿No tienes cuenta? Créala",
                color = Runway.MoradoOscuro,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.height(Espacio.xl))
    }
}
