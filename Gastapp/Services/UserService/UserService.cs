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
                    CategoryName = "Categoria 1",
                    UserId = userId,
                };
                _db.Categories.Add(category);
                var category2 = new Category
                {
                    CategoryName = "Categoria 2",
                    UserId = userId,
                };
                _db.Categories.Add(category2);
                var category3 = new Category
                {
                    CategoryName = "Categoria 3",
                    UserId = userId,
                };
                _db.Categories.Add(category3);
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

        public async Task<User?> GetUser()
        {
            return await _db.Users.Include(u => u.IncomeType).FirstAsync();
        }

        public async Task<User?> UpdateUser(User user)
        {
            try
            {
                var currentUser = await _db.Users.FirstOrDefaultAsync();
                if (currentUser == null)
                    return null;

                currentUser.IncomeTypeId = user.IncomeTypeId;
                currentUser.FirstPayDay = user.FirstPayDay;
                currentUser.SecondPayDay = user.SecondPayDay;
                currentUser.Salary = user.Salary;

                await _db.SaveChangesAsync();
                return currentUser;
            }
            catch (Exception e)
            {
                Console.WriteLine(e);
                return null;
            }
        }
    }
}