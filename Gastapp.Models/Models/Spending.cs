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
        public DateTime Date { get; set; } = DateTime.Now;

        public virtual User? User { get; set; }
        public virtual Category? Category { get; set; }
    }
}
