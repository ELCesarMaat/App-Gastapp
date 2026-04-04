using System.Security.Claims;
using System.Security.Cryptography.X509Certificates;
using System.Text.Json;
using Gastapp_API.Data;
using Gastapp.Models;
using Gastapp.Models.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;
using Npgsql;

namespace Gastapp_API.Controllers
{
    [Authorize]
    [Route("api/[controller]")]
    [ApiController]
    public class SpendingsController : ControllerBase
    {
        private readonly GastappDbContext _db;
        private readonly ILogger<SpendingsController> _logger;

        public SpendingsController(GastappDbContext db, ILogger<SpendingsController> logger)
        {
            _db = db;
            _logger = logger;
        }

        private void LogEndpointError(Exception ex, string endpoint, object? payload = null)
        {
            var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value ?? "anonymous";
            var payloadText = SerializePayload(payload);

            if (ex.GetBaseException() is PostgresException postgresException)
            {
                _logger.LogError(
                    ex,
                    "Database error in {Endpoint}. UserId: {UserId}. SqlState: {SqlState}. Constraint: {Constraint}. Payload: {@Payload}",
                    endpoint,
                    userId,
                    postgresException.SqlState,
                    postgresException.ConstraintName,
                    payload);

                Console.Error.WriteLine(
                    $"[GastappAPI][DB_ERROR] Endpoint: {endpoint} UserId: {userId} SqlState: {postgresException.SqlState} Constraint: {postgresException.ConstraintName} Payload: {payloadText}{Environment.NewLine}{ex}");
                return;
            }

            _logger.LogError(
                ex,
                "Error in {Endpoint}. UserId: {UserId}. Payload: {@Payload}",
                endpoint,
                userId,
                payload);

            Console.Error.WriteLine(
                $"[GastappAPI][ERROR] Endpoint: {endpoint} UserId: {userId} Payload: {payloadText}{Environment.NewLine}{ex}");
        }

        private static string SerializePayload(object? payload)
        {
            if (payload is null)
                return "null";

            try
            {
                return JsonSerializer.Serialize(payload);
            }
            catch
            {
                return payload.ToString() ?? "null";
            }
        }

        private static DateTime NormalizeIncomingSpendingDate(DateTime date)
        {
            if (date.Kind == DateTimeKind.Utc)
                return date;

            if (date.Kind == DateTimeKind.Local)
                return date.ToUniversalTime();

            return DateTime.SpecifyKind(date, DateTimeKind.Utc);
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
                    category.IsDefaultCategory = false;
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
                LogEndpointError(ex, nameof(SyncNewCategories), new
                {
                    Categories = categories?.Select(c => new
                    {
                        c.CategoryId,
                        c.UserId,
                        c.CategoryName,
                        c.IsDefaultCategory,
                        c.IsSynced
                    }).ToList()
                });
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
                    spending.Date = NormalizeIncomingSpendingDate(spending.Date);
                    spending.Description = NormalizeDescription(spending.Description);
                    spending.IsSynced = true;
                }

                await _db.Spendings.AddRangeAsync(newSpendings);

                await _db.SaveChangesAsync();


                return true;
            }
            catch (Exception ex)
            {
                LogEndpointError(ex, nameof(SyncNewSpendings), new
                {
                    Spendings = spendings?.Select(s => new
                    {
                        s.SpendingId,
                        s.UserId,
                        s.CategoryId,
                        s.Title,
                        s.Description,
                        s.Amount,
                        s.Date,
                        s.IsSynced,
                        s.IsDeleted
                    }).ToList()
                });
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
                        IsDefaultCategory = false,
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
                        Description = NormalizeDescription(spending.Description),
                        Amount = spending.Amount,
                        IsSynced = true,
                        Date = NormalizeIncomingSpendingDate(spending.Date),
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
                LogEndpointError(ex, nameof(SyncAllData), new
                {
                    User = data?.User is null
                        ? null
                        : new
                        {
                            data.User.UserId,
                            data.User.Name,
                            data.User.Salary,
                            data.User.PercentSave,
                            data.User.IncomeTypeId,
                            data.User.IsSynced
                        },
                    Categories = data?.Categories?.Select(c => new
                    {
                        c.CategoryId,
                        c.UserId,
                        c.CategoryName,
                        c.IsDefaultCategory,
                        c.IsSynced
                    }).ToList(),
                    Spendings = data?.Spendings?.Select(s => new
                    {
                        s.SpendingId,
                        s.UserId,
                        s.CategoryId,
                        s.Title,
                        s.Description,
                        s.Amount,
                        s.Date,
                        s.IsSynced,
                        s.IsDeleted
                    }).ToList()
                });
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
                        IsDefaultCategory = false,
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
                    Description = NormalizeDescription(spending.Description),
                    Amount = spending.Amount,
                    IsSynced = true,
                    Date = NormalizeIncomingSpendingDate(spending.Date)
                };

