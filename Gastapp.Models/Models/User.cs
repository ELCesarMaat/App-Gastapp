using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Gastapp.Models
{
    public class User
    {
        [Key] public string OnlineUserId { get; set; } = Guid.NewGuid().ToString();
        public string LocalUserId { get; set; } = null!;
        public decimal Salary { get; set; }
        public string Name { get; set; } = null!;
        public string? Email { get; set; }
        public string PassWordHash { get; set; } = null!;
        public DateTime BirthDate { get; set; }
        public int IncomeTypeId { get; set; }
        public int? FirstPayDay { get; set; }
        public int? SecondPayDay { get; set; }
        public int? WeekPayDay { get; set; }
        public virtual IncomeType IncomeType { get; set; }
        public virtual ICollection<Category> Categories { get; set; } = new List<Category>();
    }
}
