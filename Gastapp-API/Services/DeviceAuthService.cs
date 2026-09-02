using System;
using System.Collections.Generic;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Gastapp.Models.Models;
using Gastapp_API.Data;
using Gastapp_API.Models;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;

namespace Gastapp.Services
{
    // Emparejamiento de dispositivos por device authorization grant (RFC 8628).
    // El reloj pide un codigo, lo muestra en pantalla, y el usuario lo teclea en la app
    // del telefono ya autenticado. Mientras tanto el reloj hace polling hasta recibir
    // sus tokens.
    public class DeviceAuthService : IDeviceAuthService
    {
        private static readonly TimeSpan CodeLifetime = TimeSpan.FromMinutes(10);
        private static readonly TimeSpan AccessTokenLifetime = TimeSpan.FromMinutes(15);

        // Tope de sondeos: 10 min / 5 s = 120, con margen para reintentos.
        private const int MaxPollCount = 200;

        // Sin este limite, seis caracteres son forzables desde una cuenta cualquiera.
        private const int MaxFailedLinkAttempts = 5;
        private static readonly TimeSpan LinkAttemptWindow = TimeSpan.FromMinutes(15);

        // Alfabeto sin caracteres ambiguos: no hay O/0, I/1, U (para no formar palabras).
        private const string CodeAlphabet = "23456789BCDFGHJKLMNPQRSTVWXZ";
        private const int UserCodeLength = 6;

        // Intentos fallidos de vinculacion por usuario. En memoria a proposito: el plan
        // gratuito de Render corre una sola instancia, y perder el conteo al reiniciar es
        // aceptable para este caso. Si algun dia hay varias instancias, mover a la base.
        private static readonly Dictionary<string, List<DateTime>> FailedLinkAttempts = new();
        private static readonly object FailedLinkLock = new();

        private readonly GastappDbContext _db;
        private readonly IUserService _userService;
        private readonly ILogger<DeviceAuthService> _logger;

        public DeviceAuthService(GastappDbContext db, IUserService userService, ILogger<DeviceAuthService> logger)
        {
            _db = db;
            _userService = userService;
            _logger = logger;
        }

        public async Task<DeviceCodeResponse> CreateAuthorizationAsync(DeviceCodeRequest request, CancellationToken cancellationToken)
        {
            var deviceCode = GenerateOpaqueToken();
            var userCode = await GenerateUniqueUserCodeAsync(cancellationToken);

            var authorization = new DeviceAuthorization
            {
                DeviceCodeHash = Hash(deviceCode),
                UserCode = userCode,
                DeviceName = string.IsNullOrWhiteSpace(request.DeviceName) ? "Dispositivo" : request.DeviceName.Trim(),
                Platform = string.IsNullOrWhiteSpace(request.Platform) ? "wearos" : request.Platform.Trim(),
                Status = DeviceAuthorizationStatus.Pending,
                ExpiresAt = DateTime.UtcNow.Add(CodeLifetime),
                CreatedAt = DateTime.UtcNow
            };

            await _db.DeviceAuthorizations.AddAsync(authorization, cancellationToken);
            await _db.SaveChangesAsync(cancellationToken);

            return new DeviceCodeResponse
            {
                DeviceCode = deviceCode,
                UserCode = FormatUserCode(userCode),
                ExpiresIn = (int)CodeLifetime.TotalSeconds,
                Interval = authorization.IntervalSeconds
            };
        }

