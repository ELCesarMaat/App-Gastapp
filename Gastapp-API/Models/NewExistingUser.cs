using System.ComponentModel.DataAnnotations;
using Gastapp.Models;

namespace Gastapp_API.Models
{
    public class NewExistingUser
    {
        //Este modelo es para cuando el usuario ya tiene creada una cuenta local en su telefono
        //por lo que la mayoria de datos ya los tiene. Solo falta el hash y el id generado automatiamente
        public string Id { get; set; } = Guid.NewGuid().ToString();
        public string Email { get; set; }

        public string PasswordHash { get; set; }

        public User Profile { get; set; }
        public string? OnlineUserId { get; set; }
        public string LocalUserId { get; set; } = null!;
        public decimal Salary { get; set; }
        public string Name { get; set; } = null!;
        public DateTime BirthDate { get; set; }
        public int IncomeTypeId { get; set; }
        public int? FirstPayDay { get; set; }
        public int? SecondPayDay { get; set; }
        public int? WeekPayDay { get; set; }
    }
}