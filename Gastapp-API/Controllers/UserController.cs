using Gastapp_API.Data;
using Gastapp.Models;
using Gastapp.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Gastapp.Models.Models;
using System.Security.Claims;

namespace Gastapp_API.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    public class UserController : ControllerBase
    {
        private readonly GastappDbContext _db;
        private readonly IUserService _userService;

        public UserController(GastappDbContext context, IUserService userService)
        {
            _db = context;
            _userService = userService;
        }

        [HttpPost("CreateUser")]
        public async Task<ActionResult<CreateUserResponse>> CreateNewUser(CreateUserModel user)
        {
            var emailExists = await _db.Users.AnyAsync(s => s.Email == user.Email);
            if (emailExists)
                return BadRequest("Email en uso");

            try
            {
                _db.Users.Add(new User
                {
                    UserId = user.UserId,
                    Salary = user.Salary,
                    PercentSave = user.PercentSave,
                    Name = user.Name,
                    Email = user.Email,
                    PassWordHash = user.Password,//Falta hacer el HASH
                    BirthDate = user.BirthDate,
                    IncomeTypeId = user.IncomeTypeId,
                    FirstPayDay = user.FirstPayDay,
                    SecondPayDay = user.SecondPayDay,
                    WeekPayDay = user.WeekPayDay
                });

                await _db.SaveChangesAsync();
                var authResponse = await _userService.AuthenticateAsync(new AuthenticateRequest
                {
                    Email = user.Email,
                    Password = user.Password
                });

                _db.Categories.Add(new Category
                {
                    CategoryName = "SIN CATEGORIA",
                    IsSynced = true,
                    UserId = user.UserId
                });

                await _db.SaveChangesAsync();
                await CheckForUserHasNoCategories(user.UserId);


                return Ok(new CreateUserResponse
                {
                    Token = authResponse.Token,
                    UserId = user.UserId
                });
            }
            catch (Exception e)
            {
                return BadRequest(e.Message);
            }
        }

        [HttpPost("Login")]
        public async Task<ActionResult<AllUserData>> Login(AuthenticateRequest model)
        {
            // 1. Autenticación con JWT
            var authResponse = await _userService.AuthenticateAsync(model);

            if (authResponse == null)
                return BadRequest("Credenciales inválidas");

            // 2. Obtener datos del usuario
            var dbUser = await _db.Users
                .Include(u => u.IncomeType)
                .FirstOrDefaultAsync(u => u.UserId == authResponse.UserId);

            if (dbUser == null)
                return NotFound("Usuario no encontrado");

            await CheckForUserHasNoCategories(dbUser.UserId);

            // 3. Obtener datos relacionados
            var userCategories = await _db.Categories
                .Where(c => c.UserId == dbUser.UserId)
                .Select(c => new CategoryDto
                {
                    CategoryName = c.CategoryName,
                    CategoryId = c.CategoryId,
                    IsSynced = c.IsSynced,
                    UserId = c.UserId
                }).ToListAsync();

            var userSpendings = await _db.Spendings
                .Where(s => s.UserId == dbUser.UserId)
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

            var incomes = await _db.IncomeTypes.ToListAsync();

            // 4. Crear respuesta
            var userData = new AllUserData
            {
                User = dbUser,
                Categories = userCategories,
                Spendings = userSpendings,
                Incomes = incomes,
                Token = authResponse.Token // Agregamos el token aquí
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