using Gastapp.Services;
using Microsoft.AspNetCore.Mvc;

namespace Gastapp_API.Controllers
{
    [Route("api/[controller]")]
    [ApiController]
    public class AppController : ControllerBase
    {
        private readonly IAppUpdateService _appUpdateService;

        public AppController(IAppUpdateService appUpdateService)
        {
            _appUpdateService = appUpdateService;
        }

        [HttpGet("LatestVersion")]
        public async Task<IActionResult> GetLatestVersion(CancellationToken cancellationToken)
        {
            var latest = await _appUpdateService.GetLatestVersionAsync(cancellationToken);
            if (latest == null)
                return NotFound();

            return Ok(latest);
        }
    }
}
