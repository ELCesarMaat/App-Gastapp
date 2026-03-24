using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Gastapp.Data;
using Gastapp.Models;
using Gastapp.Models.Models;
using Gastapp.Services.ApiService;
using Gastapp.Utils;
using Microsoft.EntityFrameworkCore;

namespace Gastapp.Services.UserService
{
    public class UserService(GastappDbContext db, IApiService api) : IUserService
    {
        private readonly GastappDbContext _db = db;
        private readonly IApiService _api = api;

        public async Task<User?> AddUserData(AllUserData userData)
        {
            try
            {
                await _db.Database.EnsureDeletedAsync();
                await _db.Database.EnsureCreatedAsync();
                _db.ChangeTracker.Clear();
                Preferences.Set("token", userData.Token);
                Preferences.Set("tokenexpiration", userData.TokenExpiration.ToString());
                var user = userData.User;
                user.PassWordHash = string.Empty;
                user.IncomeType = null;
                var categories = userData.Categories;
                var spendings = userData.Spendings;
                var incomes = userData.Incomes;

                var localIncomes = await _db.IncomeTypes.ToListAsync();
                if (!await _db.IncomeTypes.AnyAsync())
                {
                    foreach (var c in incomes)
                    {
                        await _db.IncomeTypes.AddAsync(new IncomeType
                        {
                            IncomeTypeId = c.IncomeTypeId,
                            IncomeTypeName = c.IncomeTypeName,
                        });
                    }
                }

                //await _db.SaveChangesAsync();

                await _db.Users.AddAsync(user);
                foreach (var c in categories)
                {
                    await _db.Categories.AddAsync(new Category
                    {
                        CategoryId = c.CategoryId,
                        CategoryName = c.CategoryName,
                        IsDefaultCategory = c.IsDefaultCategory,
                        IsSynced = c.IsSynced,
                        UserId = user.UserId,
                    });
                }

                foreach (var s in spendings)
                {
                    await _db.Spending.AddAsync(new Spending
                    {
                        SpendingId = s.SpendingId,
                        CategoryId = s.CategoryId,
                        Amount = s.Amount,
                        Date = DateTimeUtils.SpendingFromApiToLocal(s.Date),
                        Description = s.Description,
                        IsSynced = s.IsSynced,
                        UserId = user.UserId,
                        Title = s.Title,
                        IsDeleted = s.IsDeleted
                    });
                }

                await _db.SaveChangesAsync();
                //await CreateFirstUserCategory(user.UserId);
                return user;
            }
            catch (Exception ex)
            {
                return null;
            }
        }

        public async Task<bool> CreateNewUser(CreateUserModel user, string token)
        {
            try
            {
                await _db.Database.EnsureDeletedAsync();
                await _db.Database.EnsureCreatedAsync();
                _db.ChangeTracker.Clear();
                _db.Users.Add(new User
                {
                    UserId = user.UserId,
                    Salary = user.Salary,
                    PercentSave = user.PercentSave,
                    Name = user.Name,
                    Email = user.Email,
                    PassWordHash = string.Empty,
                    BirthDate = user.BirthDate,
                    IncomeTypeId = user.IncomeTypeId,
                    FirstPayDay = user.FirstPayDay,
                    SecondPayDay = user.SecondPayDay,
                    WeekPayDay = user.WeekPayDay
                });
                var incomes = await _api.GetIncomes(token);
                if (!await _db.IncomeTypes.AnyAsync())
                    await _db.IncomeTypes.AddRangeAsync(incomes);

                await _db.SaveChangesAsync();
                await CreateFirstUserCategory(user.UserId);

                return true;
            }
            catch (Exception ex)
            {
                return false;
            }
        }

