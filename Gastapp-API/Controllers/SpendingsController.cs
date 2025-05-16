using Gastapp_API.Data;
using Gastapp.Models;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;

namespace Gastapp_API.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    public class SpendingsController : ControllerBase
    {
        private GastappDbContext _db;

        public SpendingsController(GastappDbContext db)
        {
            _db = db;
        }

        [HttpPost("SyncNewCategories")]
        public async Task<bool> SyncNewCategories(List<Category> categories)
        {
            try
            {
                var newCategories = categories.Where(c => !c.IsSynced).ToList();
                foreach (var category in newCategories)
                {
                    category.IsSynced = true;
                }
                await _db.Categories.AddRangeAsync(newCategories);
                await _db.SaveChangesAsync();
                return true;
            }
            catch (Exception ex)
            {
                return false;
            }
        }

        [HttpPost("SyncNewSpendings")]
        public async Task<bool> SyncNewSpendings(List<Spending> spendings)
        {
            try
            {
                var newSpendings = spendings.Where(s => !s.IsSynced && !s.IsDeleted).ToList();
                foreach (var spending in newSpendings)
                {
                    spending.Date = DateTime.SpecifyKind(spending.Date, DateTimeKind.Utc);
                    spending.IsSynced = true;
                }
                await _db.Spendings.AddRangeAsync(newSpendings);

                await _db.SaveChangesAsync();


                return true;
            }
            catch (Exception ex)
            {
                return false;
            }
        }

        [HttpPost("SyncDeletedSpendings")]
        public async Task<bool> SyncDeletedSpendings(List<Spending> spendings)
        {
            try
            {
                var deletedSpendings = spendings.Where(s => !s.IsSynced && s.IsDeleted).ToList();
                _db.Spendings.RemoveRange(deletedSpendings);

                await _db.SaveChangesAsync();

                return true;
            }
            catch (Exception ex)
            {
                return false;
            }
        }
    }
}