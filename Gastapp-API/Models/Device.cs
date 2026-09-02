using System;

namespace Gastapp_API.Models
{
    // Dispositivo vinculado a una cuenta (por ahora, relojes Wear OS).
    // Persiste hasta que el usuario lo revoque desde la app del telefono.
    public class Device
    {
        public string DeviceId { get; set; } = Guid.NewGuid().ToString();
        public string UserId { get; set; } = null!;

        public string Name { get; set; } = null!;
        public string Platform { get; set; } = "wearos";

        // SHA-256 del refresh token vigente, en base64. Se rota en cada refresh.
        public string RefreshTokenHash { get; set; } = null!;

        // Permisos separados por espacio, igual que el claim "scope" del JWT.
        public string Scopes { get; set; } = DeviceScopes.Default;

        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
        public DateTime? LastSeenAt { get; set; }
        public DateTime? RevokedAt { get; set; }
    }

    public static class DeviceScopes
    {
        public const string ExpensesWrite = "expenses:write";
        public const string ExpensesReadSummary = "expenses:read_summary";

        public const string Default = ExpensesWrite + " " + ExpensesReadSummary;
    }
}
