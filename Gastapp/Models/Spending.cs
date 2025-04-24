using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Gastapp.Models
{
    public class Spending
    {
        public int SpendingId { get; set; }

        //public int CategoryId { get; set; }

        public string Title { get; set; } = null!;

        public string? Description { get; set; } = null;

        public decimal Amount { get; set; }
        public bool IsSynced { get; set; } = false;
        public DateTime Date { get; set; } = DateTime.Now;
        //public virtual Category Category { get; set; } = null!;
    }
}
