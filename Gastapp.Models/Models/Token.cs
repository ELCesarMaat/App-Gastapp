using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Gastapp.Models.Models
{
    public class Token
    {
        public string TokenValue { get; set; } = null!;
        public required DateTime? TokenExpiration { get; set; }
    }
}
