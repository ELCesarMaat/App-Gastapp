using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Gastapp.Models
{
    public class CategoryResume
    {
        public string Name { get; set; } = null!;
        public string CategoryId { get; set; } = string.Empty;
        public decimal Amount { get; set; }
        public decimal Percentage { get; set; }
        public double ProgressValue { get; set; }
        public string AccentColor { get; set; } = "#126E63";
        public string SummaryText => $"{Percentage:N1}% del gasto total";

    }
}
