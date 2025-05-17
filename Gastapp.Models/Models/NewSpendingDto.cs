using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Gastapp.Models.Models
{
    public class NewSpendingDto
    {
        public SpendingDto Spending { get; set; }
        public CategoryDto Category { get; set; }
    }
}