        public async Task<DeviceTokenOutcome> PollAsync(string deviceCode, CancellationToken cancellationToken)
        {
            if (string.IsNullOrWhiteSpace(deviceCode))
                return new DeviceTokenOutcome(DeviceAuthOutcome.AccessDenied, null);

            var hash = Hash(deviceCode);
            var authorization = await _db.DeviceAuthorizations
                .FirstOrDefaultAsync(a => a.DeviceCodeHash == hash, cancellationToken);

            if (authorization == null)
                return new DeviceTokenOutcome(DeviceAuthOutcome.AccessDenied, null);

            if (authorization.Status == DeviceAuthorizationStatus.Consumed)
                return new DeviceTokenOutcome(DeviceAuthOutcome.ExpiredToken, null);

            if (authorization.Status == DeviceAuthorizationStatus.Denied)
                return new DeviceTokenOutcome(DeviceAuthOutcome.AccessDenied, null);

            if (authorization.ExpiresAt <= DateTime.UtcNow)
            {
                authorization.Status = DeviceAuthorizationStatus.Expired;
                await _db.SaveChangesAsync(cancellationToken);
                return new DeviceTokenOutcome(DeviceAuthOutcome.ExpiredToken, null);
            }

            // Sondear mas rapido que el intervalo pactado sube el intervalo, como en RFC 8628.
            if (authorization.LastPolledAt is { } last
                && DateTime.UtcNow - last < TimeSpan.FromSeconds(authorization.IntervalSeconds))
            {
                authorization.IntervalSeconds = Math.Min(authorization.IntervalSeconds + 5, 30);
                authorization.LastPolledAt = DateTime.UtcNow;
                await _db.SaveChangesAsync(cancellationToken);
                return new DeviceTokenOutcome(DeviceAuthOutcome.SlowDown, null);
            }

            authorization.LastPolledAt = DateTime.UtcNow;
            authorization.PollCount++;

            if (authorization.PollCount > MaxPollCount)
            {
                authorization.Status = DeviceAuthorizationStatus.Expired;
                await _db.SaveChangesAsync(cancellationToken);
                _logger.LogWarning("Autorizacion {Id} abortada por exceso de sondeos.", authorization.DeviceAuthorizationId);
                return new DeviceTokenOutcome(DeviceAuthOutcome.ExpiredToken, null);
            }

            if (authorization.Status != DeviceAuthorizationStatus.Approved || authorization.UserId == null)
            {
                await _db.SaveChangesAsync(cancellationToken);
                return new DeviceTokenOutcome(DeviceAuthOutcome.AuthorizationPending, null);
            }

            var user = await _db.Users.FirstOrDefaultAsync(u => u.UserId == authorization.UserId, cancellationToken);
            if (user == null)
            {
                authorization.Status = DeviceAuthorizationStatus.Denied;
                await _db.SaveChangesAsync(cancellationToken);
                return new DeviceTokenOutcome(DeviceAuthOutcome.AccessDenied, null);
            }

            // Un solo uso: a partir de aqui el device_code ya no sirve.
            authorization.Status = DeviceAuthorizationStatus.Consumed;

            var refreshToken = GenerateOpaqueToken();
            var device = new Device
            {
                UserId = user.UserId,
                Name = authorization.DeviceName,
                Platform = authorization.Platform,
                RefreshTokenHash = Hash(refreshToken),
                Scopes = DeviceScopes.Default,
                CreatedAt = DateTime.UtcNow,
                LastSeenAt = DateTime.UtcNow
            };

            await _db.Devices.AddAsync(device, cancellationToken);
            await _db.SaveChangesAsync(cancellationToken);

            var access = _userService.GenerateDeviceToken(user, device.DeviceId, device.Scopes, AccessTokenLifetime);

            _logger.LogInformation("Dispositivo {DeviceId} vinculado al usuario {UserId}.", device.DeviceId, user.UserId);

            return new DeviceTokenOutcome(DeviceAuthOutcome.Ok, new DeviceTokenResponse
            {
                AccessToken = access.TokenValue,
                RefreshToken = refreshToken,
                ExpiresIn = (int)AccessTokenLifetime.TotalSeconds,
                DeviceId = device.DeviceId,
                Scopes = device.Scopes
            });
        }

        public async Task<DeviceLinkOutcome> LinkAsync(string userId, string userCode, CancellationToken cancellationToken)
        {
            if (IsLinkRateLimited(userId))
                return new DeviceLinkOutcome(DeviceAuthOutcome.TooManyAttempts, null);

            var normalized = NormalizeUserCode(userCode);
            if (normalized.Length != UserCodeLength)
            {
                RegisterFailedLink(userId);
                return new DeviceLinkOutcome(DeviceAuthOutcome.InvalidCode, null);
            }

            var authorization = await _db.DeviceAuthorizations
                .FirstOrDefaultAsync(a => a.UserCode == normalized
                                          && a.Status == DeviceAuthorizationStatus.Pending
                                          && a.ExpiresAt > DateTime.UtcNow,
                                     cancellationToken);

            if (authorization == null)
            {
                RegisterFailedLink(userId);
                _logger.LogWarning("Intento fallido de vinculacion del usuario {UserId}.", userId);
                return new DeviceLinkOutcome(DeviceAuthOutcome.InvalidCode, null);
            }

            authorization.UserId = userId;
            authorization.Status = DeviceAuthorizationStatus.Approved;
            await _db.SaveChangesAsync(cancellationToken);

            ClearFailedLinks(userId);

            return new DeviceLinkOutcome(DeviceAuthOutcome.Ok, new LinkDeviceResponse
            {
                DeviceName = authorization.DeviceName,
                Platform = authorization.Platform
            });
        }

        public async Task<DeviceTokenOutcome> RefreshAsync(string refreshToken, CancellationToken cancellationToken)
        {
            if (string.IsNullOrWhiteSpace(refreshToken))
                return new DeviceTokenOutcome(DeviceAuthOutcome.AccessDenied, null);

            var hash = Hash(refreshToken);
            var device = await _db.Devices.FirstOrDefaultAsync(d => d.RefreshTokenHash == hash, cancellationToken);

            if (device == null)
            {
                // El token no corresponde a ningun dispositivo vivo. Puede ser basura, o
                // puede ser un token ya rotado: en ese caso el dispositivo legitimo ya
                // tiene otro hash y este es una reutilizacion. No hay a quien revocar
                // porque el hash viejo no se guarda, asi que solo se rechaza y se registra.
                _logger.LogWarning("Refresh token no reconocido.");
                return new DeviceTokenOutcome(DeviceAuthOutcome.AccessDenied, null);
            }

            if (device.RevokedAt != null)
                return new DeviceTokenOutcome(DeviceAuthOutcome.AccessDenied, null);

            var user = await _db.Users.FirstOrDefaultAsync(u => u.UserId == device.UserId, cancellationToken);
            if (user == null)
                return new DeviceTokenOutcome(DeviceAuthOutcome.AccessDenied, null);

            // Rotacion: el token anterior deja de servir en cuanto se emite el nuevo.
            var newRefresh = GenerateOpaqueToken();
            device.RefreshTokenHash = Hash(newRefresh);
            device.LastSeenAt = DateTime.UtcNow;
            await _db.SaveChangesAsync(cancellationToken);

            var access = _userService.GenerateDeviceToken(user, device.DeviceId, device.Scopes, AccessTokenLifetime);

            return new DeviceTokenOutcome(DeviceAuthOutcome.Ok, new DeviceTokenResponse
            {
                AccessToken = access.TokenValue,
                RefreshToken = newRefresh,
                ExpiresIn = (int)AccessTokenLifetime.TotalSeconds,
                DeviceId = device.DeviceId,
                Scopes = device.Scopes
            });
        }

