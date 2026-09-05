using Android.App;
using Android.Content;
using Android.Gms.Wearable;
using Android.Util;
using CommunityToolkit.Mvvm.Messaging;
using Gastapp.Messages;
using Gastapp.Models;
using Gastapp.Models.Models;
using Gastapp.Services.ApiService;
using Gastapp.Services.SpendingService;
using Gastapp.Services.UserService;
using Plugin.LocalNotification;
using Refit;
using System.Globalization;
using System.Text.Json;

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

    /// <summary>El reloj serializa en camelCase; asi se acepta sin depender del caso.</summary>
    private static readonly JsonSerializerOptions OpcionesJson = new()
    {
        PropertyNameCaseInsensitive = true
    };

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
                Avisar(new DevicesChangedMessage("reloj"));
                break;

            case RutaExpense:
                var cuerpo = System.Text.Encoding.UTF8.GetString(messageEvent.GetData());
                RegistrarGastoAsync(cuerpo).GetAwaiter().GetResult();
                break;
        }
    }

    /// <summary>
    /// Registra en el telefono un gasto capturado en el reloj, y lo notifica.
    ///
    /// El gasto se INSERTA en la base local en vez de esperar a bajarlo del API: asi
    /// aparece en la lista aunque el telefono no tenga internet. El reloj lo sube por
    /// su cuenta y tanto Device/Expenses como SyncAllData hacen upsert por SpendingId,
    /// asi que no acaba duplicado.
    /// </summary>
    private async Task RegistrarGastoAsync(string cuerpo)
    {
        WearExpensePayload? payload;
        try
        {
            payload = JsonSerializer.Deserialize<WearExpensePayload>(cuerpo, OpcionesJson);
        }
        catch (Exception ex)
        {
            Log.Warn(Tag, $"Aviso de gasto ilegible: {ex.Message}");
            return;
        }

        if (payload == null || string.IsNullOrWhiteSpace(payload.SpendingId))
        {
            Log.Warn(Tag, "Aviso de gasto sin spendingId.");
            return;
        }

        var servicios = IPlatformApplication.Current?.Services;
        var spendingService = servicios?.GetService<ISpendingService>();
        var userService = servicios?.GetService<IUserService>();

        if (spendingService == null || userService == null)
        {
            Log.Warn(Tag, "El contenedor de MAUI no esta disponible; solo se notifica.");
            await NotificarAsync(payload);
            return;
        }

        try
        {
            // El reloj puede reenviar el mismo gasto, y ademas pudo llegar ya por la
            // sincronizacion normal. Comprobarlo evita duplicarlo en la lista.
            var existente = await spendingService.GetSpendingByIdAsync(payload.SpendingId);
            if (existente != null)
            {
                Log.Info(Tag, $"El gasto {payload.SpendingId} ya estaba registrado.");
                await NotificarAsync(payload);
                return;
            }

            var usuario = await userService.GetUser();
            if (usuario == null || string.IsNullOrWhiteSpace(usuario.UserId))
            {
                Log.Warn(Tag, "Sin usuario local; el gasto llegara por sincronizacion.");
                await NotificarAsync(payload);
                return;
            }

            var categoriaId = await ResolverCategoriaAsync(spendingService, payload.CategoryId);
            if (categoriaId == null)
            {
                Log.Warn(Tag, "El usuario no tiene categorias; el gasto llegara por sincronizacion.");
                await NotificarAsync(payload);
                return;
            }

            var gasto = new Spending
            {
                SpendingId = payload.SpendingId,
                UserId = usuario.UserId,
                CategoryId = categoriaId,
                Title = string.IsNullOrWhiteSpace(payload.Title) ? "Gasto desde el reloj" : payload.Title,
                Amount = payload.Amount,
                Description = payload.Description,
                Date = payload.OccurredAt.ToLocalTime(),

                // El reloj no maneja tarjetas: mismos valores que pone el API para los
                // gastos que entran por Device/Expenses.
                IsCreditCard = false,
                CreditCardId = null,
                PaymentMethod = "Cash",
                IsMsi = false,
                TotalInstallments = 1,
                CurrentInstallment = 1,
                InstallmentMonthlyAmount = 0m
            };

            var creado = await spendingService.CreateNewSpending(gasto);
            Log.Info(Tag, creado
                ? $"Gasto del reloj registrado en local: {payload.SpendingId}"
                : $"No se pudo registrar el gasto {payload.SpendingId}");

            if (creado)
            {
                // Esto es lo que hace que la lista ya abierta se actualice sola, sin
                // tener que cerrar y volver a entrar en la app.
                Avisar(new SpendingChangedMessage(payload.SpendingId));
            }
        }
        catch (Exception ex)
        {
            // Que falle no pierde el gasto: el reloj lo sube igual al API.
            Log.Warn(Tag, $"No se pudo registrar el gasto del reloj: {ex.Message}");
        }

        await NotificarAsync(payload);
    }

    /// <summary>
    /// La categoria que mando el reloj, si sigue siendo del usuario. Si no, la que
    /// tenga por defecto: nunca se descarta un gasto por la categoria.
    /// </summary>
    private static async Task<string?> ResolverCategoriaAsync(
        ISpendingService spendingService, string? categoriaDelReloj)
    {
        var categorias = await spendingService.GetCategoriesList();
        if (categorias.Count == 0)
            return null;

        if (!string.IsNullOrWhiteSpace(categoriaDelReloj)
            && categorias.Any(c => c.CategoryId == categoriaDelReloj))
        {
            return categoriaDelReloj;
        }

        return (categorias.FirstOrDefault(c => c.IsDefaultCategory) ?? categorias[0]).CategoryId;
    }

    private async Task NotificarAsync(WearExpensePayload payload)
    {
        var importe = payload.Amount.ToString(payload.Amount % 1 == 0 ? "C0" : "C2", CulturaMx);
        var titulo = payload.Title?.Trim();

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
            Avisar(new WearDeviceLinkedMessage(dispositivo?.DeviceName ?? string.Empty));
            Avisar(new DevicesChangedMessage("vinculado"));
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

    /// <summary>
    /// Publica un mensaje SIEMPRE en el hilo de UI.
    ///
    /// Este servicio corre en un hilo secundario, y los ViewModels que escuchan tocan
    /// ObservableCollection en sus manejadores sin marshalling propio. Enviarlo desde
    /// aqui tal cual acabaria fallando de forma intermitente.
    /// </summary>
    private static void Avisar<T>(T mensaje) where T : class
    {
        MainThread.BeginInvokeOnMainThread(() => WeakReferenceMessenger.Default.Send(mensaje));
    }

    private void Responder(string nodoDestino, string ruta, string cuerpo)
    {
        WearableClass.GetMessageClient(this)
            .SendMessage(nodoDestino, ruta, System.Text.Encoding.UTF8.GetBytes(cuerpo));
    }
}
