using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Gastapp.Models
{
    public class Category
    {
        public string CategoryId { get; set; } = Guid.NewGuid().ToString();

        public string UserId { get; set; } = null!;

        public string CategoryName { get; set; } = null!;
        public bool IsDefaultCategory { get; set; } = false;
        public bool IsSynced { get; set; } = false;

        public virtual User? User { get; set; }
        public virtual ICollection<Spending> Spendings { get; set; } = new List<Spending>();

    }
}