        public async Task<List<DeviceDto>> ListAsync(string userId, CancellationToken cancellationToken)
        {
            return await _db.Devices
                .Where(d => d.UserId == userId && d.RevokedAt == null)
                .OrderByDescending(d => d.CreatedAt)
                .Select(d => new DeviceDto
                {
                    DeviceId = d.DeviceId,
                    Name = d.Name,
                    Platform = d.Platform,
                    CreatedAt = d.CreatedAt,
                    LastSeenAt = d.LastSeenAt
                })
                .ToListAsync(cancellationToken);
        }

        public async Task<bool> RevokeAsync(string userId, string deviceId, CancellationToken cancellationToken)
        {
            var device = await _db.Devices
                .FirstOrDefaultAsync(d => d.DeviceId == deviceId && d.UserId == userId, cancellationToken);

            if (device == null)
                return false;

            device.RevokedAt = DateTime.UtcNow;

            // Invalidar el refresh token corta el acceso en cuanto expire el access token
            // vigente, que dura 15 minutos.
            device.RefreshTokenHash = Hash(GenerateOpaqueToken());

            await _db.SaveChangesAsync(cancellationToken);

            _logger.LogInformation("Dispositivo {DeviceId} revocado por el usuario {UserId}.", deviceId, userId);
            return true;
        }

        // ---- Utilidades ----

        private async Task<string> GenerateUniqueUserCodeAsync(CancellationToken cancellationToken)
        {
            for (var intento = 0; intento < 10; intento++)
            {
                var code = GenerateUserCode();
                var enUso = await _db.DeviceAuthorizations.AnyAsync(
                    a => a.UserCode == code && a.Status == DeviceAuthorizationStatus.Pending,
                    cancellationToken);

                if (!enUso)
                    return code;
            }

            throw new InvalidOperationException("No se pudo generar un codigo de emparejamiento unico.");
        }

        private static string GenerateUserCode()
        {
            var chars = new char[UserCodeLength];
            for (var i = 0; i < UserCodeLength; i++)
                chars[i] = CodeAlphabet[RandomNumberGenerator.GetInt32(CodeAlphabet.Length)];

            return new string(chars);
        }

        /// <summary>Agrega el guion solo para mostrarlo: "K7M2QX" queda "K7M-2QX".</summary>
        private static string FormatUserCode(string code) =>
            code.Length == UserCodeLength ? $"{code[..3]}-{code[3..]}" : code;

        /// <summary>Mayusculas, sin guiones ni espacios. Es como se guarda y se compara.</summary>
        private static string NormalizeUserCode(string? code) =>
            (code ?? string.Empty).Trim().ToUpperInvariant().Replace("-", string.Empty).Replace(" ", string.Empty);

        private static string GenerateOpaqueToken()
        {
            var bytes = RandomNumberGenerator.GetBytes(32);
            return Convert.ToBase64String(bytes)
                .Replace("+", "-").Replace("/", "_").TrimEnd('=');
        }

        private static string Hash(string value) =>
            Convert.ToBase64String(SHA256.HashData(Encoding.UTF8.GetBytes(value)));

        private static bool IsLinkRateLimited(string userId)
        {
            lock (FailedLinkLock)
            {
                if (!FailedLinkAttempts.TryGetValue(userId, out var attempts))
                    return false;

                attempts.RemoveAll(a => DateTime.UtcNow - a > LinkAttemptWindow);
                return attempts.Count >= MaxFailedLinkAttempts;
            }
        }

        private static void RegisterFailedLink(string userId)
        {
            lock (FailedLinkLock)
            {
                if (!FailedLinkAttempts.TryGetValue(userId, out var attempts))
                {
                    attempts = new List<DateTime>();
                    FailedLinkAttempts[userId] = attempts;
                }

                attempts.RemoveAll(a => DateTime.UtcNow - a > LinkAttemptWindow);
                attempts.Add(DateTime.UtcNow);
            }
        }

        private static void ClearFailedLinks(string userId)
        {
            lock (FailedLinkLock)
            {
                FailedLinkAttempts.Remove(userId);
            }
        }
    }
}
