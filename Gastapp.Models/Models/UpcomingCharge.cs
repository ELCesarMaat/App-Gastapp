using System;

namespace Gastapp.Models
{
    /// <summary>
    /// Un cobro que todavia no ocurre. Es la version reducida que consume el
    /// calendario de "Proximos cobros", igual que <see cref="CreditCardPendingInfo"/>
    /// lo es para la pantalla de ahorros.
    /// </summary>
    public class UpcomingCharge
    {
        public string SubscriptionId { get; set; } = null!;
        public string ServiceName { get; set; } = null!;
        public string? PlanName { get; set; }
        public decimal Amount { get; set; }
        public DateTime Date { get; set; }
        public int DaysUntil { get; set; }
        public string ColorHex { get; set; } = "#7C3AED";

        /// <summary>"Hoy", "Mañana", "En 5 días".</summary>
        public string WhenText { get; set; } = string.Empty;

        public string DateText { get; set; } = string.Empty;
        public string StatusColor { get; set; } = "#126E63";

        /// <summary>Se cobra a una tarjeta de credito, no en efectivo.</summary>
        public bool IsChargedToCard { get; set; }

        public string PaymentSourceText { get; set; } = string.Empty;
    }
}
