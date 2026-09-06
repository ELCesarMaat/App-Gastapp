using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace Gastapp.Models
{
    /// <summary>
    /// Lo que consume la UI de suscripciones. Mismo papel que
    /// <see cref="CreditCardSummary"/>: el servicio ya deja aqui los textos, los
    /// colores y las fechas calculadas para que la vista solo tenga que enlazar.
    /// </summary>
    public class SubscriptionSummary : INotifyPropertyChanged
    {
        private bool _isSelected;

        /// <summary>
        /// Marca la suscripcion elegida en el carrusel. Notifica porque la seleccion
        /// cambia sin recargar la lista: al tocar otra tarjeta, o al tocar la misma
        /// para deseleccionarla.
        /// </summary>
        public bool IsSelected
        {
            get => _isSelected;
            set
            {
                if (_isSelected == value)
                    return;

                _isSelected = value;
                OnPropertyChanged();
            }
        }

        public event PropertyChangedEventHandler? PropertyChanged;

        private void OnPropertyChanged([CallerMemberName] string? name = null) =>
            PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));

        public Subscription Subscription { get; set; } = null!;

        /// <summary>Lo que se cobra cada ciclo, tal cual lo contrato el usuario.</summary>
        public decimal Amount { get; set; }

        /// <summary>El costo llevado a mes, para poder sumar peras con manzanas.</summary>
        public decimal MonthlyEquivalent { get; set; }

        public decimal YearlyEquivalent { get; set; }

        public DateTime NextChargeDate { get; set; }
        public int DaysUntilCharge { get; set; }

        public string ChargeStatusText { get; set; } = string.Empty;
        public string ChargeStatusColor { get; set; } = "#126E63";

        /// <summary>"Mensual", "Anual"... ya traducido para la vista.</summary>
        public string BillingCycleText { get; set; } = string.Empty;

        /// <summary>"$149.00 al mes", "$1,790.00 al año".</summary>
        public string PriceCycleText { get; set; } = string.Empty;

        public string PaymentMethodText { get; set; } = string.Empty;

        /// <summary>Nombre de la tarjeta a la que se carga, si aplica.</summary>
        public string LinkedCardName { get; set; } = string.Empty;

        public bool HasLinkedCard { get; set; }

        public string CategoryName { get; set; } = string.Empty;

        public bool IsActive { get; set; } = true;

        /// <summary>
        /// Si entra en el gasto mensual y anual. Activa no basta: durante una prueba
        /// gratis no se esta pagando nada, asi que sumarla inflaria el total con
        /// dinero que todavia no sale.
        /// </summary>
        public bool CountsTowardTotals { get; set; } = true;

        /// <summary>Texto del boton que pausa o reanuda, segun como este ahora.</summary>
        public string ToggleStateActionText { get; set; } = "Pausar";

        /// <summary>El cobro del periodo en curso ya se registro como gasto.</summary>
        public bool IsCurrentCycleCharged { get; set; }

        /// <summary>"Cobro de este periodo registrado el 12/sep".</summary>
        public string LastChargeText { get; set; } = string.Empty;

        public string Notes { get; set; } = string.Empty;
        public bool HasNotes { get; set; }

        /// <summary>Texto y color del chip de estado (Activa / Pausada / Prueba).</summary>
        public string StatusBadgeText { get; set; } = string.Empty;
        public string StatusBadgeColor { get; set; } = "#126E63";

        public bool IsTrialActive { get; set; }
        public int DaysUntilTrialEnds { get; set; }
        public string TrialStatusText { get; set; } = string.Empty;

        /// <summary>Que tanto pesa esta suscripcion dentro del gasto mensual total (0..1).</summary>
        public double ShareOfMonthlyRatio { get; set; }

        public string ShareOfMonthlyText { get; set; } = string.Empty;

        public string CardBackgroundGradientStart { get; set; } = "#7C3AED";
        public string CardBackgroundGradientEnd { get; set; } = "#4C1D95";

        /// <summary>Los proximos cobros de esta suscripcion, para el detalle.</summary>
        public List<UpcomingCharge> UpcomingCharges { get; set; } = [];
    }
}
