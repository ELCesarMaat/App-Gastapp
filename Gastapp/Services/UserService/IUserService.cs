using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Gastapp.Models;
using Gastapp.Models.Models;

namespace Gastapp.Services.UserService
{
    public interface IUserService
    {
        public Task<bool> CreateNewUser(CreateUserModel user, string token);
        public Task<User?> AddUserData(AllUserData userData);

        public Task<IncomeType?> GetIncomeTypeById(int id);
        public string GetUserId();

        public Task<User?> GetUser();

        public Task<User?> UpdateUser(User user);

    }
}
