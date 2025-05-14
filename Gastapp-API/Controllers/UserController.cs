using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;

namespace Gastapp_API.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    public class UserController : ControllerBase
    {
        public UserController()
        {
        }

        [HttpGet("Hello")]
        public IActionResult HelloApi()
        {
            return Ok("Hello from UserController");
        }
    }
}
