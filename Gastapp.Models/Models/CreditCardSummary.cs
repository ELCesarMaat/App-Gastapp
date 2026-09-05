using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace Gastapp.Models
{
    public class CreditCardSummary : INotifyPropertyChanged
    {
        private bool _isSelected;

        /// <summary>
        /// Marca la tarjeta elegida en el carrusel. Notifica porque la seleccion cambia
        /// sin recargar la lista: al tocar otra tarjeta, o al tocar la misma para
        /// deseleccionarla.
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

        public CreditCard Card { get; set; } = null!;
        public decimal CreditLimit { get; set; }
        public decimal TotalDebt { get; set; }
        public decimal AvailableCredit { get; set; }
        public double UsagePercentage { get; set; }
        public double UsageProgressRatio => CreditLimit > 0 ? Math.Clamp((double)(TotalDebt / CreditLimit), 0.0, 1.0) : 0.0;
        public decimal CurrentCycleAmount { get; set; }
        public decimal TotalMsiRemainingDebt { get; set; }
        public int ActiveMsiCount { get; set; }
        public DateTime NextCutOffDate { get; set; }
        public DateTime NextPaymentDueDate { get; set; }
        public int DaysUntilCutOff { get; set; }
        public int DaysUntilPayment { get; set; }
        public string CutOffStatusText { get; set; } = string.Empty;
        public string PaymentStatusText { get; set; } = string.Empty;
        public string PaymentStatusColor { get; set; } = "#126E63";
        public string UsageStatusColor { get; set; } = "#126E63";
        public string CardBackgroundGradientStart { get; set; } = "#126E63";
        public string CardBackgroundGradientEnd { get; set; } = "#0B534A";
        public List<Spending> CurrentCycleSpendings { get; set; } = [];
        public List<Spending> ActiveMsiSpendings { get; set; } = [];
    }
}
