using Android.App;
using Android.Content;
using Android.Gms.Wearable;
using Android.Util;
using CommunityToolkit.Mvvm.Messaging;
using Gastapp.Messages;
using Gastapp.Models.Models;
using Gastapp.Services.ApiService;
using Plugin.LocalNotification;
using Refit;
using System.Globalization;

namespace Gastapp.Platforms.Android.Wear;

/// <summary>
/// Recibe los mensajes que manda la app del reloj por la Wearable Data Layer.
///
/// Play Services arranca este servicio al llegar un mensaje, aunque la app este
/// cerrada: por eso no hace falta ni socket ni FCM para enterarse de lo del reloj.
///
/// La entrega SOLO ocurre entre apps con el mismo applicationId y la misma firma.
/// Si algun dia deja de llegar nada sin ningun error, empieza por comprobar eso.
/// </summary>
[Service(Exported = true)]
[IntentFilter(
    new[] { "com.google.android.gms.wearable.MESSAGE_RECEIVED" },
    DataScheme = "wear",
    DataHost = "*",
    DataPathPrefix = RutaBase)]
public class GastappWearListenerService : WearableListenerService
{
    private const string Tag = "GastappCanal";

    private const string RutaBase = "/gastapp";
    private const string RutaPing = RutaBase + "/ping";
    private const string RutaPong = RutaBase + "/pong";
    private const string RutaPair = RutaBase + "/pair";
    private const string RutaPairResult = RutaBase + "/pair/result";
    private const string RutaUnlinked = RutaBase + "/unlinked";
    private const string RutaExpense = RutaBase + "/expense";

    /// <summary>
    /// Id fijo: cada aviso del reloj reemplaza al anterior en vez de apilar
    /// notificaciones. Fuera del rango de los recordatorios (6100) y del de prueba.
    /// </summary>
    private const int NotificacionGastoId = 7200;

    private const string ResultadoOk = "ok";

    /// <summary>Justo por debajo de los 60 s que espera el reloj antes de rendirse.</summary>
    private static readonly TimeSpan EsperaMaxima = TimeSpan.FromSeconds(55);

    /// <summary>El reloj formatea en pesos mexicanos; el aviso debe verse igual.</summary>
    private static readonly CultureInfo CulturaMx = CultureInfo.GetCultureInfo("es-MX");

    public override void OnMessageReceived(IMessageEvent messageEvent)
    {
        base.OnMessageReceived(messageEvent);

        Log.Info(Tag, $"Mensaje del reloj: {messageEvent.Path}");

        switch (messageEvent.Path)
        {
            case RutaPing:
                // Responder al mismo nodo que pregunto. El reloj mide el viaje de ida
                // y vuelta con esto, asi confirma que el canal va en los dos sentidos.
                Responder(messageEvent.SourceNodeId, RutaPong, string.Empty);
                Log.Info(Tag, "Pong enviado al reloj.");
                break;

            case RutaPair:
                var userCode = System.Text.Encoding.UTF8.GetString(messageEvent.GetData());

                // Se espera aqui a proposito, no se lanza y se olvida. OnMessageReceived
                // corre en un hilo secundario y el servicio sigue vivo mientras dure la
                // llamada; si se soltara sin esperar, el proceso podria morir a mitad de
                // Device/Link y el reloj se quedaria colgado sin veredicto.
                VincularAsync(messageEvent.SourceNodeId, userCode)
                    .GetAwaiter()
                    .GetResult();
                break;

            case RutaUnlinked:
                // El reloj se desvinculo por su cuenta. Si Ajustes esta abierto,
                // refresca la lista sola; si no, se cargara al entrar y no hay nada
                // que hacer aqui.
                Log.Info(Tag, "El reloj avisa que se desvinculo.");
                WeakReferenceMessenger.Default.Send(new DevicesChangedMessage("reloj"));
                break;

            case RutaExpense:
                var cuerpo = System.Text.Encoding.UTF8.GetString(messageEvent.GetData());
                NotificarGastoAsync(cuerpo).GetAwaiter().GetResult();
                break;
        }
    }

