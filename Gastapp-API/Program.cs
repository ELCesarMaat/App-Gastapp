using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Gastapp.Models;
using Gastapp.Services;
using Microsoft.AspNetCore.Authorization;
using Gastapp_API.Data;
using Gastapp.Models.Models;
using System.Security.Claims;

namespace Gastapp.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class UserController : ControllerBase
    {
        private readonly GastappDbContext _db;
        private readonly IUserService _userService;

        public UserController(GastappDbContext db, IUserService userService)
        {
            _db = db;
            _userService = userService;
        }

        [HttpPost("Login")]
        public async Task<ActionResult<AuthenticateResponse>> Login(AuthenticateRequest model)
        {
            // Autenticación con JWT
            var authResponse = await _userService.AuthenticateAsync(model);

            if (authResponse == null)
                return BadRequest(new { message = "Email o contraseña incorrectos" });

            // Obtener datos adicionales del usuario
            var dbUser = await _db.Users
                .Include(u => u.IncomeType)
                .FirstOrDefaultAsync(u => u.UserId == authResponse.UserId);

            if (dbUser == null)
                return NotFound("Usuario no encontrado");

            await CheckForUserHasNoCategories(dbUser.UserId);

            var userData = new AllUserData
            {
                User = dbUser,
                Categories = await GetUserCategories(dbUser.UserId),
                Spendings = await GetUserSpendings(dbUser.UserId),
                Incomes = await _db.IncomeTypes.ToListAsync(),
                Token = authResponse.Token // Incluir el token en la respuesta
            };

            return Ok(userData);
        }

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

            return Ok(new AllUserData
            {
                User = dbUser,
                Categories = await GetUserCategories(userId),
                Spendings = await GetUserSpendings(userId),
                Incomes = await _db.IncomeTypes.ToListAsync()
            });
        }

        private async Task CheckForUserHasNoCategories(string userId)
        {
            // Tu implementación existente
        }

        private async Task<List<CategoryDto>> GetUserCategories(string userId)
        {
            return await _db.Categories
                .Where(c => c.UserId == userId)
                .Select(c => new CategoryDto
                {
                    CategoryName = c.CategoryName,
                    CategoryId = c.CategoryId,
                    IsSynced = c.IsSynced,
                    UserId = c.UserId
                }).ToListAsync();
        }

        private async Task<List<SpendingDto>> GetUserSpendings(string userId)
        {
            return await _db.Spendings
                .Where(s => s.UserId == userId)
                .Select(s => new SpendingDto
                {
                    Amount = s.Amount,
                    CategoryId = s.CategoryId,
                    Date = s.Date,
                    Description = s.Description,
                    SpendingId = s.SpendingId,
                    UserId = s.UserId,
                    IsSynced = s.IsSynced,
                    Title = s.Title,
                }).ToListAsync();
        }
    }
}