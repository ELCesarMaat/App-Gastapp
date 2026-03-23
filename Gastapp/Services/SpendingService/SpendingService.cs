using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Gastapp.Data;
using Gastapp.Models;
using Gastapp.Models.Models;
using Gastapp.Services.ApiService;
using Microsoft.EntityFrameworkCore;
using System.Globalization;

namespace Gastapp.Services.SpendingService
{
    public class SpendingService(GastappDbContext db, IApiService api) : ISpendingService
    {
        private readonly GastappDbContext _db = db;
        private readonly IApiService _api = api;

        private async Task<Category?> EnsureDefaultCategoryForUser(string userId)
        {
            if (string.IsNullOrWhiteSpace(userId))
                return null;

            var userCategories = await _db.Categories
                .Where(c => c.UserId == userId)
                .ToListAsync();

            var defaultCategory = userCategories.FirstOrDefault(c => IsDefaultCategoryName(c.CategoryName));
            if (defaultCategory != null)
                return defaultCategory;

            defaultCategory = new Category
            {
                CategoryName = "SIN CATEGORIA",
                UserId = userId,
                IsSynced = false
            };

            await _db.Categories.AddAsync(defaultCategory);
            await _db.SaveChangesAsync();

            return defaultCategory;
        }

        private static bool IsDefaultCategoryName(string? categoryName)
        {
            if (string.IsNullOrWhiteSpace(categoryName))
                return false;

            var normalized = categoryName.Normalize(NormalizationForm.FormD);
            var sb = new StringBuilder();
            foreach (var c in normalized)
            {
                if (CharUnicodeInfo.GetUnicodeCategory(c) != UnicodeCategory.NonSpacingMark)
                    sb.Append(c);
            }

            var plain = sb.ToString().Normalize(NormalizationForm.FormC).Trim().ToUpperInvariant();
            return plain == "SIN CATEGORIA";
        }

        public async Task<List<Spending>> GetSpendingListByDateAsync(DateTime date)
        {
            return await _db.Spending
                .Include(s => s.Category)
                .Where(s => s.Date.Date == date.Date && !s.IsDeleted)
                .OrderBy(s => s.Date)
                .ToListAsync();
        }

        public async Task<decimal> GetTotalAmountByPeriod(DateTime? start, DateTime? end)
        {
            if (start == null || end == null)
                return 0;
            return await _db.Spending
                .Include(s => s.Category)
                .Where(s => s.Date >= start && s.Date <= end && !s.IsDeleted)
                .SumAsync(s => s.Amount);
        }

        public async Task<List<Spending>> GetSpendingListByCategoryAndDate(string categoryId, DateTime date)
        {
            return await _db.Spending
                .Where(s => s.Date.Date == date.Date && s.CategoryId == categoryId && !s.IsDeleted)
                .OrderBy(s => s.Date)
                .ToListAsync();
        }

        public async Task<Spending?> GetSpendingByIdAsync(string spendingId)
        {
            return await _db.Spending
                .Include(s => s.Category)
            .FirstOrDefaultAsync(s => s.SpendingId == spendingId);
        }

        public async Task<bool> CreateNewSpending(Spending spending)
        {
            try
            {
                await _db.Spending.AddAsync(spending);
                await _db.SaveChangesAsync();
                var category = await _db.Categories.FirstOrDefaultAsync(c => c.CategoryId == spending.CategoryId);

                var newSpendingDto = new NewSpendingDto
                {
                    Spending = new SpendingDto
                    {
                        Amount = spending.Amount,
                        CategoryId = spending.CategoryId,
                        Date = spending.Date,
                        Description = spending.Description,
                        SpendingId = spending.SpendingId,
                        Title = spending.Title,
                        UserId = spending.UserId,
                        IsDeleted = spending.IsDeleted,
                        IsSynced = spending.IsSynced
                    },
                    Category = new CategoryDto
                    {
                        CategoryId = category.CategoryId,
                        CategoryName = category.CategoryName,
                        IsSynced = category.IsSynced,
                        UserId = category.UserId
                    }
                };
                _ = SyncNewSpending(newSpendingDto);
                return true;
            }
            catch (Exception ex)
            {
                Console.Write(ex.Message);
                return false;
            }
        }

        public async Task<bool> RemoveSpendingById(string spendingId)
        {
            try
            {
                var spending = await _db.Spending.FirstAsync(s => s.SpendingId == spendingId);
                spending.IsDeleted = true;

                _ = SyncDeleteSpending(spendingId);
                await _db.SaveChangesAsync();
                return true;
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex.Message);
                return false;
            }
        }


        public async Task<List<DateTime>> GetDaysWithSpendings()
        {
            return await _db.Spending
                .Where(s => !s.IsDeleted)
                .Select(s => s.Date.Date)
                .Distinct()
                .OrderByDescending(d => d)
                .ToListAsync();
        }


        public async Task<List<Category>> GetCategoriesList()
        {
            var userId = await _db.Users
                .Select(u => u.UserId)
                .FirstOrDefaultAsync();

            if (!string.IsNullOrWhiteSpace(userId))
            {
                await EnsureDefaultCategoryForUser(userId);
            }

            return await _db.Categories
                .OrderBy(c => c.CategoryId)
                .ToListAsync();
        }

        public async Task<List<CategoryResume>> GetCategoryResumeByDay(DateTime day)
        {
            var result = await _db.Spending
                .Include(s => s.Category)
                .Where(s => s.Date >= day.Date && s.Date < day.Date.AddDays(1) && !s.IsDeleted)
                .GroupBy(s => s.Category.CategoryName)
                .Select(g => new CategoryResume()
                {
                    Name = $"{g.Key}  ${g.Sum(s => s.Amount):N2}",
                    Amount = g.Sum(s => s.Amount)
                })
                .ToListAsync();
            return result;
        }


