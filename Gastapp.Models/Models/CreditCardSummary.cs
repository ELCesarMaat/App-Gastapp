using System;
using System.Collections.Generic;

namespace Gastapp.Models
{
    public class CreditCardSummary
    {
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
