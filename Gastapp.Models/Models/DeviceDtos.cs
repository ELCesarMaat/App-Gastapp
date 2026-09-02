using System;
using System.Collections.Generic;

namespace Gastapp.Models.Models
{
    // ---- Emparejamiento: los usa el reloj (sin autenticar) ----

    public class DeviceCodeRequest
    {
        public string DeviceName { get; set; } = null!;
        public string Platform { get; set; } = "wearos";
    }

    public class DeviceCodeResponse
    {
        /// <summary>Credencial bearer opaca. Solo la conoce el reloj.</summary>
        public string DeviceCode { get; set; } = null!;

        /// <summary>Codigo corto que el usuario teclea en el telefono, con guion: "K7M-2QX".</summary>
        public string UserCode { get; set; } = null!;

        public int ExpiresIn { get; set; }
        public int Interval { get; set; }
    }

    public class DeviceTokenRequest
    {
        public string DeviceCode { get; set; } = null!;
    }

    public class DeviceTokenResponse
    {
        public string AccessToken { get; set; } = null!;
        public string RefreshToken { get; set; } = null!;
        public int ExpiresIn { get; set; }
        public string DeviceId { get; set; } = null!;
        public string Scopes { get; set; } = null!;
    }

    public class DeviceRefreshRequest
    {
        public string RefreshToken { get; set; } = null!;
    }

    // ---- Vinculacion y administracion: los usa la app del telefono (autenticado) ----

    public class LinkDeviceRequest
    {
        public string UserCode { get; set; } = null!;
    }

    public class LinkDeviceResponse
    {
        public string DeviceName { get; set; } = null!;
        public string Platform { get; set; } = null!;
    }

    public class RevokeDeviceRequest
    {
        public string DeviceId { get; set; } = null!;
    }

    public class DeviceDto
    {
        public string DeviceId { get; set; } = null!;
        public string Name { get; set; } = null!;
        public string Platform { get; set; } = null!;
        public DateTime CreatedAt { get; set; }
        public DateTime? LastSeenAt { get; set; }
    }

    // ---- Datos que consume el reloj ----

    public class DeviceCategoryDto
    {
        public string CategoryId { get; set; } = null!;
        public string CategoryName { get; set; } = null!;
        public bool IsDefaultCategory { get; set; }
    }

    public class DeviceExpenseDto
    {
        /// <summary>UUID generado en el reloj. Es la clave de idempotencia: no lo regeneres al reintentar.</summary>
        public string SpendingId { get; set; } = null!;

        public decimal Amount { get; set; }
        public string? Title { get; set; }

        /// <summary>Null deja que el servidor asigne la categoria por defecto del usuario.</summary>
        public string? CategoryId { get; set; }

        public DateTime OccurredAt { get; set; }

        /// <summary>Texto dictado tal cual. Se guarda en Description para poder mejorar el parser.</summary>
        public string? RawInput { get; set; }

        /// <summary>El parser no pudo sacar el monto: se marca el titulo para que el usuario lo corrija.</summary>
        public bool NeedsReview { get; set; }
    }

    public class DeviceExpenseResult
    {
        public string SpendingId { get; set; } = null!;

        /// <summary>false = ya existia. Para el reloj ambos casos son exito.</summary>
        public bool Created { get; set; }
    }

    public class DeviceExpenseBatchResult
    {
        public List<DeviceExpenseResult> Results { get; set; } = new();
    }

    public class DeviceSummaryResponse
    {
        public string Period { get; set; } = null!;
        public decimal Total { get; set; }
        public int Count { get; set; }
        public string Currency { get; set; } = "MXN";
    }
}
