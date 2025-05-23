using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Text.Json.Serialization;



namespace Gastapp.Models
{
    public class User
    {
        [Key] public string UserId { get; set; } = Guid.NewGuid().ToString();
        //public string LocalUserId { get; set; } = Guid.NewGuid().ToString();
        public decimal Salary { get; set; }
        public decimal PercentSave { get; set; }
        public string Name { get; set; } = null!;
        public string? Email { get; set; }

        [JsonIgnore] // Para no serializar el hash al enviar respuestas
        public string PassWordHash { get; set; } = null!;

        public DateTime BirthDate { get; set; }
        public int IncomeTypeId { get; set; }
        public int? FirstPayDay { get; set; }
        public int? SecondPayDay { get; set; }
        public int? WeekPayDay { get; set; }
        public bool IsSynced { get; set; } = false;
        public virtual IncomeType? IncomeType { get; set; }
        public virtual ICollection<Category> Categories { get; set; } = new List<Category>();
        public virtual ICollection<Spending> Spendings { get; set; } = new List<Spending>();

    }
}
