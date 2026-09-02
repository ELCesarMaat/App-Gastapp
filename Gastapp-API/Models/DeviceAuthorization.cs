using System;

namespace Gastapp_API.Models
{
    // Autorizacion efimera de emparejamiento (RFC 8628, device authorization grant).
    // Vive ~10 minutos y se limpia sola desde PurgeDeletedService.
    public class DeviceAuthorization
    {
        public string DeviceAuthorizationId { get; set; } = Guid.NewGuid().ToString();

        // SHA-256 del device_code en base64. El codigo en claro nunca se guarda:
        // es una credencial bearer.
        public string DeviceCodeHash { get; set; } = null!;

        // Codigo corto que el usuario teclea en el telefono, ya normalizado
        // (mayusculas, sin guion ni espacios). El guion es solo cosmetico al mostrarlo.
        public string UserCode { get; set; } = null!;

        // Se rellena cuando el usuario aprueba desde la app del telefono.
        public string? UserId { get; set; }

        public string DeviceName { get; set; } = null!;
        public string Platform { get; set; } = "wearos";

        // pending | approved | denied | expired | consumed
        public string Status { get; set; } = "pending";

        public int PollCount { get; set; }
        public DateTime? LastPolledAt { get; set; }
        public int IntervalSeconds { get; set; } = 5;

        public DateTime ExpiresAt { get; set; }
        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    }

    public static class DeviceAuthorizationStatus
    {
        public const string Pending = "pending";
        public const string Approved = "approved";
        public const string Denied = "denied";
        public const string Expired = "expired";
        public const string Consumed = "consumed";
    }
}