        public async Task<Category> CreateNewCategory(Category category)
        {
            try
            {
                await _db.Categories.AddAsync(category);
                await _db.SaveChangesAsync();

                _ = SyncNewCategory(new CategoryDto
                {
                    CategoryName = category.CategoryName,
                    CategoryId = category.CategoryId,
                    UserId = category.UserId
                });

                return category;
            }
            catch (Exception ex)
            {
                return new Category();
            }
        }

        public async Task<List<CategoryResume>> GetCategoryResumeByPeriod(DateTime firstDay, DateTime lastDay)
        {
            var firstDateDate = firstDay.Date;
            var lastDateDate = lastDay.Date.AddDays(1);
            var result = await _db.Spending
                .Include(s => s.Category)
                .Where(s => s.Date >= firstDateDate && s.Date < lastDateDate && !s.IsDeleted)
                .GroupBy(s => s.Category.CategoryName)
                .Select(g => new CategoryResume()
                {
                    Name = $"{g.Key}  ${g.Sum(s => s.Amount):N2}",
                    Amount = g.Sum(s => s.Amount)
                })
                .ToListAsync();
            return result;
        }

        public async Task<bool> SyncNewCategory(CategoryDto category)
        {
            try
            {
                var token = Preferences.Get("token", string.Empty);
                var res = await api.CreateNewCategory(category, token);
                if (res)
                {
                    var item = await _db.Categories.FirstOrDefaultAsync(c => c.CategoryId == category.CategoryId);
                    if (item == null)
                        return false;
                    item.IsSynced = true;
                    await _db.SaveChangesAsync();
                    return true;
                }

                return res;
            }
            catch (Exception ex)
            {
                return false;
            }
        }

        private async Task<bool> SyncDeleteSpending(string spendingId)
        {
            try
            {
                var token = Preferences.Get("token", string.Empty);
                var res = await api.DeleteSpending(spendingId, token);

                var item = await _db.Spending.FirstOrDefaultAsync(c => c.SpendingId == spendingId);
                if (item == null)
                    return false;
                item.IsSynced = res;
                await _db.SaveChangesAsync();

                return res;
            }
            catch
            {
                return false;
            }
        }


        public async Task<bool> SyncNewSpending(NewSpendingDto spending)
        {
            try
            {
                var token = Preferences.Get("token", string.Empty);
                var res = await api.CreateNewSpending(spending, token);

                var item = await _db.Spending.FirstOrDefaultAsync(c =>
                    c.SpendingId == spending.Spending.SpendingId);
                if (item == null)
                    return false;

                item.IsSynced = res;
                await _db.SaveChangesAsync();

                return res;
            }
            catch (Exception ex)
            {
                return false;
            }
        }

        public async Task<bool> RemoveCategoryById(string categoryId)
        {
            try
            {
                var category = await _db.Categories.FirstOrDefaultAsync(c => c.CategoryId == categoryId);
                if (category == null)
                    return false;

                if (IsDefaultCategoryName(category.CategoryName))
                    return false;

                var sinCategoria = await EnsureDefaultCategoryForUser(category.UserId);

                if (sinCategoria != null)
                {
                    var spendings = await _db.Spending
                        .Where(s => s.CategoryId == categoryId)
                        .ToListAsync();

                    foreach (var spending in spendings)
                        spending.CategoryId = sinCategoria.CategoryId;
                }

                _db.Categories.Remove(category);
                await _db.SaveChangesAsync();
                _ = SyncDeleteCategory(categoryId);
                return true;
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex.Message);
                return false;
            }
        }

        private async Task<bool> SyncDeleteCategory(string categoryId)
        {
            try
            {
                var token = Preferences.Get("token", string.Empty);
                await api.DeleteCategory(categoryId, token);
                return true;
            }
            catch
            {
                return false;
            }
        }

        public async Task<bool> UpdateSpending(Spending spending)
        {
            try
            {
                var existing = await _db.Spending.FirstAsync(s => s.SpendingId == spending.SpendingId);
                existing.Title = spending.Title;
                existing.Description = spending.Description;
                existing.Amount = spending.Amount;
                existing.CategoryId = spending.CategoryId;
                existing.Date = spending.Date;
                existing.IsSynced = false;
                await _db.SaveChangesAsync();

                _ = SyncUpdateSpending(new SpendingDto
                {
                    SpendingId = spending.SpendingId,
                    UserId = spending.UserId,
                    CategoryId = spending.CategoryId,
                    Title = spending.Title,
                    Description = spending.Description,
                    Amount = spending.Amount,
                    Date = spending.Date,
                    IsSynced = false,
                    IsDeleted = false
                });
                return true;
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex.Message);
                return false;
            }
        }

        public async Task<int> CountActiveSpendingsByCategory(string categoryId)
        {
            return await _db.Spending
                .Where(s => s.CategoryId == categoryId && !s.IsDeleted)
                .CountAsync();
        }

        private async Task<bool> SyncUpdateSpending(SpendingDto dto)
        {
            try
            {
                var token = Preferences.Get("token", string.Empty);
                var res = await api.UpdateSpending(dto, token);

                var item = await _db.Spending.FirstOrDefaultAsync(c => c.SpendingId == dto.SpendingId);
                if (item == null)
                    return false;

                item.IsSynced = res;
                await _db.SaveChangesAsync();
                return res;
            }
            catch
            {
                return false;
            }
        }
    }
}