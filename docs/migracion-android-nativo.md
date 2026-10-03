# Migración de Gastapp a Android nativo (Kotlin + Jetpack Compose)

> Plan de trabajo por fases para reemplazar la app del teléfono (`Gastapp/`, .NET MAUI)
> por una app nativa. Redactado el 2 de octubre de 2026.
> Referencia visual: el demo en `Gastapp.ComposeDemo/` (aprobado).
>
> **Estado (2 oct 2026):** Fase 1 terminada (68,809 casos iguales a MAUI). Fase 0 empezada:
> falta renombrar la carpeta a `Gastapp.Android/` (bloqueado mientras Android Studio la
> tenga abierta) y el resto del andamiaje. **Para retomar en un chat nuevo, leer primero la
> [sección 7](#7-bitácora-y-cómo-retomar).** Se trabaja un chat por fase.

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
- Se instala en el Pixel 9 Pro XL por `adb` inalámbrico y tú navegas. Lo que toca el reloj
  se valida con el Pixel Watch 4 real.

---

## 2. Decisiones tomadas

| Tema | Decisión | Por qué |
|---|---|---|
| Ubicación | Renombrar `Gastapp.WearOS/` a `Gastapp.Android/` (**confirmado**), con módulos `:app` (teléfono), `:wear` (reloj), `:core` (red compartida) y `:domain` (lógica pura) | Un solo proyecto Gradle: misma firma de debug y release, versiones alineadas y código común entre teléfono y reloj |
| Paquete | `applicationId = "com.binc.gastapp"`, igual que MAUI y que el reloj | Actualizar encima de MAUI y que la Wearable Data Layer entregue mensajes |
| Versión | `versionCode` desde **200** y `versionName` **2.0.0** | Debe superar 130 (MAUI hoy); el 200 marca la era nativa |
| SDK | `minSdk 31` (Android 12, **decidido**), `targetSdk 36`, `compileSdk 36` | Deja fuera Android 8 a 11. A cambio: `java.time` nativo, color dinámico (Material You) y Splash Screen en todos los teléfonos |
| UI | Compose + Material 3 con tema de marca desde `#126E63`. Material You como interruptor opcional | Lo aprobado en el demo |
| Colores de estado | `#C62828`, `#D97706` y `#126E63` fuera del esquema de Material, con variante oscura | Significan algo; no deben cambiar con el fondo de pantalla |
| Arquitectura | MVVM: `ViewModel` + `StateFlow<UiState>`, repositorios y funciones de dominio puras en el módulo `:domain` (Kotlin/JVM, sin Android) | Patrón estándar de Android. Al ser un módulo JVM, la lógica no puede depender de Android aunque se quiera, y sus pruebas corren en segundos |
| Inyección | Hilt | El estándar de Android, integrado con ViewModel, WorkManager y Navigation |
| Navegación | Navigation Compose con rutas tipadas. Atrás predictivo como el demo; ver spike 0.7 | |
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
| 0. Andamiaje y fundaciones | 🟡 Empezada | 1 semana | — | — |
| 1. Dominio y paridad | ✅ Terminada (2 oct 2026) | 1 semana | 0.1 (renombrar) y el módulo `:domain` | 0, 2 |
| 2. Datos locales | ⬜ Pendiente | 3–4 días | 0 | 1 |
| 3. API, sesión y sincronización | ⬜ Pendiente | 1 semana | 2 | 4.1 |
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

**Criterios de salida.**
- [ ] La app vacía abre en el teléfono con tema claro y oscuro, cuatro pestañas y FAB.
- [ ] El reloj compila e instala igual que antes.
- [ ] El release está firmado con la huella del keystore real.
- [ ] La decisión del spike 0.7 está anotada aquí.

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
./gradlew :domain:test -Pkotlin.compiler.execution.strategy=in-process
```

Desde la terminal, Gradle tiene que correr con el **JDK 21** (`JAVA_HOME` en
`~/.jdks/ms-21.0.12.1`, el mismo que usa Android Studio): Kotlin 2.0.21 no reconoce el JDK 25
que está como predeterminado. El `in-process` evita que se reutilice un demonio de Kotlin que
haya arrancado con el JDK 25. Al subir Kotlin a 2.2 en la Fase 0 desaparece el problema.

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

2.5 **Limpieza del primer arranque.**
- Borrar `databases/gastapp.db` (con `-wal` y `-shm`) y el SharedPreferences `com.binc.gastapp.microsoft.maui.essentials.preferences`.
- Marcarlo en DataStore para no repetirlo.

2.6 **Pruebas de DAO** con Robolectric: inserción, borrado lógico, totales con centavos,
cascadas y `SET_NULL`.

**Criterios de salida.**
- [ ] Pruebas de DAO en verde.
- [ ] Las pantallas vacías de la Fase 0 muestran datos de prueba leídos de Room.
- [ ] Al instalar encima de la versión MAUI, desaparecen `gastapp.db` y las Preferences viejas.

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

**Criterios de salida.**
- [ ] Con una cuenta de prueba, el login baja todo (gastos, categorías, tarjetas, suscripciones e ingresos).
- [ ] En modo avión: crear, editar y borrar gastos, tarjetas y suscripciones. Al volver la red, todo llega al servidor (verificar en otra instalación o en Neon).
- [ ] Un gasto creado desde el reloj aparece en el teléfono después del pull.
- [ ] Un 401 manda a iniciar sesión; un 500 o un timeout no.

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
| `Pages/Menu/SummaryPage` + `SummaryViewModel` | Navegador de periodo según el tipo de ingreso (anterior y siguiente), tira con **todos los días del periodo**, total del día animado, «Ir a hoy», accesos a Tarjetas y Suscripciones, lista del día; **tocar un gasto abre su detalle** |
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

| MAUI | Nativo |
|---|---|
| `Pages/Menu/ProfilePage` + `ProfileViewModel` | Nombre, ingreso, frecuencia (semanal, quincenal o mensual) con sus días de pago, modo de ahorro (% o monto) y guardar |
| `Pages/Menu/SettingsPage` + `SettingsViewModel` | Recordatorios (sí/no y frecuencia), activar notificaciones del sistema, notificación de prueba, tema y Material You, estado de la nube, respaldos, dispositivos vinculados, cerrar sesión |
| `Popups/LinkDevicePopup` | Diálogo para teclear el código del reloj (`K7M-2QX`) |

**Requisitos de calidad de cada pantalla.**
- Tema claro y oscuro.
- Fuente del sistema al 200 % sin cortes.
- TalkBack lee las acciones principales.
- Atrás predictivo en las pantallas apiladas.
- Animaciones del demo: entrada escalonada, montos que cuentan, listas que animan altas y bajas sin encimarse.

**Criterios de salida.**
- [ ] Cada pantalla cumple su lista del anexo D.
- [ ] Pruebas de ViewModel en verde.
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
- **Precisión:** WorkManager con retraso calculado basta, porque no hace falta exactitud al minuto. Si algún día se exige, se usa `AlarmManager` con `setAndAllowWhileIdle`.

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

**Criterios de salida.**
- [ ] Llegan las notificaciones de prueba, recordatorio y tarjeta, también con la app cerrada y tras reiniciar el teléfono.
- [ ] Exportar y restaurar un respaldo deja los mismos datos, suscripciones incluidas.
- [ ] Con un `version.json` de prueba, la app ofrece, descarga e instala la actualización.
- [ ] Con el **Pixel Watch 4 real**: vincular, ping, gasto desde el reloj (aparece en el teléfono sin internet) y revocar.

---

### Fase 6. Pruebas integrales y pulido

**Tareas.**

6.1 **Regresión** con las listas del anexo D, completas, en el Pixel 9 Pro XL.

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
- [ ] Listas del anexo D completas.
- [ ] La prueba 6.6 pasa con el APK de release firmado.
- [ ] Una semana usando la app nativa como app principal sin fallas bloqueantes.

---

### Fase 7. Lanzamiento y corte

**Pasos.**

1. **Aviso a usuarios (1–2 días antes):** «Abran Gastapp con internet para sincronizar».
   La versión MAUI empuja lo pendiente al arrancar (`SyncAllData`).
2. **Release firmado** con el keystore real. Comprobar la huella SHA-256 contra la del APK
   MAUI publicado. `versionCode` 200, `versionName` 2.0.0.
3. **GitHub Release** con el APK del teléfono y `version.json` (`versionCode: 200`), más el
   APK del reloj si cambió. El API lo detecta solo (caché de 10 minutos).
4. **Actualización:** la app MAUI ofrece «Nueva versión disponible» e instala encima. Primer
   arranque nativo: limpieza (2.5), login y descarga de todo.
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
  - Abrir Tarjetas.
  - Abrir Suscripciones.
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
| ¿Reporte de errores en producción (Crashlytics o Sentry)? | Sí, ligero, en la Fase 6; con pocos usuarios, cada cierre que no se reporta pesa |
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
2. Revisar si la carpeta ya se renombró: `ls -d Gastapp.WearOS Gastapp.Android`. Si sigue
   `Gastapp.WearOS/`, pedir al usuario que cierre el proyecto en Android Studio y correr
   `git mv Gastapp.WearOS Gastapp.Android` (ver 7.4).
3. Comprobar que el dominio sigue en verde antes de tocar nada, desde la carpeta del proyecto
   Gradle:
   `JAVA_HOME="$HOME/.jdks/ms-21.0.12.1" ./gradlew :domain:test -Pkotlin.compiler.execution.strategy=in-process`.
   Al terminar, `./gradlew --stop` con el mismo `JAVA_HOME`, para no dejar procesos
   bloqueando la carpeta.
4. No hacer commits: los hace el usuario. Dejar los cambios listos y decirle qué incluir.

### 7.2 Estado por fase

**Fase 0 (empezada).** Hecho:
- Decisiones confirmadas (sección 2): renombrar a `Gastapp.Android`, `minSdk 31`, página de
  detalle de gasto con botón Editar.
- Módulo `:domain` registrado: `include(":domain")` en `settings.gradle.kts`, plugin
  `kotlin-jvm` en `gradle/libs.versions.toml` y `alias(libs.plugins.kotlin.jvm) apply false`
  en el `build.gradle.kts` raíz.

Falta, en este orden:
1. **Renombrar** la carpeta (bloqueado: `git mv` da *Permission denied* mientras Android Studio
   tiene abierto el proyecto). Después: actualizar la fila `Gastapp.WearOS/` de la tabla de
   `CLAUDE.md`, las rutas de este documento y de las memorias, y reabrir el proyecto en Studio
   desde la carpeta nueva.
2. **Subir versiones:** Kotlin 2.0.21 → 2.2.x (quita el problema con el JDK 25), Compose BOM a la
   del demo (2025.10.00, Material 3 1.4) y AGP; agregar Hilt, Navigation Compose, DataStore,
   `material-icons-extended`, Turbine, Robolectric y MockWebServer. **Comprobar que `:wear`
   sigue compilando** (wear-compose con el BOM nuevo) antes de seguir.
3. **Crear `:app`** sustituyendo la plantilla `mobile/` (hoy tiene `applicationId
   com.binc.gastapp.wo` y `wearApp(project(":wear"))`; las dos cosas se quitan).
   `applicationId` y `namespace` `com.binc.gastapp`, `minSdk 31`, `versionCode 200`,
   `versionName 2.0.0`, `API_BASE_URL` en `BuildConfig`. Crear también `:core` (librería
   Android, vacía por ahora).
4. **Firma compartida:** sacar el truco `mauiDebugKeystore` de `wear/build.gradle.kts` a una
   configuración en la raíz que usen `:app` y `:wear` (mientras exista MAUI, el keystore de
   debug de MAUI). Release con `gastappRelease` desde `local.properties`; verificar la huella
   (memoria `firmar-apk-con-keystore-real`: la buena empieza con `2B:DD:2F:06`).
5. **Base de la app:** copiar del demo `ui/theme` y los componentes; `GastappApplication` con
   Hilt; `MainActivity` (edge-to-edge, Splash API, ícono monocromático); 4 pestañas vacías,
   barra inferior y FAB; manifiesto con permisos, `enableOnBackInvokedCallback` y FileProvider.
6. **Spike de atrás predictivo** con Navigation Compose (medio día) y anotar aquí la decisión.

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

**Fases 2 a 7:** sin empezar. Su planeación sigue igual, salvo que la Fase 2 ya puede mapear
las entidades de Room contra los modelos de `:domain`.

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

### 7.4 Entorno y herramientas

- **JDK:** el `JAVA_HOME` del sistema es el JDK 25 y Kotlin 2.0.21 falla con él
  (`IllegalArgumentException: 25.0.2`). Desde la terminal usar `~/.jdks/ms-21.0.12.1` y
  `-Pkotlin.compiler.execution.strategy=in-process`. Android Studio usa `jbr-21` como Gradle
  JVM y no tiene el problema. El demo (`Gastapp.ComposeDemo`, Kotlin 2.2.10) sí compila con
  el JDK 25.
- **Android Studio** tenía abierto el proyecto del reloj y bloqueaba renombrar la carpeta.
- **Teléfono de pruebas:** Pixel 9 Pro XL con Android 17 y navegación por gestos, por `adb`
  inalámbrico (`adb connect 192.168.1.2:<puerto>`; el puerto cambia y lo da el usuario). El
  usuario prefiere probar él: instalar y avisar, sin relanzar la app para tomar capturas
  mientras la usa.
- **Emulador:** AVD `Pixel_10a` (teléfono) y `Wear_OS_XL_Round` (reloj). El usuario cerró el
  emulador cuando la app ya estaba en su teléfono.
- **Demo instalado en el teléfono** como `com.binc.gastapp.demo` (convive con la app real).
  Se compila con `./gradlew :app:assembleDebug` dentro de `Gastapp.ComposeDemo/` y acepta
  extras para abrir pantallas: `--es pantalla resumen|ahorros|perfil|ajustes|tarjetas|suscripciones|periodo`,
  `--es tema oscuro`, `--ez hoja true`, `--ez editar true`, `--ez dinamico true`.

### 7.5 Lo que quedó sin commit (2 oct 2026)

El usuario hace los commits. Al cierre del chat 1:
- Modificados: `CLAUDE.md`, `Gastapp.WearOS/build.gradle.kts`,
  `Gastapp.WearOS/settings.gradle.kts` y `Gastapp.WearOS/gradle/libs.versions.toml`.
- Nuevos: `docs/` (este plan), `tools/Gastapp.Paridad/`, `Gastapp.WearOS/domain/` y
  `Gastapp.ComposeDemo/` (demo de interfaz; su `.gitignore` excluye `build/` y
  `local.properties`; trae `capturas/`).

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
