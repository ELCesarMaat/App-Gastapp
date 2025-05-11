using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Gastapp.Models
{
    public class Category
    {
        public int CategoryId { get; set; }

        public string UserId { get; set; }

        public string CategoryName { get; set; } = null!;
        public bool IsSynced { get; set; } = false;

        public virtual User User { get; set; } = null!;
        public virtual ICollection<Spending> Spendings { get; set; } = new List<Spending>();

    }
}
