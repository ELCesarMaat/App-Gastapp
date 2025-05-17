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
            var dbUser = await _db.Users.FirstOrDefaultAsync(s => s.Email == login.Email);
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