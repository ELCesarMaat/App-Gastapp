using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Gastapp.Models
{
    public class Spending
    {
        public string SpendingId { get; set; } = Guid.NewGuid().ToString();
        public string UserId { get; set; } = null!;
        public string CategoryId { get; set; } = null!;
        public string Title { get; set; } = null!;

        public string? Description { get; set; }

        public decimal Amount { get; set; }
        public bool IsSynced { get; set; } = false;
        public bool IsDeleted { get; set; } = false;
        /// <summary>Cuando se marco como borrado. Null si nunca se borro. Sirve para purgar despues de N dias.</summary>
        public DateTime? DeletedAt { get; set; }
        public DateTime Date { get; set; } = DateTime.Now;

        public bool IsCreditCard { get; set; } = false;
        public string? CreditCardId { get; set; }

        public string PaymentMethod { get; set; } = "Cash";
        public bool IsMsi { get; set; } = false;
        public int TotalInstallments { get; set; } = 1;
        public int CurrentInstallment { get; set; } = 1;
        public string? ParentSpendingId { get; set; }
        public decimal InstallmentMonthlyAmount { get; set; } = 0m;

        public virtual User? User { get; set; }
        public virtual Category? Category { get; set; }
        public virtual CreditCard? CreditCard { get; set; }
    }
}
