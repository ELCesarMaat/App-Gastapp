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
                .Where(s => s.Date.Date == date.Date)
                .OrderBy(s => s.Date)
                .ToListAsync();
        }

        public async Task<List<Spending>> GetSpendingListByCategoryAndDate(int categoryId, DateTime date)
        {
            //return await _db.Spending
            //    .Where(s => s.Date.Date == date.Date && s.CategoryId == categoryId)
            //    .OrderBy(s => s.Date)
            //    .ToListAsync();
            return null;
        }

        public async Task<Spending> GetSpendingByIdAsync(int spendingId)
        {
            return await _db.Spending.FirstAsync(s => s.SpendingId == spendingId);
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
            catch(Exception ex)
            {
                Console.WriteLine(ex.Message);
                return false;
            }
        }

        public async Task<List<DateTime>> GetDaysWithSpendings()
        {
            var listDates = new List<DateTime>();
            var today = DateTime.Now;
            listDates.Add(today);
            for (var i = 1; i < 30; i++)
            {
                listDates.Add(today.AddDays(-i));
            }
            return listDates;
        }
    }
}