                await _db.Spendings.AddAsync(newSpending);
                await _db.SaveChangesAsync();

                return true;
            }
            catch (Exception ex)
            {
                LogEndpointError(ex, nameof(CreateNewSpending), new
                {
                    Spending = data?.Spending is null
                        ? null
                        : new
                        {
                            data.Spending.SpendingId,
                            data.Spending.UserId,
                            data.Spending.CategoryId,
                            data.Spending.Title,
                            data.Spending.Description,
                            data.Spending.Amount,
                            data.Spending.Date,
                            data.Spending.IsSynced,
                            data.Spending.IsDeleted
                        },
                    Category = data?.Category is null
                        ? null
                        : new
                        {
                            data.Category.CategoryId,
                            data.Category.UserId,
                            data.Category.CategoryName,
                            data.Category.IsDefaultCategory,
                            data.Category.IsSynced
                        }
                });
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
                LogEndpointError(ex, nameof(DeleteSpending), new { spendingId });
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
                category.IsDefaultCategory = false;
                category.IsSynced = true;

                await _db.Categories.AddAsync(new Category
                {
                    CategoryId = category.CategoryId,
                    UserId = category.UserId,
                    CategoryName = category.CategoryName,
                    IsDefaultCategory = category.IsDefaultCategory,
                    IsSynced = category.IsSynced
                });
                await _db.SaveChangesAsync();
                return true;
            }
            catch (Exception ex)
            {
                LogEndpointError(ex, nameof(CreateNewCategory), new
                {
                    category?.CategoryId,
                    category?.UserId,
                    category?.CategoryName,
                    category?.IsDefaultCategory,
                    category?.IsSynced
                });
                return false;
            }
        }

        [HttpPost("DeleteCategory")]
        public async Task<ActionResult<bool>> DeleteCategory(string categoryId)
        {
            try
            {
                var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
                if (userId == null)
                    return Unauthorized();

                var category = await _db.Categories.FirstOrDefaultAsync(c => c.CategoryId == categoryId);
                if (category == null)
                    return NotFound("Categoría no encontrada.");

                if (category.UserId != userId)
                    return BadRequest("La categoría no pertenece al usuario autenticado.");

                if (category.IsDefaultCategory)
                    return BadRequest("No puedes eliminar la categoría predeterminada.");

                var sinCategoria = await EnsureDefaultCategoryForUser(userId);

                if (sinCategoria != null)
                {
                    var spendings = await _db.Spendings
                        .Where(s => s.CategoryId == categoryId && s.UserId == userId)
                        .ToListAsync();

                    foreach (var spending in spendings)
                        spending.CategoryId = sinCategoria.CategoryId;
                }

                _db.Categories.Remove(category);
                await _db.SaveChangesAsync();
                return true;
            }
            catch (Exception ex)
            {
                LogEndpointError(ex, nameof(DeleteCategory), new { categoryId });
                return StatusCode(500, "An error occurred while deleting the category.");
            }
        }

        [HttpPost("UpdateCategory")]
        public async Task<ActionResult<bool>> UpdateCategory(CategoryDto data)
        {
            try
            {
                var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
                if (userId == null)
                    return Unauthorized();

                if (data.UserId != userId)
                    return BadRequest("La categoría no pertenece al usuario autenticado.");

                var category = await _db.Categories
                    .FirstOrDefaultAsync(c => c.CategoryId == data.CategoryId && c.UserId == userId);

                if (category == null)
                    return NotFound("Categoría no encontrada.");

                if (category.IsDefaultCategory)
                    return BadRequest("No puedes editar la categoría predeterminada.");

                category.CategoryName = data.CategoryName;
                category.IsSynced = true;

                await _db.SaveChangesAsync();
                return true;
            }
            catch (Exception ex)
            {
                LogEndpointError(ex, nameof(UpdateCategory), new
                {
                    data?.CategoryId,
                    data?.UserId,
                    data?.CategoryName,
                    data?.IsDefaultCategory,
                    data?.IsSynced
                });
                return StatusCode(500, "An error occurred while updating the category.");
            }
        }

        private async Task<Category> EnsureDefaultCategoryForUser(string userId)
        {
            var defaultCategory = await _db.Categories
                .FirstOrDefaultAsync(c => c.UserId == userId && c.IsDefaultCategory);

            if (defaultCategory != null)
                return defaultCategory;

            defaultCategory = await _db.Categories
                .FirstOrDefaultAsync(c => c.UserId == userId && IsLegacyDefaultCategoryName(c.CategoryName));

            if (defaultCategory != null)
            {
                defaultCategory.IsDefaultCategory = true;
                if (!string.Equals(defaultCategory.CategoryName, "Sin categoria", StringComparison.Ordinal))
                    defaultCategory.CategoryName = "Sin categoria";

                await _db.SaveChangesAsync();
                return defaultCategory;
            }

            defaultCategory = new Category
            {
                CategoryName = "Sin categoria",
                UserId = userId,
                IsDefaultCategory = true,
                IsSynced = true,
            };

            await _db.Categories.AddAsync(defaultCategory);
            await _db.SaveChangesAsync();
            return defaultCategory;
        }

        private static bool IsLegacyDefaultCategoryName(string? categoryName)
        {
            return string.Equals(categoryName, "SIN CATEGORIA", StringComparison.OrdinalIgnoreCase)
                || string.Equals(categoryName, "SIN CATEGORÍA", StringComparison.OrdinalIgnoreCase)
                || string.Equals(categoryName, "Sin categoria", StringComparison.OrdinalIgnoreCase)
                || string.Equals(categoryName, "Sin categoría", StringComparison.OrdinalIgnoreCase);
        }

        private static string NormalizeDescription(string? description)
        {
            if (string.IsNullOrWhiteSpace(description))
                return string.Empty;

            var normalized = description.Trim();
            return string.Equals(normalized, "*SIN DESCRIPCION*", StringComparison.OrdinalIgnoreCase)
                ? string.Empty
                : normalized;
        }

        [HttpPost("UpdateSpending")]
        public async Task<ActionResult<bool>> UpdateSpending(SpendingDto data)
        {
            try
            {
                var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
                if (userId == null)
                    return Unauthorized();

                var spending = await _db.Spendings.FirstOrDefaultAsync(s => s.SpendingId == data.SpendingId);
                if (spending == null)
                    return NotFound("Gasto no encontrado.");

                if (spending.UserId != userId)
                    return BadRequest("El gasto no pertenece al usuario autenticado.");

                spending.Title = data.Title;
                spending.Description = NormalizeDescription(data.Description);
                spending.Amount = data.Amount;
                spending.CategoryId = data.CategoryId;
                spending.Date = NormalizeIncomingSpendingDate(data.Date);
                spending.IsSynced = true;

                await _db.SaveChangesAsync();
                return true;
            }
            catch (Exception ex)
            {
                LogEndpointError(ex, nameof(UpdateSpending), new
                {
                    data?.SpendingId,
                    data?.UserId,
                    data?.CategoryId,
                    data?.Title,
                    data?.Description,
                    data?.Amount,
                    data?.Date,
                    data?.IsSynced,
                    data?.IsDeleted
                });
                return StatusCode(500, "An error occurred while updating the spending.");
            }
        }
    }
}