        private async Task CreateFirstUserCategory(string userId)
        {
            try
            {
                var category = new Category
                {
                    CategoryName = "Sin categoria",
                    UserId = userId,
                    IsDefaultCategory = true,
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

        public async Task<User?> GetUser()
        {
            return await _db.Users
                .Include(u => u.IncomeType)
                .FirstOrDefaultAsync();
        }

        public void ClearLocalSession()
        {
            _db.DeleteDatabase();
        }

        public async Task<CloudSyncStatusSummary> GetCloudSyncStatusAsync()
        {
            var pendingUserChanges = await _db.Users.AsNoTracking().CountAsync(user => !user.IsSynced);
            var pendingCategories = await _db.Categories.AsNoTracking().CountAsync(category => !category.IsSynced);
            var pendingDeletedSpendings = await _db.Spending.AsNoTracking().CountAsync(spending => !spending.IsSynced && spending.IsDeleted);
            var pendingActiveSpendings = await _db.Spending.AsNoTracking().CountAsync(spending => !spending.IsSynced && !spending.IsDeleted);

            var totalPendingItems = pendingUserChanges + pendingCategories + pendingDeletedSpendings + pendingActiveSpendings;

            return new CloudSyncStatusSummary
            {
                IsComplete = totalPendingItems == 0,
                TotalPendingItems = totalPendingItems,
                PendingUserChanges = pendingUserChanges,
                PendingCategories = pendingCategories,
                PendingDeletedSpendings = pendingDeletedSpendings,
                PendingActiveSpendings = pendingActiveSpendings,
                Breakdown = BuildSyncBreakdown(pendingUserChanges, pendingCategories, pendingDeletedSpendings, pendingActiveSpendings)
            };
        }

        private static string BuildSyncBreakdown(int pendingUserChanges, int pendingCategories, int pendingDeletedSpendings, int pendingActiveSpendings)
        {
            var parts = new List<string>();

            if (pendingUserChanges > 0)
                parts.Add($"{pendingUserChanges} cambio{(pendingUserChanges == 1 ? string.Empty : "s")} de perfil");

            if (pendingActiveSpendings > 0)
                parts.Add($"{pendingActiveSpendings} gasto{(pendingActiveSpendings == 1 ? string.Empty : "s")} nuevo{(pendingActiveSpendings == 1 ? string.Empty : "s")} o editado{(pendingActiveSpendings == 1 ? string.Empty : "s")}");

            if (pendingDeletedSpendings > 0)
                parts.Add($"{pendingDeletedSpendings} elemento{(pendingDeletedSpendings == 1 ? string.Empty : "s")} eliminado{(pendingDeletedSpendings == 1 ? string.Empty : "s")}");

            if (pendingCategories > 0)
                parts.Add($"{pendingCategories} categor{(pendingCategories == 1 ? "ía" : "ías")}");

            return parts.Count > 0
                ? "Pendiente por subir: " + string.Join(", ", parts) + "."
                : string.Empty;
        }

        public async Task<User?> UpdateUserPayInfo(User user)
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
                currentUser.PercentSave = user.PercentSave;
                currentUser.IsSynced = false;

                await _db.SaveChangesAsync();

                _ = SyncUpdatedPayInfo(new UserInfoDto
                {
                    UserId = currentUser.UserId,
                    Salary = currentUser.Salary,
                    PercentSave = currentUser.PercentSave,
                    IncomeTypeId = currentUser.IncomeTypeId,
                    FirstPayDay = currentUser.FirstPayDay,
                    SecondPayDay = currentUser.SecondPayDay,
                    WeekPayDay = currentUser.WeekPayDay,
                    Name = currentUser.Name
                });

                return currentUser;
            }
            catch (Exception e)
            {
                Console.WriteLine(e);
                return null;
            }
        }

        public string GetUserId()
        {
            try
            {
                var user = _db.Users.FirstOrDefault();
                if (user == null)
                    return string.Empty;
                return user.UserId;
            }
            catch (Exception ex)
            {
                return string.Empty;
            }
        }

        public async Task<bool> SyncUpdatedPayInfo(UserInfoDto newUserInfo)
        {
            try
            {
                var token = Preferences.Get("token", string.Empty);
                var res = await _api.UpdateUserPayInfo(newUserInfo, token);

                var user = await _db.Users.FirstOrDefaultAsync(c => c.UserId == newUserInfo.UserId);
                if (user == null)
                    return false;

                user.IsSynced = res;
                await _db.SaveChangesAsync();


                return res;
            }
            catch (Exception ex)
            {
                return false;
            }
        }

        public async Task<bool> PasswordResetRequest(string email)
        {
            try
            {
                var res = await _api.PasswordResetRequest(email);
                return res;
            }
            catch (Exception e)
            {
                return false;
            }
        }

        public async Task<bool> PasswordResetVerify(string email, string code)
        {
            try
            {
                var res =  await _api.PasswordResetVerify(email, code);
                return res;
            }
            catch (Exception e)
            {
                return false;
            }
        }

        public async Task<bool> PasswordResetConfirm(string email, string code, string password)
        {
            try
            {
                var res = await _api.PasswordResetConfirm(email, code, password);
                return res;
            }
            catch (Exception e)
            {
                return false;
            }
        }

        public async Task<bool> GenerateTemporaryPassword(string email)
        {
            try
            {
                var res = await _api.GenerateTemporaryPassword(email);
                return !string.IsNullOrWhiteSpace(res.Message);
            }
            catch (Exception e)
            {
                return false;
            }
        }

    }

}
