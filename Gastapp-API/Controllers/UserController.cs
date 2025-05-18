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
    public class UserController : ControllerBase
    {
        private readonly GastappDbContext _db;

        public UserController(GastappDbContext context)
        {
            _db = context;
        }

        [HttpPost("CreateUser")]
        public async Task<ActionResult<string>> CreateNewUser(User user)
        {
            var emailExists = _db.Users.Any(s => s.Email == user.Email);
            if (emailExists)
                return BadRequest("Email en uso");
            try
            {
                _db.Users.Add(user);
                await _db.SaveChangesAsync();
                _db.Categories.Add(new Category
                {
                    CategoryName = "SIN CATEGORIA",
                    IsSynced = true,
                    UserId = user.UserId
                });

                await _db.SaveChangesAsync();
                await CheckForUserHasNoCategories(user.UserId);


                return Ok(user.UserId);
            }
            catch (Exception e)
            {
                return BadRequest(e.Message);
            }
        }

        [HttpPost("Login")]
        public async Task<ActionResult<AllUserData>> Login(LoginModel login)
        {
            // 1. Autenticación con JWT
            var authResponse = await _userService.AuthenticateAsync(model);

            if (authResponse == null)
                return BadRequest("Credenciales inválidas");

            // 2. Obtener datos del usuario
            var dbUser = await _db.Users.FirstOrDefaultAsync(u => u.UserId == authResponse.UserId);

                .FirstOrDefaultAsync(u => u.UserId == authResponse.UserId);

            if (dbUser == null)
                return BadRequest("Usuario no encontrado");
            if (dbUser.PassWordHash != login.Password)
                return BadRequest("Contraseña incorrecta");

            dbUser.IncomeType = null;
            var incomes = await _db.IncomeTypes.ToListAsync();

            await CheckForUserHasNoCategories(dbUser.UserId);
            var userCategories = await _db.Categories.Select(s => new CategoryDto
            {
                CategoryName = s.CategoryName,
                CategoryId = s.CategoryId,
                IsSynced = s.IsSynced,
                UserId = s.UserId
            }).Where(s => s.UserId == dbUser.UserId).ToListAsync();

            var userSpendings = await _db.Spendings.Select(s => new SpendingDto
            {
                Amount = s.Amount,
                CategoryId = s.CategoryId,
                Date = s.Date,
                Description = s.Description,
                SpendingId = s.SpendingId,
                UserId = s.UserId,
                IsSynced = s.IsSynced,
                Title = s.Title,
            }).Where(s => s.UserId == dbUser.UserId).ToListAsync();

            var userData = new AllUserData
            {
                User = dbUser,
                Categories = userCategories,
                Spendings = userSpendings,
                Incomes = incomes
            };


            return Ok(userData);
        [Authorize]
        [HttpGet("profile")]
        public async Task<ActionResult<AllUserData>> GetProfile()
        {
            var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
            if (string.IsNullOrEmpty(userId))
                return Unauthorized();

            var dbUser = await _db.Users
                .Include(u => u.IncomeType)
                .FirstOrDefaultAsync(u => u.UserId == userId);

            if (dbUser == null)
                return NotFound("Usuario no encontrado");

            // Obtener los mismos datos que en el Login
            var userCategories = await _db.Categories
                .Where(c => c.UserId == userId)
                .Select(c => new CategoryDto
                {
                    /* ... */
                })
                .ToListAsync();

            var userSpendings = await _db.Spendings
                .Where(s => s.UserId == userId)
                .Select(s => new SpendingDto
                {
                    /* ... */
                })
                .ToListAsync();

            var incomes = await _db.IncomeTypes.ToListAsync();

            return new AllUserData
            {
                User = dbUser,
                Categories = userCategories,
                Spendings = userSpendings,
                Incomes = incomes
                // No necesitamos devolver el token aquí
            };
        }

        }

        private async Task CheckForUserHasNoCategories(string userId)
        {
            if (await _db.Categories.AnyAsync(c => c.UserId == userId))
                return;

            _db.Categories.Add(new Category
            {
                CategoryName = "SIN CATEGORIA",
                IsSynced = true,
                UserId = userId
            });
            await _db.SaveChangesAsync();
        }
    }
}