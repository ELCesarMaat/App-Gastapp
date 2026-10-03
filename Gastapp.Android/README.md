# Gastapp Android

Proyecto Gradle con las apps nativas de Gastapp (antes `Gastapp.WearOS/`). El plan de la
migración del teléfono está en `../docs/migracion-android-nativo.md`.

| Módulo | Qué es |
|---|---|
| `:app` | Teléfono, `com.binc.gastapp`. Sustituye a la app MAUI (`versionCode` desde 200) |
| `:wear` | Reloj Wear OS, mismo `applicationId` |
| `:core` | Librería Android compartida teléfono/reloj (red y Data Layer; vacía por ahora) |
| `:domain` | Lógica de negocio en Kotlin puro, con pruebas de paridad contra MAUI |

## Compilar

```bash
./gradlew :domain:test :app:assembleDebug :wear:assembleDebug
```

Funciona con el JDK predeterminado (25). Al terminar, `./gradlew --stop`: un demonio
vivo bloquea la carpeta y no deja renombrarla ni moverla.

## Firma

`:app` y `:wear` firman **siempre con la misma llave**, configurada una sola vez en el
`build.gradle.kts` raíz: la Wearable Data Layer solo entrega mensajes entre apps con el
mismo `applicationId` y la misma firma, y Android no deja actualizar entre firmas
distintas.

- **Debug:** el keystore de debug de .NET Android (`%LOCALAPPDATA%\Xamarin\Mono for
  Android\debug.keystore`), el mismo de la app MAUI de debug. Así el reloj habla con
  cualquiera de las dos y la app nativa se instala encima de la MAUI.
- **Release:** `gastappkeystore`, leído de `local.properties` (o de las variables
  `GASTAPP_*`). Si falta, el release sale **sin firmar** a propósito.

```properties
# Con diagonales normales: en .properties la \ es caracter de escape.
gastappKeystore=C:/Users/.../gastappkeystore/gastappkeystore.keystore
gastappKeyAlias=gastappkeystore
gastappKeystorePassword=...
gastappKeyPassword=...
```

## Reloj (`:wear`)

App de reloj para registrar gastos por voz. Habla directo con la API de Gastapp
(`https://app-gastapp.onrender.com/api/`) y funciona sin la app del teléfono una vez
vinculada.

### Cómo se vincula

1. Abre Gastapp en el reloj: muestra un código de 6 caracteres (`K7M-2QX`).
2. En el teléfono: **Ajustes → Dispositivos → Vincular reloj** y teclea ese código.
3. El reloj queda vinculado. El código sirve una sola vez y expira a los 10 minutos.

Es un *device authorization grant* (RFC 8628). El reloj nunca pide la contraseña.

### Estructura

```
data/
  local/     Room: gastos pendientes, categorías, caché del resumen
  remote/    Retrofit, interceptor de auth, renovación de token
  auth/      TokenStore (AndroidKeyStore), repositorio de emparejamiento
domain/      Parser del dictado, emparejador de categorías
ui/
  pairing/   Pantalla de código y sondeo
  quickadd/  Captura por voz y confirmación
  home/      Total del día
tile/        Tile con el total y acceso directo
sync/        SyncWorker
```

### Cuatro cosas que no se pueden cambiar sin romper algo

**Timeouts de 90 segundos.** La API vive en el plan gratuito de Render, que apaga el
servicio por inactividad. El arranque en frío tarda 50 segundos o más. Un timeout
nunca se trata como error permanente.

**El `spendingId` se genera en el reloj y nunca se regenera.** Es lo único que hace
idempotente el reenvío: sin eso, cada reintento duplicaría el gasto.

**El refresh del token está serializado con un `Mutex`.** El servidor rota el refresh
token en cada uso e invalida el anterior. Dos refresh concurrentes con el mismo token
harían que el segundo reciba 401 y el reloj se desvincule sin motivo.

**El tile jamás hace red.** Lee de Room; lo actualiza el `SyncWorker`. Una llamada
síncrona ahí congelaría el tile durante el arranque en frío.

### Desarrollo contra una API local

En `wear/build.gradle.kts`, dentro de `buildTypes.debug`, descomenta:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:5199/api/\"")
```

`10.0.2.2` es el host de la máquina vista desde el emulador de Android.

Para HTTP sin TLS en debug hace falta además una `network_security_config` que permita
tráfico en claro hacia ese host.

### Publicación

No hay Play Store: la distribución es por GitHub Releases.

El APK **debe** firmarse con `gastappkeystore`. Verifica siempre de forma positiva
antes de publicar:

```bash
apksigner verify --print-certs <apk>
```

Debe imprimir `CN="Cesar Maat, ..."`. Si dice `CN=Android Debug`, no publiques: Android
no permite actualizar entre firmas distintas y obligaría a desinstalar.

### Estado

En uso desde septiembre de 2026, emparejado con la app MAUI por la Data Layer.
