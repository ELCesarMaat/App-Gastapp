package com.binc.gastapp.ui.navigation

import android.annotation.SuppressLint
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.binc.gastapp.ui.components.EmphasizedAccelerate
import com.binc.gastapp.ui.components.EmphasizedDecelerate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Atras predictivo de las pantallas apiladas (spike 0.7 del plan), con el mismo efecto
// que PilaAtrasPredictivo del demo: mientras el dedo arrastra, la pantalla se encoge a
// 0.9, se redondea, se recorre hacia el lado del gesto, sigue un poco al dedo en
// vertical y deja ver la de abajo atenuada; al soltar sale desde donde quedo.
//
// Lo mueve Navigation Compose: su NavHost "busca" (seek) la transicion de regreso con
// el progreso del gesto, asi que la pantalla de abajo ya esta compuesta y visible
// durante el arrastre. Lo unico que NavHost no le da a las pantallas es el lado del
// gesto y la altura del dedo; eso lo aporta ProvideBackGesture.

/** Duracion de la salida al regresar. NavHost busca sobre ella durante el gesto. */
const val PopDurationMillis = 350

/** Duracion de la entrada de una pantalla apilada. */
const val PushDurationMillis = 420

/** Atenuacion de la pantalla de abajo mientras hay una apilada encima. */
private const val VeilAlpha = 0.32f

/** Lo que se sabe del gesto de atras en curso. Lo escribe ProvideBackGesture. */
@Stable
class BackGestureState internal constructor(private val scope: CoroutineScope) {

    /** 0 al empezar el gesto, 1 con el dedo al otro lado de la pantalla. */
    var progress by mutableFloatStateOf(0f)
        private set

    /** BackEventCompat.EDGE_LEFT o EDGE_RIGHT. */
    var swipeEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
        private set

    /** Cuanto se ha movido el dedo en vertical desde que empezo el gesto, en px. */
    var fingerDeltaY by mutableFloatStateOf(0f)
        private set

    var isDragging by mutableStateOf(false)
        private set

    /** Progreso en el que se solto el dedo si el gesto se confirmo; null si no hubo gesto. */
    var releasedAt by mutableStateOf<Float?>(null)
        private set

    private var startY = 0f
    private var settling: Job? = null

    internal fun onStarted(event: BackEventCompat) {
        settling?.cancel()
        startY = event.touchY
        releasedAt = null
        isDragging = true
        update(event)
    }

    internal fun onProgressed(event: BackEventCompat) {
        if (isDragging) update(event)
    }

    internal fun onCommitted() {
        releasedAt = if (isDragging) progress else null
        isDragging = false
    }

