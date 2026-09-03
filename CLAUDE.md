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
  (`Users`, `IncomeTypes`, `Categories`, `Spending`, `CreditCards`).
- **Cliente HTTP (Refit)** → `Gastapp/Services/ApiService/IApiService.cs` — es solo la interfaz,
  Refit genera la implementacion. Agregar endpoint = agregar metodo aqui + accion en el controller del API.
- **Servicios** → `Gastapp/Services/<Nombre>Service/` (cada uno con su `I...` interfaz):
  `SpendingService`, `CreditCardService`, `UserService`, `Notifications/ReminderNotificationService`,
  `BackupService`, `AppUpdateService`, `Navigation/NavigationService`.
- **ViewModels** (CommunityToolkit.Mvvm, `[RelayCommand]` / `[ObservableProperty]`) → `Gastapp/ViewModels/`.
- **Vistas** → `Gastapp/Pages/` (`Menu/`, `Register/`), `Gastapp/BottomSheets/`, `Gastapp/Popups/`,
  `Gastapp/Controls/`.
- **Helpers** → `Gastapp/Utils/` (`AlertHelper`, `DateTimeUtils`, `PagesUtils`).

## Donde esta cada cosa en el API

- Controllers: `Gastapp-API/Controllers/` — `UserController` (auth, verificacion de correo,
  reset de password), `SpendingsController` (gastos, categorias, tarjetas, `SyncAllData`),
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

## Convenciones

- Comentarios y textos de UI en espanol; los comentarios del codigo van sin acentos.
- Colores de estado que se repiten por toda la app: `#C62828` (rojo/vencido), `#D97706` (ambar),
  `#126E63` (verde/ok).
- Los ViewModels usan CommunityToolkit.Mvvm; los comandos son `[RelayCommand]` y se enlazan como
  `NombreCommand` en XAML.
- Firmar el APK de release requiere `AndroidKeyStore=true` con el keystore real; sin eso MSBuild
  firma con el debug key en silencio.
