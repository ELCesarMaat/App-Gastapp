using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;

namespace Gastappp_API.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    public class ValuesController : ControllerBase
    {
        [HttpGet]
        public ActionResult<string> GetTime(string name, int age)
        {
            return Ok($" Hola {name} de edad {age} son las {DateTime.Now.ToLongTimeString()}");
        }
    }
}
