# CLAUDE.md — Mapa del repo Gastapp

Guia para ubicar rapido que archivo tocar. Todo el codigo y los comentarios estan en
espanol (sin acentos en comentarios de codigo, por convencion del repo).

## Proyectos de la solucion

| Proyecto | Que es | Notas |
|---|---|---|
| `Gastapp/` | App movil .NET MAUI (MVVM), `net8.0-android` unicamente | Es donde vive casi toda la logica de negocio del cliente |
| `Gastapp.Models/` | Modelos y DTOs **compartidos** entre app y API | Cambiar algo aqui afecta a los dos lados |
| `Gastapp-API/` | API REST ASP.NET Core 8 + EF Core + PostgreSQL (Neon), desplegada en Render via `Dockerfile` | |
| `Gastapp.WearOS/` | App Wear OS en Kotlin/Compose (Gradle, fuera de la `.sln`) | `wear/` es el reloj, `mobile/` es el companion |

`Gastapp.sln` solo contiene los tres proyectos .NET. WearOS se compila con Gradle aparte.

### Migracion a Android nativo (en curso)

La app del telefono se va a reescribir en Kotlin + Jetpack Compose. El plan por fases
(decisiones, tareas, criterios de salida y reglas que no se pueden romper) esta en
`docs/migracion-android-nativo.md`. Se trabaja **un chat por fase**: para retomar, leer
primero su seccion 7 (bitacora: estado de cada fase, hallazgos, entorno y siguiente paso) y
actualizarla al cerrar el chat. La referencia visual aprobada es el demo
`Gastapp.ComposeDemo/` (Compose + Material 3, con datos de muestra; no es la app real).

- `:domain` (dentro del proyecto Gradle del reloj, que se renombra a `Gastapp.Android/`):
  la logica de negocio portada a Kotlin puro. Sus pruebas comparan contra MAUI con los
  fixtures de `domain/src/test/resources/paridad/`, que genera `tools/Gastapp.Paridad`
  (copia fiel de los calculos de MAUI; no se "arregla" nada ahi).
- Si cambia un calculo en MAUI antes de retirarla, hay que copiarlo tambien a
  `tools/Gastapp.Paridad/Referencia/` y regenerar los fixtures.
- Desde la terminal, Gradle necesita el JDK 21 (`~/.jdks/ms-21.0.12.1`): Kotlin 2.0.21 falla
  con el JDK 25 predeterminado. Ver la Fase 1 del plan para los comandos.

## Comandos

```bash
dotnet build Gastapp/Gastapp.csproj -f net8.0-android
```

Desplegar al emulador/dispositivo — **siempre con `-t:Install`, nunca `adb install`**
(Fast Deployment de Debug: el APK no lleva los ensamblados y quedaria corriendo codigo viejo):

```bash
dotnet build Gastapp/Gastapp.csproj -f net8.0-android -c Debug -p:RuntimeIdentifier=android-x64 -t:Install
```

API local: `cd Gastapp-API && dotnet run` (necesita `.env`, ver README).

## Donde esta cada cosa en la app MAUI

- **Registro de dependencias, URL del API, rutas de Shell** → `Gastapp/MauiProgram.cs`
  (la base del API esta hardcodeada ahi, ~linea 77: `https://app-gastapp.onrender.com/api`;
  arriba estan comentadas la de emulador `10.0.2.2:5118` y la de devtunnel).
- **Base local SQLite** → `Gastapp/Data/GastappDbContext.cs`
  (`Users`, `IncomeTypes`, `Categories`, `Spending`, `CreditCards`, `Subscriptions`).
- **Cliente HTTP (Refit)** → `Gastapp/Services/ApiService/IApiService.cs` — es solo la interfaz,
  Refit genera la implementacion. Agregar endpoint = agregar metodo aqui + accion en el controller del API.
