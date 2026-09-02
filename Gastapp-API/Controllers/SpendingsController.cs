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

        // Mantiene DeletedAt coherente con IsDeleted en todos los puntos de sincronizacion.
        // Conserva la marca original si ya existe (para que reenviar el borrado no reinicie
        // el conteo de dias), acepta la del cliente la primera vez, y cae a la hora del
        // servidor si el cliente es viejo y no manda el campo. Si el registro se restaura,
        // la limpia.
        private static DateTime? ResolveDeletedAt(bool isDeleted, DateTime? current, DateTime? incoming)
        {
            if (!isDeleted)
                return null;

            return NormalizeIncomingSpendingDate(current ?? incoming ?? DateTime.UtcNow);
        }

        // Copia los campos editables del gasto. Centralizado para que al agregar
        // un campo nuevo al modelo no se olvide en alguno de los puntos de sincronizacion.
        private static void ApplySpendingFields(Spending target, SpendingDto source)
        {
            target.CategoryId = source.CategoryId;
            target.Title = source.Title;
            target.Description = NormalizeDescription(source.Description);
            target.Amount = source.Amount;
            target.Date = NormalizeIncomingSpendingDate(source.Date);
            target.IsCreditCard = source.IsCreditCard;
            target.CreditCardId = source.CreditCardId;
            target.PaymentMethod = source.PaymentMethod;
            target.IsMsi = source.IsMsi;
            target.TotalInstallments = source.TotalInstallments;
            target.CurrentInstallment = source.CurrentInstallment;
            target.ParentSpendingId = source.ParentSpendingId;
            target.InstallmentMonthlyAmount = source.InstallmentMonthlyAmount;
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

                var pendingCategories = categories.Where(c => !c.IsSynced).ToList();
                if (!pendingCategories.Any())
                    return Ok(true);

                var categoryIds = pendingCategories
                    .Where(c => !string.IsNullOrWhiteSpace(c.CategoryId))
                    .Select(c => c.CategoryId)
                    .Distinct()
                    .ToList();

                var existingCategories = categoryIds.Any()
                    ? await _db.Categories
                        .Where(c => c.UserId == userId && categoryIds.Contains(c.CategoryId))
                        .ToDictionaryAsync(c => c.CategoryId)
                    : new Dictionary<string, Category>();

                foreach (var category in pendingCategories)
                {
                    if (existingCategories.TryGetValue(category.CategoryId, out var existingCategory))
                    {
                        existingCategory.CategoryName = category.CategoryName;
                        if (!existingCategory.IsDefaultCategory)
                            existingCategory.IsDefaultCategory = category.IsDefaultCategory;
                        existingCategory.IsSynced = true;
                        continue;
                    }

                    await _db.Categories.AddAsync(new Category
                    {
                        CategoryId = category.CategoryId,
                        UserId = category.UserId,
                        CategoryName = category.CategoryName,
                        IsDefaultCategory = category.IsDefaultCategory,
                        IsSynced = true,
                    });
                }

                await _db.SaveChangesAsync();
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
        public async Task<ActionResult<bool>> SyncNewSpendings(List<SpendingDto> spendings)
        {
            try
            {
                var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
                if (userId == null)
                    return Unauthorized();
                if (spendings.Any(s => s.UserId != userId))
                    return BadRequest("Los gastos no pertenecen al usuario autenticado.");

                var pendingSpendings = spendings.Where(s => !s.IsSynced).ToList();
                if (!pendingSpendings.Any())
                    return Ok(true);

                var spendingIds = pendingSpendings
                    .Where(s => !string.IsNullOrWhiteSpace(s.SpendingId))
                    .Select(s => s.SpendingId)
                    .Distinct()
                    .ToList();

                var existingSpendings = spendingIds.Any()
                    ? await _db.Spendings
                        .Where(s => s.UserId == userId && spendingIds.Contains(s.SpendingId))
                        .ToDictionaryAsync(s => s.SpendingId)
                    : new Dictionary<string, Spending>();

                foreach (var spending in pendingSpendings)
                {
                    if (existingSpendings.TryGetValue(spending.SpendingId, out var existingSpending))
                    {
                        ApplySpendingFields(existingSpending, spending);
                        existingSpending.IsDeleted = spending.IsDeleted;
                        existingSpending.DeletedAt = ResolveDeletedAt(spending.IsDeleted, existingSpending.DeletedAt, spending.DeletedAt);
                        existingSpending.IsSynced = true;
                        continue;
                    }

                    if (spending.IsDeleted)
                        continue;

                    var newSpending = new Spending
                    {
                        SpendingId = spending.SpendingId,
                        UserId = spending.UserId,
                        IsSynced = true,
                        IsDeleted = false
                    };
                    ApplySpendingFields(newSpending, spending);
                    await _db.Spendings.AddAsync(newSpending);
                }

                await _db.SaveChangesAsync();
                return Ok(true);
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
                var creditCards = data.CreditCards ?? new List<CreditCardDto>();

                if (!categories.Any() && !spendings.Any() && userData == null && !creditCards.Any())
                    return BadRequest("No hay datos para sincronizar.");

                var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
                if (userId == null)
                    return Unauthorized();

                if (spendings.Any(s => s.UserId != userId))
                    return BadRequest("Los gastos no pertenecen al usuario autenticado.");

                var pendingCategories = categories.Where(c => !c.IsSynced).ToList();
                var pendingSpendings = spendings.Where(s => !s.IsSynced).ToList();
                var pendingCards = creditCards.Where(c => !c.IsSynced).ToList();

                var categoryIds = pendingCategories
                    .Where(c => !string.IsNullOrWhiteSpace(c.CategoryId))
                    .Select(c => c.CategoryId)
                    .Distinct()
                    .ToList();

                var existingCategories = categoryIds.Any()
                    ? await _db.Categories
                        .Where(c => c.UserId == userId && categoryIds.Contains(c.CategoryId))
                        .ToDictionaryAsync(c => c.CategoryId)
                    : new Dictionary<string, Category>();

                foreach (var category in pendingCategories)
                {
                    if (existingCategories.TryGetValue(category.CategoryId, out var existingCategory))
                    {
                        existingCategory.CategoryName = category.CategoryName;
                        if (!existingCategory.IsDefaultCategory)
                            existingCategory.IsDefaultCategory = category.IsDefaultCategory;
                        existingCategory.IsSynced = true;
                        continue;
                    }

                    await _db.Categories.AddAsync(new Category
                    {
                        CategoryId = category.CategoryId,
                        UserId = category.UserId,
                        CategoryName = category.CategoryName,
                        IsDefaultCategory = category.IsDefaultCategory,
                        IsSynced = true,
                    });
                }

                var cardIds = pendingCards
                    .Where(c => !string.IsNullOrWhiteSpace(c.CreditCardId))
                    .Select(c => c.CreditCardId)
                    .Distinct()
                    .ToList();

                var existingCards = cardIds.Any()
                    ? await _db.CreditCards
                        .Where(c => c.UserId == userId && cardIds.Contains(c.CreditCardId))
                        .ToDictionaryAsync(c => c.CreditCardId)
                    : new Dictionary<string, CreditCard>();

                foreach (var card in pendingCards)
                {
                    if (existingCards.TryGetValue(card.CreditCardId, out var existingCard))
                    {
                        existingCard.CardName = card.CardName;
                        existingCard.BankName = card.BankName;
                        existingCard.LastFourDigits = card.LastFourDigits;
                        existingCard.CutOffDay = card.CutOffDay;
                        existingCard.PaymentDay = card.PaymentDay;
                        existingCard.CreditLimit = card.CreditLimit;
                        existingCard.ColorHex = card.ColorHex;
                        existingCard.IsDeleted = card.IsDeleted;
                        existingCard.DeletedAt = ResolveDeletedAt(card.IsDeleted, existingCard.DeletedAt, card.DeletedAt);
                        existingCard.IsSynced = true;
                        continue;
                    }

                    await _db.CreditCards.AddAsync(new CreditCard
                    {
                        CreditCardId = card.CreditCardId,
                        UserId = card.UserId,
                        CardName = card.CardName,
                        BankName = card.BankName,
                        LastFourDigits = card.LastFourDigits,
                        CutOffDay = card.CutOffDay,
                        PaymentDay = card.PaymentDay,
                        CreditLimit = card.CreditLimit,
                        ColorHex = card.ColorHex,
                        IsSynced = true,
                        IsDeleted = card.IsDeleted
                    });
                }

                var spendingIds = pendingSpendings
                    .Where(s => !string.IsNullOrWhiteSpace(s.SpendingId))
                    .Select(s => s.SpendingId)
                    .Distinct()
                    .ToList();

                var existingSpendings = spendingIds.Any()
                    ? await _db.Spendings
                        .Where(s => s.UserId == userId && spendingIds.Contains(s.SpendingId))
                        .ToDictionaryAsync(s => s.SpendingId)
                    : new Dictionary<string, Spending>();

                foreach (var spending in pendingSpendings)
                {
                    if (existingSpendings.TryGetValue(spending.SpendingId, out var existingSpending))
                    {
                        ApplySpendingFields(existingSpending, spending);
                        existingSpending.IsDeleted = spending.IsDeleted;
                        existingSpending.DeletedAt = ResolveDeletedAt(spending.IsDeleted, existingSpending.DeletedAt, spending.DeletedAt);
                        existingSpending.IsSynced = true;
                        continue;
                    }

                    if (spending.IsDeleted)
                        continue;

                    var newSpending = new Spending
                    {
                        SpendingId = spending.SpendingId,
                        UserId = spending.UserId,
                        IsSynced = true,
                        IsDeleted = false
                    };
                    ApplySpendingFields(newSpending, spending);
                    await _db.Spendings.AddAsync(newSpending);
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
                return Ok(true);
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
                    CreditCards = data?.CreditCards?.Select(cc => new
                    {
                        cc.CreditCardId,
                        cc.UserId,
                        cc.CardName,
                        cc.BankName,
                        cc.IsSynced,
                        cc.IsDeleted
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

        /// <summary>
        /// Gastos del usuario para que el cliente baje lo que se creo en otro
        /// dispositivo (por ejemplo el reloj). Hasta ahora la app solo empujaba y solo
        /// descargaba todo al iniciar sesion, asi que un gasto hecho en el reloj nunca
        /// le llegaba al telefono.
        /// </summary>
        [HttpGet("GetSpendings")]
        public async Task<ActionResult<List<SpendingDto>>> GetSpendings()
        {
            var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
            if (userId == null)
                return Unauthorized();

            var spendings = await _db.Spendings
                .Where(s => s.UserId == userId && !s.IsDeleted)
                .Select(s => new SpendingDto
                {
                    SpendingId = s.SpendingId,
                    UserId = s.UserId,
                    CategoryId = s.CategoryId,
                    Title = s.Title,
                    Description = s.Description,
                    Amount = s.Amount,
                    // La columna es timestamptz, asi que EF ya la entrega en UTC.
                    Date = s.Date,
                    IsSynced = true,
                    IsDeleted = s.IsDeleted,
                    DeletedAt = s.DeletedAt,
                    IsCreditCard = s.IsCreditCard,
                    CreditCardId = s.CreditCardId,
                    PaymentMethod = s.PaymentMethod,
                    IsMsi = s.IsMsi,
                    TotalInstallments = s.TotalInstallments,
                    CurrentInstallment = s.CurrentInstallment,
                    ParentSpendingId = s.ParentSpendingId,
                    InstallmentMonthlyAmount = s.InstallmentMonthlyAmount
                })
                .ToListAsync(HttpContext.RequestAborted);

            return Ok(spendings);
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

                var existingCategory = await _db.Categories
                    .FirstOrDefaultAsync(c => c.CategoryId == spending.CategoryId && c.UserId == spending.UserId);

                if (existingCategory == null)
                {
                    await _db.Categories.AddAsync(new Category
                    {
                        CategoryId = category.CategoryId,
                        CategoryName = category.CategoryName,
                        UserId = spending.UserId,
                        IsDefaultCategory = false,
                        IsSynced = true
                    });
                }
                else if (!existingCategory.IsDefaultCategory && !string.IsNullOrWhiteSpace(category?.CategoryName))
                {
                    existingCategory.CategoryName = category.CategoryName;
                    existingCategory.IsSynced = true;
                }

                var existingSpending = await _db.Spendings
                    .FirstOrDefaultAsync(s => s.SpendingId == spending.SpendingId && s.UserId == spending.UserId);

                if (existingSpending == null)
                {
                    var newSpending = new Spending
                    {
                        SpendingId = spending.SpendingId,
                        UserId = spending.UserId,
                        IsSynced = true,
                        IsDeleted = spending.IsDeleted
                    };
                    ApplySpendingFields(newSpending, spending);
                    await _db.Spendings.AddAsync(newSpending);
                }
                else
                {
                    ApplySpendingFields(existingSpending, spending);
                    existingSpending.IsDeleted = spending.IsDeleted;
                    existingSpending.DeletedAt = ResolveDeletedAt(spending.IsDeleted, existingSpending.DeletedAt, spending.DeletedAt);
                    existingSpending.IsSynced = true;
                }

                await _db.SaveChangesAsync();
                return Ok(true);
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
                spending.DeletedAt = DateTime.UtcNow;

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

                ApplySpendingFields(spending, data);
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

        [HttpPost("CreateCreditCard")]
        public async Task<ActionResult<bool>> CreateCreditCard(CreditCardDto card)
        {
            try
            {
                var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
                if (userId == null)
                    return Unauthorized();
                if (card.UserId != userId)
                    return BadRequest("La tarjeta no pertenece al usuario autenticado.");

                var existing = await _db.CreditCards
                    .FirstOrDefaultAsync(c => c.CreditCardId == card.CreditCardId && c.UserId == userId);

                if (existing == null)
                {
                    await _db.CreditCards.AddAsync(new CreditCard
                    {
                        CreditCardId = card.CreditCardId,
                        UserId = card.UserId,
                        CardName = card.CardName,
                        BankName = card.BankName,
                        LastFourDigits = card.LastFourDigits,
                        CutOffDay = card.CutOffDay,
                        PaymentDay = card.PaymentDay,
                        CreditLimit = card.CreditLimit,
                        ColorHex = card.ColorHex,
                        IsSynced = true,
                        IsDeleted = card.IsDeleted
                    });
                }
                else
                {
                    existing.CardName = card.CardName;
                    existing.BankName = card.BankName;
                    existing.LastFourDigits = card.LastFourDigits;
                    existing.CutOffDay = card.CutOffDay;
                    existing.PaymentDay = card.PaymentDay;
                    existing.CreditLimit = card.CreditLimit;
                    existing.ColorHex = card.ColorHex;
                    existing.IsDeleted = card.IsDeleted;
                    existing.DeletedAt = ResolveDeletedAt(card.IsDeleted, existing.DeletedAt, card.DeletedAt);
                    existing.IsSynced = true;
                }

                await _db.SaveChangesAsync();
                return Ok(true);
            }
            catch (Exception ex)
            {
                LogEndpointError(ex, nameof(CreateCreditCard), card);
                return StatusCode(500, false);
            }
        }

        [HttpPost("DeleteCreditCard")]
        public async Task<ActionResult<bool>> DeleteCreditCard(string creditCardId)
        {
            try
            {
                var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
                if (userId == null)
                    return Unauthorized();

                var card = await _db.CreditCards.FirstOrDefaultAsync(c => c.CreditCardId == creditCardId && c.UserId == userId);
                if (card == null)
                    return NotFound("Tarjeta no encontrada.");

                card.IsDeleted = true;
                card.DeletedAt = DateTime.UtcNow;
                card.IsSynced = true;

                await _db.SaveChangesAsync();
                return Ok(true);
            }
            catch (Exception ex)
            {
                LogEndpointError(ex, nameof(DeleteCreditCard), new { creditCardId });
                return StatusCode(500, false);
            }
        }
    }
}
