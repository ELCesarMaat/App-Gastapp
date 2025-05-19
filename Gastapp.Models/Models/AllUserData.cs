using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Gastapp.Models.Models
{
    public class AllUserData
    {
        public User User { get; set; }
        public List<CategoryDto> Categories { get; set; }
        public List<SpendingDto> Spendings { get; set; }
        public List<IncomeType> Incomes { get; set; }
        public string Token { get; set; } = null!;
        public required DateTime? TokenExpiration { get; set; }
    }
}