- **Servicios** → `Gastapp/Services/<Nombre>Service/` (cada uno con su `I...` interfaz):
  `SpendingService`, `CreditCardService`, `SubscriptionService`, `UserService`,
  `Notifications/ReminderNotificationService`, `BackupService`, `AppUpdateService`,
  `Navigation/NavigationService`.
- **ViewModels** (CommunityToolkit.Mvvm, `[RelayCommand]` / `[ObservableProperty]`) → `Gastapp/ViewModels/`.
- **Vistas** → `Gastapp/Pages/` (`Menu/`, `Register/`), `Gastapp/BottomSheets/`, `Gastapp/Popups/`,
  `Gastapp/Controls/`.
- **Helpers** → `Gastapp/Utils/` (`AlertHelper`, `DateTimeUtils`, `PagesUtils`).

## Donde esta cada cosa en el API

- Controllers: `Gastapp-API/Controllers/` — `UserController` (auth, verificacion de correo,
  reset de password), `SpendingsController` (gastos, categorias, tarjetas, suscripciones,
  `SyncAllData`),
  `DeviceController` (vinculacion de dispositivos / WearOS), `AppController` (version).
- `Gastapp-API/Data/GastappDbContext.cs` — agrega `EmailVerifications`, `DeviceAuthorizations`, `Devices`.
- `Gastapp-API/Services/` — correo (Resend si hay `RESEND_API_KEY`, si no SMTP), verificacion,
  reset de password, purga de borrados, update de app.
- Migraciones EF en `Gastapp-API/Migrations/`.

## Sincronizacion offline-first

Cada entidad local trae `IsSynced` / `IsDeleted` (+ `DeletedAt` en tarjetas). Patron:
se escribe **primero en SQLite**, la UI no espera a la red, y despues se dispara el push
(`_ = SyncNewCreditCard(card)` fire-and-forget en `CreditCardService`). Al arrancar se
refresca el token y se empuja lo pendiente con `SyncAllData`; al iniciar sesion se baja
todo el estado del servidor. Purga local en `Gastapp/Data/PurgeDeletedLocal.cs`.

## Tarjetas de credito — el area mas delicada

Archivos clave:

- `Gastapp/Services/CreditCardService/CreditCardService.cs` — toda la logica de ciclo y saldos.
- `Gastapp.Models/Models/CreditCard.cs` — solo guarda `CutOffDay` y `PaymentDay` (numeros de dia
  del mes, 1-31). **No existe ninguna fecha de corte ni de pago persistida, ni historial de cortes.**
- `Gastapp.Models/Models/CreditCardSummary.cs` — el objeto que consume la UI (`NextCutOffDate`,
  `NextPaymentDueDate`, `DaysUntilPayment`, `PaymentStatusText`, `PaymentStatusColor`...).
- `Gastapp.Models/Models/CreditCardPendingInfo.cs` — version reducida para la pantalla de ahorros.
- UI: `Gastapp/Pages/Menu/CreditCardsPage.xaml` (lineas ~390 y ~457 muestran la fecha limite),
  `Gastapp/ViewModels/CreditCardsViewModel.cs`, `Gastapp/ViewModels/SavesViewModel.cs`.
- Notificaciones: `Gastapp/Services/Notifications/ReminderNotificationService.cs:160-198`
  (aviso de corte -2 dias, aviso de pago -3 dias, y recordatorio el dia del pago si `TotalDebt > 0`).

Como se calcula hoy:

- `CreditCardService.cs:109` `CalculateCycleDates(cutOffDay, paymentDay, referenceDate)` — corte y
  pago se calculan **por separado**, cada uno como "la proxima vez que ocurre ese dia del mes"
  (`NextOccurrenceOfDay`, linea 125). Esto es intencional: calcular el pago a partir del proximo
  corte saltaba un ciclo completo cuando ya paso el corte pero aun no llega el dia de pago.
