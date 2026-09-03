using Android.Gms.Wearable;
using Android.Util;
using Gastapp.Services.WearService;

namespace Gastapp.Platforms.Android.Wear;

/// <summary>
/// Implementacion Android de <see cref="IWearChannel"/> sobre la Wearable Data Layer.
///
/// Recordatorio: la entrega solo ocurre entre apps con el mismo applicationId y la
/// misma firma. Si deja de llegar nada sin ningun error, empieza por comprobar eso.
/// </summary>
public class WearChannel : IWearChannel
{
    private const string Tag = "GastappCanal";
    private const string RutaRevoked = "/gastapp/revoked";

    /// <summary>Clave dentro del DataMap donde viaja el JSON.</summary>
    private const string ClaveJson = "json";

    /// <summary>
    /// Marca de tiempo en el DataMap. Sin esto, volver a poner un contenido idéntico
    /// no cuenta como cambio y el reloj no recibe nada.
    /// </summary>
    private const string ClaveSello = "ts";

    public async Task<bool> NotifyDeviceRevokedAsync(string deviceId)
    {
        if (string.IsNullOrWhiteSpace(deviceId))
            return false;

        try
        {
            var contexto = global::Android.App.Application.Context;

            var nodos = await WearableClass.GetNodeClient(contexto)
                .GetConnectedNodesAsync();

            if (nodos is not { Count: > 0 })
            {
                Log.Info(Tag, "Sin relojes conectados a los que avisar.");
                return false;
            }

            var cuerpo = System.Text.Encoding.UTF8.GetBytes(deviceId);
            var messageClient = WearableClass.GetMessageClient(contexto);

            // Se manda a todos: con varios relojes en la cuenta, cada uno compara el
            // deviceId con el suyo y solo actua el que coincide.
            foreach (var nodo in nodos)
                await messageClient.SendMessageAsync(nodo.Id, RutaRevoked, cuerpo);

            Log.Info(Tag, $"Aviso de revocacion enviado a {nodos.Count} reloj(es).");
            return true;
        }
        catch (Exception ex)
        {
            // Que falle no cambia nada: el servidor ya revoco y el reloj se enterara
            // igual la proxima vez que llame al API.
            Log.Warn(Tag, $"No se pudo avisar al reloj: {ex.Message}");
            return false;
        }
    }

    public async Task<bool> PutDataAsync(string path, string json)
    {
        if (string.IsNullOrWhiteSpace(path) || json == null)
            return false;

        try
        {
            var contexto = global::Android.App.Application.Context;

            var peticion = PutDataMapRequest.Create(path);
            peticion.DataMap.PutString(ClaveJson, json);
            peticion.DataMap.PutLong(ClaveSello, DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());

            // Urgent: sin esto la Data Layer puede retrasar la entrega hasta media
            // hora, y el objetivo es justo que el reloj no muestre datos viejos.
            var datos = peticion.AsPutDataRequest();
            datos.SetUrgent();

            await WearableClass.GetDataClient(contexto).PutDataItemAsync(datos);

            Log.Info(Tag, $"Datos empujados a {path} ({json.Length} car.)");
            return true;
        }
        catch (Exception ex)
        {
            // El reloj seguira tirando del API por su cuenta; esto es un atajo.
            Log.Warn(Tag, $"No se pudieron empujar datos a {path}: {ex.Message}");
            return false;
        }
    }
}
