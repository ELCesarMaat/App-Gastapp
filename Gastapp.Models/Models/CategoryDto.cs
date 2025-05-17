using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Gastapp.Models
{
    public class CategoryDto
    {
        public string CategoryId { get; set; } = null!;

        public string UserId { get; set; } = null!;

        public string CategoryName { get; set; } = null!;
        public bool IsSynced { get; set; } = false;

    }
}