- `CreditCardService.cs:86` `GetPendingAmountForCardAsync` — deuda = suma de gastos con
  `IsCreditCard = true` menos suma de pagos con `IsCreditCard = false`, ambos filtrados por
  `CreditCardId`. **Los pagos a la tarjeta se guardan como `Spending` con `IsCreditCard = false`**
  (ver `CreditCardsViewModel.cs:747 PayCard` y `SavesViewModel.cs:370 PayCreditCard`).
- `CreditCardService.cs:149` `GetCurrentCycleSpendingsAsync` — el "ciclo actual" se define como
  el mes que termina en el proximo corte (`nextCutOff.AddMonths(-1)` .. `nextCutOff + 1 dia`).
- `CreditCardService.cs:261` `AdjustCardBalanceAsync` — ajusta saldo creando un gasto o un abono
  sinteticos, no edita nada.

### Fecha limite de pago y ciclos ya pagados

`CalculateCycleDates` sigue siendo aritmetica pura de calendario. Encima esta
`CalculateCycleDatesAsync(card, referenceDate)` (`CreditCardService.cs`), que es la que
consumen `GetCardSummaryAsync` y `SavesViewModel`: ajusta la fecha limite cuando el corte
vigente ya quedo cubierto.

Criterio: la fecha limite liquida el ultimo corte ocurrido **antes** de esa fecha de pago
(`PreviousOccurrenceOfDay`). Si ese corte todavia no llega, no se toca nada (el estado de
cuenta ni se ha generado). Si ya paso, `IsStatementSettledAsync` compara el acumulado de
compras con `Date <= corte` contra **todos** los abonos registrados; si la resta es <= 0.01
la fecha limite salta al siguiente mes.

`ReminderNotificationService` lee `NextPaymentDueDate` del summary, asi que hereda el ajuste
y deja de recordar pagos ya hechos.

## Suscripciones y membresias

Se armo calcada de tarjetas, de punta a punta (modelo compartido -> servicio con la
logica -> summary masticado -> ViewModel -> pagina + hoja de formulario, mas DTO,
endpoints y sincronizacion offline-first).

Archivos clave:

- `Gastapp.Models/Models/Subscription.cs` — la entidad, mas las constantes
  `SubscriptionBillingCycles` y `SubscriptionPaymentMethods`.
- `Gastapp.Models/Models/SubscriptionSummary.cs` — lo que consume la UI.
- `Gastapp.Models/Models/UpcomingCharge.cs` — cobro futuro, para el calendario.
- `Gastapp.Models/Models/SubscriptionDto.cs` — lo que viaja entre app y API.
- `Gastapp/Services/SubscriptionService/SubscriptionService.cs` — ciclos, totales y push.
- `Gastapp/ViewModels/SubscriptionsViewModel.cs`, `Gastapp/Pages/Menu/SubscriptionsPage.xaml`,
  `Gastapp/BottomSheets/SubscriptionFormBottomSheet.xaml`.
- API: `CreateSubscription` y `DeleteSubscription` en `SpendingsController`, mas el bloque
  de suscripciones dentro de `SyncAllData` y la bajada en el `Login` de `UserController`.
- Entrada: banner en `Gastapp/Pages/Menu/SummaryPage.xaml` (~linea 115) ->
  `SummaryViewModel.OpenSubscriptionsPage`.

### Fechas: dos tipos que NO se tratan igual

Es lo mas facil de romper de toda el area. `FirstChargeDate` y `TrialEndDate` son
**fechas de calendario** (columna `date` en Postgres, `datetime` en SQLite) y viajan
tal cual: **nunca** pasan por `DateTimeUtils.SpendingToApiUtc` / `SpendingFromApiToLocal`.
Una fecha de las 00:00 convertida a UTC cae en el dia anterior, y con ella se correrian
todos los cobros calculados desde el ancla. `LastChargeRegisteredAt` y `DeletedAt` si
son instantes y viajan en UTC como el resto de la app.