    /// <summary>
    /// Muestra el aviso de un gasto capturado en el reloj.
    ///
    /// Es solo eso, un aviso: el gasto sube al API por su cuenta desde el reloj. Si
    /// esto falla no se pierde nada.
    /// </summary>
    private async Task NotificarGastoAsync(string cuerpo)
    {
        // Formato "monto|titulo", en ese orden y con un solo separador.
        var partes = cuerpo.Split('|', 2);
        if (partes.Length != 2 || !double.TryParse(
                partes[0], NumberStyles.Any, CultureInfo.InvariantCulture, out var monto))
        {
            Log.Warn(Tag, $"Aviso de gasto ilegible: '{cuerpo}'");
            return;
        }

        var titulo = partes[1].Trim();
        var importe = monto.ToString(monto % 1 == 0 ? "C0" : "C2", CulturaMx);

        try
        {
            await LocalNotificationCenter.Current.Show(new NotificationRequest
            {
                NotificationId = NotificacionGastoId,
                Title = "Gasto desde el reloj registrado",
                Description = string.IsNullOrWhiteSpace(titulo) ? importe : $"{importe} · {titulo}",
                ReturningData = "wear-expense"
            });

            Log.Info(Tag, $"Notificado: {importe} · {titulo}");
        }
        catch (Exception ex)
        {
            Log.Warn(Tag, $"No se pudo notificar el gasto: {ex.Message}");
        }
    }

    /// <summary>
    /// Vincula el reloj sin que el usuario teclee nada. El codigo llega por Bluetooth
    /// y aqui solo se reenvia al mismo endpoint de siempre, con el token de la sesion
    /// abierta en el telefono.
    /// </summary>
    private async Task VincularAsync(string nodoOrigen, string userCode)
    {
        if (string.IsNullOrWhiteSpace(userCode))
        {
            Responder(nodoOrigen, RutaPairResult, "Código vacío");
            return;
        }

        var token = Preferences.Get("token", string.Empty);
        if (string.IsNullOrWhiteSpace(token))
        {
            Log.Warn(Tag, "Llego un codigo pero no hay sesion en el telefono.");
            Responder(nodoOrigen, RutaPairResult, "Inicia sesión en el teléfono");
            return;
        }

        // El servicio puede haber arrancado con la app cerrada, asi que el contenedor
        // de MAUI podria no estar listo todavia.
        var api = IPlatformApplication.Current?.Services?.GetService<IApiService>();
        if (api == null)
        {
            Log.Warn(Tag, "El contenedor de MAUI no esta disponible.");
            Responder(nodoOrigen, RutaPairResult, "Abre Gastapp en el teléfono");
            return;
        }

        try
        {
            // Tope por debajo de lo que espera el reloj: si Render tarda mas que esto,
            // vale mas devolver un motivo a tiempo que una respuesta que ya no escucha.
            using var limite = new CancellationTokenSource(EsperaMaxima);

            var dispositivo = await api
                .LinkDevice(new LinkDeviceRequest { UserCode = userCode }, token)
                .WaitAsync(limite.Token);

            Log.Info(Tag, $"Reloj vinculado automaticamente: {dispositivo?.DeviceName}");
            Responder(nodoOrigen, RutaPairResult, ResultadoOk);

            // Para que el popup de espera pase a "vinculado" y Ajustes refresque su
            // lista. Si la app esta cerrada no hay quien escuche, y no pasa nada.
            WeakReferenceMessenger.Default.Send(
                new WearDeviceLinkedMessage(dispositivo?.DeviceName ?? string.Empty));
            WeakReferenceMessenger.Default.Send(new DevicesChangedMessage("vinculado"));
        }
        catch (OperationCanceledException)
        {
            Log.Warn(Tag, "Device/Link tardo demasiado.");
            Responder(nodoOrigen, RutaPairResult, "El servidor tardó demasiado");
        }
        catch (ApiException ex)
        {
            // Mismos casos que el popup de teclear el codigo, para que el reloj muestre
            // un motivo util en vez de un "no se pudo" generico.
            var motivo = ex.StatusCode switch
            {
                System.Net.HttpStatusCode.TooManyRequests => "Demasiados intentos",
                System.Net.HttpStatusCode.BadRequest => "Código no válido o expirado",
                System.Net.HttpStatusCode.Unauthorized => "Sesión caducada en el teléfono",
                System.Net.HttpStatusCode.NotFound => "Falta actualizar el servidor",
                _ => $"Error del servidor ({(int)ex.StatusCode})"
            };

            Log.Warn(Tag, $"No se pudo vincular ({(int)ex.StatusCode}): {ex.Content}");
            Responder(nodoOrigen, RutaPairResult, motivo);
        }
        catch (Exception ex)
        {
            Log.Warn(Tag, $"Fallo inesperado al vincular: {ex.Message}");
            Responder(nodoOrigen, RutaPairResult, "Sin conexión");
        }
    }

    private void Responder(string nodoDestino, string ruta, string cuerpo)
    {
        WearableClass.GetMessageClient(this)
            .SendMessage(nodoDestino, ruta, System.Text.Encoding.UTF8.GetBytes(cuerpo));
    }
}
