using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Gastapp.Models
{
    public class SpendingDto
    {
        public int SpendingId { get; set; }

        public int CategoryId { get; set; }

        public string UserId { get; set; } = null!;

        public string Title { get; set; } = null!;

        public string? Description { get; set; }

        public decimal Amount { get; set; }
        public bool IsSynced { get; set; } = false;
        public bool IsDeleted { get; set; } = false;
        public DateTime Date { get; set; } = DateTime.Now;
    }
}