using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Text.Json.Serialization;
using System.Threading.Tasks;

namespace Gastapp.Models.Models
{
    public class UserPayInfoDto
    {
        public string UserId { get; set; } = null!;
        public decimal Salary { get; set; }
        public decimal PercentSave { get; set; }
        public int IncomeTypeId { get; set; }
        public int? FirstPayDay { get; set; }
        public int? SecondPayDay { get; set; }
        public int? WeekPayDay { get; set; }
    }
}
