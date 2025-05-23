using System.Security.Claims;
using System.Security.Cryptography.X509Certificates;
using Gastapp_API.Data;
using Gastapp.Models;
using Gastapp.Models.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace Gastapp_API.Controllers
{
    [Authorize]
    [Route("api/[controller]")]
    [ApiController]
    public class SpendingsController : ControllerBase
    {
        private GastappDbContext _db;

        public SpendingsController(GastappDbContext db)
        {
            _db = db;
        }

        [Authorize]
        [HttpPost("SyncNewCategories")]
        public async Task<ActionResult<bool>> SyncNewCategories(List<Category> categories)
        {
            try
            {
                var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;

                if (userId == null)
                    return Unauthorized();

                if (categories.Any(c => c.UserId != userId))
                    return BadRequest("Las categorías no pertenecen al usuario autenticado.");

                var newCategories = categories.Where(c => !c.IsSynced).ToList();
                foreach (var category in newCategories)
                {
                    category.IsSynced = true;
                }

                if (newCategories.Any())
                {
                    await _db.Categories.AddRangeAsync(newCategories);
                    await _db.SaveChangesAsync();
                }

                return Ok(true);
            }
            catch (Exception ex)
            {
                return StatusCode(500, false);
            }
        }


        [HttpPost("SyncNewSpendings")]
        public async Task<ActionResult<bool>> SyncNewSpendings(List<Spending> spendings)
        {
            try
            {
                var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
                if (userId == null)
                    return Unauthorized();
                if (spendings.Any(s => s.UserId != userId))
                    return BadRequest("Los gastos no pertenecen al usuario autenticado.");


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

        [HttpPost("SyncAllData")]
        public async Task<ActionResult<bool>> SyncAllData(SyncDataDto data)
        {
            try
            {
                var userData = data.User;
                var categories = data.Categories;
                var spendings = data.Spendings;

                if (!categories.Any() && !spendings.Any() && userData == null)
                    return BadRequest("No hay datos para sincronizar.");


                var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
                if (userId == null)
                    return Unauthorized();

                if (spendings.Any(s => s.UserId != userId))
                    return BadRequest("Los gastos no pertenecen al usuario autenticado.");

                var deletedSpendings = spendings.Where(s => s.IsDeleted && !s.IsSynced).ToList();
                var newSpendings = spendings.Where(s => !s.IsDeleted && !s.IsSynced).ToList();
                var newCategories = categories.Where(c => !c.IsSynced).ToList();

                foreach (var category in newCategories)
                {
                    var newCategory = new Category
                    {
                        CategoryId = category.CategoryId,
                        UserId = category.UserId,
                        CategoryName = category.CategoryName,
                        IsSynced = true,
                    };
                    await _db.Categories.AddAsync(newCategory);
                }


                foreach (var spending in newSpendings)
                {
                    var newSpending = new Spending
                    {
                        SpendingId = spending.SpendingId,
                        UserId = spending.UserId,
                        CategoryId = spending.CategoryId,
                        Title = spending.Title,
                        Description = spending.Description,
                        Amount = spending.Amount,
                        IsSynced = true,
                        Date = DateTime.SpecifyKind(spending.Date, DateTimeKind.Utc),
                        IsDeleted = false,
                    };

                    await _db.Spendings.AddAsync(newSpending);
                }

                foreach (var spending in deletedSpendings)
                {
                    var existingSpending = await _db.Spendings.FirstOrDefaultAsync(s =>
                        s.SpendingId == spending.SpendingId && s.UserId == userId);
                    if (existingSpending != null)
                    {
                        existingSpending.IsDeleted = true;
                    }
                }

                if (userData is { IsSynced: false })
                {
                    var dbUser = await _db.Users.FirstOrDefaultAsync(u => u.UserId == userData.UserId);
                    if (dbUser != null)
                    {
                        dbUser.Salary = userData.Salary;
                        dbUser.PercentSave = userData.PercentSave;
                        dbUser.IncomeTypeId = userData.IncomeTypeId;
                        dbUser.FirstPayDay = userData.FirstPayDay;
                        dbUser.SecondPayDay = userData.SecondPayDay;
                        dbUser.WeekPayDay = userData.WeekPayDay;
                        dbUser.IsSynced = true;
                        dbUser.Name = userData.Name;
                        dbUser.BirthDate = DateTime.SpecifyKind(userData.BirthDate, DateTimeKind.Utc);
                        _db.Users.Update(dbUser);
                    }
                }

                await _db.SaveChangesAsync();
                return true;
            }
            catch (Exception ex)
            {
                return StatusCode(500, "Ocurrió un error al sincronizar los gastos.");
            }
        }


        [HttpGet("GetIncomes")]
        public async Task<ActionResult<List<IncomeType>>> GetIncomes()
        {
            var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
            if (userId == null)
                return Unauthorized();

            var incomes = await _db.IncomeTypes.ToListAsync();
            return Ok(incomes);
        }

        [HttpPost("CreateNewSpending")]
        public async Task<ActionResult<bool>> CreateNewSpending(NewSpendingDto data)
        {
            try
            {
                var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
                if (userId == null)
                    return Unauthorized();
                if (data.Spending.UserId != userId)
                    return BadRequest("El gasto no pertenece al usuario autenticado.");

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

        [HttpPost("DeleteSpending")]
        public async Task<ActionResult<bool>> DeleteSpending(string spendingId)
        {
            try
            {
                var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
                if (userId == null)
                    return Unauthorized();

                var spending = await _db.Spendings.FirstOrDefaultAsync(s => s.SpendingId == spendingId);
                if (spending == null)
                    return NotFound("Spending not found");

                if (spending.UserId != userId)
                    return BadRequest("El gasto no pertenece al usuario autenticado.");

                spending.IsDeleted = true;


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
                var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
                if (userId == null)
                    return Unauthorized();
                if (category.UserId != userId)
                    return BadRequest("La categoría no pertenece al usuario autenticado.");

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