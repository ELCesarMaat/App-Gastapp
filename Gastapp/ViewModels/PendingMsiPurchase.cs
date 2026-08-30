using System;
using CommunityToolkit.Mvvm.ComponentModel;

namespace Gastapp.ViewModels
{
    /// <summary>
    /// Compra a meses sin intereses capturada mientras se da de alta una tarjeta
    /// que ya venia en uso. Se convierte en un Spending real al guardar la tarjeta.
    /// </summary>
    public partial class PendingMsiPurchase : ObservableObject
    {
        [ObservableProperty] private string _title = string.Empty;
        [ObservableProperty] private decimal _monthlyAmount;

        /// <summary>Mensualidades que el usuario ya pago de este plan.</summary>
        [ObservableProperty] private int _paidInstallments;

        [ObservableProperty] private int _totalInstallments = 12;

        public string DisplayTitle => string.IsNullOrWhiteSpace(Title) ? "Compra a MSI" : Title.Trim();

        /// <summary>Lo que costo la compra completa.</summary>
        public decimal TotalAmount => MonthlyAmount * TotalInstallments;

        public int RemainingInstallments => Math.Max(0, TotalInstallments - PaidInstallments);

        /// <summary>Lo que todavia se debe: es el monto que suma a la deuda de la tarjeta.</summary>
        public decimal RemainingAmount => MonthlyAmount * RemainingInstallments;

        public string PlanSummary => $"${MonthlyAmount:N0} al mes · {PaidInstallments} de {TotalInstallments} pagadas";

        public string RemainingSummary => RemainingInstallments > 0
            ? $"Te faltan {RemainingInstallments} · debes ${RemainingAmount:N0}"
            : "Ya la terminaste de pagar";
    }
}
