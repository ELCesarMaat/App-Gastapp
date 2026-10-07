# Migración de Gastapp a Android nativo (Kotlin + Jetpack Compose)

> Plan de trabajo por fases para reemplazar la app del teléfono (`Gastapp/`, .NET MAUI)
> por una app nativa. Redactado el 2 de octubre de 2026.
> Referencia visual: el demo en `Gastapp.ComposeDemo/` (aprobado).
>
> **Estado (2 oct 2026):** Fases 0, 1, 2 y 3 terminadas. **Sigue la Fase 4 (pantallas).** El proyecto Gradle
> es `Gastapp.Android/` con `:app`, `:wear`, `:core` y `:domain`; Room es la fuente de verdad
> local, `:core` tiene el cliente del API y el `SyncWorker` sube y baja con WorkManager.
> Para retomar en un chat nuevo, leer primero la
> [sección 7](#7-bitácora-y-cómo-retomar). Se trabaja un chat por fase.

## Índice

1. [Resumen](#1-resumen)
2. [Decisiones tomadas](#2-decisiones-tomadas)
3. [Estructura del proyecto](#3-estructura-del-proyecto)
4. [Calendario](#4-calendario)
5. [Fases](#5-fases)
   - [Fase 0. Andamiaje y fundaciones](#fase-0-andamiaje-y-fundaciones)
   - [Fase 1. Dominio y paridad con MAUI](#fase-1-dominio-y-paridad-con-maui)
   - [Fase 2. Datos locales](#fase-2-datos-locales)
   - [Fase 3. API, sesión y sincronización](#fase-3-api-sesión-y-sincronización)
   - [Fase 4. Pantallas](#fase-4-pantallas)
   - [Fase 5. Funciones de plataforma](#fase-5-funciones-de-plataforma)
   - [Fase 6. Pruebas integrales y pulido](#fase-6-pruebas-integrales-y-pulido)
   - [Fase 7. Lanzamiento y corte](#fase-7-lanzamiento-y-corte)
6. [Anexos](#6-anexos)
7. [Bitácora y cómo retomar](#7-bitácora-y-cómo-retomar)

---

## 1. Resumen

**Qué se hace.** Se reescribe la app del teléfono en Kotlin con Jetpack Compose y
Material 3, dentro del proyecto Gradle donde ya vive la app del reloj.

**Qué no cambia.**
- El API (`Gastapp-API/`) y la base de Neon. Los contratos JSON se respetan tal cual.
- `Gastapp.Models/` sigue existiendo porque lo compila el API.
- La app del reloj no cambia de lógica; solo se muda de carpeta y comparte firma con el teléfono.

**Qué se acepta perder.** La base SQLite local de la versión MAUI. Somos pocos usuarios y
todo está respaldado en la nube: el primer arranque nativo pide iniciar sesión y baja
todo del servidor. Lo único que se pierde es lo que no se haya sincronizado; por eso, antes
de publicar, cada usuario abre la versión MAUI con internet (ver Fase 7).

**Cómo llega a los usuarios.** Mismo paquete (`com.binc.gastapp`), mismo keystore y un
`versionCode` mayor que el de MAUI. La app MAUI ya instalada detecta la nueva versión en
GitHub Releases y la ofrece como una actualización normal.

**Forma de trabajo.**
- Cada fase en su propia rama (`migracion/fase-0-andamiaje`, ...). Los commits y el push los haces tú.
- Cada fase termina con criterios de salida verificables. No se pasa a la siguiente sin cumplirlos.
- Se prueba en el emulador (AVD `Pixel_10a`, Android 17 con gestos), **nunca en el teléfono
  del usuario** (lo pidió en la Fase 0). Lo que necesite el reloj real o el teléfono real
  (Data Layer) se consulta antes.

---

## 2. Decisiones tomadas

| Tema | Decisión | Por qué |
|---|---|---|
| Ubicación | `Gastapp.Android/` (antes `Gastapp.WearOS/`, **renombrado en la Fase 0**), con módulos `:app` (teléfono), `:wear` (reloj), `:core` (red compartida) y `:domain` (lógica pura) | Un solo proyecto Gradle: misma firma de debug y release, versiones alineadas y código común entre teléfono y reloj |
| Paquete | `applicationId = "com.binc.gastapp"`, igual que MAUI y que el reloj | Actualizar encima de MAUI y que la Wearable Data Layer entregue mensajes |
| Versión | `versionCode` desde **200** y `versionName` **2.0.0** | Debe superar al de MAUI (131 en `origin/master`, 1.1.3-alpha1); el 200 marca la era nativa |
| SDK | `minSdk 31` (Android 12, **decidido**), `targetSdk 36`, `compileSdk 36` | Deja fuera Android 8 a 11. A cambio: `java.time` nativo, color dinámico (Material You) y Splash Screen en todos los teléfonos |
| UI | Compose + Material 3 con tema de marca desde `#126E63`. Material You como interruptor opcional | Lo aprobado en el demo |
| Colores de estado | `#C62828`, `#D97706` y `#126E63` fuera del esquema de Material, con variante oscura | Significan algo; no deben cambiar con el fondo de pantalla |
| Arquitectura | MVVM: `ViewModel` + `StateFlow<UiState>`, repositorios y funciones de dominio puras en el módulo `:domain` (Kotlin/JVM, sin Android) | Patrón estándar de Android. Al ser un módulo JVM, la lógica no puede depender de Android aunque se quiera, y sus pruebas corren en segundos |
| Inyección | Hilt | El estándar de Android, integrado con ViewModel, WorkManager y Navigation |
| Navegación | Navigation Compose con rutas tipadas **para todo** (decidido en el spike 0.7). Las pestañas son estado de la pantalla principal; las pantallas apiladas se envuelven en `PredictiveBackLayer` para el atrás predictivo del demo | Navigation ya "busca" la transición con el gesto; solo le faltaba el lado y la altura del dedo, que aporta `ProvideBackGesture` |
| Base local | Room con esquema nuevo. **No** se importa la base de MAUI | Acordado: los datos están en la nube |
| Dinero | `Long` en centavos en Room; `BigDecimal` con escala 2 en dominio y UI; redondeo `HALF_EVEN` | Sumas exactas en SQL. `HALF_EVEN` reproduce el `Math.Round` de .NET |
| Fechas | `LocalDate` para fechas de calendario, `Instant` para instantes, `LocalDateTime` para la fecha de un gasto | Ver anexo C. Es lo más fácil de romper |
| Red | Retrofit + OkHttp + kotlinx.serialization, igual que el reloj | Ya probado en el repo |
| Sincronización | WorkManager: trabajo único `sync` con condición de red, push con `SyncAllData` y pull con `GetSpendings` | Sobrevive a que se cierre la app; hoy los envíos se lanzan sin esperar respuesta |
| Preferencias | DataStore | Reemplaza `Preferences` y `SecureStorage` |
| Notificaciones | WorkManager + `NotificationCompat` con canales | Sobrevive reinicios sin receptor de arranque |
| Nombres en código | Entidades igual que en el API (`Spending`, `CreditCard`, `Subscription`, `Category`); comentarios y textos en español, comentarios sin acentos | El mapeo con los DTO queda 1:1, como en el resto del repo |
| Pruebas | JUnit 4 + `kotlinx-coroutines-test` + Turbine; Robolectric para Room; MockWebServer para contratos del API | Todo corre en la PC, sin emulador |

---

## 3. Estructura del proyecto

```
Gastapp.Android/                      (antes Gastapp.WearOS/)
├── settings.gradle.kts               include(":app", ":wear", ":core", ":domain")
├── gradle/libs.versions.toml         versiones unificadas
├── app/                              TELÉFONO · com.binc.gastapp
│   └── src/main/java/com/binc/gastapp/
│       ├── GastappApplication.kt     @HiltAndroidApp, arranque (ver 5.5)
│       ├── MainActivity.kt           edge-to-edge, splash, NavHost
│       ├── di/                       módulos de Hilt
│       ├── ui/theme/                 color, tipografía, tema (del demo)
│       ├── ui/components/            MontoAnimado, EstadoChip, FilaAgrupada... (del demo)
│       ├── ui/navigation/            rutas tipadas, barra inferior, pila con atrás predictivo
│       ├── feature/session/          inicio, login, registro, recuperar contraseña
│       ├── feature/summary/          Resumen, Explorar periodo, formulario de gasto
│       ├── feature/savings/          Ahorros
│       ├── feature/cards/            Mis tarjetas, formularios, MSI, pagos
│       ├── feature/subscriptions/    Suscripciones y su formulario
│       ├── feature/profile/          Perfil
│       ├── feature/settings/         Ajustes, dispositivos, respaldos
│       ├── data/local/               Room: entidades, DAOs, convertidores, base
│       ├── data/repository/          repositorios (Room primero, luego encolan sync)
│       ├── data/prefs/               DataStore (sesión, ajustes, borrador de registro)
│       ├── sync/                     SyncWorker, PurgeWorker
│       └── platform/                 notificaciones, respaldo, actualización, reloj
├── domain/                           KOTLIN/JVM PURO · lógica de negocio (Fase 1)
│   ├── src/main/kotlin/com/binc/gastapp/domain/   dinero, fechas, tarjetas, suscripciones, periodos, ahorro
│   └── src/test/                                  pruebas de paridad + fixtures generados desde C#
├── core/                             LIBRERÍA ANDROID compartida teléfono/reloj
│   └── src/main/java/com/binc/gastapp/core/
│       ├── remote/                   GastappApi (Retrofit), DTOs @Serializable, serializadores
│       └── wear/                     rutas y payloads de la Data Layer (WearExpensePayload...)
└── wear/                             RELOJ (sin cambios de lógica)
```

---

## 4. Calendario

Estimación para una persona a tiempo completo.

| Fase | Estado | Duración | Depende de | Se puede traslapar con |
|---|---|---|---|---|
| 0. Andamiaje y fundaciones | ✅ Terminada (2 oct 2026) | 1 semana | — | — |
| 1. Dominio y paridad | ✅ Terminada (2 oct 2026) | 1 semana | 0.1 (renombrar) y el módulo `:domain` | 0, 2 |
| 2. Datos locales | ✅ Terminada (2 oct 2026) | 3–4 días | 0 | 1 |
| 3. API, sesión y sincronización | ✅ Terminada (2 oct 2026) | 1 semana | 2 | 4.1 |
| 4. Pantallas | ⬜ Pendiente | 4–5 semanas | 1, 2, 3 | 5.4 (reloj) |
| 5. Funciones de plataforma | ⬜ Pendiente | 1 semana | 2, 3 | final de 4 |
| 6. Pruebas integrales y pulido | ⬜ Pendiente | 1 semana | 4, 5 | — |
| 7. Lanzamiento y corte | ⬜ Pendiente | 2–3 días | 6 | — |
| **Total** | | **≈ 10 semanas** (≈ 8 traslapando) | | |

---

## 5. Fases

Cada fase indica qué archivos de MAUI son su fuente, las tareas en orden, cómo se verifica
y los criterios de salida.

---

### Fase 0. Andamiaje y fundaciones

**Objetivo.** Un proyecto que compile, firme e instale: una app vacía con el tema, la
navegación y la estructura de la sección 3, sin romper el reloj.

**Fuente MAUI.** `Gastapp/Gastapp.csproj` (paquete, versión), `Gastapp/Platforms/Android/AndroidManifest.xml`
(permisos), `Gastapp/MauiProgram.cs` (qué servicios existen) y `Gastapp.ComposeDemo/` (tema y componentes).

**Tareas.**

0.1 **Mudar el proyecto.**
- `git mv Gastapp.WearOS Gastapp.Android`.
- Actualizar las rutas en `CLAUDE.md`, en `README.md` y en las memorias que mencionen `Gastapp.WearOS`.
- Comprobar que `:wear` compila e instala igual que antes.

0.2 **Crear `:app`.**
- Reemplaza la plantilla `mobile/`, que hoy tiene `applicationId com.binc.gastapp.wo` y `wearApp(project(":wear"))`. Las dos cosas se quitan.
- `namespace = "com.binc.gastapp"`, `applicationId = "com.binc.gastapp"`, `minSdk = 31`, `versionCode = 200`, `versionName = "2.0.0"`.
- `buildConfigField` `API_BASE_URL` = `https://app-gastapp.onrender.com/api/`, con la línea comentada para `http://10.0.2.2:5118/api/`.

0.3 **Crear `:core`** (librería Android) y mover ahí los DTO y el cliente Retrofit cuando llegue la Fase 3.

0.4 **Versiones.** Subir el catálogo a lo que usa el demo (Kotlin 2.2.x, Compose BOM 2025.10 o
posterior, Material 3 1.4) y agregar:
- Hilt (+ `hilt-navigation-compose`, `hilt-work`).
- Navigation Compose, DataStore y `material-icons-extended`.
- Pruebas: `kotlinx-coroutines-test`, Turbine, Robolectric y MockWebServer.

Hay que verificar que `:wear` siga compilando con las versiones nuevas de wear-compose.

0.5 **Firma.**
- *Debug:* `:app` y `:wear` deben firmar con **el mismo** keystore, o la Data Layer no entrega
  y sin dar ningún error. Mientras exista MAUI, ambos usan el keystore de debug de MAUI (el truco
  `mauiDebugKeystore` del reloj, movido a una configuración compartida en la raíz). Al retirar
  MAUI, ambos pasan al de debug de Gradle.
- *Release:* `gastappRelease` desde `local.properties`, como ya hace el reloj.
- Verificar la huella con `apksigner verify --print-certs` contra la del APK de MAUI publicado.

0.6 **Tema y componentes.** Copiar del demo `ui/theme/` (esquemas claro y oscuro, colores de
estado, Baloo para cifras) y los componentes (`MontoAnimado`, `aparecer`, `EstadoChip`,
`FilaAgrupada`, `GrupoLista`, `IconoTonal`, `escalaAlPresionar`, `seDesplazaHaciaArriba`).
Los nombres se ajustan a la convención de la sección 2.

0.7 **Spike de atrás predictivo (medio día).**
- Probar si las transiciones de *pop* de Navigation Compose reproducen el efecto del demo: escala a 0.9, esquinas redondeadas, desplazamiento hacia el dedo y velo atenuado.
- Si sí: Navigation para todo.
- Si no: Navigation para las pestañas y `PilaConAtrasPredictivo` (del demo) para las pantallas apiladas (Mis tarjetas, Suscripciones, Explorar periodo, formularios de pantalla completa).
- Anotar la decisión en este documento.

0.8 **Esqueleto.**
- `GastappApplication` con `@HiltAndroidApp`.
- `MainActivity` con `enableEdgeToEdge()` y la Splash Screen API; ícono adaptable con capa monocromática.
- Cuatro pestañas vacías (Resumen, Ahorros, Perfil, Ajustes), barra inferior y FAB.

0.9 **Manifiesto.**
- Permisos: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `REQUEST_INSTALL_PACKAGES` y `VIBRATE`.
- `android:enableOnBackInvokedCallback="true"`.
- FileProvider con autoridad `com.binc.gastapp.fileprovider`.
- **Sin** `usesCleartextTraffic`.

**Cómo se verifica.**
```bash
./gradlew :app:installDebug :wear:assembleDebug
```
```bash
./gradlew :app:assembleRelease
```
```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

**Resultado (2 oct 2026).**
- **0.1** Carpeta renombrada con `git mv` (`Gastapp.WearOS/` → `Gastapp.Android/`); rutas
  actualizadas en `CLAUDE.md`, el `README.md` del proyecto Gradle (ahora describe los cuatro
  módulos y la firma) y las memorias.
- **0.2** `:app` sustituye a la plantilla `mobile/` (borrada): `com.binc.gastapp`, `minSdk 31`,
  `targetSdk 36`, `versionCode 200`, `versionName 2.0.0`, `API_BASE_URL` en `BuildConfig`.
- **0.3** `:core` creado (librería Android, `minSdk 30` por el reloj), vacío.
- **0.4** Versiones: AGP 8.13.2, Kotlin 2.2.21, KSP 2.2.21-2.0.5, Compose BOM 2025.10.00
  (Material 3 1.4.0), Navigation 2.9.5, Hilt 2.57.2 (+ `androidx.hilt` 1.3.0), Room 2.8.4,
  Lifecycle 2.9.4, DataStore 1.1.7, WorkManager 2.10.5, kotlinx.serialization 1.9.0,
  corrutinas 1.10.2, Turbine 1.2.1, Robolectric 4.16 y MockWebServer 4.12.0 (el de OkHttp).
  Es la línea del demo. Ya existen Kotlin 2.4, AGP 9.4 y BOM 2026.09, pero AGP 9 trae Kotlin
  integrado y obliga a reescribir los `build.gradle.kts`; queda para después del lanzamiento.
  `:wear` compila igual con las versiones nuevas (solo cambió `kotlinOptions` →
  `compilerOptions` y el nombre de dos alias del catálogo). Con Kotlin 2.2, Gradle ya corre
  con el JDK 25 predeterminado.
- **0.5** La firma vive en el `build.gradle.kts` raíz y la usan `:app` y `:wear`
  (`subprojects { plugins.withId("com.android.application") ... }`). Debug: keystore de debug
  de .NET Android (huella `86:DC:28:43…`). Release: `gastappRelease` desde `local.properties` o
  `GASTAPP_*`; los dos release salen con `2B:DD:2F:06…` y `CN="Cesar Maat"`. La app nativa de
  debug se instaló **encima** de la MAUI de debug en el emulador (versión 130 → 200, misma
  firma): la ruta de actualización funciona.
- **0.6** Tema y componentes del demo con nombres en inglés: `GastappTheme`, `LightColors` /
  `DarkColors`, `StatusColors` (`ok`, `warning`, `critical`; el neutro sale del tema y se mapea
  desde `StatusLevel` de `:domain`), `amountLarge` / `amountMedium` (Baloo), `AnimatedAmount`,
  `appear`, `scaleOnPress`, `isScrollingUp`, `StatusChip`, `TonalIcon`, `InitialAvatar`,
  `ListGroup`, `GroupedRow`, `SectionHeader`. Montos con `formatMoney` (es-MX).
- **0.8** `GastappApplication` con Hilt, `MainActivity` con `installSplashScreen()` y
  `enableEdgeToEdge()`. El ícono es **el mismo de MAUI** (fondo menta `#C9F5D8` y el cerdito),
  con capa monocromática; el splash usa el mismo menta (en oscuro, fondo oscuro con el cerdito
  sobre un círculo menta). Cuatro pestañas provisionales, barra inferior y FAB.
- **0.9** Manifiesto con los cinco permisos, `enableOnBackInvokedCallback`, FileProvider
  `com.binc.gastapp.fileprovider` (`cache-path apk_updates`, como MAUI) y sin
  `usesCleartextTraffic`. Solo en debug, `network_security_config` permite http a `10.0.2.2`
  para la API local. `allowBackup="false"` (MAUI tenía `true`): los datos se bajan al iniciar
  sesión y restaurar una sesión vieja solo traería datos desfasados.

**Decisión del spike 0.7: Navigation Compose para todo.** Reproduce el efecto del demo. Su
`NavHost` "busca" (seek) la transición de regreso con el progreso del gesto, así que la
pantalla de abajo está compuesta y visible durante el arrastre. Lo único que no le pasa a
las pantallas es el lado del gesto y la altura del dedo. Eso lo aporta `ProvideBackGesture`
(`ui/navigation/PredictiveBack.kt`): un despachador intermedio entre la Activity y el
`NavHost` que anota cada `BackEventCompat` y se lo reenvía. Cada pantalla apilada se envuelve
en `PredictiveBackLayer`, que pinta el velo, anima su entrada (desde la derecha o desde abajo),
aplica la pose del gesto (escala 0.9, esquinas de 32 dp, se recorre hacia el lado del gesto,
sigue al dedo en vertical, sombra) y, al soltar, la salida desde donde quedó. La pantalla de
abajo se recorre un 8 % como en el demo.
- Las pestañas **no** son rutas: son estado de `MainRoute` con *fade through*, como en el demo.
  Atrás desde otra pestaña regresa a Resumen.
- Probado en el emulador con pantallas provisionales (Mis tarjetas, Suscripciones, Explorar
  periodo): gesto desde la izquierda y desde la derecha, confirmar y cancelar. **Falta que el
  usuario lo compare a mano con el demo**, que quedó instalado en el mismo emulador.
- Riesgo: `dispatchOnBackStarted/Progressed/Cancelled` de `OnBackPressedDispatcher` son
  públicos pero marcados `@VisibleForTesting`. Si una versión futura de activity o navigation
  pasa el `NavHost` a `NavigationEventDispatcher`, el gesto seguiría cerrando la pantalla pero
  sin la pose durante el arrastre: revisar `ProvideBackGesture` al subir esas librerías.

**Criterios de salida.**
- [x] La app vacía abre con tema claro y oscuro, cuatro pestañas y FAB (en el emulador).
- [x] El reloj compila igual que antes (`:wear:assembleDebug` y `assembleRelease`).
- [x] El release está firmado con la huella del keystore real (`2B:DD:2F:06…`).
- [x] La decisión del spike 0.7 está anotada aquí.

---

### Fase 1. Dominio y paridad con MAUI

**Objetivo.** Toda la lógica de cálculo en Kotlin puro, con pruebas que demuestren que da
exactamente lo mismo que la versión MAUI.

**Fuente MAUI.**

| Archivo | Qué se porta |
|---|---|
| `Services/CreditCardService/CreditCardService.cs` | `CalculateCycleDates`, `NextOccurrenceOfDay`, `PreviousOccurrenceOfDay`, `GetLastCutOffDate`, `CalculateCycleDatesAsync` + `IsStatementSettledAsync` (fecha límite que salta cuando el corte quedó cubierto), deuda (`GetPendingAmountForCardAsync`), compras del ciclo, MSI activas y el armado de `CreditCardSummary` (`DaysUntilPayment`, `PaymentStatusText`, `PaymentStatusColor`) |
| `Services/SubscriptionService/SubscriptionService.cs` | `CalculateNextChargeDate`, `CalculatePreviousChargeDate`, `CountsTowardTotals`, `GetMonthlyEquivalent`, nombres y sufijos de periodicidad, cobros próximos a 45 días, `IsCurrentCycleCharged`, `ShareOfMonthlyRatio` |
| `Services/SpendingService/SpendingService.cs` | Periodos de pago por tipo de ingreso (`GetAllPeriodDays` y sus ayudantes): **1** semanal (`WeekPayDay`, domingo = 0), **2** quincenal (`FirstPayDay`/`SecondPayDay`), **3** mensual (`FirstPayDay`), con desplazamiento hacia periodos anteriores. Totales y resumen por categoría |
| `ViewModels/SavesViewModel.cs` | Presupuesto del periodo, salud, promedio diario (`Math.Round(..., 2)`) y categoría principal |
| `ViewModels/NewSpendingViewModel.cs` | Mensualidad MSI (`Math.Round(monto / plazo, 2)`) |
| `ViewModels/ProfileViewModel.cs` | Porcentaje de ahorro (`Math.Round(..., 4)`) y modo porcentaje o monto |
| `ViewModels/CreditCardsViewModel.cs` | Alta de una tarjeta en uso (saldo de contado + MSI previas, con lo que **falta** por pagar) y pago a tarjeta (`PayCard`: abono como `Spending` con `IsCreditCard = false`) |
| `ViewModels/SubscriptionsViewModel.cs` | `RegisterCharge`: crea el `Spending` del cobro (`IsCreditCard = true` si va a tarjeta) |
| `Utils/DateTimeUtils.cs` | `SpendingToApiUtc` / `SpendingFromApiToLocal` |

**Tareas.**

1.1 Crear el módulo `:domain` (Kotlin/JVM, sin Android):
- `money` (centavos ↔ `BigDecimal`, `HALF_EVEN`).
- `time` (conversiones del anexo C, reloj inyectable para pruebas).
- `cards` (ciclos y resumen de tarjeta).
- `subscriptions` (cobros y totales).
- `periods` (periodos de pago).
- `savings` (presupuesto y salud).

Las funciones reciben listas y fechas, no repositorios. Así se prueban sin base de datos.

1.2 **Herramienta de paridad** en `tools/Gastapp.Paridad/` (consola .NET 8):
- **Copia** (no enlaza) las funciones puras de MAUI, porque viven en un proyecto `net8.0-android` que una consola no puede referenciar.
- Recorre una rejilla de casos y escribe `fixtures/*.json`:
  - Días de corte y de pago del 1 al 31, cada día de 2025 y 2026.
  - Las 5 periodicidades.
  - Anclas en días 28 a 31.
  - Los 3 tipos de ingreso con todos sus días de pago.
  - Desplazamientos de periodo de 0 a 6.

1.3 **Pruebas JUnit** que leen esos fixtures y comparan resultado por resultado.

1.4 **Pruebas con nombre** para las reglas de `CLAUDE.md` (anexo C): una prueba por regla, con
el nombre de la regla, para que al romperse se entienda qué se rompió.

**Cómo se verifica.**
```bash
dotnet run --project tools/Gastapp.Paridad -- Gastapp.Android/domain/src/test/resources/paridad
```
```bash
./gradlew :domain:test
```

Desde la Fase 0 (Kotlin 2.2), Gradle corre con el JDK 25 predeterminado; antes hacía falta el
JDK 21 y `-Pkotlin.compiler.execution.strategy=in-process`.

**Resultado (2 oct 2026):** 68,809 casos iguales a MAUI en 12 comparaciones (fechas de corte
y pago, resumen de tarjeta, alta de tarjeta en uso, cobros y resumen de suscripciones,
próximos cobros, periodos de pago, presupuesto y salud, porcentajes por categoría, MSI y meta
de ahorro), más 16 pruebas con nombre de las reglas del anexo C. Se comprobó que las pruebas
detectan errores: cambiar el redondeo bancario por el normal da 43 diferencias, y mover un
umbral de 3 a 4 días da 30.

**Criterios de salida.**
- [x] 100 % de los casos de los fixtures coinciden.
- [x] Están en verde las pruebas con nombre del anexo C.
- [x] `:domain` es un módulo JVM: no puede importar `android.*`.

---

### Fase 2. Datos locales

**Objetivo.** Room como fuente de verdad local, con consultas reactivas para la UI.

**Fuente MAUI.** `Data/GastappDbContext.cs` (modelo, llaves foráneas, valores por defecto),
`Data/PurgeDeletedLocal.cs` (retención de 30 días), `Gastapp.Models/Models/*.cs` (campos),
`Services/RegisterDraftService.cs` y el uso de `Preferences` en toda la app (`token`,
`tokenexpiration`, `reminders_enabled`, `reminder_frequency_hours`, `SavingsModeIsPercent`).

**Tareas.**

2.1 **Entidades** con los mismos campos que los modelos de MAUI:

| Entidad | Campos | Notas |
|---|---|---|
| `users` | `userId`, `name`, `email`, `salary`, `percentSave`, `birthDate`, `incomeTypeId`, `firstPayDay`, `secondPayDay`, `weekPayDay`, `isSynced` | `passWordHash` y los de reset **no** se guardan en el teléfono |
| `income_types` | `incomeTypeId`, `incomeTypeName` | |
| `categories` | `categoryId`, `userId`, `categoryName`, `isDefaultCategory`, `isSynced`, **`isDeleted`** | `isDeleted` es nuevo: hoy borrar una categoría sin red se pierde |
| `spendings` | `spendingId`, `userId`, `categoryId`, `title`, `description`, `amountCents`, `date` (`LocalDateTime`), `isCreditCard`, `creditCardId`, `paymentMethod`, `isMsi`, `totalInstallments`, `currentInstallment`, `parentSpendingId`, `installmentMonthlyAmountCents`, `isSynced`, `isDeleted`, `deletedAt` | Índices por `date` y por `creditCardId` |
| `credit_cards` | `creditCardId`, `userId`, `cardName`, `bankName`, `lastFourDigits`, `cutOffDay`, `paymentDay`, `creditLimitCents`, `colorHex`, `isSynced`, `isDeleted`, `deletedAt` | |
| `subscriptions` | todos los de `Subscription.cs` | `firstChargeDate` y `trialEndDate` como `LocalDate`; llaves foráneas a tarjeta y categoría con `SET_NULL` |

**Llaves foráneas** iguales a `OnModelCreating`:
- Borrar un usuario borra todo lo suyo en cascada.
- Una categoría arrastra sus gastos.
- Borrar una tarjeta **no** borra la suscripción.

**Convertidores:** `LocalDate` como texto ISO (`2026-10-02`), `LocalDateTime` como texto ISO
(`2026-10-02T14:15`) e `Instant` en milisegundos.

2.2 **DAOs** con `Flow`:
- Gastos de un día.
- Totales por día de un rango (para la tira y Explorar periodo).
- Totales por categoría.
- Tarjetas con su deuda.
- Suscripciones.
- Pendientes de sincronizar (`isSynced = 0`).
- Purga (`isDeleted = 1 AND deletedAt < ahora − 30 días`).

2.3 **Repositorios** (`SpendingRepository`, `CategoryRepository`, `CreditCardRepository`,
`SubscriptionRepository`, `UserRepository`):
- Escriben en Room y marcan `isSynced = false`.
- Borrar = marcar `isDeleted` + `deletedAt`.
- Después avisan al `SyncScheduler` (en esta fase basta un método vacío).

2.4 **DataStore.**
- Sesión: token y expiración en milisegundos.
- Ajustes: recordatorios sí/no, frecuencia (4 h por defecto), modo de ahorro, tema y colores dinámicos.
- Borrador del registro, **sin** la contraseña: se vuelve a pedir si se interrumpe el registro.

2.5 ~~**Limpieza del primer arranque.**~~ **Descartada** (decisión del usuario, 2 oct 2026):
no hace falta ninguna compatibilidad con MAUI; la app nativa se instala desde cero
(desinstalando MAUI antes). La base nativa se llama `gastapp_room.db` y no `gastapp.db`, para
que Room no intente abrir la de MAUI si alguien instalara encima sin desinstalar.

2.6 **Pruebas de DAO** con Robolectric: inserción, borrado lógico, totales con centavos,
cascadas y `SET_NULL`.

**Resultado (2 oct 2026).** Todo en `app/src/main/java/com/binc/gastapp/`:
- **2.1** `data/local/Entities.kt`: `income_types`, `users`, `categories`, `credit_cards`,
  `spendings` y `subscriptions`, con los campos de MAUI, dinero en centavos (`...Cents`) y el
  porcentaje de ahorro como texto exacto (`BigDecimal`). Llaves foráneas: usuario → todo en
  cascada; categoría → gastos en cascada; gasto → tarjeta `NO ACTION` (como MAUI: no deja
  borrar una tarjeta con gastos); suscripción → tarjeta y categoría `SET NULL`; usuario →
  tipo de ingreso `RESTRICT`. `data/local/Converters.kt`: `LocalDate` como `2026-10-02`,
  `LocalDateTime` como texto ISO **de ancho fijo** (`2026-10-02T14:15:00.000000000`, para que
  comparar texto sea comparar tiempo y `substr(date, 1, 10)` sea el día), `Instant` en
  milisegundos. `GastappDatabase` versión 1, esquema exportado en `app/schemas/`.
  `data/local/Mappers.kt` pasa de entidades a modelos de `:domain` (se agregaron `User`,
  `IncomeType`, `DEFAULT_CATEGORY_NAME` e `isDefaultCategoryName` a `:domain`).
- **2.2** `data/local/Daos.kt`: gastos de un rango, totales por día con **los dos criterios**
  (`totalCents` con compras con tarjeta para el total del día y `withoutCardPurchasesCents`
  para el del periodo, regla 16), totales por categoría (sin compras con tarjeta), tarjetas
  con su saldo, movimientos de tarjeta para `domain/cards`, pendientes (`isSynced = 0`),
  `markSynced`, borrado lógico, deshacer y purga. Siempre `@Upsert`, nunca `REPLACE` (que
  borraría en cascada). `data/local/LocalPurge.kt`: 30 días, solo lo sincronizado, gastos y
  suscripciones antes que tarjetas, y solo tarjetas que ya nadie referencia.
- **2.3** `data/repository/`: `UserRepository`, `CategoryRepository`, `SpendingRepository`,
  `CreditCardRepository` y `SubscriptionRepository`. Escriben en Room con `isSynced = false`
  y llaman a `SyncScheduler.requestSync()` (`sync/SyncScheduler.kt`, por ahora
  `NoOpSyncScheduler`). Borrar = `isDeleted` + `deletedAt`. Además: deshacer un borrado de
  gasto, `addPlanned` (guarda los `PlannedSpending` del dominio), alta de tarjeta en uso con
  sus movimientos en una transacción, `registerCharge` (gasto del cobro + `lastChargeRegisteredAt`
  en una transacción) y borrar categoría como MAUI (sus gastos pasan a «Sin categoria», se
  anula la categoría de las suscripciones y la categoría solo se **marca** `isDeleted`).
- **2.4** `data/prefs/`: `SessionStore` (token y expiración en ms), `SettingsStore`
  (recordatorios sí/no y cada 4 h, modo de ahorro en %, tema y Material You apagado) y
  `RegisterDraftStore` (borrador sin contraseña, caduca a los 2 días como en MAUI). Tres
  archivos de DataStore: cerrar sesión borrará sesión y borrador, no los ajustes.
- **Hilt:** `di/DataModule.kt` (base, `Clock`, DataStores y el `SyncScheduler`).
- **Pantalla:** en Resumen, la tarjeta provisional «Datos locales (Room)»
  (`ui/main/LocalDataPreview.kt`) calcula con los repositorios y `:domain` el total de hoy, el
  del periodo, las categorías, la deuda y fecha límite de cada tarjeta y el costo mensual de
  suscripciones. En debug tiene «Cargar muestra» / «Vaciar» (`data/local/DevSampleData.kt`,
  datos marcados como sincronizados para que la Fase 3 nunca los suba). Se borra en la 4.2.
- **2.6** 35 pruebas en `app/src/test/` (Robolectric para Room): convertidores, rangos de
  día, borrado y deshacer, totales con centavos, llaves foráneas (cascadas, `SET NULL`,
  `NO ACTION` y que una referencia colgante truena), purga, repositorios, DataStore y
  `SqlVsDomainTest`, que compara 600 gastos aleatorios: los totales de SQL dan exactamente
  lo mismo que las funciones de `:domain` (las que tienen paridad con MAUI). Sabotaje: quitar
  el filtro de compras con tarjeta del SQL por categoría tumba dos pruebas.
- **Emulador:** instalada desde cero (sin MAUI); con la muestra cargada los números cuadran a
  mano y sobreviven a cerrar la app.

**Criterios de salida.**
- [x] Pruebas de DAO en verde.
- [x] Las pantallas vacías de la Fase 0 muestran datos de prueba leídos de Room.
- [x] ~~Al instalar encima de la versión MAUI, desaparecen `gastapp.db` y las Preferences viejas.~~
  Descartado: la app se instala desde cero.

---

### Fase 3. API, sesión y sincronización

**Objetivo.** Iniciar sesión, registrarse, recuperar la contraseña y sincronizar en los dos
sentidos, sin bloquear la UI.

**Fuente MAUI.**
- `Services/ApiService/IApiService.cs` (llamadas).
- `Services/UserService/UserService.cs` (login: reemplaza la base, anula referencias colgantes con `knownCardIds` y `knownCategoryIds`).
- `App.xaml.cs`: `CheckUser`, `RefreshToken`, `SyncData` y `PullRemoteSpendings`.
- `ViewModels/StartPageViewModel.cs`, `RegisterViewModel.cs` y `ForgetPasswordViewModel.cs` (flujos).
- `Gastapp.Models/Models/*Dto.cs`, `AllUserData.cs`, `SyncDataDto.cs` y `Token.cs` (contratos).

**Tareas.**

3.1 **Cliente Retrofit** en `:core` solo con lo que la app nativa usa (anexo B, columna «Nativa»).

- Los DTO son `@Serializable`, con los nombres **camelCase** que devuelve ASP.NET Core.
- Los parámetros sueltos van en *query string*, igual que los manda Refit hoy (`?spendingId=`, `?Email=&Code=`).
- Timeout de 120 s: Render despierta en frío.

3.2 **Serializadores.**
- `BigDecimal` ↔ número JSON, sin pasar por `Double`.
- Instantes: aceptar fechas con y sin `Z`; sin zona = UTC.
- Fechas de calendario (`firstChargeDate`, `trialEndDate`, `birthDate`): solo la fecha, **sin** conversión de zona (anexo C).

3.3 **Sesión.**
- **Login:** guardar el token y su expiración (de `tokenExpiration`, ya no con `DateTime.ToString()`). Reemplazar toda la base local con `AllUserData`, anulando las referencias a tarjetas y categorías que no vinieron.
- **Registro:** pedir el código de correo (`EmailVerification/request`), verificarlo (`EmailVerification/verify`) y llamar a `CreateUser`. La respuesta trae token: se entra directo.
- **Recuperar contraseña:** `PasswordReset/request`, `verify` y `confirm`, más la opción de contraseña temporal.
- **Al arrancar con sesión:**
  - Si el token sigue vigente y hay red: refrescar el token, luego empujar y luego bajar (mismo orden que `App.xaml.cs`).
  - Sin red: entrar con los datos locales.
  - Si el token ya venció: aviso «Inicia sesión de nuevo para volver a sincronizar», sin expulsar.
- **401 explícito:** borrar el token y mandar a iniciar sesión. Cualquier otro error (5xx, timeout) **no** expulsa al usuario.
- **Cerrar sesión:** borrar Room y DataStore.

3.4 **Sincronización offline-first** (`sync/SyncWorker`).
- Cada escritura local encola el trabajo único `sync`: `ExistingWorkPolicy.KEEP`, condición `CONNECTED`, backoff exponencial y un retraso de unos 2 s para agrupar ráfagas.
- **Push:** un solo `SyncAllData` con todo lo pendiente (usuario, categorías, gastos, tarjetas y suscripciones; borrados incluidos). El API ya hace *upsert* y aplica `IsDeleted` y `DeletedAt` en gastos, tarjetas y suscripciones. Las fechas de gasto se convierten como `SpendingToApiUtc`. Si responde bien, se marca `isSynced = true`.
- **Categorías borradas:** `DeleteCategory` por cada una (el API no recibe `IsDeleted` de categorías) y luego se borran localmente.
- **Pull:** `GetSpendings` e insertar **solo los que faltan**, sin sobrescribir nunca un gasto local, con la misma protección para tarjetas inexistentes. Así llegan los gastos del reloj.
- **Estado para Ajustes:** pendientes por tipo, el equivalente de `CloudSyncStatusSummary`.

3.5 **Pruebas de contrato** con MockWebServer.
- Capturar con una cuenta de prueba las respuestas reales de `Login`, `GetSpendings`, `RefreshToken` y `LatestVersion`, y guardarlas como fixtures.
- Probar que se deserializan y que `SyncAllData` sale con la forma esperada.

**Resultado (2 oct 2026, chat 4).**
- **3.1** `:core/remote/`: `GastappApi` (Retrofit, solo lo de la columna «Nativa» del anexo B;
  el token va como parámetro `@Header` en cada llamada que lo pide, como el `[Authorize]` de
  Refit), `Dtos.kt` (`@Serializable`, camelCase) y `ApiClient.kt` (timeout de 120 s, `ApiJson`
  y `apiCall`, que devuelve `ApiResult`: `Success`, `HttpError(code, message)`,
  `NetworkError` o `InvalidResponse`; el mensaje se saca como `ExtractApiMessage` de MAUI).
  Sin interceptor de logs: el login lleva la contraseña y `PasswordReset/confirm` la lleva en
  la URL. El reloj sigue con su propia red (moverla a `:core` va después, anexo F).
- **3.2** `Serializers.kt`: `BigDecimalSerializer` (número JSON, nunca `Double`),
  `ApiInstantSerializer` (lee con `Z`, con desfase o sin zona = UTC; escribe UTC con `Z`),
  `CalendarDateSerializer` (toma solo el día; escribe `2026-01-31T00:00:00`) y
  `CalendarDateUtcSerializer`, solo para `BirthDate` en `CreateUser` (7.3, hallazgo 14).
- **3.3** `data/session/SessionRepository.kt` + `SessionGuard.kt`. Estados: `LoggedOut`,
  `Active`, `Expired` (token vencido por tiempo: se usa lo local, sin expulsar) y `Revoked`
  (401: a iniciar sesión). El login guarda token y expiración en ms y reemplaza Room
  (`sync/LocalDataWriter.replaceAll`). **Dos diferencias con MAUI:** (1) si la base era de la
  misma cuenta, lo pendiente de subir se conserva y gana sobre lo del servidor (MAUI lo
  borraba); (2) crear la cuenta hace `CreateUser` y luego `Login` con las mismas credenciales,
  en vez de armar el usuario a mano (MAUI duplicaba «Sin categoria»). Registro y recuperar
  contraseña quedan como funciones con los mensajes de MAUI; sus pantallas son la 4.1.
  Cerrar sesión: cancela la sincronización, `clearAllTables()`, sesión y borrador.
- **3.4** `sync/`: `WorkManagerSyncScheduler` (reemplaza al no-op; trabajo único `sync`,
  `CONNECTED`, backoff exponencial de 30 s, 2 s para juntar ráfagas, `APPEND_OR_REPLACE` con
  una bandera para no perder una escritura que llega mientras otro envío corre),
  `SyncWorker` (`@HiltWorker`, hasta 6 intentos; un 400 no se reintenta) y `SyncEngine`
  (refresh → `SyncAllData` → `DeleteCategory` → `GetSpendings`, con `Mutex`). El push marca
  como subido **solo lo que no cambió mientras se subía**. `DeleteCategory`: 404 = ya no
  existe allá (se borra aquí), 400 = es su categoría por defecto (se restaura aquí). Un 400
  del push no detiene el pull. El pull inserta solo los gastos que faltan (nunca sobrescribe
  ni revive uno borrado aquí); tarjeta desconocida → null, categoría desconocida → «Sin
  categoria». La completa (con refresh y pull) la pide `MainActivity` al abrirse con sesión
  vigente; la normal, cada escritura. `GastappApplication` da la `HiltWorkerFactory` y el
  manifiesto quita el inicializador automático de WorkManager. Estado para Ajustes:
  `SyncDao.observePendingCounts()` (el `CloudSyncStatusSummary`) y `SyncEngine.runState`.
- **3.5** Fixtures de contrato generados con los **tipos C# reales**: `tools/Gastapp.Contratos`
  serializa con `JsonSerializerDefaults.Web` (lo que usa ASP.NET Core) las respuestas de
  `Login`, `RefreshToken`, `CreateUser`, `GetSpendings`, `LatestVersion`, `Device/List` y la
  contraseña temporal en `core/src/test/resources/contratos/`; en modo `verificar` lee
  `sync_all_data_request.json` y `create_user_request.json` (que una prueba de Kotlin compara
  contra lo que genera la app) y comprueba con System.Text.Json montos exactos y el `Kind` de
  cada fecha. No se capturaron respuestas de producción: haría falta una cuenta y mandar una
  contraseña real.
- **Pruebas:** 19 en `:core` (serializadores, contratos, cliente con MockWebServer: rutas,
  query string, token, 400 con mensaje, 401/500/timeout, respuesta ilegible) y 17 nuevas en
  `:app` (`sync/LoginTest`, `sync/SyncEngineTest`: login con el `login.json` real,
  referencias colgantes, conservar pendientes, mensajes de error, alta de cuenta, cerrar
  sesión, push con conversión de zona, edición a media subida, 401 vs 500 vs timeout, token
  vencido, orden de la completa, pull que no sobrescribe, `DeleteCategory` 404/400, 400 que no
  frena el pull). Total de `:app`: 52. Sabotajes: marcar como subido sin comparar y tratar el
  401 como reintento tumban dos pruebas; quitar la `Z` de `BirthDate` tumba la verificación C#.
- **UI provisional** (se borra en la 4.1 / 4.6): compuerta de sesión en `ui/GastappApp.kt`
  (el splash espera a saber si hay sesión), login mínimo en `ui/session/ProvisionalLogin.kt`
  (en debug, «Entrar con datos de muestra») y la tarjeta «Nube (Fase 3)» en Resumen
  (`ui/main/SyncPreview.kt`): estado de la sesión, pendientes por tipo, última sincronización,
  «Sincronizar ahora», «Cerrar sesión» y, en debug, un laboratorio que crea, edita y borra
  gastos, tarjetas y suscripciones «Prueba sync» para probar el modo avión.
- **Emulador:** instalada encima de la de la Fase 2 (con la muestra): abre sin errores en el
  log, trata la muestra como sesión vencida (aviso, sin expulsar) y WorkManager arranca.

- **Prueba en vivo (chat 4, emulador, cuenta del usuario):** el login bajó 3 tipos de
  ingreso, el usuario, 8 categorías, 4 tarjetas, 74 gastos y 2 suscripciones, todo marcado
  como sincronizado y sin referencias colgantes. En modo avión el laboratorio creó, editó y
  borró gasto, tarjeta y suscripción: seis pendientes, sesión intacta. Al volver la red,
  WorkManager subió todo solo en ~4 s. Cerrar sesión y volver a entrar bajó del servidor lo
  editado (124.45, límite 10,500, 109.00), la hora del gasto sin moverse y la fecha de la
  suscripción sin correrse; el gasto y la suscripción borrados no volvieron y la tarjeta
  borrada volvió marcada como borrada (hallazgo 18).

**Criterios de salida.**
- [x] Con una cuenta de prueba, el login baja todo (gastos, categorías, tarjetas, suscripciones e ingresos).
- [x] En modo avión: crear, editar y borrar gastos, tarjetas y suscripciones. Al volver la red, todo llega al servidor (verificar en otra instalación o en Neon).
- [x] Un gasto creado desde el reloj aparece en el teléfono después del pull. (En vivo: «Test»,
  $20.00, llegó con la sincronización completa al abrir la app, en hora local y en la
  «Sin categoria» del servidor.)
- [x] Un 401 manda a iniciar sesión; un 500 o un timeout no. (Con MockWebServer; en vivo, sin
  red no expulsa.)

---

### Fase 4. Pantallas

**Objetivo.** Cada pantalla de MAUI con la misma información y las mismas acciones, con el
diseño y las animaciones del demo.

**Método para cada pantalla.**
1. `UiState` + `ViewModel` con los repositorios reales: una función por cada `[RelayCommand]` de MAUI.
2. Pantalla en Compose a partir del demo.
3. Pruebas del ViewModel con repositorios falsos y Turbine.
4. Repasar su lista del anexo D contra la pantalla de MAUI.

**Orden.** Primero lo que más se usa.

#### 4.1 Arranque y sesión (≈ 1 semana)

| MAUI | Nativo |
|---|---|
| `StartPage` + `StartPageViewModel` | Pantalla de bienvenida: login, mostrar contraseña, crear cuenta, olvidé mi contraseña, aviso de privacidad |
| `BottomSheets/LoginBottomSheet` | Hoja o pantalla de login |
| `Pages/WizardRegister` + `Register/RegisterName`, `RegisterBirthDate`, `RegisterSalary`, `RegisterAccount`, `RegisterEmailCode` + `RegisterViewModel` | Asistente de 5 pasos con barra de progreso y atrás predictivo entre pasos; reenviar código |
| `Validators/RegisterValidator`, `UserValidator` (FluentValidation) | Funciones de validación en el ViewModel, con los mismos mensajes |
| `Services/RegisterDraftService` | Borrador en DataStore (fase 2) |
| `Pages/ForgetPasswordPage` + `ForgetPasswordViewModel` | Enviar código, verificarlo, nueva contraseña, contraseña temporal, volver al login |

`OfflineRegisterViewModel` **no** se migra: está apagado en `MauiProgram.cs`.

#### 4.2 Principal y Resumen (≈ 1 semana)

| MAUI | Nativo |
|---|---|
| `Pages/Menu/MainPage` + `MainPageViewModel` | `Scaffold` con barra inferior (4 pestañas), FAB «Nuevo gasto» que se encoge al bajar y transición entre pestañas |
| `Pages/Menu/SummaryPage` + `SummaryViewModel` | Navegador de periodo según el tipo de ingreso (anterior y siguiente), tira con **todos los días del periodo**, total del día animado, «Ir a hoy», lista del día (los accesos a Tarjetas y Suscripciones ya no están en la lista: ver «Cambio posterior» en 4.6); **tocar un gasto abre su detalle** |
| `SfCalendar` + `CalendarSelectionChange` | Pantalla **Explorar periodo** del demo: atajos, calendario con intensidad de gasto por día, resumen del rango con gráfica y categorías, «Ver N días» |
| `BottomSheets/NewSpendingBottomSheet` + `NewSpendingViewModel` | Formulario de gasto del demo (alta y edición). Suma lo que falta del de MAUI: **crear y borrar categorías** (avisando cuántos gastos tiene, `CountActiveSpendingsByCategory`) y la regla de la mensualidad MSI. «Eliminar» con aviso y **Deshacer** |
| `Pages/Menu/SpendingDetailPage` + `DetailViewModel` | **Se queda (decidido)**, igual que en MAUI: pantalla apilada de solo lectura con todos los datos del gasto y el botón **Editar**, que abre el formulario de gasto |
| `Pages/Menu/CategoryDetailPage` + `CategoryDetailViewModel` | Gastos de una categoría en el periodo; tocar uno abre su detalle; borrar con Deshacer |
| `Popups/AlertPopup`, `Utils/AlertHelper` | `AlertDialog` de Material y Snackbar |
| `Popups/LoadingPopup` | Indicadores dentro de la pantalla: nunca se bloquea toda la pantalla |

#### 4.3 Ahorros (≈ 3 días)

| MAUI | Nativo |
|---|---|
| `Pages/Menu/SavesPage` + `SavesViewModel` | Periodo anterior y siguiente, total gastado, salud del periodo, anillo de presupuesto, «Te quedan», promedio diario, categoría principal, tarjetas por pagar con **Registrar pago**, distribución por categoría (tocar abre la categoría) |

#### 4.4 Tarjetas (≈ 1 semana)

| MAUI | Nativo |
|---|---|
| `Pages/Menu/CreditCardsPage` + `CreditCardsViewModel` (16 comandos) | Pantalla apilada **Mis tarjetas**: resumen global, carrusel, detalle animado por tarjeta, MSI activas, compras del ciclo |
| `BottomSheets/CreditCardFormBottomSheet` | Formulario de pantalla completa: alta y edición, colores, **tarjeta en uso** (saldo de contado + compras MSI previas) |
| `BottomSheets/MsiPurchaseBottomSheet` | Hoja para agregar una compra MSI previa |
| `Popups/AmountInputPopup` | Hoja de monto para **Ajustar saldo** y **Registrar pago** (con opciones rápidas: «Sin deuda», «Pago para no generar intereses») |

#### 4.5 Suscripciones (≈ 4 días)

| MAUI | Nativo |
|---|---|
| `Pages/Menu/SubscriptionsPage` + `SubscriptionsViewModel` | Pantalla apilada: gasto recurrente al mes y al año, chip «N activas · M en prueba · P pausadas», lista, próximos cobros a 45 días |
| `BottomSheets/SubscriptionFormBottomSheet` | Formulario de pantalla completa: servicio, plan, monto, periodicidad, primer cobro, forma de pago (**las mismas 4** que un gasto), tarjeta, categoría, prueba gratis, color y notas |
| `RegisterCharge`, `ToggleSubscriptionState` | Registrar cobro: **avisa sin bloquear** si ya se registró el del periodo. Pausar y reanudar |

#### 4.6 Perfil y Ajustes (≈ 4 días)

**Cambio posterior (3 oct 2026, chat 8, a pedido del usuario).** Los accesos a Tarjetas y a
Suscripciones salieron de la lista de Resumen:
- **Resumen:** tres iconos en la barra superior (tarjeta, suscripciones y calendario de «Explorar
  periodo», en ese orden). El de tarjeta lleva un `Badge` cuando el chip de vencimiento está en
  ámbar o rojo (`cardChip.level.ordinal > 0`, la misma regla con la que antes pulsaba el chip),
  del color de ese nivel, y TalkBack lo anuncia. Está en `ui/main/MainScreen.kt`.
- **Perfil:** las dos tarjetas de acceso, tal cual estaban (mismo texto, subtítulo y chip), debajo
  del avatar y antes de «Ingreso y ahorro» (`ProfileShortcuts` y `ShortcutCard` en
  `ui/profile/ProfileScreen.kt`). Los textos salen del mismo `SummaryUiState` que ya calcula
  Resumen; `MainScreen` se los pasa a Perfil, así que no hay lógica duplicada.

| MAUI | Nativo |
|---|---|
| `Pages/Menu/ProfilePage` + `ProfileViewModel` | Nombre, ingreso, frecuencia (semanal, quincenal o mensual) con sus días de pago, modo de ahorro (% o monto) y guardar |
| `Pages/Menu/SettingsPage` + `SettingsViewModel` | Recordatorios (sí/no y frecuencia), activar notificaciones del sistema, notificación de prueba, tema y Material You, estado de la nube, respaldos, dispositivos vinculados, cerrar sesión |
| `Popups/LinkDevicePopup` | Diálogo para teclear el código del reloj (`K7M-2QX`) |

**Resultado de la 4.6 (3 oct 2026, chat 6).**
- `ui/profile/` (`ProfileScreen` + `ProfileViewModel`): la cuenta, un resumen que se actualiza
  mientras se edita, frecuencia y días de pago (selectores compartidos con el registro en
  `ui/components/PaySchedule.kt`), sueldo y meta en % o monto fijo (el modo se recuerda en
  `SettingsStore`), con el estimado «Ahorrarías / Tendrías». Se guarda solo a los 700 ms, con
  los mensajes de validación de MAUI; va a Room con `isSynced = false` y sube en `SyncAllData`.
  Con monto fijo se guarda el porcentaje con 4 decimales (hallazgo 22).
- `ui/settings/` (`SettingsScreen` + `SettingsViewModel`): recordatorios sí/no y frecuencia
  (2 a 24 h), permiso `POST_NOTIFICATIONS` pedido en contexto y, si se niega, salto a los
  ajustes de notificaciones de la app; notificación de prueba; tema (Sistema, Claro, Oscuro) y
  Material You, que aplica `MainActivity` (`AppearanceViewModel`) junto con el color de los
  íconos de las barras, y el splash espera a leerlos; estado de la nube (pendientes por tipo,
  última sincronización, «Sincronizar ahora» o «Iniciar sesión» si la sesión venció); «Mis
  tarjetas de crédito»; relojes vinculados (`data/repository/DeviceRepository`: lista, recargar,
  quitar con confirmación y vincular tecleando el código del reloj, hallazgo 20); versión de la
  app; cerrar sesión **con confirmación** que avisa cuántos cambios sin subir se perderían (MAUI
  cerraba sin preguntar).
- `notifications/AppNotifier`: el canal «Recordatorios» y la notificación de prueba; nada más.
- Se borraron las pantallas provisionales (`ui/main/Placeholders.kt`, `LocalDataPreview.kt` y
  `SyncPreview.kt` con el laboratorio de la Fase 3).
- **Pasa a la Fase 5:** programar los recordatorios (los ajustes ya se guardan; la 5.1 solo
  observa `SettingsStore` y reprograma), los avisos de tarjeta, los respaldos (en MAUI solo
  existían en debug, hallazgo 21), avisar al reloj con `/gastapp/revoked` al quitarlo y la
  vinculación automática por Bluetooth.
- Fuente al 200 % (forzada dentro de la app, sin tocar el emulador) y tema oscuro revisados en
  todas las pantallas. Se corrigió: etiquetas de una línea que se partían letra por letra
  (`FitText` encoge en vez de cortar: pestañas, botones segmentados y montos), botones a la
  derecha que dejaban sin ancho al texto (con `isLargeFontScale` pasan abajo: «Registrar pago»
  en Ahorros y las acciones de Ajustes), tarjetas de plástico de alto fijo que encimaban textos
  (Mis tarjetas y su formulario), el total del periodo de Resumen, las categorías de Explorar y
  el anillo de presupuesto.

**Requisitos de calidad de cada pantalla.**
- Tema claro y oscuro.
- Fuente del sistema al 200 % sin cortes.
- TalkBack lee las acciones principales.
- Atrás predictivo en las pantallas apiladas.
- Animaciones del demo: entrada escalonada, montos que cuentan, listas que animan altas y bajas sin encimarse.

**Criterios de salida.**
- [ ] Cada pantalla cumple su lista del anexo D.
- [x] Pruebas de ViewModel en verde (129 en `:app` al cerrar el chat 6).
- [ ] Recorrido completo en el teléfono sin cierres: login → registrar gasto → editar → tarjeta → pago → suscripción → cobro → cambiar perfil → cerrar sesión.

---

### Fase 5. Funciones de plataforma

**Objetivo.** Todo lo que no es una pantalla: notificaciones, respaldos, actualización,
reloj y las tareas de arranque.

**Fuente MAUI.**
- `Services/Notifications/ReminderNotificationService.cs`.
- `Services/BackupService/BackupService.cs`.
- `Services/AppUpdateService/AppUpdateService.cs`.
- `Platforms/Android/Wear/GastappWearListenerService.cs` y `WearChannel.cs`.
- `Services/WearService/WearSyncService.cs`.
- `App.xaml.cs` (`OnStart`, `OnResume`).

**Tareas.**

5.1 **Notificaciones.**
- **Canales:** «Recordatorios», «Tarjetas» y «Reloj». Pedir `POST_NOTIFICATIONS` (Android 13+) en contexto, no al abrir.
- **Recordatorios de gasto:** cada N horas (1 a 24, 4 por defecto), rotando los 6 mensajes de hoy, con WorkManager periódico. Se reprograman al cambiar el ajuste.
- **Avisos de tarjeta**, iguales a hoy:
  - Corte: 2 días antes a las 9:00.
  - Fecha límite: 3 días antes a las 9:00, con el saldo.
  - Día de pago: a las 8:30, solo si hay deuda.
  - Leen la fecha límite **ya ajustada** (no recuerdan pagos ya hechos).
  - Se recalculan al cambiar tarjetas o gastos y una vez al día.
- **Notificación de prueba** y abrir los ajustes de notificaciones del sistema.
- **Precisión:** ~~WorkManager con retraso calculado basta~~. Cambiado en el chat 11: con WorkManager los avisos llegaban hasta abrir la app (ver «Resultado de la Fase 5»). Ahora son alarmas exactas «allow while idle» de `AlarmManager`.

5.2 **Respaldos.**
- Exportar un JSON propio y versionado, que **incluya suscripciones** (hoy el de MAUI no las trae). Se guarda con `CreateDocument` (SAF) o se comparte.
- Restaurar con `OpenDocument`.
- El `.db` crudo de MAUI no se migra (era el SQLite de EF Core).
- Importar respaldos JSON de MAUI es opcional (anexo F).

5.3 **Actualización automática.**
- Mismo flujo: `GET /App/LatestVersion`, comparar `versionCode` con el instalado, descargar el APK a caché con progreso, FileProvider e intent de instalación.
- Pedir el permiso de «instalar apps desconocidas» si falta (`canRequestPackageInstalls`).
- El origen sigue siendo GitHub Releases (APK del teléfono + `version.json`).

5.4 **Reloj (Data Layer).** Se puede hacer en paralelo con la Fase 4.
- `WearableListenerService` del teléfono en Kotlin, con **las mismas rutas**:
  - Recibe `/gastapp/ping` y responde `/gastapp/pong`.
  - Recibe `/gastapp/pair` con el código de usuario, vincula con `/Device/Link` y responde `/gastapp/pair/result`.
  - Recibe `/gastapp/unlinked`.
  - Recibe `/gastapp/expense`: inserta el gasto en Room **con el `SpendingId` del reloj** (el servidor hace upsert por ese id: no se duplica) y muestra la notificación con id fijo 7200.
- Al revocar un dispositivo se envía `/gastapp/revoked`.
- Se publican los DataItems `/gastapp/today` (`WearTodayPayload`) y `/gastapp/categories` cuando cambian, observando el `Flow` de Room. Desaparece el «StartWatching» manual.
- Rutas y payloads (`WearExpensePayload`, `WearTodayPayload`) se mueven a `:core/wear` para que teléfono y reloj usen exactamente los mismos.

5.5 **Arranque y cambio de día.**
- Lo de `App.OnStart` pasa a `GastappApplication` y a un `StartupWorker`: refresh → sync → pull, purga de 30 días, reprogramar recordatorios, revisar actualización y publicar al reloj.
- `DayChangedMessage` se reemplaza por un `Flow` de «hoy» que emite al volver de segundo plano y a medianoche. La UI se actualiza sola.

**Resultado de la Fase 5 (3 oct 2026, chat 7).** Código hecho y probado con pruebas; en el
emulador se vio lo que no escribe en la cuenta. Falta lo que necesita al usuario (ver 7.2).
- **Arranque (5.5):** `startup/StartupCoordinator` lo arranca `GastappApplication` en cada
  proceso: programa o quita los recordatorios al cambiar `SettingsStore` o la sesión, recalcula
  los avisos de tarjeta al cambiar tarjetas, gastos o el día, publica al reloj, purga lo borrado
  hace 30 días y deja encolado `MaintenanceWorker` (diario: purga + avisos de tarjeta, para
  cuando la app no se abre). `startup/DayClock` es el «hoy» de lo que corre sin pantalla (emite
  al volver de segundo plano con `ProcessLifecycleOwner` y a medianoche); las pantallas ya lo
  hacían solas desde la Fase 4 (`refreshToday`), así que no se tocaron. Refrescar, subir y bajar
  lo sigue pidiendo `MainActivity`; la actualización la busca la UI (pregunta al usuario).
- **Notificaciones (5.1):** `notifications/AppNotifier` con los tres canales («Recordatorios»,
  «Tarjetas» con importancia alta, «Reloj») y ids fijos (6100 recordatorio, 7100 prueba, 7200
  reloj, 8000+ tarjetas). `Reminders.kt`: `ReminderWorker` periódico cada N horas, con los 6
  mensajes de MAUI rotando (turno en `SettingsStore`). Diferencias a propósito: solo se
  reprograma si cambia la frecuencia (MAUI reprogramaba en cada arranque y el primer aviso
  llegaba a los 5 minutos de abrir la app; aquí llega a las N horas) y el aviso nuevo reemplaza
  al anterior (MAUI podía apilar 6). `CardReminders.kt`: `planCardReminders` (función pura,
  mismos textos y horas que MAUI, lee la fecha límite ya ajustada) y un trabajo de una sola vez
  por aviso (`CardReminderWorker`). Sin cuenta, apagados o sin permiso no se muestra nada.
  **Cambio posterior (chat 11):** el usuario notó que a veces las notificaciones no llegaban
  hasta abrir la app. Causa: un trabajo de WorkManager es diferible; con el teléfono en Doze o
  la app en la cubeta «rare» de App Standby se posponía y corría al abrirla. Ahora los dos
  tipos de aviso son alarmas de `AlarmManager` (`notifications/Alarms.kt`):
  `setExactAndAllowWhileIdle` con `USE_EXACT_ALARM` (13+) / `SCHEDULE_EXACT_ALARM` (12), y
  `setAndAllowWhileIdle` si faltara el permiso. El recordatorio periódico es una cadena (al
  sonar pone el siguiente, contando desde la hora que tocaba) y guarda la siguiente hora en
  `SettingsStore` para seguir la serie tras reiniciar sin repetir los perdidos.
  `RescheduleReceiver` las vuelve a poner al reiniciar, al actualizar, al cambiar la hora o
  la zona y al cambiar el permiso; `StartupCoordinator` cancela los trabajos viejos
  (`reminders`, `card_reminder`). Visto en el emulador: tras `adb install -r`, sin abrir la
  app, quedaron puestas las alarmas exactas, y con Doze forzado + cubeta «rare» no se
  posponen. Ojo si la app va a Play: `USE_EXACT_ALARM` se tiene que justificar ahí.
  Sonido propio (pedido del usuario): «cobro», un clic de moneda y una campanita de dos notas
  (`res/raw/notificacion_moneda.wav`, lo genera `tools/sonidos/moneda.py`, que guarda las demás
  opciones que se probaron). Como Android no deja cambiar el sonido de un canal existente, los
  ids llevan versión (`recordatorios_v3`...) y los viejos se borran al arrancar.
- **Respaldos (5.2):** `backup/BackupFormat.kt` (JSON propio, `format: gastapp-backup`,
  `version: 1`, dinero como texto exacto, fechas sin zona donde corresponde) y
  `BackupRepository`: exporta lo vigente con suscripciones (y las tarjetas borradas que algún
  gasto sigue usando). **Restaurar no borra la base** (MAUI sí): lo del respaldo vuelve a como
  estaba, lo registrado después se queda, todo queda pendiente de subir para que la nube también
  lo recupere, y solo se acepta un respaldo de la misma cuenta. UI en Ajustes → «Tus datos»
  (`BackupViewModel`, selector del sistema `CreateDocument`/`OpenDocument`, confirmación con lo
  que trae). No se importan respaldos de MAUI (anexo F).
- **Actualización (5.3):** `update/AppUpdater` (mismo flujo que MAUI: `App/LatestVersion`,
  compara `versionCode`, baja a `cacheDir/gastapp-update.apk` con progreso, revisa que el APK
  sea de `com.binc.gastapp` y más nuevo, FileProvider e instalador; pide «instalar apps
  desconocidas» y sigue solo al volver). `ui/update/` (diálogo en la raíz de la app, con o sin
  sesión, y «Buscar actualización» en Ajustes). Para probar sin publicar: en debug,
  `UPDATE_MANIFEST_URL` apunta a un `version.json` propio (comentado en `app/build.gradle.kts`).
- **Reloj (5.4):** rutas y payloads en `:core/wear` (`WearContract.kt`); `:wear` ya depende de
  `:core` y usa los mismos (solo cambiaron imports y constantes; compila). En el teléfono:
  `wear/PhoneWearListenerService` (ping → pong, pair → `Device/Link` con 55 s y motivos cortos,
  unlinked, expense → `WatchExpenseImporter` inserta con el id del reloj y notifica),
  `DeviceRepository.revoke` avisa con `/gastapp/revoked`, `WearPublisher` publica `/gastapp/today`
  y `/gastapp/categories` observando Room (sin cuenta no publica, para no vaciarle las categorías
  al reloj) y `WearEvents` avisa a Ajustes (recarga la lista; el diálogo de vincular pasa solo a
  «vinculado» cuando el reloj se vincula por Bluetooth). El total del día que se publica suma
  **todo**, compras con tarjeta incluidas, igual que `GET /Device/Summary` del API (hallazgo 25).
- 25 pruebas nuevas (154 en `:app`), con sabotaje (mover el aviso de corte a 3 días o dejar lo
  restaurado como ya subido rompe 3). `robolectric.properties` usa `android.app.Application`
  para que las pruebas no arranquen el `StartupCoordinator`.
- Visto en el emulador: arranca sin cierres; WorkManager con el recordatorio cada 4 h, 5 avisos
  de tarjeta y el mantenimiento; exportar (44 KB, 72 gastos, 4 tarjetas, 2 suscripciones);
  restaurar hasta la confirmación (se canceló: escribiría en la cuenta real) y «Buscar
  actualización» (el API publica la 131 de MAUI: «Ya tienes la versión más reciente»).

**Criterios de salida.**
- [ ] Llegan las notificaciones de prueba, recordatorio y tarjeta, también con la app cerrada y tras reiniciar el teléfono.
- [ ] Exportar y restaurar un respaldo deja los mismos datos, suscripciones incluidas.
- [ ] Con un `version.json` de prueba, la app ofrece, descarga e instala la actualización.
- [ ] Con el **Pixel Watch 4 real**: vincular, ping, gasto desde el reloj (aparece en el teléfono sin internet) y revocar.

---

### Fase 6. Pruebas integrales y pulido

**Tareas.**

6.1 **Regresión** con las listas del anexo D, completas, en el emulador.

6.2 **Casos de borde.**
- Sin red y con red lenta (Render en frío).
- Cambio de zona horaria.
- Fin de mes, día 31 y febrero.
- Corte el mismo día del pago.
- Suscripción anual con ancla el 29 de febrero.
- Modo semanal con pago en domingo.

6.3 **Accesibilidad.** Etiquetas de TalkBack, áreas táctiles de 48 dp y fuente al 200 %.

6.4 **Release.**
- R8 activado, con reglas para kotlinx.serialization y Retrofit.
- Probar el APK de release, no el de debug.
- Opcional: Baseline Profile para un arranque más rápido.

6.5 **Errores en producción** (opcional, anexo F): Crashlytics o Sentry, para enterarse de
cierres que los usuarios no reportan.

6.6 **Prueba de actualización real.**
- Instalar la versión MAUI publicada en un teléfono con una cuenta de prueba, crear datos y sincronizar.
- Instalar encima el release nativo.
- Verificar que actualiza sin desinstalar, limpia los restos, pide login y baja todo.

**Criterios de salida.**
- [x] ~~Listas del anexo D completas.~~ Descartado por el usuario (chat 10): ya no se compara contra MAUI.
- [ ] La prueba 6.6 pasa con el APK de release firmado.
- [ ] Una semana usando la app nativa como app principal sin fallas bloqueantes.

**Resultado de la Fase 6 (chat 10, 4 oct 2026; en curso).**
- **6.2 Casos de borde.** `CasosDeBordeTest` en `:domain`: corte el 31 en febrero (y bisiesto),
  fecha límite el 31 recortada a febrero que al pagarse salta al 31 de marzo, corte y pago el
  mismo día (liquida el corte del mes anterior), anualidad con ancla el 29 de febrero, mensual
  con ancla el 30, semanal con pago en domingo siendo domingo, quincena 15/30 en febrero,
  mensual el 31 y la conversión de zona. Todos pasaron sin cambiar `:domain`. Sin red y red
  lenta ya estaban cubiertos (Fase 3: 120 s, reintentos exponenciales, modo avión probado).
  **Error encontrado:** `DataModule.provideClock()` era `Clock.systemDefaultZone()`, que fija la
  zona al crearse; como es singleton, al cambiar de zona con la app viva «hoy» seguía en la
  zona vieja hasta matar el proceso. Ahora es `SystemZoneClock`, que lee la zona en cada
  consulta (prueba `SystemZoneClockTest`). Los gastos se guardan en hora local como en MAUI:
  si se viaja, la hora mostrada no cambia y la conversión a UTC usa la zona del momento en que
  se sube (igual que MAUI; documentado en la prueba).
- **6.3 Accesibilidad.** Auditoría con `uiautomator dump` de Resumen, Mis tarjetas,
  Suscripciones, Explorar, Detalle, Ahorros, Perfil, Ajustes y la hoja de gasto: elementos
  tocables sin nombre o de menos de 48 dp (script en el scratchpad, se puede rehacer). Único
  hallazgo real: el FAB extendido «Nuevo gasto» no expone su texto (y contraído solo es el
  ícono): el nombre va ahora en el ícono. Lo demás eran chips cortados por el borde de un
  carrusel o de la pantalla y la manija de la hoja (32 dp, la de Material). Todos los
  `IconButton` ya tenían `contentDescription`. Fuente al 200 % revisada en la 4.6.
- **6.4 R8.** `isMinifyEnabled` + `isShrinkResources` y `proguard-rules.pro` (solo conserva
  archivo y línea; las librerías traen sus reglas). Probado en el emulador con el release
  firmado con la llave de debug de MAUI (para instalarlo encima sin perder la sesión):
  arranque, `SyncWorker` con SUCCESS contra el API, datos al reloj y todas las pantallas sin
  errores. 45 → 5.6 MB. El usuario lo dejó apagado en la 2.0.1 hasta probar el login.
- **6.6** La hizo el usuario (actualizar de MAUI a la nativa funcionó).
- **Ojo con adb:** el teléfono del usuario suele seguir conectado por depuración inalámbrica
  (`192.168.1.2:<puerto>`); todo comando va con `-s emulator-5554`. Y antes de cada toque por
  adb, comprobar que la app está al frente (`dumpsys activity activities | grep
  topResumedActivity`): un «atrás» de más sacó la app y los toques siguientes cayeron en el
  lanzador.

---

### Fase 7. Lanzamiento y corte

**Pasos.**

1. **Aviso a usuarios (1–2 días antes):** «Abran Gastapp con internet para sincronizar».
   La versión MAUI empuja lo pendiente al arrancar (`SyncAllData`).
2. **Release firmado** con el keystore real. Comprobar la huella SHA-256 contra la del APK
   MAUI publicado. `versionCode` 200, `versionName` 2.0.0.
3. **GitHub Release** con el APK del teléfono y `version.json` (`versionCode: 200`), más el
   APK del reloj si cambió. El API lo detecta solo (caché de 10 minutos).
4. **Instalación:** desde cero (decidido en la Fase 2): desinstalar MAUI e instalar la nativa;
   el primer arranque pide login y baja todo. Ya no se depende de que MAUI la ofrezca como
   actualización (aunque se conserva el paquete y la llave, que el reloj sí necesita).
5. **Primeros días:** estar atento a reportes y tener lista una 2.0.1.
6. **Plan de reversa:** Android no deja instalar un `versionCode` menor sin desinstalar.
   - Ante un fallo grave, lo normal es publicar una 2.0.1 nativa corregida.
   - Como último recurso, compilar MAUI con `versionCode` mayor (p. ej. 210) y publicarla: los usuarios «actualizarían» de regreso.
7. **Retiro de MAUI** (tras unas semanas estables):
   - Quitar `Gastapp/` de `Gastapp.sln` y del repo (queda en el historial de git).
   - Pasar el debug del reloj al keystore de Gradle.
   - Actualizar `CLAUDE.md` y `README.md`.
   - `Gastapp.Models/` **se queda**: lo usa el API.

**Criterios de salida.**
- [ ] Todos los usuarios en la 2.0.x.
- [ ] Sin reportes de pérdida de datos.
- [ ] MAUI retirada y la documentación al día.

---

## 6. Anexos

### A. Mapa de archivos MAUI → nativo

| MAUI | Destino nativo | Fase |
|---|---|---|
| `App.xaml(.cs)` | `GastappApplication`, `StartupWorker`, `MainActivity` | 0, 5.5 |
| `AppShell.xaml(.cs)`, `Services/Navigation/*` | Navigation Compose (`ui/navigation`) | 0 |
| `MauiProgram.cs` | Módulos de Hilt (`di/`) | 0 |
| `Platforms/Android/MainActivity.cs`, `MainApplication.cs`, `AndroidManifest.xml`, `Resources/xml/file_paths.xml` | `MainActivity`, `GastappApplication`, manifiesto, `res/xml` | 0 |
| `Platforms/Android/Wear/GastappWearListenerService.cs`, `WearChannel.cs` | `platform/wear/` | 5.4 |
| `Platforms/iOS`, `MacCatalyst`, `Windows`, `Tizen` | No se migran | — |
| `Resources/Styles/Colors.xaml`, `Styles.xaml`, `Icons.xaml` | `ui/theme` (del demo) y Material Symbols | 0 |
| `Resources/Fonts` | Baloo → `res/font`; OpenSans y Font Awesome no se migran | 0 |
| `Resources/AppIcon`, `Resources/Splash` | Ícono adaptable con capa monocromática y Splash Screen API | 0 |
| `Data/GastappDbContext.cs` | `data/local` (Room) | 2 |
| `Data/PurgeDeletedLocal.cs` | `sync/PurgeWorker` | 2, 5.5 |
| `Services/ApiService/IApiService.cs` | `:core/remote/GastappApi` | 3 |
| `Services/SpendingService/*` | `SpendingRepository`, `CategoryRepository`, `domain/periods` | 1, 2 |
| `Services/CreditCardService/*` | `CreditCardRepository`, `domain/cards` | 1, 2 |
| `Services/SubscriptionService/*` | `SubscriptionRepository`, `domain/subscriptions` | 1, 2 |
| `Services/UserService/*` | `SessionRepository`, `UserRepository` | 3 |
| `Services/Notifications/*` | `platform/notifications` | 5.1 |
| `Services/BackupService/*` | `platform/backup` | 5.2 |
| `Services/AppUpdateService/*` | `platform/update` | 5.3 |
| `Services/WearService/*` | `platform/wear` | 5.4 |
| `Services/RegisterDraftService.cs` | DataStore (borrador sin contraseña) | 2 |
| `Messages/*` (`DayChanged`, `SpendingChanged`, `DevicesChanged`, `WearDeviceLinked`) | `Flow` de Room, de «hoy» y de dispositivos | 2, 5.5 |
| `Utils/DateTimeUtils.cs` | `domain/time` | 1 |
| `Utils/AlertHelper.cs`, `Popups/AlertPopup` | `AlertDialog` y Snackbar | 4 |
| `Utils/PagesUtils.cs` | Revisar al portar; probablemente desaparece | 4 |
| `Validators/*` | Validación en ViewModels | 4.1 |
| `Controls/GastappLargeButton` | `Button` de Material | 4 |
| `ViewModels/*` y `Pages/*`, `BottomSheets/*`, `Popups/*` | Ver tablas de la Fase 4 | 4 |
| `ViewModels/DayItem`, `PendingMsiPurchase`, `ReminderFrequencyOption`, `SubscriptionPickerOptions` | Modelos de UI en su `feature/` | 4 |
| `ViewModels/OfflineRegisterViewModel` | No se migra (apagado) | — |

### B. Endpoints del API

«Nativa» marca lo que necesita la app nativa. Con `SyncAllData` haciendo upsert y borrados,
sobran casi todas las llamadas individuales.

| Método y ruta | Cuerpo o parámetros | Nativa |
|---|---|---|
| `POST /User/Login` | `{ email, password }` → `AllUserData` | ✅ |
| `POST /User/CreateUser` | `CreateUserModel` → `CreateUserResponse` | ✅ |
| `POST /User/RefreshToken` | Bearer → `Token` | ✅ |
| `POST /User/EmailVerification/request` | `?Email=` | ✅ |
| `POST /User/EmailVerification/verify` | `?Email=&Code=` | ✅ |
| `POST /User/PasswordReset/request` | `?Email=` | ✅ |
| `POST /User/PasswordReset/verify` | `?Email=&Code=` | ✅ |
| `POST /User/PasswordReset/confirm` | `?email=&code=&newPassword=` ⚠️ | ✅ |
| `POST /User/PasswordReset/temporary` | `?email=` | ✅ |
| `POST /User/UpdateUserPayInfo` | `UserInfoDto` | — (va en `SyncAllData`) |
| `POST /Spendings/SyncAllData` | `SyncDataDto` | ✅ |
| `GET /Spendings/GetSpendings` | → `List<SpendingDto>` | ✅ |
| `POST /Spendings/DeleteCategory` | `?categoryId=` | ✅ |
| `GET /Spendings/GetIncomes` | → `List<IncomeType>` | — (vienen en `AllUserData`) |
| `POST /Spendings/CreateNewSpending`, `UpdateSpending`, `DeleteSpending` | | — (`SyncAllData`) |
| `POST /Spendings/CreateNewCategory`, `UpdateCategory` | | — (`SyncAllData`) |
| `POST /Spendings/CreateCreditCard`, `DeleteCreditCard` | | — (`SyncAllData`) |
| `POST /Spendings/CreateSubscription`, `DeleteSubscription` | | — (`SyncAllData`) |
| `POST /Spendings/SyncNewSpendings`, `SyncNewCategories` | | — (heredadas) |
| `POST /Device/Link` | `LinkDeviceRequest` → `LinkDeviceResponse` | ✅ |
| `GET /Device/List` | → `List<DeviceDto>` | ✅ |
| `POST /Device/Revoke` | `RevokeDeviceRequest` | ✅ |
| `GET /App/LatestVersion` | → `AppLatestVersionDto` | ✅ |
| `/Device/Code`, `Token`, `Refresh`, `Categories`, `Expenses`, `Summary` | | — (solo el reloj) |

⚠️ `PasswordReset/confirm` recibe la contraseña nueva en la URL, y las URL quedan en los logs
de Render. No bloquea la migración, pero conviene cambiarlo después a un cuerpo JSON, en el
API y en la app a la vez.

### C. Reglas de negocio que no se pueden romper

Todas llevan prueba con nombre en la Fase 1.

1. **Corte y pago se calculan por separado**, cada uno como «la próxima vez que llega ese
   día del mes». Calcular el pago desde el próximo corte salta un ciclo completo cuando ya
   pasó el corte pero no ha llegado el pago.
2. **La fecha límite salta al mes siguiente** cuando el corte vigente quedó cubierto: compras
   con `fecha <= corte` menos **todos** los abonos ≤ 0.01. Si ese corte aún no llega, no se toca.
3. **Los pagos a tarjeta son `Spending` con `IsCreditCard = false`**. La deuda es la suma de
   compras con `IsCreditCard = true` menos la de abonos, por `CreditCardId`.
4. **Ancla de suscripciones:** cada cobro es `ancla + n × meses`, **nunca** encadenando
   `plusMonths` sobre el anterior (un día 31 recortado a 30 se perdería para siempre).
   `plusMonths` de Java recorta igual que `AddMonths` de .NET.
5. **Una suscripción pausada o en prueba gratis no cuenta en los totales.** El mismo criterio
   alimenta el porcentaje del total mensual.
6. **Registrar cobro avisa pero no bloquea** si el del periodo ya se registró.
7. **Formas de pago:** las mismas cuatro en gasto y suscripción (`Cash`, `Debit`, `Transfer`,
   `CreditCard`) y con las mismas etiquetas.
8. **Fechas de calendario sin zona horaria.** `FirstChargeDate`, `TrialEndDate` y `BirthDate`
   viajan tal cual. Convertir una fecha de las 00:00 a UTC la corre al día anterior.
9. **Instantes en UTC.** `DeletedAt`, `LastChargeRegisteredAt` y la fecha de un gasto
   (`Date`) se convierten a UTC al enviar y de UTC a local al recibir (`DateTimeUtils`).
10. **Modo semanal:** el día de pago se guarda en `FirstPayDay` (no en `WeekPayDay`, que no se
    usa) con la convención de .NET: domingo = 0 … sábado = 6. En `java.time` se traduce con
    `dayOfWeek.value % 7`, y lo guardado no se cambia.
11. **Redondeo bancario:** donde MAUI usa `Math.Round` (mensualidad MSI, promedio diario,
    porcentajes) se usa `RoundingMode.HALF_EVEN`.
12. **Referencias colgantes:** si una tarjeta o categoría no existe localmente, se anula la
    referencia y se conserva el registro. Guardarla tumba el lote completo.
13. **Un gasto del reloj conserva el `SpendingId` que generó el reloj** (idempotencia: el
    servidor hace upsert por ese id).
14. **Purga local** de lo borrado a los 30 días de `DeletedAt`.
15. **El periodo en curso termina hoy**, no en su último día natural: los días futuros no se
    muestran en la tira de Resumen.
16. **Las compras con tarjeta no cuentan en el total del periodo** (ni en Ahorros ni en el
    resumen por categoría): cuenta el pago a la tarjeta cuando se hace. En cambio, el **total
    del día** suma todo lo del día, compras con tarjeta incluidas. Así viene de MAUI; ver
    anexo F.
17. **En «Tarjetas por pagar» de Ahorros el ámbar empieza a 5 días**; en Mis tarjetas, a 3.

**Diferencia intencional con MAUI:** el total del periodo del calendario comparaba contra las
00:00 del último día y dejaba fuera lo gastado ese día. En la versión nativa se incluye el
último día completo (`periodTotal` en `:domain`).

### D. Lista de paridad por pantalla

Sale de los `[RelayCommand]` de cada ViewModel de MAUI. Cada punto se marca al probarlo en
el teléfono.

- **Inicio (StartPage):**
  - Iniciar sesión.
  - Mostrar u ocultar la contraseña.
  - Crear cuenta.
  - Olvidé mi contraseña.
  - Aviso de privacidad.
  - Entrar directo si hay sesión.
- **Registro (5 pasos):**
  - Siguiente y anterior.
  - Validaciones con los mismos mensajes.
  - Código de correo y reenviarlo.
  - Aviso de privacidad.
  - Borrador que sobrevive a cerrar la app.
- **Recuperar contraseña:**
  - Enviar código.
  - Verificarlo.
  - Nueva contraseña.
  - Contraseña temporal.
  - Volver al login.
- **Principal:**
  - Las 4 pestañas.
  - FAB de nuevo gasto.
- **Resumen:**
  - Ir a hoy.
  - Periodo anterior y siguiente.
  - Explorar periodo y aplicar un rango.
  - Tocar un gasto abre su detalle.
  - Borrar un gasto con Deshacer.
  - Abrir Tarjetas y Suscripciones desde los iconos de la barra superior (con el puntito de pago por vencer) y desde las tarjetas de Perfil.
- **Formulario de gasto:**
  - Las 4 formas de pago.
  - Elegir tarjeta.
  - MSI con plazos de 3 a 24 meses y la mensualidad.
  - Crear categoría.
  - Borrar categoría avisando cuántos gastos tiene.
  - Título, descripción y fecha (sin futuro).
  - Guardar.
  - Eliminar (en edición).
- **Detalle de gasto:**
  - Todos los datos del gasto (monto, categoría, forma de pago, tarjeta, MSI, fecha, descripción).
  - Editar (abre el formulario).
  - Volver con atrás predictivo.
- **Detalle de categoría:**
  - Lista del periodo.
  - Tocar un gasto.
  - Borrar un gasto.
  - Volver.
- **Ahorros:**
  - Periodo anterior y siguiente.
  - Abrir Tarjetas.
  - Abrir una categoría.
  - Registrar pago de una tarjeta.
- **Mis tarjetas:**
  - Seleccionar tarjeta.
  - Agregar y editar con color.
  - Tarjeta en uso: saldo de contado + MSI previas, agregar y quitar.
  - Eliminar.
  - Ajustar saldo.
  - Registrar pago.
  - Nueva compra.
  - Compra a MSI.
  - Ver una compra.
  - Volver con atrás predictivo.
- **Suscripciones:**
  - Seleccionar una.
  - Agregar y editar: color, periodicidad, forma de pago, tarjeta, categoría, prueba y notas.
  - Eliminar.
  - Pausar y reanudar.
  - Registrar cobro con el aviso de duplicado.
  - Volver con atrás predictivo.
- **Perfil:**
  - Frecuencia de ingreso y sus días de pago.
  - Ingreso.
  - Modo de ahorro (% o monto).
  - Guardar (se sincroniza).
- **Ajustes:**
  - Recordatorios: sí/no y frecuencia.
  - Activar notificaciones del sistema.
  - Notificación de prueba.
  - Exportar respaldo.
  - Restaurar respaldo.
  - Vincular reloj.
  - Revocar dispositivo.
  - Recargar dispositivos.
  - Abrir Tarjetas.
  - Estado de la nube.
  - Tema y Material You.
  - Cerrar sesión.

### E. Lo que no se migra

- Syncfusion: calendario, barra circular y radio buttons. Se quita también la licencia escrita en `App.xaml.cs`.
- CommunityToolkit.Maui, The49.BottomSheet, Plugin.LocalNotification, FluentValidation y Refit: tienen equivalente nativo.
- `HttpClientHandler.DangerousAcceptAnyServerCertificateValidator` y `usesCleartextTraffic`: el API es HTTPS.
- Los pines de `Xamarin.GooglePlayServices.Wearable` y de los `*.Ktx` en `Gastapp.csproj`.
- Carpetas de iOS, MacCatalyst, Windows y Tizen, `OfflineRegisterViewModel` y la exportación del `.db` crudo.
- OpenSans y Font Awesome (se usan la fuente del sistema y Material Symbols).

### F. Decisiones pendientes

| Pregunta | Recomendación |
|---|---|
| ¿Se queda la página de detalle de gasto? | **Decidido: sí**, con el botón Editar dentro, igual que en MAUI |
| ¿Renombrar `Gastapp.WearOS/`? | **Decidido: sí**, a `Gastapp.Android/` |
| ¿Versión mínima? | **Decidido: Android 12** (`minSdk 31`) |
| ¿Importar respaldos JSON hechos con la versión MAUI? | No: los datos ya están en la nube |
| ¿Reporte de errores en producción (Crashlytics o Sentry)? | **Decidido (chat 10): ninguno por ahora**, queda pendiente. Si se retoma, Sentry pide solo un DSN; Crashlytics, proyecto de Firebase y `google-services.json` |
| ¿Mover a `:core` la red y los DTO que hoy tiene el reloj? | Después del lanzamiento; en la Fase 5.4 solo se comparten rutas y payloads de la Data Layer |
| ¿Widget de inicio, atajos del ícono, botones en notificaciones? | Después del lanzamiento (versión 2.1) |
| ¿Material You encendido por defecto? | No: tema de marca por defecto, interruptor en Ajustes |
| ¿Unificar el total del día y el del periodo? En MAUI el del día cuenta las compras con tarjeta y el del periodo no (regla 16) | Decidir antes de la Fase 4. Recomendación: que los dos sigan el criterio del periodo (cuenta el pago, no la compra), y mostrar las compras con tarjeta del día con su etiqueta, sin sumarlas |

### G. Riesgos y cómo se cubren

| Riesgo | Efecto | Mitigación |
|---|---|---|
| Firma o paquete distintos a MAUI | La actualización falla y hay que desinstalar | Verificar la huella en la Fase 0 y antes del release; prueba 6.6 |
| `:app` y `:wear` firmados con keystores de debug distintos | La Data Layer no entrega y no da ningún error | Configuración de firma de debug compartida (0.5) |
| Cálculos que difieren de MAUI | Fechas de pago o totales incorrectos | Fixtures de paridad (Fase 1) |
| Fechas y zonas horarias | Cobros corridos un día | Tipos separados (anexo C) y pruebas con otra zona |
| Render en frío | Primer login o sincronización lentos | Timeout de 120 s, reintentos de WorkManager e indicadores sin bloquear |
| Datos sin sincronizar al actualizar | Se pierden gastos locales | Aviso previo; MAUI sincroniza al abrir (Fase 7) |
| La UI tarda más de lo estimado | Retraso | Orden por uso; el demo ya resolvió diseño y componentes |


---

## 7. Bitácora y cómo retomar

La migración se trabaja **un chat por fase**. Al cerrar cada chat se actualiza esta sección y
la memoria del proyecto (`migracion-android-estado` en la memoria de Claude), para que el
chat siguiente arranque sin perder nada.

### 7.1 Cómo retomar en un chat nuevo

1. Leer `CLAUDE.md` (sección «Migracion a Android nativo»), este documento completo y la
   memoria `migracion-android-estado`.
2. Comprobar que todo sigue en verde antes de tocar nada, desde `Gastapp.Android/`:
   `./gradlew :domain:test :app:assembleDebug :wear:assembleDebug` (con el JDK predeterminado).
   Al terminar, `./gradlew --stop`, para no dejar un demonio bloqueando la carpeta.
3. Probar solo en el emulador (AVD `Pixel_10a`), nunca en el teléfono del usuario. El usuario
   a veces usa el emulador al mismo tiempo: avisar antes de tomar capturas o mandar gestos.
4. No hacer commits: los hace el usuario. Dejar los cambios listos y decirle qué incluir.

### 7.2 Estado por fase

**Fase 0 (terminada el 2 oct 2026).** El detalle está en el «Resultado» de la Fase 0 (sección
5). Dónde quedó cada cosa:
- `Gastapp.Android/settings.gradle.kts`: `:app`, `:wear`, `:core`, `:domain`.
- `Gastapp.Android/build.gradle.kts`: plugins y la **firma compartida** de `:app` y `:wear`.
- `Gastapp.Android/gradle/libs.versions.toml`: versiones; los alias de wear-compose ahora son
  `androidx-wear-compose-material` y `androidx-wear-compose-foundation`.
- `app/src/main/java/com/binc/gastapp/`: `GastappApplication`, `MainActivity`,
  `ui/theme` (Color, Theme), `ui/components` (Motion, Components), `ui/format/MoneyFormat`,
  `ui/navigation` (Routes, TopLevelTab, PredictiveBack), `ui/GastappApp` (NavHost) y
  `ui/main` (MainScreen y `Placeholders.kt`, que se va borrando conforme llegan las pantallas
  reales de la Fase 4).
- `app/src/debug/`: configuración de red solo para debug (http a `10.0.2.2`).
- Pendiente del usuario: comparar en el emulador el atrás predictivo de la app con el del demo
  (Resumen → iconos de tarjetas, suscripciones o «Explorar periodo» en la barra superior, y gesto de atrás).

**Fase 2 (terminada el 2 oct 2026, chat 3).** Detalle en el «Resultado» de la Fase 2.
Decisión del usuario en este chat: **nada de compatibilidad con MAUI**, la app se instala
desde cero (se descartó la limpieza 2.5). Pendientes para la Fase 3: `markSynced` puede marcar
como subido algo editado mientras se subía (comparar antes de marcar); al bajar del servidor
hay que anular referencias a tarjetas/categorías que no existan (una colgante truena la llave)
y los gastos con categoría desconocida van a «Sin categoria»; cambiar `NoOpSyncScheduler` por
el de WorkManager; al cerrar sesión, `clearAllTables()` + borrar `SessionStore` y
`RegisterDraftStore`. Las pruebas de DataStore usan un DataStore en memoria: el de archivo no
funciona en la JVM de Windows.

**Fase 3 (terminada el 2 oct 2026, chat 4).** Detalle en el «Resultado» de la Fase 3. Los
cuatro criterios se comprobaron en el emulador con la cuenta del usuario (él teclea la
contraseña: Claude no escribe contraseñas de un servicio real). En su cuenta quedaron
registros «Prueba sync» (un gasto, una suscripción y dos tarjetas, una borrada); se borran
con el laboratorio. Pendientes para la Fase 4:
- **Dos «Sin categoria» en la cuenta del usuario** (hallazgo 13): se ven como dos renglones
  en el resumen por categoría. Hay que decidir si la app las une (mover los gastos a una y
  borrar la otra; `DeleteCategory` rechaza las de por defecto, así que hace falta tocar el
  API) o si solo se agrupan al mostrar. Mientras, `ensureDefault` toma la primera que
  encuentra.
- Hallazgo 18 (tarjeta sin `DeletedAt`), arreglo de una línea en el API.

**Fase 4 (pantallas hechas; falta revisarla con el usuario. Chats 5 y 6, 3 oct 2026).**
Decisiones del usuario: el total del día sigue el criterio del periodo (cuenta el pago a la
tarjeta, no la compra; las compras se listan con la etiqueta «Cuenta al pagar la tarjeta»), y
las dos «Sin categoria» solo se agrupan al mostrar (`ui/category/CategoryDirectory`). Hecho y
probado en el emulador con la cuenta del usuario: 4.1 (`ui/start/`: inicio, hoja de login,
registro de 5 pasos con borrador, recuperar contraseña; la contraseña temporal NO se ofrece,
hallazgo 19), 4.2 (`ui/summary`, `ui/spending`, `ui/category`, `ui/explore`), 4.3
(`ui/savings`), 4.4 (`ui/cards`), 4.5 (`ui/subscriptions`) y 4.6 (`ui/profile`,
`ui/settings`; detalle en su «Resultado», sección 5). Avisos con «Deshacer» compartidos
(`ui/components/AppMessages`). Tema oscuro y fuente al 200 % revisados en todas las pantallas.
129 pruebas de `:app` en verde.

**Falta para cerrar la Fase 4 (con el usuario):**
1. ~~Revisar la 4.1 en el emulador~~: la revisó el usuario (chat 6) y volvió a entrar. Encontró
   dos cosas, ya corregidas: el «000000» del campo del código no estaba centrado como lo
   tecleado (registro y recuperar contraseña; ahora es `ui/start/VerificationCode.kt`) y en
   recuperar contraseña se podía reenviar el código sin límite (hallazgo 23). Pendiente que lo
   vea corregido (solo aparece con la sesión cerrada).
2. El recorrido de salida (login → gasto → editar → tarjeta → pago → suscripción → cobro →
   cambiar perfil → cerrar sesión). Crea datos reales en su cuenta: lo hace él o lo autoriza.
3. Probar el permiso de notificaciones concediéndolo y la notificación de prueba (en el
   emulador el permiso nunca se ha concedido; solo se vio que se pide y que, al negarlo, ofrece
   abrir los ajustes).
4. Vincular un reloj tecleando el código (necesita el emulador del reloj o el Pixel Watch).

Los registros «Prueba sync» que quedaron en su cuenta (Fase 3) ya se pueden borrar desde las
pantallas reales; el laboratorio ya no existe.

**Siguiente paso:** cerrar los pendientes de arriba con el usuario y pasar a la **Fase 5**
(funciones de plataforma) en un chat nuevo.

**Fase 1 (terminada el 2 oct 2026).**
- Código: `domain/src/main/kotlin/com/binc/gastapp/domain/` con los paquetes `model`
  (Spending, CreditCard, Subscription, Category, PlannedSpending, StatusLevel y las constantes
  PaymentMethods, BillingCycles, IncomeTypes), `money` (BigDecimal, HALF_EVEN, centavos,
  formato N2), `time` (conversión UTC del API), `cards` (CardCycle, CardSummary,
  CardMovements), `subscriptions` (SubscriptionSchedule, SubscriptionSummary), `periods`
  (PayPeriods), `spendings` (SpendingTotals) y `savings` (Savings).
- Pruebas: `domain/src/test/`, con tres clases de paridad (tarjetas, suscripciones, periodos y
  ahorro) y `ReglasDeNegocioTest` con 16 reglas con nombre.
- Herramienta: `tools/Gastapp.Paridad/` (consola .NET 8 que referencia `Gastapp.Models`). En
  `Referencia/` está la copia de la lógica de MAUI; `Program.cs` genera 9 archivos JSON
  (~5.5 MB) en `domain/src/test/resources/paridad/`. Es determinista (semillas fijas).
- Resultado: 68,809 casos iguales a MAUI en 12 comparaciones, más las 16 reglas. Prueba de
  sabotaje: cambiar el redondeo bancario por el normal dio 43 diferencias; mover un umbral de
  3 a 4 días dio 30.
- Limitaciones: se compara contra una copia del código de MAUI (si la copia tuviera un error,
  no se notaría); los textos con fecha formateada («(05/oct)») no se comparan porque dependen
  de la cultura; los cálculos repiten los errores de MAUI, salvo la diferencia intencional del
  anexo C.

**Fase 5 (código hecho; falta probarla con el usuario. Chat 7, 3 oct 2026).** Detalle en el
«Resultado de la Fase 5» (sección 5). 154 pruebas de `:app` en verde. Falta, con el usuario:
1. Conceder el permiso de notificaciones en el emulador (sigue en `granted=false`) y ver la de
   prueba, un recordatorio y un aviso de tarjeta; luego con la app cerrada y tras reiniciar el
   emulador. Para no esperar horas: la frecuencia más corta (2 h) o una tarjeta con corte en
   dos días.
2. ~~Restaurar de verdad~~: hecho con su permiso (chat 7). Dejó 88 pendientes (perfil, 72
   gastos, 8 categorías, 5 tarjetas, 2 suscripciones), subieron en ~8 s y quedó «Todo está en
   la nube». Las notificaciones las probó él en el emulador y sí llegan.
3. Actualización con un `version.json` de prueba (APK con `versionCode` 201 servido desde la
   máquina) hasta abrir el instalador.
4. Con el Pixel Watch 4 real (o los dos emuladores emparejados): vincular sin teclear, ping,
   gasto desde el reloj sin internet y quitarlo desde Ajustes (le debe llegar `/revoked`).
5. Decidir el hallazgo 25 (total del día en el reloj).
También siguen abiertos los pendientes de la Fase 4 (recorrido de salida, ver arriba).

**Fase 6 (en curso, chat 10, 4 oct 2026).** Detalle en el «Resultado de la Fase 6» (sección 5).
Hecho: 6.2 casos de borde (`domain/.../CasosDeBordeTest.kt`, 9 casos, y un error real corregido:
el `Clock` no seguía el cambio de zona horaria), 6.3 accesibilidad (auditoría con uiautomator;
el FAB «Nuevo gasto» no tenía nombre para TalkBack) y 6.4 R8 configurado y probado en el
emulador (45 → 5.6 MB). 6.6 la hizo el usuario por su cuenta (MAUI → nativa, funcionó). 6.5
(Sentry/Crashlytics) se deja pendiente por decisión del usuario. Falta:
1. **Activar R8:** el usuario lo apagó en `f8f401d` hasta comprobar login, sincronización y
   Room con el APK minificado. Sincronización, Room y todas las pantallas ya se vieron bien en
   el emulador (chat 10); falta que él cierre sesión y vuelva a entrar con el minificado.
2. Una semana usándola como app principal (criterio de salida).

Decididos por el usuario en el chat 10: **la regresión 6.1 contra MAUI no se hace** («ya
dejemos la app MAUI atrás») y el **hallazgo 25 se queda como está**.

**Fase 7 (adelantada por el usuario).** Ya publicó el prerelease `v2.0.0-alpha1` en GitHub
(APK de 52 MB sin minificar, `version.json` con 200). El siguiente release se prepara con
`Gastapp.Android/preparar-release.ps1` (compila, verifica la huella completa de
gastappkeystore y deja el APK, `version.json` y el `mapping.txt` en `release/<etiqueta>/`;
imprime el `gh release create`, no publica). El retiro de MAUI sigue para después de unas
semanas estables.

### 7.3 Hallazgos sobre MAUI que no hay que perder

1. **Totales inconsistentes:** el total del día suma las compras con tarjeta; el del periodo, el
   resumen por categoría y Ahorros no (cuentan el pago a la tarjeta). Decisión pendiente
   (anexo F).
2. **Error de MAUI corregido en la versión nativa:** el total del periodo del calendario
   comparaba contra las 00:00 del último día y dejaba fuera lo gastado ese día
   (`SummaryViewModel.GetRangeDays` → `GetTotalAmountByPeriod`). `periodTotal` sí lo incluye.
3. **Modo semanal:** el día de pago va en `FirstPayDay` con la numeración de .NET
   (0 = domingo); `WeekPayDay` no se usa en ningún lado.
4. **El periodo en curso termina hoy**, no en su último día natural.
5. **«Tarjetas por pagar» de Ahorros** pone ámbar desde 5 días; Mis tarjetas desde 3.
6. **Respaldo JSON sin suscripciones:** el de MAUI no las incluye (quedó una tarea sugerida
   para MAUI; el respaldo nativo sí las lleva).
7. **Borrar una categoría sin red se pierde:** MAUI llama `DeleteCategory` en el momento y no
   lo reintenta. La versión nativa agrega `isDeleted` a las categorías (Fase 2).
8. **`SyncAllData` ya hace upsert y borrados** (`IsDeleted`/`DeletedAt`) de gastos, tarjetas
   y suscripciones; de categorías solo upsert. Por eso la app nativa necesita 16 de las 28
   llamadas (anexo B).
9. **`PasswordReset/confirm` manda la contraseña nueva en la URL**, que queda en los logs de
   Render. Corregir después en API y app a la vez.
10. **Publicación:** el API (`Services/AppUpdateService.cs`) lee los releases de GitHub, toma
    el APK del teléfono y el `version.json` del release, con caché de 10 minutos. La versión
    nativa se publica igual, con `versionCode` mayor que 130.
11. **Formas de pago y periodicidades son texto**, no enums: un valor desconocido se trata
    como «Efectivo» o «Mensual». Se respetó en `:domain`.
12. **Archivos de MAUI en el teléfono** (comprobado en la Fase 0): la base es
    `databases/gastapp.db`, las `Preferences` viven en `shared_prefs/com.binc.gastapp_preferences.xml`
    (no en `...microsoft.maui.essentials.preferences`) y Plugin.LocalNotification guarda sus
    notificaciones en `shared_prefs/plugin.LocalNotification.NotificationRepository.xml`.
13. **MAUI duplica «Sin categoria» al registrarse:** `CreateUser` ya crea la del servidor y
    `UserService.CreateNewUser` crea otra local con id nuevo, que luego sube. La nativa entra
    con `Login` después de `CreateUser`.
14. **`CreateUser` guarda `BirthDate` en `timestamptz` sin normalizar** (`SyncAllData` sí hace
    `SpecifyKind`): Npgsql rechaza un `DateTime` sin zona ahí, así que debe ir con `Z`
    (MAUI lo manda con `SpecifyKind(Utc)`). Fechas del API según su `Kind`: gastos y
    `DeletedAt` con `Z` (timestamptz), `FirstChargeDate`/`TrialEndDate` sin zona (columna
    `date`) y `TokenExpiration` con desfase, porque el API usa `DateTime.Now`.
15. **El login devuelve la entidad `User` completa**, con `passwordResetCodeHash`,
    `passwordResetCodeExpiresAt`, `incomeType` y colecciones vacías (solo `PassWordHash` lleva
    `[JsonIgnore]`). No bloquea; conviene un DTO en el API.
16. **Volver a iniciar sesión en MAUI borraba lo pendiente de subir** (`ResetDatabaseAsync`).
17. **`SyncAllData` responde 400 a un envío vacío** y solo procesa lo que llega con
    `IsSynced = false`; `DeleteCategory` responde 404 si ya no existe y 400 si es la de por
    defecto. Las categorías creadas en otro dispositivo no bajan (solo los gastos, por
    `GetSpendings`), igual que en MAUI.
18. **Una tarjeta que llega a `SyncAllData` nueva y ya borrada se guarda sin `DeletedAt`**
    (el alta de tarjetas no llama a `ResolveDeletedAt`; gastos y suscripciones en ese caso ni
    se crean). Sin `DeletedAt` ni la purga del servidor ni la local la quitan nunca. Arreglo
    de una línea en el API (`DeletedAt = ResolveDeletedAt(card.IsDeleted, null, card.DeletedAt)`).
19. **`PasswordReset/temporary` es anónimo y solo pide el correo:** genera una contraseña
    temporal, la aplica y la manda por correo. Cualquiera puede cambiarle la contraseña a otra
    cuenta (la víctima no pierde datos, pero se queda fuera hasta revisar su correo). La app
    nativa no ofrece esa opción; corregir en el API (exigir el código verificado).
20. **El reloj ya no necesita que se teclee el código:** `LinkDevicePopup` de MAUI solo espera;
    el reloj muestra el código y además se lo manda al teléfono por Bluetooth, y
    `GastappWearListenerService` llama a `Device/Link`. Mientras llega la Fase 5.4, la nativa
    deja teclear el código que muestra el reloj (el API ignora guiones y mayúsculas).
21. **Los respaldos de MAUI solo existían en debug** (`IsBackupToolsVisible`): en producción
    nadie podía exportar ni restaurar. Los de la Fase 5.2 serán nuevos para los usuarios.
22. **Meta de ahorro con monto fijo:** MAUI calcula el porcentaje con 4 decimales pero lo pasa
    por el texto `"0.##"` y guarda 2 (3,000 de 9,000 → 33.33 %, que devuelve 2,999.70). La nativa
    guarda los 4 decimales de `savingsPercentFromAmount`.
23. **Pedir códigos por correo no tenía límite en el API** (`EmailVerification/request`,
    `PasswordReset/request` y `/temporary`): cada llamada generaba un código y mandaba un correo,
    así que se podía llenar de correos a cualquiera (y gastar el plan de Resend). MAUI y la app
    nativa esperaban 60 s solo en el registro; en recuperar contraseña ninguna. **Corregido en el
    API** (`Services/CodeRequestLimiter.cs`): uno cada 60 s y máximo 5 por hora por correo y
    propósito; si no, responde 429 con «Espera N segundos para pedir otro código.» y
    `Retry-After`. Cuenta también los correos sin cuenta (la respuesta no revela si existen) y no
    cuenta el intento si el correo no salió. Vive en memoria, como los intentos de vincular un
    reloj. La app nativa espera los mismos 60 s en los dos flujos y, si se regresa al paso del
    correo y se sigue con el mismo, no pide otro (el anterior sirve). MAUI pide uno en cada
    «Siguiente» del registro: si alguien va y viene en menos de un minuto verá el mensaje del 429.
24. **El código de recuperar contraseña no tenía límite de intentos** (`PasswordReset/verify` y
    `confirm`): un código de 6 dígitos que vive 15 minutos se podía adivinar a fuerza de
    intentos. La verificación del registro ya cortaba a los 5. **Corregido en el API**: al quinto
    fallo el código se borra de la base y responde «Demasiados intentos fallidos. Solicita un
    código nuevo.» (contador en memoria, ligado al código vigente).
25. **El total del día del reloj no sigue el criterio del teléfono:** MAUI y `GET /Device/Summary`
    suman todo lo del día, compras con tarjeta incluidas; el teléfono, desde la decisión del chat
    5, no las cuenta. La versión nativa publica al reloj lo mismo que el API (si no, el total del
    reloj cambiaría según de dónde llegara el dato). Igualarlo al teléfono obliga a cambiar
    también el API. **Decidido (chat 10): se queda así.**

### 7.4 Entorno y herramientas

- **JDK:** el `JAVA_HOME` del sistema es el JDK 25. Desde la Fase 0 (Kotlin 2.2.21) Gradle
  compila con él sin trucos; el JDK 21 y el `in-process` ya no hacen falta. Android Studio usa
  `jbr-21`.
- **Android Studio** bloqueaba renombrar la carpeta mientras tenía abierto el proyecto. Ya se
  renombró: hay que abrir el proyecto desde `Gastapp.Android/` (el de `Gastapp.WearOS/` en la
  lista de recientes ya no existe).
- **Pruebas solo en el emulador** (lo pidió el usuario en la Fase 0): AVD `Pixel_10a`
  (Android 17, navegación por gestos, `emulator-5554`) y `Wear_OS_XL_Round` para el reloj. No
  conectarse por `adb` al Pixel 9 Pro XL del usuario. Para el gesto de atrás por `adb`:
  `input touchscreen motionevent DOWN 3 1300`, varios `MOVE` horizontales y `UP`. Un primer
  movimiento casi vertical no cuenta como gesto de atrás y desplaza la lista.
- **Estado del emulador:** la app nativa de debug (2.0.0) quedó instalada **encima** de MAUI
  1.1.2-alpha1, con los archivos viejos de MAUI todavía en su carpeta de datos. Para volver a
  tener MAUI ahí: `adb uninstall com.binc.gastapp` y el `-t:Install` de siempre.
- **Demo** instalado en el emulador como `com.binc.gastapp.demo` (convive con la app real).
  Se compila con `./gradlew :app:installDebug` dentro de `Gastapp.ComposeDemo/` y acepta
  extras para abrir pantallas: `--es pantalla resumen|ahorros|perfil|ajustes|tarjetas|suscripciones|periodo`,
  `--es tema oscuro`, `--ez hoja true`, `--ez editar true`, `--ez dinamico true`.
- **Preparar un release:** `Gastapp.Android/preparar-release.ps1 -Etiqueta vX.Y.Z-alphaN`
  (con las variables `GASTAPP_*` puestas). Desde build-tools 37, `apksigner` escribe
  «V2 Signer: certificate SHA-256 digest» en vez de «Signer #1 ...»; el script acepta los dos.
- **Release firmado:** `local.properties` todavía no tiene las claves `gastappKeystore*`; en la
  Fase 0 se firmó pasando las variables `GASTAPP_KEYSTORE`, `GASTAPP_KEY_ALIAS`,
  `GASTAPP_KEYSTORE_PASSWORD` y `GASTAPP_KEY_PASSWORD` (datos en la memoria
  `firmar-apk-con-keystore-real`). El release pesa 45 MB porque no se minifica y lleva
  `material-icons-extended` entero: activar R8 en la Fase 6.
- **Fixtures de contrato:** `dotnet run --project tools/Gastapp.Contratos` desde la raíz
  regenera las respuestas y verifica los cuerpos que manda la app (`-- generar` o
  `-- verificar` para hacer solo una cosa). Si cambia un DTO en `Gastapp.Models`, correrlo y
  luego las pruebas de `:core` y `:app` (`:app` lee los mismos fixtures de
  `core/src/test/resources`).
- **adb** no está en el PATH: `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`.
- **Advertencias conocidas** al compilar `:wear` (código del reloj sin tocar):
  `fallbackToDestructiveMigration()` deprecado desde Room 2.8 y `Locale(String, String)`.

### 7.5 Commits

El usuario hace los commits. Todo lo del chat 1 quedó en el commit `29147fe`
(«Se agrego proyecto android nativo», 2 oct 2026).

La Fase 0 quedó en el commit `18c5447` («Fase 0 terminada», 2 oct 2026): 160 archivos, con el
renombre `Gastapp.WearOS/` → `Gastapp.Android/` registrado como 88 renombres, el borrado de
`mobile/`, `app/`, `core/`, los builds, el README, `CLAUDE.md` y este plan. Quedó en `master`.

Después se fusionó con `6b7fb87` de `origin/master` («RELEASE: 1.1.3-alpha1 (versionCode 131)»,
de Néstor Daniel, 6 sep; solo toca `Gastapp/Gastapp.csproj`) en el merge `580f0cd`, y se subió:
`master` y `origin/master` quedaron iguales. Al mensaje de ese merge se le colaron las líneas
de ayuda de git (`# Please enter a commit message...`); es solo cosmético y no se reescribe
porque `master` es compartida. MAUI publicada va en la versión **131**; el 200 de la app
nativa la sigue superando.

La Fase 2 quedó en el commit `c0307fa` («Fase 2 terminada», 2 oct 2026), ya en `origin/master`.

Las Fases 3 y 4 quedaron en `f4f1fcf` («fase 4», 123 archivos; incluye los cambios del API
de los hallazgos 23 y 24, `CodeRequestLimiter`), la Fase 5 y el chat 9 en `277be21` («Fase 5»;
se subió también `tools/sonidos/opciones/`). Después, en una sesión aparte, el usuario hizo
`a9d9247` («TDC Fix»: el ajuste de saldo deja de contar como gasto, `NOT_ADJUSTMENT` en
`Daos.kt`; incluyó también lo del chat 10 hasta ese momento: R8, `SystemZoneClock` y
`CasosDeBordeTest`) y `f8f401d` («Build grade changes»: versión 2.0.1 / 201 y R8 apagado).
Pendiente de confirmar con el usuario: si el API con el limitador ya está desplegado en Render.

**Sin commit (chat 10):** `app/.../ui/main/MainScreen.kt` (nombre para TalkBack en el FAB),
`Gastapp.Android/preparar-release.ps1` (nuevo), este plan, `CLAUDE.md`.

### 7.6 Historial de chats

**Chat 1 (2 oct 2026): análisis, demo, plan y Fase 1.**
1. Análisis de lo que implica pasar la app MAUI a Kotlin + Compose (≈20 mil líneas: ViewModels,
   servicios, 16 páginas, 5 hojas y 4 popups). El usuario aceptó perder los datos locales.
2. Recomendación de interfaz: Material 3 con tema de marca `#126E63`. Aprobada.
3. Demo en `Gastapp.ComposeDemo/`, primero en el emulador y luego en el teléfono. Rondas de
   comentarios del usuario: rehacer «Explorar periodo» (calendario propio con intensidad de
   gasto, atajos, resumen del rango y gráfica), tira con todos los días del periodo, editar un
   gasto, atrás predictivo como en Gmail y más animaciones. Después se corrigió que la tira
   encimaba los días al cambiar de periodo: ahora es una tira nueva por periodo con
   `AnimatedContent`, y las listas ya no desvanecen lo que sale encima de lo que entra
   (`animateItem(fadeOutSpec = null)`).
4. Este plan, con las decisiones del usuario: renombrar a `Gastapp.Android`, Android 12
   mínimo y conservar la página de detalle con botón Editar.
5. Fase 1 completa (7.2) y explicación al usuario de cómo se generan los casos de paridad.

**Chat 2 (2 oct 2026): Fase 0.**
1. Se renombró la carpeta en cuanto el usuario cerró Android Studio, se subieron las versiones
   (el reloj siguió compilando y `:domain` siguió en verde, ya con el JDK 25), se creó `:app`
   en lugar de `mobile/`, se creó `:core` y la firma pasó a la raíz.
2. Base de la app: tema y componentes del demo, Hilt, splash, ícono de MAUI con capa
   monocromática, cuatro pestañas provisionales, barra inferior y FAB.
3. Spike 0.7: Navigation Compose reproduce el atrás predictivo del demo con
   `ProvideBackGesture` + `PredictiveBackLayer`. Decisión: Navigation para todo.
4. A media sesión el usuario pidió probar **todo en el emulador** y desconectarse de su
   teléfono. Se verificó ahí: tema claro y oscuro, gesto de atrás desde los dos bordes
   (confirmar y cancelar) y la actualización encima de MAUI (130 → 200, misma firma).
5. Hallazgo para la Fase 2: las `Preferences` de MAUI están en
   `shared_prefs/com.binc.gastapp_preferences.xml` (se corrigió 2.5).

**Chat 3 (2 oct 2026): Fase 2.** Room, repositorios, DataStore y la tarjeta «Datos locales»
(detalle en el «Resultado» de la Fase 2). El usuario decidió no hacer nada de compatibilidad
con MAUI (se descartó 2.5). Quedó en el commit `c0307fa`.

**Chat 4 (2 oct 2026): Fase 3.**
1. Contratos leídos del API (`UserController`, `SpendingsController`, `DeviceController`) y de
   MAUI (`App.xaml.cs`, `UserService`, los ViewModels de sesión). Hallazgos 13 a 17 de 7.3.
2. `:core` con el cliente y los DTO; en `:app` la sesión, el `SyncEngine` con WorkManager y
   la UI provisional para probar. Fixtures generados con los tipos C# reales
   (`tools/Gastapp.Contratos`), igual que la paridad de la Fase 1.
3. 19 pruebas en `:core` y 52 en `:app` en verde, con sabotajes.
4. Prueba en vivo en el emulador con la cuenta del usuario (él inició sesión): login, modo
   avión con el laboratorio, subida automática, vuelta del servidor y un gasto del reloj que
   llegó con el pull. Fase 3 terminada. Hallazgo 18 y las dos «Sin categoria» de su cuenta.

**Chat 5 (3 oct 2026): Fase 4, de la 4.1 a la 4.5.** Pantallas de arranque y sesión,
Principal y Resumen, Ahorros, Tarjetas y Suscripciones, probadas en el emulador con la cuenta
del usuario. Decisiones del usuario: el total del día sigue el criterio del periodo y las dos
«Sin categoria» solo se agrupan al mostrar. Hallazgo 19. Se acabó el uso antes de la 4.6.

**Chat 6 (3 oct 2026): Fase 4.6 y revisión de calidad.**
1. Perfil y Ajustes portados de MAUI con el diseño del demo (detalle en el «Resultado» de la
   4.6). Hallazgos 20 a 22.
2. Otra sesión de Claude («Fase 4») empezó la 4.6 en la misma carpeta al mismo tiempo; el
   usuario eligió seguir en esta, que unificó el trabajo y borró los duplicados de aquella.
3. 14 pruebas nuevas (Perfil y Ajustes), con sabotaje; 127 de `:app` en verde.
4. Revisión en el emulador: tema oscuro, Material You y fuente al 200 % en todas las
   pantallas; se corrigieron los cortes que salieron (ver el «Resultado» de la 4.6).
5. El usuario revisó la 4.1 con la sesión cerrada y reportó el «000000» descentrado y el
   reenvío sin límite en recuperar contraseña. Se corrigió en la app y en el API, y de paso el
   límite de intentos del código de recuperación (hallazgos 23 y 24). 129 pruebas de `:app`.

**Chat 7 (3 oct 2026): Fase 5.** Notificaciones (recordatorios y avisos de tarjeta con
WorkManager), respaldos JSON con suscripciones, actualización automática, el servicio del reloj
en el teléfono con el contrato compartido en `:core/wear`, y el arranque (`StartupCoordinator`,
`DayClock`, mantenimiento diario). 25 pruebas nuevas (154 en `:app`). Visto en el emulador lo que
no escribe en la cuenta; lo demás queda para revisarlo con el usuario (7.2). Hallazgo 25.

**Chat 8 (3 oct 2026): ajuste de navegación.** A pedido del usuario, los accesos a Mis tarjetas
y Suscripciones pasaron de la lista de Resumen a iconos en su barra superior (con puntito cuando
una tarjeta está por vencer) y a tarjetas en Perfil (detalle en 4.6, «Cambio posterior»). Se
instaló la versión release en el Pixel 9 Pro XL del usuario.

**Chat 9 (3 oct 2026): Mis tarjetas.** A pedido del usuario, el carrusel se ordena por fecha
límite y un pago que no cubre el corte pregunta «¿Es el pago de este mes?» (anexo C y
`CLAUDE.md`). Después, en otra sesión, el usuario sacó los ajustes de saldo de los gastos
(«TDC Fix»).

**Chat 10 (3-4 oct 2026): Fases 6 y 7.** Casos de borde con el error del `Clock` y la zona
horaria, auditoría de accesibilidad (FAB sin nombre), R8 probado en el emulador y
`preparar-release.ps1`. El usuario, en paralelo, publicó `v2.0.0-alpha1`, subió a 2.0.1 y dejó
R8 apagado hasta probar el login. Decidió no hacer la regresión 6.1 contra MAUI y dejar el
hallazgo 25 como está. Queda con él probar el login con el minificado (detalle en 7.2).

**Chat 11 (4 oct 2026): notificaciones que llegaban tarde.** El usuario reportó que a veces no
llegaban hasta abrir la app. Los avisos pasaron de WorkManager a `AlarmManager` (detalle en el
«Resultado» de la Fase 5, «Cambio posterior»). Después, para asegurar los avisos de
vencimiento: al sonar se recalculan con los datos de ese momento (si ya se pagó no se avisa y
el saldo sale al día), la notificación se identifica por tarjeta + tipo (antes el id dependía
del lugar en el plan y el aviso de una tarjeta podía reemplazar en la bandeja al de otra) y al
sonar se reprograma todo. Se vio en el log que instalar una actualización fuerza la detención
y borra las alarmas; `RescheduleReceiver` (MY_PACKAGE_REPLACED) las repone sin abrir la app.
Prueba de punta a punta en el emulador (con permiso del usuario): hora
adelantada con `adb shell cmd alarm set-time` (sin root; `time_detector` no lo deja), proceso
matado con `am kill`, Doze profundo forzado y cubeta «rare»: los dos avisos de fecha límite
llegaron a las 09:00:00.8, con Android arrancando la app solo para la alarma. Esa prueba
destapó que, si el reloj se atrasa, el recordatorio periódico podía quedar días adelante; ahora
`nextReminderAt` nunca lo deja a más de N horas. 166 pruebas de `:app` en verde; sin commit.

**Chat 12 (4 oct 2026): inglés y moneda según el teléfono (pedido del usuario).** Todos los
textos de `:app` y `:wear` pasaron a recursos: inglés en `values/` (base) y español en
`values-es/`; un teléfono en otro idioma ve inglés. La moneda y el formato de montos y fechas
salen de la región del teléfono (`ui/format/AppLocale`), sin convertir montos. Ajustes trae
una fila «Idioma y moneda». Detalle y reglas en CLAUDE.md («Idiomas y moneda»). Los textos de
`:domain` no se tocaron (paridad); se agregaron `MovementTexts`, `QuickAmount.isCycle` y
`UpcomingCharge.paymentMethod/cardName`. Cambios a propósito en español: plurales correctos
(«1 gasto», «te queda 1 día») y «Sin categoría» con acento al mostrarse. Pruebas de `:domain`,
`:core` y `:app` en verde (más `LocaleFormatTest`). Falta: verlo en el emulador en inglés y en
otra región, y en el reloj. Lint sigue con el error previo `RestrictedApi` de
`PredictiveBack.kt` (no es de este cambio). Además: los chips de categoría de «Nuevo gasto»
se quedaban invisibles hasta tocar la fila (entraban con fundido al llegar las categorías); la
fila ahora se arma ya con la lista. El release del reloj fallaba en lint por `fragment` 1.0.0
(lo trae play-services); `:wear` fija `fragment` 1.8.9. Publicado como **v2.0.3-alpha1**
(teléfono 203 / 2.0.3, reloj 4 / 1.2.0-alpha1, con APK del reloj).


**Chat 13 (7 oct 2026): portugués (pedido del usuario).** Nuevo idioma en `:app` y `:wear`:
portugués de Brasil en `values-pt/` (los 17 `strings_*.xml` del teléfono y el del reloj, con
plurales `one`/`many`/`other`; la región del teléfono sigue decidiendo la moneda, así que un
teléfono `pt-PT` ve los textos en portugués con euros). `locale_language` = `pt`; fechas con
patrones propios en `DateFormat.kt` («Sexta-feira, 2 de outubro», «16 a 30 de setembro», hora
en 24 h); `LegalDocumentsPt.kt` con el aviso y los términos traducidos (revisión legal
pendiente; la versión en español prevalece); el dictado del reloj entiende «gastei 20 reais em
almoço». `LocaleFormatTest` suma casos de portugués (formatos, textos, errores del API y
avisos legales). **Sin verificar con Gradle:** el entorno de este chat no tenía Android SDK;
solo se comprobó que las claves, los argumentos y los plurales de `values-pt/` coinciden con
`values/` y `values-es/`, y los patrones de fecha en la JVM. Falta: correr
`./gradlew :app:testDebugUnitTest :wear:assembleDebug` y verlo en el emulador y en el reloj.
