using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Gastapp.Models
{
    public class CreditCardPendingInfo
    {
        public CreditCard Card { get; set; } = null!;
        public decimal PendingAmount { get; set; }
        public int DaysUntilPayment { get; set; }
        public string StatusText { get; set; } = string.Empty;
        public string StatusColor { get; set; } = "#126E63";
        public string UrgencyColor { get; set; } = "#126E63";
    }
}
