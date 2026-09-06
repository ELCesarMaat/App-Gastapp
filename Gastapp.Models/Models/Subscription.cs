using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Gastapp.Models
{
    /// <summary>
    /// Periodicidades de cobro soportadas. Se guardan como texto y no como el numero
    /// de un enum: agregar una periodicidad nueva no recorre ni reinterpreta las filas
    /// que ya estaban escritas.
    /// </summary>
    public static class SubscriptionBillingCycles
    {
        public const string Weekly = "Weekly";
        public const string Monthly = "Monthly";
        public const string Quarterly = "Quarterly";
        public const string Semiannual = "Semiannual";
        public const string Yearly = "Yearly";
    }

    /// <summary>
    /// Formas de pago con las que se puede cobrar una suscripcion.
    ///
    /// Son los mismos cuatro valores que ofrece la hoja de gasto normal
    /// (NewSpendingBottomSheet) y que sabe leer DetailViewModel, porque el cobro de
    /// una suscripcion termina siendo un <see cref="Spending"/> como cualquier otro:
    /// si aqui hubiera un valor que alla no existe, el detalle del gasto mostraria
    /// una forma de pago distinta a la que eligio el usuario.
    /// </summary>
    public static class SubscriptionPaymentMethods
    {
        public const string Cash = "Cash";
        public const string Debit = "Debit";
        public const string CreditCard = "CreditCard";
        public const string Transfer = "Transfer";
    }

    /// <summary>
    /// Un servicio de cobro recurrente: streaming, gimnasio, nube, membresia.
    ///
    /// A diferencia de <see cref="CreditCard"/>, aqui no se guarda "el dia del mes"
    /// sino la fecha del PRIMER cobro (<see cref="FirstChargeDate"/>). Con esa fecha
    /// ancla mas la periodicidad se deducen todos los cobros siguientes, y eso
    /// resuelve de una sola forma los ciclos que no son mensuales: una anualidad no
    /// se puede describir con un numero de dia, necesita saber tambien el mes.
    /// </summary>
    public class Subscription
    {
        public string SubscriptionId { get; set; } = Guid.NewGuid().ToString();
        public string UserId { get; set; } = null!;

        /// <summary>Nombre del servicio (Netflix, Spotify, Smart Fit).</summary>
        public string ServiceName { get; set; } = null!;

        /// <summary>Plan contratado (Premium, Familiar, Anual). Opcional.</summary>
        public string? PlanName { get; set; }

        /// <summary>Lo que se cobra cada ciclo, en la moneda de la app.</summary>
        public decimal Amount { get; set; }

        /// <summary>Periodicidad del cobro. Valores en <see cref="SubscriptionBillingCycles"/>.</summary>
        public string BillingCycle { get; set; } = SubscriptionBillingCycles.Monthly;

        /// <summary>Fecha ancla: el primer cobro. De aqui salen todos los siguientes.</summary>
        public DateTime FirstChargeDate { get; set; } = DateTime.Today;

        /// <summary>Valores en <see cref="SubscriptionPaymentMethods"/>.</summary>
        public string PaymentMethod { get; set; } = SubscriptionPaymentMethods.Cash;

        /// <summary>Tarjeta a la que se carga, cuando el cobro es a credito.</summary>
        public string? CreditCardId { get; set; }

        /// <summary>Categoria con la que se registra el gasto del cobro.</summary>
        public string? CategoryId { get; set; }

        /// <summary>Una suscripcion pausada deja de contar en los totales y no avisa de cobros.</summary>
        public bool IsActive { get; set; } = true;

        /// <summary>Periodo de prueba en curso: todavia no se cobra nada.</summary>
        public bool IsTrial { get; set; } = false;

        /// <summary>Cuando termina la prueba y empieza a cobrarse. Null si no hay prueba.</summary>
        public DateTime? TrialEndDate { get; set; }

        public string ColorHex { get; set; } = "#7C3AED";

        public string? Notes { get; set; }

        /// <summary>
        /// Ultimo cobro que el usuario dio por registrado. Sirve para no ofrecerle dos
        /// veces el mismo cobro del mes.
        /// </summary>
        public DateTime? LastChargeRegisteredAt { get; set; }

        public bool IsSynced { get; set; } = false;
        public bool IsDeleted { get; set; } = false;

        /// <summary>Cuando se marco como borrada. Null si nunca se borro. Sirve para purgar despues de N dias.</summary>
        public DateTime? DeletedAt { get; set; }

        public virtual User? User { get; set; }
        public virtual CreditCard? CreditCard { get; set; }
        public virtual Category? Category { get; set; }
    }
}
