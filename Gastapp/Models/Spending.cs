using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Gastapp.Models
{
    public class Spending
    {
        public string Title { get; set; }
        public string Description { get; set; }
        public decimal Amount { get; set; }

        public DateTime Date { get; set; }
    }
}
