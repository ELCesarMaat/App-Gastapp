using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Gastapp.Models;

namespace Gastapp.Services.UserService
{
    public interface IUserService
    {
        public Task<User?> CreateNewUser(User user);
        public Task<IncomeType?> GetIncomeTypeById(int id);

    }
}