El API normaliza las de calendario con `NormalizeCalendarDate` en `SpendingsController`.

### Referencias colgantes

Una suscripcion apunta a una tarjeta y a una categoria. Si el id no existe del otro
lado (se creo sin conexion, o la tarjeta ya se purgo), guardar la referencia rompe la
llave foranea y **tumba todo el lote**. Los dos extremos anulan la referencia y
conservan la suscripcion: `ResolveReference` / `GetKnownReferencesAsync` en el API, y
los `knownCardIds` / `knownCategoryIds` del login en `UserService`.

Diferencia importante con tarjetas: aqui **no** se guarda un "dia del mes". Se guarda
`FirstChargeDate` como ancla y de ahi salen todos los cobros (`CalculateNextChargeDate`).
Un numero de dia no alcanza para una anualidad, que necesita tambien el mes. El
candidato siempre se calcula como `anchor.AddMonths(n * meses)` desde el ancla, nunca
encadenando AddMonths sobre el resultado anterior: si el ancla cae 31 y un mes lo
recorta a 30, encadenar perderia el dia 31 para siempre.

Una suscripcion no mueve dinero por si sola. Lo que mueve dinero es el `Spending` que
crea `RegisterCharge` en el ViewModel, con `IsCreditCard = true` cuando el cobro va a
una tarjeta (asi suma a la deuda de esa tarjeta como cualquier otra compra).

Las formas de pago son las **mismas cuatro** que ofrece `NewSpendingBottomSheet` y que
sabe leer `DetailViewModel` (`Cash`, `Debit`, `Transfer`, `CreditCard`), con las mismas
etiquetas. Si aqui se agrega un valor que alla no existe, el detalle del gasto le
muestra al usuario una forma de pago distinta a la que eligio.

Que cuenta en los totales lo decide `SubscriptionService.CountsTowardTotals`: una
pausada no cuenta, y **una en prueba gratis tampoco**, porque todavia no sale dinero.
El chip del encabezado separa "N activas · M en prueba · P pausadas" para que se
entienda por que el total no las incluye. Ese mismo criterio alimenta el porcentaje de
`ShareOfMonthlyRatio`, asi que los dos numeros no se pueden contradecir.

`LastChargeRegisteredAt` + `CalculatePreviousChargeDate` sirven para saber si el cobro
del periodo en curso ya se registro (`IsCurrentCycleCharged`). Al tocar "Registrar
Cobro" se **avisa pero no se bloquea**: hay casos legitimos, como un cargo doble del
proveedor.

`User` **no** expone `ICollection<Subscription>` a proposito: `User` vive en
`Gastapp.Models`, que tambien compila el API, y esa navegacion haria que el DbContext
de Postgres descubriera la entidad y mapeara una tabla que alla no existe. La relacion
se declara solo en el `GastappDbContext` de la app, con `.WithMany()`.

La tabla `Subscriptions` de Postgres se crea en `EnsureSchemaUpToDate` del
`GastappDbContext` del API, **no con una migracion EF**. Es la misma via por la que se
agregaron `DeletedAt`, `Devices` y `EmailVerifications`: la base de produccion no se
creo con migraciones y se completa de forma idempotente en cada arranque. Ojo con esto
si algun dia se corre `dotnet ef migrations add`: el snapshot no incluye estas tablas y
la migracion generada intentaria crearlas otra vez.

## Convenciones

- Comentarios y textos de UI en espanol; los comentarios del codigo van sin acentos.
- Colores de estado que se repiten por toda la app: `#C62828` (rojo/vencido), `#D97706` (ambar),
  `#126E63` (verde/ok).
- Los ViewModels usan CommunityToolkit.Mvvm; los comandos son `[RelayCommand]` y se enlazan como
  `NombreCommand` en XAML.
- Firmar el APK de release requiere `AndroidKeyStore=true` con el keystore real; sin eso MSBuild
  firma con el debug key en silencio.
