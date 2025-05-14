using Gastapp_API.Data;
using Gastapp.Models;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;

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
                return Ok(Guid.NewGuid().ToString());
            }
            catch (Exception e)
            {
                return BadRequest(e.Message);
            }
        }
    }
}
