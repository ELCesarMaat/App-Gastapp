package com.binc.gastapp.demo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.binc.gastapp.demo.datos.Gasto
import com.binc.gastapp.demo.datos.Hoy
import com.binc.gastapp.demo.datos.Rango
import com.binc.gastapp.demo.datos.RepositorioDemo
import com.binc.gastapp.demo.datos.delDia
import com.binc.gastapp.demo.datos.quincenaDe
import com.binc.gastapp.demo.ui.pantallas.AhorrosScreen
import com.binc.gastapp.demo.ui.pantallas.AjustesScreen
import com.binc.gastapp.demo.ui.pantallas.ExplorarPeriodoScreen
import com.binc.gastapp.demo.ui.pantallas.FormularioGastoSheet
import com.binc.gastapp.demo.ui.pantallas.PerfilScreen
import com.binc.gastapp.demo.ui.pantallas.PreferenciaTema
import com.binc.gastapp.demo.ui.pantallas.ResumenScreen
import com.binc.gastapp.demo.ui.pantallas.SuscripcionesScreen
import com.binc.gastapp.demo.ui.pantallas.TarjetasScreen
import kotlinx.coroutines.launch

enum class Pestana(val titulo: String, val icono: ImageVector, val iconoSeleccionado: ImageVector) {
    Resumen("Resumen", Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
    Ahorros("Ahorros", Icons.Outlined.Savings, Icons.Rounded.Savings),
    Perfil("Perfil", Icons.Outlined.Person, Icons.Rounded.Person),
    Ajustes("Ajustes", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

/** Pantallas que se apilan encima de las pestanas. */
enum class Capa { Tarjetas, Suscripciones, Periodo }

/** Hoja de gasto abierta: [gasto] null es un gasto nuevo. */
private data class HojaGasto(val gasto: Gasto?)

/**
 * Raiz del demo. La navegacion es estado simple a proposito: en la app real iria
 * Navigation Compose, pero aqui solo importa como se ve y como se mueve.
 */
@Composable
fun GastappDemoApp(
    pantallaInicial: String,
    hojaInicial: Boolean,
    editarInicial: Boolean,
    tema: PreferenciaTema,
    onTema: (PreferenciaTema) -> Unit,
    dinamico: Boolean,
    onDinamico: (Boolean) -> Unit,
) {
    var pestana by remember {
        mutableStateOf(Pestana.entries.firstOrNull { it.name.equals(pantallaInicial, ignoreCase = true) } ?: Pestana.Resumen)
    }
    var capa by remember {
        mutableStateOf(Capa.entries.firstOrNull { it.name.equals(pantallaInicial, ignoreCase = true) })
    }
    var periodo by remember { mutableStateOf(quincenaDe(Hoy)) }
    var dia by remember { mutableStateOf(Hoy) }
    var hoja by remember {
        mutableStateOf(
            when {
                editarInicial -> HojaGasto(RepositorioDemo.gastos.delDia(Hoy).firstOrNull())
                hojaInicial -> HojaGasto(null)
                else -> null
            }
        )
    }
    val avisos = remember { SnackbarHostState() }
    val alcance = rememberCoroutineScope()
    val listaResumen = rememberLazyListState()
    val listaAhorros = rememberLazyListState()

    fun cambiarPeriodo(nuevo: Rango) {
        periodo = nuevo
        dia = if (nuevo.contiene(Hoy)) Hoy else minOf(nuevo.fin, Hoy)
    }

    fun avisar(texto: String, deshacer: (() -> Unit)? = null) {
        avisos.currentSnackbarData?.dismiss()
        alcance.launch {
            val resultado = avisos.showSnackbar(
                message = texto,
                actionLabel = if (deshacer != null) "Deshacer" else null,
                duration = SnackbarDuration.Short,
            )
            if (resultado == SnackbarResult.ActionPerformed) deshacer?.invoke()
        }
    }

    fun guardar(gasto: Gasto, esNuevo: Boolean) {
        RepositorioDemo.guardar(gasto)
        if (!periodo.contiene(gasto.fecha)) periodo = quincenaDe(gasto.fecha)
        dia = gasto.fecha
        avisar(if (esNuevo) "Gasto guardado" else "Cambios guardados")
    }

    fun eliminar(gasto: Gasto) {
        RepositorioDemo.eliminar(gasto)
        avisar("Gasto eliminado") { RepositorioDemo.guardar(gasto) }
    }

    // Desde otra pestana, "atras" regresa a Resumen antes de salir de la app.
    BackHandler(enabled = capa == null && pestana != Pestana.Resumen) { pestana = Pestana.Resumen }

    PilaConAtrasPredictivo(
        capa = capa,
        onAtras = { capa = null },
        entrada = { if (it == Capa.Periodo) EntradaCapa.DesdeAbajo else EntradaCapa.Lateral },
        base = {
            PantallaPrincipal(
                pestana = pestana,
                onPestana = { pestana = it },
                avisos = avisos,
                listaResumen = listaResumen,
                listaAhorros = listaAhorros,
                onNuevoGasto = { hoja = HojaGasto(null) },
                onExplorar = { capa = Capa.Periodo },
            ) { p, padding ->
                when (p) {
                    Pestana.Resumen -> ResumenScreen(
                        contentPadding = padding,
                        estadoLista = listaResumen,
                        periodo = periodo,
                        onPeriodo = ::cambiarPeriodo,
                        dia = dia,
                        onDia = { dia = it },
                        onIrAHoy = {
                            if (!periodo.contiene(Hoy)) periodo = quincenaDe(Hoy)
                            dia = Hoy
                        },
                        onExplorar = { capa = Capa.Periodo },
                        onAbrirTarjetas = { capa = Capa.Tarjetas },
                        onAbrirSuscripciones = { capa = Capa.Suscripciones },
                        onEditarGasto = { hoja = HojaGasto(it) },
                    )
                    Pestana.Ahorros -> AhorrosScreen(
                        contentPadding = padding,
                        estadoLista = listaAhorros,
                        onAbrirTarjetas = { capa = Capa.Tarjetas },
                    )
                    Pestana.Perfil -> PerfilScreen(contentPadding = padding)
                    Pestana.Ajustes -> AjustesScreen(
                        contentPadding = padding,
                        tema = tema,
                        onTema = onTema,
                        dinamico = dinamico,
                        onDinamico = onDinamico,
                    )
                }
            }
        },
        contenido = { c ->
            when (c) {
                Capa.Tarjetas -> TarjetasScreen(onVolver = { capa = null })
                Capa.Suscripciones -> SuscripcionesScreen(onVolver = { capa = null })
                Capa.Periodo -> ExplorarPeriodoScreen(
                    rangoInicial = periodo,
                    onCerrar = { capa = null },
                    onAplicar = { rango ->
                        cambiarPeriodo(rango)
                        capa = null
                    },
                )
            }
        },
    )

    hoja?.let { abierta ->
        key(abierta) {
            FormularioGastoSheet(
                gastoInicial = abierta.gasto,
                fechaPorDefecto = dia,
                onGuardar = { gasto -> guardar(gasto, esNuevo = abierta.gasto == null) },
                onEliminar = abierta.gasto?.let { gasto -> { eliminar(gasto) } },
                onCerrar = { hoja = null },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PantallaPrincipal(
    pestana: Pestana,
    onPestana: (Pestana) -> Unit,
    avisos: SnackbarHostState,
    listaResumen: LazyListState,
    listaAhorros: LazyListState,
    onNuevoGasto: () -> Unit,
    onExplorar: () -> Unit,
    contenido: @Composable (Pestana, PaddingValues) -> Unit,
) {
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    // Cada pestana empieza con la barra superior "limpia".
    LaunchedEffect(pestana) { scroll.state.contentOffset = 0f }

    val fabExtendido = when (pestana) {
        Pestana.Resumen -> listaResumen.seDesplazaHaciaArriba()
        Pestana.Ahorros -> listaAhorros.seDesplazaHaciaArriba()
        else -> true
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = pestana.titulo,
                        transitionSpec = { fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90)) },
                        label = "titulo",
                    ) { Text(it) }
                },
                actions = {
                    AnimatedVisibility(
                        visible = pestana == Pestana.Resumen,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                    ) {
                        IconButton(onClick = onExplorar) {
                            Icon(Icons.Rounded.DateRange, contentDescription = "Explorar periodo")
                        }
                    }
                },
                scrollBehavior = scroll,
            )
        },
        bottomBar = {
            NavigationBar {
                Pestana.entries.forEach { p ->
                    NavigationBarItem(
                        selected = p == pestana,
                        onClick = { onPestana(p) },
                        icon = {
                            Icon(
                                if (p == pestana) p.iconoSeleccionado else p.icono,
                                contentDescription = null,
                            )
                        },
                        label = { Text(p.titulo) },
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = pestana == Pestana.Resumen || pestana == Pestana.Ahorros,
                enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                ExtendedFloatingActionButton(
                    onClick = onNuevoGasto,
                    expanded = fabExtendido,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Nuevo gasto") },
                )
            }
        },
        snackbarHost = { SnackbarHost(avisos) },
    ) { padding ->
        // "Fade through" de Material entre pestanas.
        AnimatedContent(
            targetState = pestana,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 90)) +
                    scaleIn(tween(220, delayMillis = 90), initialScale = 0.94f)) togetherWith
                    fadeOut(tween(90))
            },
            label = "pestanas",
        ) { p ->
            contenido(p, padding)
        }
    }
}
