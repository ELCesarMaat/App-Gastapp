using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Gastapp.Models
{
    public class User
    {
        public string LocalUserId { get; set; } = null!;
        public string? OnlineUserId { get; set; }
        public decimal Salary { get; set; }
        public string Name { get; set; } = null!;
        public string? Email { get; set; }

        public DateTime BirthDate { get; set; }
        public int IncomeTypeId { get; set; }
        public int? FirstPayDay { get; set; }
        public int? SecondPayDay { get; set; }
        public int? WeekPayDay { get; set; }
        public virtual IncomeType IncomeType { get; set; } = null!;
        public virtual ICollection<Category> Categories { get; set; } = new List<Category>();
    }
}
