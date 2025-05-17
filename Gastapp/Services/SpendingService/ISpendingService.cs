using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Gastapp.Models;


namespace Gastapp.Services.SpendingService
{
    public interface ISpendingService
    {

        public Task<List<DateTime>> GetDaysWithSpendings();
        public Task<List<Spending>> GetSpendingListByDateAsync(DateTime date);
        public Task<List<Spending>> GetSpendingListByCategoryAndDate(string categoryId, DateTime date);
        public Task<Spending> GetSpendingByIdAsync(string spendingId);
        public Task<bool> CreateNewSpending(Spending spending);
        public Task<bool> RemoveSpendingById(string spendingId);
        public Task<List<Category>> GetCategoriesList();
        public Task<decimal> GetTotalAmountByPeriod(DateTime? start, DateTime? end);
        public Task<List<CategoryResume>> GetCategoryResumeByDay(DateTime day);
        public Task<Category> CreateNewCategory(Category category);
        public Task<List<CategoryResume>> GetCategoryResumeByPeriod(DateTime firstDay, DateTime lastDay);

    }
}