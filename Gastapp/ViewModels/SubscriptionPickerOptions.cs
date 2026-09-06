namespace Gastapp.ViewModels
{
    /// <summary>
    /// Opcion de periodicidad para el Picker del formulario. Se separa el valor que
    /// se guarda del texto que se ve, igual que <see cref="ReminderFrequencyOption"/>.
    /// </summary>
    public class BillingCycleOption
    {
        /// <summary>Uno de los valores de <see cref="Gastapp.Models.SubscriptionBillingCycles"/>.</summary>
        public string Value { get; set; } = null!;

        public string Label { get; set; } = null!;
    }

    /// <summary>Opcion de forma de pago para el Picker del formulario.</summary>
    public class SubscriptionPaymentMethodOption
    {
        /// <summary>Uno de los valores de <see cref="Gastapp.Models.SubscriptionPaymentMethods"/>.</summary>
        public string Value { get; set; } = null!;

        public string Label { get; set; } = null!;
    }
}
