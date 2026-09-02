using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Gastapp.Models
{
    public class CreditCardDto
    {
        public string CreditCardId { get; set; } = null!;
        public string UserId { get; set; } = null!;
        public string CardName { get; set; } = null!;
        public string BankName { get; set; } = null!;
        public string? LastFourDigits { get; set; }
        public int CutOffDay { get; set; }
        public int PaymentDay { get; set; }
        public decimal CreditLimit { get; set; } = 0m;
        public string ColorHex { get; set; } = "#126E63";
        public bool IsSynced { get; set; } = false;
        public bool IsDeleted { get; set; } = false;
        /// <summary>Cuando se marco como borrado. Null si nunca se borro. Sirve para purgar despues de N dias.</summary>
        public DateTime? DeletedAt { get; set; }
    }
}
