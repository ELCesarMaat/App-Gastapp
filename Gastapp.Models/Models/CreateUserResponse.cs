using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Gastapp.Models.Models
{
    public class CreateUserResponse
    {
        public string UserId { get; set; } = null!;
        public string Token { get; set; } = null!;

    }
}
