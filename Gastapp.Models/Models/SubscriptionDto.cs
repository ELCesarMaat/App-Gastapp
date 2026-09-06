using System;

namespace Gastapp.Models
{
    /// <summary>
    /// Lo que viaja entre la app y el API. Mismo papel que <see cref="CreditCardDto"/>.
    ///
    /// Ojo con las fechas, porque aqui conviven dos tipos distintos:
    ///
    /// - <see cref="FirstChargeDate"/> y <see cref="TrialEndDate"/> son fechas de
    ///   calendario (dia, sin hora). Viajan tal cual, SIN convertir a UTC ni a local:
    ///   son columnas `date` en Postgres. Convertirlas correria el dia y con el todos
    ///   los cobros calculados a partir del ancla.
    /// - <see cref="LastChargeRegisteredAt"/> y <see cref="DeletedAt"/> si son
    ///   instantes, y viajan en UTC como el resto de la app.
    /// </summary>
    public class SubscriptionDto
    {
        public string SubscriptionId { get; set; } = null!;
        public string UserId { get; set; } = null!;
        public string ServiceName { get; set; } = null!;
        public string? PlanName { get; set; }
        public decimal Amount { get; set; }
        public string BillingCycle { get; set; } = SubscriptionBillingCycles.Monthly;

        /// <summary>Fecha de calendario. No se convierte de zona horaria.</summary>
        public DateTime FirstChargeDate { get; set; }

        public string PaymentMethod { get; set; } = SubscriptionPaymentMethods.Cash;
        public string? CreditCardId { get; set; }
        public string? CategoryId { get; set; }
        public bool IsActive { get; set; } = true;
        public bool IsTrial { get; set; }

        /// <summary>Fecha de calendario. No se convierte de zona horaria.</summary>
        public DateTime? TrialEndDate { get; set; }

        public string ColorHex { get; set; } = "#7C3AED";
        public string? Notes { get; set; }

        /// <summary>Instante. Viaja en UTC.</summary>
        public DateTime? LastChargeRegisteredAt { get; set; }

        public bool IsSynced { get; set; } = false;
        public bool IsDeleted { get; set; } = false;

        /// <summary>Instante. Viaja en UTC. Sirve para purgar despues de N dias.</summary>
        public DateTime? DeletedAt { get; set; }
    }
}
