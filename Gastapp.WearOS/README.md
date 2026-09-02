# Gastapp Wear OS

App de reloj para registrar gastos por voz. Habla directo con la API de Gastapp
(`https://app-gastapp.onrender.com/api/`) y funciona sin la app del teléfono una vez
vinculada.

## Cómo se vincula

1. Abre Gastapp en el reloj: muestra un código de 6 caracteres (`K7M-2QX`).
2. En el teléfono: **Ajustes → Dispositivos → Vincular reloj** y teclea ese código.
3. El reloj queda vinculado. El código sirve una sola vez y expira a los 10 minutos.

Es un *device authorization grant* (RFC 8628). El reloj nunca pide la contraseña.

## Estructura

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

## Cuatro cosas que no se pueden cambiar sin romper algo

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

## Desarrollo contra una API local

En `wear/build.gradle.kts`, dentro de `buildTypes.debug`, descomenta:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:5199/api/\"")
```

`10.0.2.2` es el host de la máquina vista desde el emulador de Android.

Para HTTP sin TLS en debug hace falta además una `network_security_config` que permita
tráfico en claro hacia ese host.

## Publicación

No hay Play Store: la distribución es por GitHub Releases.

El APK **debe** firmarse con `gastappkeystore`. Verifica siempre de forma positiva
antes de publicar:

```bash
apksigner verify --print-certs <apk>
```

Debe imprimir `CN="Cesar Maat, ..."`. Si dice `CN=Android Debug`, no publiques: Android
no permite actualizar entre firmas distintas y obligaría a desinstalar.

## Estado

Implementado y sin compilar todavía. La API y la pantalla de vinculación del teléfono
ya están listas y probadas.
