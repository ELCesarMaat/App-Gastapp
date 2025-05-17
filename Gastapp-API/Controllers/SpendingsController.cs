using System.Security.Cryptography.X509Certificates;
using Gastapp_API.Data;
using Gastapp.Models;
using Gastapp.Models.Models;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

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

        [HttpGet("GetIncomes")]
        public async Task<ActionResult<List<IncomeType>>> GetIncomes()
        {
            var incomes = await _db.IncomeTypes.ToListAsync();
            return Ok(incomes);
        }

        [HttpPost("CreateNewSpending")]
        public async Task<ActionResult<bool>> CreateNewSpending(NewSpendingDto data)
        {
            try
            {
                var spending = data.Spending;
                var category = data.Category;

                var user = await _db.Users.FirstOrDefaultAsync(u => u.UserId == spending.UserId);
                if (user == null)
                    return NotFound("User not found");

                var categoryExists = await _db.Categories
                    .AnyAsync(c => c.CategoryId == spending.CategoryId && c.UserId == spending.UserId);

                if (!categoryExists)
                {
                    var newCategory = new Category
                    {
                        CategoryId = category.CategoryId,
                        CategoryName = category.CategoryName,
                        UserId = spending.UserId,
                        IsSynced = true
                    };

                    await _db.Categories.AddAsync(newCategory);
                    await _db.SaveChangesAsync();
                }

                var newSpending = new Spending
                {
                    SpendingId = spending.SpendingId,
                    UserId = spending.UserId,
                    CategoryId = spending.CategoryId,
                    Title = spending.Title,
                    Description = spending.Description,
                    Amount = spending.Amount,
                    IsSynced = true,
                    Date = DateTime.SpecifyKind(spending.Date, DateTimeKind.Utc)
                };

                await _db.Spendings.AddAsync(newSpending);
                await _db.SaveChangesAsync();

                return true;
            }
            catch (Exception ex)
            {
                return StatusCode(500, "An error occurred while saving the spending.");
            }
        }

        [HttpPost("CreateNewCategory")]
        public async Task<ActionResult<bool>> CreateNewCategory(CategoryDto category)
        {
            try
            {
                var user = await _db.Users.FirstOrDefaultAsync(s => s.UserId == category.UserId);
                if (user == null)
                    return NotFound("User not found");
                category.IsSynced = true;

                await _db.Categories.AddAsync(new Category
                {
                    CategoryId = category.CategoryId,
                    UserId = category.UserId,
                    CategoryName = category.CategoryName,
                    IsSynced = category.IsSynced
                });
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