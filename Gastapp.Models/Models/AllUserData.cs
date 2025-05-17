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
        public List<CategoryDto> Categories { get; set; } = new List<CategoryDto>();
        public List<SpendingDto> Spendings { get; set; } = new List<SpendingDto>();
        public List<IncomeType> Incomes { get; set; } = new List<IncomeType>();
    }
}
