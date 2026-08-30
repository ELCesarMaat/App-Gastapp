# 💸 Gastapp

**Gastapp** es una app de finanzas personales para Android, hecha en **.NET MAUI**, pensada para el contexto mexicano: además de llevar tus gastos diarios, entiende **tarjetas de crédito** con sus fechas de corte, límites y **compras a Meses Sin Intereses**.

Funciona **offline-first**: todo se guarda al instante en el dispositivo (SQLite) y se sincroniza con la nube cuando hay conexión.

---

## 📱 Capturas

| Resumen del día | Mis tarjetas | Detalle de tarjeta |
|---|---|---|
| ![Resumen](screenshots/resumen.png) | ![Tarjetas](screenshots/tarjetas.png) | ![Detalle](screenshots/tarjeta-detalle.png) |

| Nuevo gasto | Compra a meses | Confirmar correo |
|---|---|---|
| ![Nuevo gasto](screenshots/nuevo-gasto.png) | ![Compra a meses](screenshots/compra-meses.png) | ![Confirmar correo](screenshots/confirmar-correo.png) |

---

## 🚀 Características

### Gastos
- Registro rápido de gastos con categorías personalizables
- Método de pago: efectivo, débito, transferencia o crédito
- Resumen diario y navegación por periodos según tu frecuencia de pago
- Detalle por gasto y por categoría

### Tarjetas de crédito
- Wallet con límite de crédito, día de corte y día límite de pago
- Cálculo automático de deuda, crédito disponible y % de uso
- **Compras a Meses Sin Intereses**: varias por tarjeta, con seguimiento de mensualidades pagadas y pendientes
- Alta de tarjetas **que ya vienes usando**: capturas tu saldo actual y tus MSI en curso, y la app deduce sola cuánto es de contado
- Registro de pagos y ajuste de saldo
- Recordatorios de fechas de corte y de pago

### Cuenta y seguridad
- Registro con **confirmación de correo** por código de 6 dígitos
- El avance del registro se guarda: si cierras la app, retomas donde ibas
- Recuperación de contraseña por código temporal
- Autenticación con **JWT**

### Otros
- Recordatorios locales configurables
- Exportar/importar la base de datos local (`.db` o `.json`) — herramienta de desarrollo
- Iconografía con Font Awesome

---

## 🏗️ Arquitectura

```
Gastapp/            App móvil .NET MAUI (MVVM)
Gastapp.Models/     Modelos y DTOs compartidos entre app y API
Gastapp-API/        API REST en ASP.NET Core 8
Gastapp-API_node/   Prototipo alterno del backend en Node/Express (no activo)
```

### Sincronización offline-first

Cada entidad local lleva banderas `IsSynced` / `IsDeleted`. La app:

1. Escribe siempre primero en **SQLite local** → la UI nunca espera a la red
2. Al arrancar, refresca el token y empuja lo pendiente vía `SyncAllData`
3. Al iniciar sesión, descarga el estado completo desde el servidor

Si no hay conexión, la app sigue funcionando con normalidad.

---

## 🛠️ Stack

**App**
- [.NET MAUI](https://learn.microsoft.com/en-us/dotnet/maui/) (net8.0-android)
- CommunityToolkit.Mvvm — MVVM con generadores de código
- Entity Framework Core + SQLite
- Refit — cliente HTTP tipado
- FluentValidation, Syncfusion, Plugin.LocalNotification

**API**
- ASP.NET Core 8 + Entity Framework Core
- PostgreSQL (Npgsql)
- JWT para autenticación
- Resend (HTTPS) o SMTP para correo transaccional

**Infraestructura**
- [Render](https://render.com) — hosting del API (Docker)
- [Neon](https://neon.com) — PostgreSQL serverless

---

## 📦 Instalación

> Requiere **.NET 8 SDK** y el workload de MAUI (`dotnet workload install maui`)

```bash
git clone https://github.com/ELCesarMaat/App-Gastapp.git
cd App-Gastapp
```

### App

```bash
dotnet build Gastapp/Gastapp.csproj -f net8.0-android
```

Para desplegar en un emulador o dispositivo:

```bash
dotnet build Gastapp/Gastapp.csproj -f net8.0-android -c Debug -p:RuntimeIdentifier=android-x64 -t:Install
```

> ⚠️ Usa el target `-t:Install`, no `adb install`. Las compilaciones Debug de MAUI usan *Fast Deployment*: el APK **no** contiene los ensamblados, se envían por separado. Instalar el APK a mano deja el código viejo corriendo o hace que la app truene al arrancar.

La URL del API se configura en [`MauiProgram.cs`](Gastapp/MauiProgram.cs).

### API

```bash
cd Gastapp-API
dotnet run
```

Crea un archivo `.env` en `Gastapp-API/`:

```env
DATABASE_URL=postgresql://usuario:password@host/basededatos?sslmode=require

JWT_SECRET=una-clave-de-minimo-32-caracteres
JWT_ISSUER=GastappAPI
JWT_AUDIENCE=GastappClient
JWT_EXPIRY_IN_DAYS=7

# Correo: usa Resend (HTTPS) o SMTP. Si defines RESEND_API_KEY, gana Resend.
RESEND_API_KEY=re_tu_llave
EMAIL_SENDER_EMAIL=no-reply@tudominio.com
EMAIL_SENDER_NAME=Gastapp

# SMTP (alternativa para desarrollo local)
EMAIL_SMTP_HOST=smtp.tuproveedor.com
EMAIL_SMTP_PORT=587
EMAIL_SMTP_USER=usuario
EMAIL_SMTP_PASSWORD=password
EMAIL_ENABLE_SSL=true
```

---

## 🌐 API

Base: `/api`

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/User/ApiAlive` | Health check |
| `POST` | `/User/EmailVerification/request` | Envía código para confirmar correo |
| `POST` | `/User/EmailVerification/verify` | Valida el código |
| `POST` | `/User/CreateUser` | Crea la cuenta (requiere correo confirmado) |
| `POST` | `/User/Login` | Inicia sesión y devuelve todos los datos |
| `POST` | `/User/RefreshToken` | Renueva el JWT |
| `POST` | `/User/PasswordReset/request` · `/verify` · `/confirm` | Recuperación de contraseña |
| `POST` | `/Spendings/SyncAllData` | Sincroniza usuario, gastos, categorías y tarjetas |
| `POST` | `/Spendings/CreateNewSpending` · `/UpdateSpending` · `/DeleteSpending` | Gastos |
| `POST` | `/Spendings/CreateCreditCard` · `/DeleteCreditCard` | Tarjetas |

---

## 📧 Nota sobre el envío de correo

Render **bloquea los puertos SMTP (25, 465 y 587) en sus planes gratuitos**, así que ahí el correo transaccional sale por la **API HTTPS de Resend**. El código elige el proveedor solo: si existe `RESEND_API_KEY` usa Resend, si no usa SMTP. Así el desarrollo local sigue funcionando con cualquier servidor SMTP.

---

## 🗺️ Pendientes

- [ ] Presupuestos por categoría con alertas
- [ ] Gastos recurrentes y suscripciones
- [ ] Reportes y gráficas
- [ ] Registro de ingresos reales (no solo sueldo estimado)
- [ ] Modo oscuro
- [ ] Búsqueda y filtros en la lista de gastos
- [ ] Exportar gastos a CSV
- [ ] Historial de cortes por tarjeta

---

## 📄 Licencia

Proyecto personal de [César Maat](https://cesarmaat.com).