    /** Gesto cancelado: la pantalla regresa a su lugar con un resorte, como en el demo. */
    internal fun onCancelled() {
        isDragging = false
        val fromProgress = progress
        val fromDeltaY = fingerDeltaY
        settling = scope.launch {
            animate(1f, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { f, _ ->
                progress = fromProgress * f
                fingerDeltaY = fromDeltaY * f
            }
        }
    }

    /** La pantalla que se cerro ya salio: el siguiente regreso empieza de cero. */
    internal fun reset() {
        if (isDragging) return
        settling?.cancel()
        progress = 0f
        fingerDeltaY = 0f
        releasedAt = null
    }

    private fun update(event: BackEventCompat) {
        progress = event.progress
        swipeEdge = event.swipeEdge
        fingerDeltaY = event.touchY - startY
    }
}

val LocalBackGesture = staticCompositionLocalOf<BackGestureState?> { null }

/**
 * Se pone entre la Activity y el NavHost para enterarse de cada gesto de atras sin
 * quitarselo a Navigation: le da al contenido un despachador propio y, desde el de la
 * Activity, le reenvia cada evento despues de anotarlo en [LocalBackGesture].
 */
@Composable
fun ProvideBackGesture(content: @Composable () -> Unit) {
    val parent = checkNotNull(LocalOnBackPressedDispatcherOwner.current) {
        "ProvideBackGesture necesita un OnBackPressedDispatcherOwner (la Activity)"
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val gesture = remember { BackGestureState(scope) }
    val relay = remember { BackRelay(gesture) }
    val owner = remember(lifecycleOwner) {
        object : OnBackPressedDispatcherOwner {
            override val onBackPressedDispatcher: OnBackPressedDispatcher = relay.child
            override val lifecycle get() = lifecycleOwner.lifecycle
        }
    }

    DisposableEffect(parent, lifecycleOwner) {
        parent.onBackPressedDispatcher.addCallback(lifecycleOwner, relay)
        onDispose { relay.remove() }
    }

    CompositionLocalProvider(
        LocalOnBackPressedDispatcherOwner provides owner,
        LocalBackGesture provides gesture,
        content = content,
    )
}

/**
 * Callback en el despachador de la Activity que reenvia todo al despachador hijo. Solo
 * esta activo cuando el hijo tiene a quien entregarle el gesto; si no, el sistema hace
 * su animacion de regresar al inicio.
 */
// Los dispatchOnBack* son publicos pero estan marcados como "para pruebas".
@SuppressLint("VisibleForTests")
private class BackRelay(private val gesture: BackGestureState) : OnBackPressedCallback(false) {

    val child = OnBackPressedDispatcher(null, Consumer<Boolean> { isEnabled = it })

    override fun handleOnBackStarted(backEvent: BackEventCompat) {
        gesture.onStarted(backEvent)
        child.dispatchOnBackStarted(backEvent)
    }

    override fun handleOnBackProgressed(backEvent: BackEventCompat) {
        gesture.onProgressed(backEvent)
        child.dispatchOnBackProgressed(backEvent)
    }

    override fun handleOnBackPressed() {
        gesture.onCommitted()
        child.onBackPressed()
    }

    override fun handleOnBackCancelled() {
        gesture.onCancelled()
        child.dispatchOnBackCancelled()
    }
}

// ------------------------------------------------- Transiciones del NavHost

/** La pantalla de abajo se hace un poco a un lado cuando se apila otra encima. */
fun stackedBaseExit(): ExitTransition =
    slideOutHorizontally(tween(PushDurationMillis, easing = EmphasizedDecelerate)) { -(it * 0.08f).roundToInt() }

/**
 * Y regresa a su sitio al cerrar la de arriba. Lineal porque durante el gesto la mueve
 * el dedo: asi avanza igual que el progreso del gesto.
 */
fun stackedBaseReturn(): EnterTransition =
    slideInHorizontally(tween(PopDurationMillis, easing = LinearEasing)) { -(it * 0.08f).roundToInt() }

/**
 * Envoltura de cada pantalla apilada. Pinta el velo sobre la de abajo y anima su propia
 * entrada y salida; por eso en el NavHost las apiladas usan EnterTransition.None y
 * ExitTransition.None al regresar.
 */
@Composable
fun AnimatedContentScope.PredictiveBackLayer(
    entry: NavBackStackEntry,
    navController: NavController,
    content: @Composable () -> Unit,
) {
    val gesture = LocalBackGesture.current
    val fromBottom = entry.entersFromBottom
    val backStack by navController.currentBackStack.collectAsState()
    val popped = backStack.none { it.id == entry.id }
    val exiting = transition.targetState == EnterExitState.PostExit

    // Avance de la salida, lineal y con la misma duracion que la base: durante el gesto
    // vale lo mismo que el progreso que NavHost le pasa a la transicion.
    val exitProgress by transition.animateFloat(
        transitionSpec = { tween(PopDurationMillis, easing = LinearEasing) },
        label = "salida",
    ) { if (it == EnterExitState.PostExit) 1f else 0f }

    // El gesto es de esta pantalla si empezo mientras ella era la que se iba.
    var ownsGesture by remember { mutableStateOf(false) }
    val dragging = gesture?.isDragging == true
    LaunchedEffect(exiting, dragging) {
        if (!exiting) ownsGesture = false else if (dragging) ownsGesture = true
    }
    DisposableEffect(gesture) {
        onDispose { gesture?.reset() }
    }

    val pose = if (ownsGesture && gesture != null) gesture.progress else 0f
    val deltaY = if (ownsGesture && gesture != null) gesture.fingerDeltaY else 0f
    val towardRight = !ownsGesture || gesture?.swipeEdge != BackEventCompat.EDGE_RIGHT
    // Salida despues de soltar el dedo (o de tocar la flecha): empieza desde la pose.
    val start = if (ownsGesture) gesture?.releasedAt ?: 0f else 0f
    val leaving = if (exiting && popped) {
        val fraction = ((exitProgress - start) / (1f - start).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
        EmphasizedAccelerate.transform(fraction)
    } else {
        0f
    }

    Box(Modifier.fillMaxSize()) {
        // Velo sobre la pantalla de abajo; es lo que se ve atenuado durante el gesto.
        Box(
            Modifier
                .fillMaxSize()
                .animateEnterExit(enter = fadeIn(tween(300)), exit = ExitTransition.None)
                .graphicsLayer { alpha = 1f - leaving }
                .background(Color.Black.copy(alpha = VeilAlpha))
        )

        Box(
            Modifier
                .fillMaxSize()
                .animateEnterExit(
                    enter = if (fromBottom) {
                        slideInVertically(tween(PushDurationMillis, easing = EmphasizedDecelerate)) { it / 6 } +
                            fadeIn(tween(260))
                    } else {
                        slideInHorizontally(tween(PushDurationMillis, easing = EmphasizedDecelerate)) { it / 4 } +
                            fadeIn(tween(260))
                    },
                    exit = ExitTransition.None,
                )
                .graphicsLayer {
                    val scale = 1f - 0.1f * pose
                    scaleX = scale
                    scaleY = scale
                    val direction = if (towardRight) 1f else -1f
                    // Se recorre hacia el lado del gesto hasta quedar a 8dp del borde contrario.
                    val margin = 8.dp.toPx()
                    val maxX = (size.width * 0.05f - margin).coerceAtLeast(0f)
                    val maxY = (size.height * 0.05f - margin).coerceAtLeast(0f)
                    translationX = direction * maxX * pose +
                        if (fromBottom) 0f else direction * size.width / 4f * leaving
                    // Y sigue un poco al dedo en vertical.
                    translationY = (deltaY * 0.5f).coerceIn(-maxY, maxY) * pose +
                        if (fromBottom) size.height / 6f * leaving else 0f
                    alpha = (1f - leaving * 1.2f).coerceAtLeast(0f)
                    // Las esquinas se redondean en cuanto empieza el gesto.
                    shape = RoundedCornerShape((32f * (pose * 4f).coerceAtMost(1f)).dp)
                    clip = pose > 0f
                    shadowElevation = 16.dp.toPx() * pose
                }
        ) {
            content()
        }
    }
}
