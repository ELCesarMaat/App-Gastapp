using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Gastapp.Data;
using Gastapp.Models;
using Microsoft.EntityFrameworkCore;

namespace Gastapp.Services.SpendingService
{
    public class SpendingService(GastappDbContext db) : ISpendingService
    {
        private readonly GastappDbContext _db = db;

        public async Task<List<Spending>> GetSpendingListByDateAsync(DateTime date)
        {
            return await _db.Spending
                .Include(s => s.Category)
                .Where(s => s.Date.Date == date.Date)
                .OrderBy(s => s.Date)
                .ToListAsync();
        }

        public async Task<decimal> GetTotalAmountByPeriod(DateTime? start, DateTime? end)
        {
            if(start == null || end == null)
                return 0;
            return await _db.Spending
                .Include(s => s.Category)
                .Where(s => s.Date >= start && s.Date <= end)
                .SumAsync(s => s.Amount);
        }

        public async Task<List<Spending>> GetSpendingListByCategoryAndDate(int categoryId, DateTime date)
        {
            return await _db.Spending
                .Where(s => s.Date.Date == date.Date && s.CategoryId == categoryId)
                .OrderBy(s => s.Date)
                .ToListAsync();
        }

        public async Task<Spending> GetSpendingByIdAsync(int spendingId)
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
                return true;
            }
            catch (Exception ex)
            {
                Console.Write(ex.Message);
                return false;
            }
        }

        public async Task<bool> RemoveSpendingById(int spendingId)
        {
            try
            {
                var spending = await _db.Spending.FirstAsync(s => s.SpendingId == spendingId);
                _db.Spending.Remove(spending);
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
    }
}