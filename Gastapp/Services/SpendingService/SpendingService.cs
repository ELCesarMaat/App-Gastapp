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

namespace Gastapp.Services.SpendingService
{
    public class SpendingService(GastappDbContext db, IApiService api) : ISpendingService
    {
        private readonly GastappDbContext _db = db;
        private readonly IApiService _api = api;

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

        public async Task<Spending> GetSpendingByIdAsync(string spendingId)
        {
            return await _db.Spending
                .Include(s => s.Category)
                .FirstAsync(s => s.SpendingId == spendingId);
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
            var listDates = new List<DateTime>();

            var user = await _db.Users.Include(u => u.IncomeType).FirstOrDefaultAsync();
            if (user == null || user?.IncomeType == null)
                return listDates;

            int firstDay = user.FirstPayDay ?? 1;
            int endDay = user.SecondPayDay ?? 1;

            DateTime today = DateTime.Today;

            switch (user.IncomeType.IncomeTypeId)
            {
                case 1:
                {
                    int todayIndex = (int)today.DayOfWeek;

                    int startOffset;

                    if (todayIndex >= firstDay)
                    {
                        startOffset = todayIndex - firstDay;
                    }
                    else
                    {
                        startOffset = 7 - (firstDay - todayIndex);
                    }

                    DateTime startDate = today.AddDays(-startOffset);
                    DateTime endDate = today;

                    for (DateTime d = endDate; d >= startDate; d = d.AddDays(-1))
                        listDates.Add(d);
                    break;
                }


                case 2:
                {
                    int year = today.Year;
                    int month = today.Month;

                    // Aseguramos que los “días de pago” estén dentro del rango válido del mes
                    int safeFirstDay = Math.Clamp(firstDay, 1, DateTime.DaysInMonth(year, month));
                    int safeSecondDay = Math.Clamp(endDay, 1, DateTime.DaysInMonth(year, month));

                    // Determinar cuál fue el último día de pago
                    DateTime lastPay;
                    if (today.Day >= safeSecondDay)
                    {
                        // Ya pasó (o es) el segundo día de pago de este mes
                        lastPay = new DateTime(year, month, safeSecondDay);
                    }
                    else if (today.Day >= safeFirstDay)
                    {
                        // Ya pasó (o es) el primer día de pago de este mes
                        lastPay = new DateTime(year, month, safeFirstDay);
                    }
                    else
                    {
                        // Antes del primer día de pago: elige el segundo día de pago del mes anterior
                        var prev = today.AddMonths(-1);
                        int prevSafeSecond = Math.Clamp(endDay, 1, DateTime.DaysInMonth(prev.Year, prev.Month));
                        lastPay = new DateTime(prev.Year, prev.Month, prevSafeSecond);
                    }

                    // Generar la lista desde HOY hasta el último día de pago, decreciendo
                    for (var d = today.Date; d >= lastPay.Date; d = d.AddDays(-1))
                        listDates.Add(d);

                    break;
                }


                case 3:
                {
                    if (firstDay <= today.Day)
                    {
                        // Dentro del ciclo actual (este mes)
                        var start = new DateTime(today.Year, today.Month, firstDay);
                        for (DateTime d = today; d >= start; d = d.AddDays(-1))
                            listDates.Add(d);
                    }
                    else
                    {
                        // El ciclo comenzó el mes anterior
                        var prevMonth = today.AddMonths(-1);
                        int safeDay = Math.Clamp(firstDay, 1, DateTime.DaysInMonth(prevMonth.Year, prevMonth.Month));
                        var start = new DateTime(prevMonth.Year, prevMonth.Month, safeDay);
                        for (DateTime d = today; d >= start; d = d.AddDays(-1))
                            listDates.Add(d);
                    }

                    break;
                }
            }

            return listDates;
        }


        public async Task<List<Category>> GetCategoriesList()
        {
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

        public async Task<bool> SyncNewSpending(NewSpendingDto spending)
        {
            try
            {
                var token = Preferences.Get("token", string.Empty);
                var res = await api.CreateNewSpending(spending, token);
                if (res)
                {
                    var item = await _db.Spending.FirstOrDefaultAsync(c =>
                        c.SpendingId == spending.Spending.SpendingId);
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
    }
}