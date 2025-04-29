using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Gastapp.Data;
using Gastapp.Models;
using Microsoft.EntityFrameworkCore;

namespace Gastapp.Services.UserService
{
    public class UserService(GastappDbContext db) : IUserService
    {
        private readonly GastappDbContext _db = db;
        public async Task<User?> CreateNewUser(User user)
        {
            try
            {
                _db.Users.Add(user);
                await _db.SaveChangesAsync();
                await CreateFirstUserCategory(user.LocalUserId);

                return user;

            }
            catch (Exception ex)
            {
                return null;
            }
        }

        private async Task CreateFirstUserCategory(string userId)
        {
            try
            {
                var category = new Category
                {
                    CategoryName = "Sin Categoria",
                    UserId = userId,
                };
                _db.Categories.Add(category);
                await _db.SaveChangesAsync();
            }
            catch (Exception ex)
            {
                return;
            }
        }

        public async Task<IncomeType?> GetIncomeTypeById(int id)
        {
            try
            {
                var res = await _db.IncomeTypes.FirstOrDefaultAsync(s => s.IncomeTypeId == id);
                return res;
            }
            catch (Exception ex)
            {
                return null;
            }   
        }
    }
}
