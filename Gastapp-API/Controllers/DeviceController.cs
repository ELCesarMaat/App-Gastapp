using System.Security.Claims;
using Gastapp.Models;
using Gastapp.Models.Models;
using Gastapp.Services;
using Gastapp_API.Data;
using Gastapp_API.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace Gastapp_API.Controllers
{
    // Emparejamiento y operaciones de dispositivos vinculados (relojes Wear OS).
    // Los endpoints de emparejamiento (Code, Token, Refresh) van sin [Authorize] porque
    // el reloj todavia no tiene credenciales; su seguridad viene del device_code opaco,
    // del TTL corto y del limite de sondeos.
    [Route("api/[controller]")]
    [ApiController]
    public class DeviceController : ControllerBase
    {
        private readonly GastappDbContext _db;
        private readonly IDeviceAuthService _deviceAuth;
        private readonly ILogger<DeviceController> _logger;

        public DeviceController(GastappDbContext db, IDeviceAuthService deviceAuth, ILogger<DeviceController> logger)
        {
            _db = db;
            _deviceAuth = deviceAuth;
            _logger = logger;
        }

        // ---- Emparejamiento ----

        [HttpPost("Code")]
        public async Task<ActionResult<DeviceCodeResponse>> Code(DeviceCodeRequest request)
        {
            var response = await _deviceAuth.CreateAuthorizationAsync(request, HttpContext.RequestAborted);
            return Ok(response);
        }

        [HttpPost("Token")]
        public async Task<IActionResult> Token(DeviceTokenRequest request)
        {
            var result = await _deviceAuth.PollAsync(request.DeviceCode, HttpContext.RequestAborted);

            // Formato de error de RFC 8628: el cliente distingue por el campo "error".
            return result.Outcome switch
            {
                DeviceAuthOutcome.Ok => Ok(result.Token),
                DeviceAuthOutcome.AuthorizationPending => BadRequest(new { error = "authorization_pending" }),
                DeviceAuthOutcome.SlowDown => BadRequest(new { error = "slow_down" }),
                DeviceAuthOutcome.ExpiredToken => BadRequest(new { error = "expired_token" }),
                _ => BadRequest(new { error = "access_denied" })
            };
        }

        [HttpPost("Refresh")]
        public async Task<IActionResult> Refresh(DeviceRefreshRequest request)
        {
            var result = await _deviceAuth.RefreshAsync(request.RefreshToken, HttpContext.RequestAborted);

            if (result.Outcome != DeviceAuthOutcome.Ok || result.Token == null)
                return Unauthorized(new { error = "invalid_grant" });

            return Ok(result.Token);
        }

        // ---- Administracion desde la app del telefono ----

        [Authorize]
        [HttpPost("Link")]
        public async Task<IActionResult> Link(LinkDeviceRequest request)
        {
            var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
            if (userId == null)
                return Unauthorized();

            var result = await _deviceAuth.LinkAsync(userId, request.UserCode, HttpContext.RequestAborted);

            return result.Outcome switch
            {
                DeviceAuthOutcome.Ok => Ok(result.Device),
                DeviceAuthOutcome.TooManyAttempts => StatusCode(
                    StatusCodes.Status429TooManyRequests,
                    "Demasiados intentos. Espera 15 minutos e intenta de nuevo."),
                _ => BadRequest("Codigo no valido o expirado.")
            };
        }

        [Authorize]
        [HttpGet("List")]
        public async Task<ActionResult<List<DeviceDto>>> List()
        {
            var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
            if (userId == null)
                return Unauthorized();

            return Ok(await _deviceAuth.ListAsync(userId, HttpContext.RequestAborted));
        }

        [Authorize]
        [HttpPost("Revoke")]
        public async Task<ActionResult<bool>> Revoke(RevokeDeviceRequest request)
        {
            var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
            if (userId == null)
                return Unauthorized();

            // Un token de dispositivo solo puede revocarse a si mismo. El del telefono no
            // trae device_id y conserva el permiso de revocar cualquiera de la cuenta.
            var callerDeviceId = User.FindFirst("device_id")?.Value;
            if (!string.IsNullOrWhiteSpace(callerDeviceId) && callerDeviceId != request.DeviceId)
                return Forbid();

            var ok = await _deviceAuth.RevokeAsync(userId, request.DeviceId, HttpContext.RequestAborted);
            if (!ok)
                return NotFound("Dispositivo no encontrado.");

            return Ok(true);
        }

        // ---- Datos que consume el reloj ----

        [Authorize]
        [HttpGet("Categories")]
        public async Task<ActionResult<List<DeviceCategoryDto>>> Categories()
        {
            var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
            if (userId == null)
                return Unauthorized();

            if (!HasScope(DeviceScopes.ExpensesWrite))
                return Forbid();

            var categories = await _db.Categories
                .Where(c => c.UserId == userId)
                .OrderByDescending(c => c.IsDefaultCategory)
                .ThenBy(c => c.CategoryName)
                // Desempate estable: un usuario puede tener mas de una categoria marcada
                // como por defecto, y sin esto cada consulta podria devolver otro orden.
                .ThenBy(c => c.CategoryId)
                .Select(c => new DeviceCategoryDto
                {
                    CategoryId = c.CategoryId,
                    CategoryName = c.CategoryName,
                    IsDefaultCategory = c.IsDefaultCategory
                })
                .ToListAsync(HttpContext.RequestAborted);

            return Ok(categories);
        }

        [Authorize]
        [HttpPost("Expenses")]
        public async Task<ActionResult<DeviceExpenseBatchResult>> Expenses(List<DeviceExpenseDto> expenses)
        {
            var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
            if (userId == null)
                return Unauthorized();

            if (!HasScope(DeviceScopes.ExpensesWrite))
                return Forbid();

            if (expenses == null || expenses.Count == 0)
                return BadRequest("No se recibio ningun gasto.");

            if (expenses.Count > 50)
                return BadRequest("Maximo 50 gastos por envio.");

            var defaultCategoryId = await GetDefaultCategoryIdAsync(userId);
            if (defaultCategoryId == null)
                return BadRequest("El usuario no tiene ninguna categoria.");

            var validCategoryIds = await _db.Categories
                .Where(c => c.UserId == userId)
                .Select(c => c.CategoryId)
                .ToListAsync(HttpContext.RequestAborted);

            // Nombre del reloj para firmar la descripcion. Si el token no trae device_id
            // (token normal del telefono) la descripcion se queda tal cual la mando el cliente.
            var deviceName = await GetDeviceNameAsync(userId);

            var results = new List<DeviceExpenseResult>();

            foreach (var dto in expenses)
            {
                if (string.IsNullOrWhiteSpace(dto.SpendingId))
                    return BadRequest("Cada gasto necesita un spendingId generado en el dispositivo.");

                // Idempotencia: el reloj genera el id, asi que reenviar el mismo gasto
                // no duplica. "Ya existia" y "se creo" son ambos exito para el reloj.
                var yaExiste = await _db.Spendings
                    .AnyAsync(s => s.SpendingId == dto.SpendingId && s.UserId == userId, HttpContext.RequestAborted);

                if (yaExiste)
                {
                    results.Add(new DeviceExpenseResult { SpendingId = dto.SpendingId, Created = false });
                    continue;
                }

                // Una categoria que no sea del usuario (o nula) cae a la de por defecto.
                // Nunca se rechaza el gasto por la categoria: perder el registro es peor
                // que clasificarlo mal.
                var categoryId = !string.IsNullOrWhiteSpace(dto.CategoryId) && validCategoryIds.Contains(dto.CategoryId)
                    ? dto.CategoryId!
                    : defaultCategoryId;

                await _db.Spendings.AddAsync(new Spending
                {
                    SpendingId = dto.SpendingId,
                    UserId = userId,
                    CategoryId = categoryId,
                    Title = BuildTitle(dto),
                    Description = BuildDescription(dto, deviceName),
                    Amount = dto.Amount,
                    Date = NormalizeIncomingDate(dto.OccurredAt),
                    IsSynced = true,
                    IsDeleted = false,
                    DeletedAt = null,

                    // El reloj no maneja tarjetas de credito. Dejar CreditCardId en null
                    // es obligatorio: una referencia colgante rompe el login del telefono.
                    IsCreditCard = false,
                    CreditCardId = null,
                    PaymentMethod = "Cash",
                    IsMsi = false,
                    TotalInstallments = 1,
                    CurrentInstallment = 1,
                    ParentSpendingId = null,
                    InstallmentMonthlyAmount = 0m
                }, HttpContext.RequestAborted);

                results.Add(new DeviceExpenseResult { SpendingId = dto.SpendingId, Created = true });
            }

            await _db.SaveChangesAsync(HttpContext.RequestAborted);

            return Ok(new DeviceExpenseBatchResult { Results = results });
        }

        [Authorize]
        [HttpGet("Summary")]
        public async Task<ActionResult<DeviceSummaryResponse>> Summary(
            [FromQuery] string period = "today",
            [FromQuery] int tzOffsetMinutes = 0)
        {
            var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
            if (userId == null)
                return Unauthorized();

            if (!HasScope(DeviceScopes.ExpensesReadSummary))
                return Forbid();

            var ventana = CalcularVentana(period, tzOffsetMinutes);
            var query = SpendingsDelPeriodo(userId, ventana);

            return Ok(new DeviceSummaryResponse
            {
                Period = ventana.Period,
                Total = await query.SumAsync(s => (decimal?)s.Amount, HttpContext.RequestAborted) ?? 0m,
                Count = await query.CountAsync(HttpContext.RequestAborted),
                Currency = "MXN"
            });
        }

        /// <summary>
        /// Lista los gastos del periodo para pintarlos en el reloj. Comparte scope y
        /// filtros con Summary: son la misma vista, uno agregado y otro al detalle.
        /// </summary>
        [Authorize]
        [HttpGet("Expenses")]
        public async Task<ActionResult<List<DeviceDaySpendingDto>>> DayExpenses(
            [FromQuery] string period = "today",
            [FromQuery] int tzOffsetMinutes = 0,
            [FromQuery] int limit = 50)
        {
            var userId = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
            if (userId == null)
                return Unauthorized();

            if (!HasScope(DeviceScopes.ExpensesReadSummary))
                return Forbid();

            // La pantalla del reloj no da para mas, y el tope evita que un mes entero
            // se descargue por accidente.
            limit = Math.Clamp(limit, 1, 100);

            var ventana = CalcularVentana(period, tzOffsetMinutes);

            var gastos = await SpendingsDelPeriodo(userId, ventana)
                .OrderByDescending(s => s.Date)
                // Desempate estable: dos gastos capturados en el mismo instante deben
                // salir siempre en el mismo orden, o la lista "salta" entre refrescos.
                .ThenBy(s => s.SpendingId)
                .Take(limit)
                .Select(s => new DeviceDaySpendingDto
                {
                    SpendingId = s.SpendingId,
                    Title = s.Title,
                    CategoryName = s.Category != null ? s.Category.CategoryName : null,
                    Amount = s.Amount,
                    OccurredAt = s.Date
                })
                .ToListAsync(HttpContext.RequestAborted);

            return Ok(gastos);
        }

        // ---- Utilidades ----

        private readonly record struct VentanaPeriodo(string Period, DateTime StartUtc, DateTime EndUtc);

        /// <summary>
        /// Traduce el periodo pedido a un rango en UTC. El reloj manda su desfase para
        /// que "hoy" sea su dia local y no el dia UTC.
        /// </summary>
        private static VentanaPeriodo CalcularVentana(string? period, int tzOffsetMinutes)
        {
            var normalizado = period?.ToLowerInvariant() == "month" ? "month" : "today";

            var offset = TimeSpan.FromMinutes(tzOffsetMinutes);
            var localNow = DateTime.UtcNow + offset;

            var localStart = normalizado == "month"
                ? new DateTime(localNow.Year, localNow.Month, 1, 0, 0, 0, DateTimeKind.Unspecified)
                : localNow.Date;

            var localEnd = normalizado == "month" ? localStart.AddMonths(1) : localStart.AddDays(1);

            return new VentanaPeriodo(
                normalizado,
                DateTime.SpecifyKind(localStart - offset, DateTimeKind.Utc),
                DateTime.SpecifyKind(localEnd - offset, DateTimeKind.Utc));
        }

        private IQueryable<Spending> SpendingsDelPeriodo(string userId, VentanaPeriodo ventana) =>
            _db.Spendings.Where(s =>
                s.UserId == userId &&
                !s.IsDeleted &&
                s.Date >= ventana.StartUtc &&
                s.Date < ventana.EndUtc);

        /// <summary>
        /// Un token de dispositivo trae el claim "scope" y queda restringido. El token de
        /// la app del telefono no lo trae y conserva acceso completo: asi la app existente
        /// sigue funcionando sin cambios. No es un hueco, es compatibilidad hacia atras.
        /// </summary>
        private bool HasScope(string required)
        {
            var scopes = User.FindFirst("scope")?.Value;
            if (string.IsNullOrWhiteSpace(scopes))
                return true;

            return scopes.Split(' ', StringSplitOptions.RemoveEmptyEntries).Contains(required);
        }

        private async Task<string?> GetDefaultCategoryIdAsync(string userId)
        {
            return await _db.Categories
                .Where(c => c.UserId == userId)
                .OrderByDescending(c => c.IsDefaultCategory)
                // Mismo desempate que en Categories: ambas rutas deben coincidir siempre
                // en cual es "la" categoria por defecto.
                .ThenBy(c => c.CategoryName)
                .ThenBy(c => c.CategoryId)
                .Select(c => c.CategoryId)
                .FirstOrDefaultAsync(HttpContext.RequestAborted);
        }

        private async Task<string?> GetDeviceNameAsync(string userId)
        {
            var deviceId = User.FindFirst("device_id")?.Value;
            if (string.IsNullOrWhiteSpace(deviceId))
                return null;

            var name = await _db.Devices
                .Where(d => d.DeviceId == deviceId && d.UserId == userId && d.RevokedAt == null)
                .Select(d => d.Name)
                .FirstOrDefaultAsync(HttpContext.RequestAborted);

            return string.IsNullOrWhiteSpace(name) ? null : name.Trim();
        }

        /// <summary>
        /// Deja constancia en la descripcion de que el gasto entro desde el reloj, para que
        /// el usuario lo distinga de los que captura en el telefono.
        /// </summary>
        private static string? BuildDescription(DeviceExpenseDto dto, string? deviceName)
        {
            if (string.IsNullOrWhiteSpace(deviceName))
                return dto.RawInput;

            var firma = $"Agregado desde mi {deviceName}";
            var crudo = dto.RawInput?.Trim();

            var descripcion = string.IsNullOrWhiteSpace(crudo) ? firma : $"{crudo} - {firma}";

            // La columna Description esta limitada a 255 caracteres; si no cabe todo se
            // recorta el texto del usuario, nunca la firma.
            if (descripcion.Length <= 255)
                return descripcion;

            var espacioParaCrudo = 255 - firma.Length - 3;
            return espacioParaCrudo <= 0
                ? Truncate(firma, 255)
                : $"{crudo![..espacioParaCrudo]} - {firma}";
        }

        private static string BuildTitle(DeviceExpenseDto dto)
        {
            if (dto.NeedsReview)
            {
                // El parser del reloj no pudo sacar el monto. Se marca para que el usuario
                // lo corrija desde el telefono, en vez de agregar una columna nueva.
                var crudo = string.IsNullOrWhiteSpace(dto.RawInput) ? "Gasto sin monto" : dto.RawInput!.Trim();
                return Truncate($"Revisar: {crudo}", 50);
            }

            return string.IsNullOrWhiteSpace(dto.Title)
                ? "Gasto desde el reloj"
                : Truncate(dto.Title!.Trim(), 50);
        }

        // La columna Title esta limitada a 50 caracteres en el modelo.
        private static string Truncate(string value, int max) =>
            value.Length <= max ? value : value[..max];

        private static DateTime NormalizeIncomingDate(DateTime date)
        {
            if (date == default)
                return DateTime.UtcNow;

            if (date.Kind == DateTimeKind.Utc)
                return date;

            if (date.Kind == DateTimeKind.Local)
                return date.ToUniversalTime();

            return DateTime.SpecifyKind(date, DateTimeKind.Utc);
        }
    }
}
