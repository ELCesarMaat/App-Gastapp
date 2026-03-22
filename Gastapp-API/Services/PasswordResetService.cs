using System;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Gastapp.Models;
using Gastapp_API.Data;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;

namespace Gastapp.Services
{
    public class PasswordResetService : IPasswordResetService
    {
        private static readonly TimeSpan CodeLifetime = TimeSpan.FromMinutes(15);
        private readonly GastappDbContext _dbContext;
        private readonly IEmailService _emailService;
        private readonly IUserService _userService;
        private readonly ILogger<PasswordResetService> _logger;

        public PasswordResetService(
            GastappDbContext dbContext,
            IEmailService emailService,
            IUserService userService,
            ILogger<PasswordResetService> logger)
        {
            _dbContext = dbContext;
            _emailService = emailService;
            _userService = userService;
            _logger = logger;
        }

        public async Task RequestPasswordResetAsync(string email, CancellationToken cancellationToken = default)
        {
            ArgumentException.ThrowIfNullOrWhiteSpace(email);

            var user = await FindUserByEmailAsync(email, cancellationToken);
            if (user == null)
            {
                _logger.LogInformation("Se solicitó restablecimiento para un correo no registrado: {Email}", email);
                return;
            }

            if (string.IsNullOrWhiteSpace(user.Email))
            {
                _logger.LogWarning("El usuario {UserId} no tiene correo configurado para restablecer contraseña", user.UserId);
                return;
            }

            var code = GenerateCode();
            var hash = HashCode(code, user.UserId);

            var previousHash = user.PasswordResetCodeHash;
            var previousExpiry = user.PasswordResetCodeExpiresAt;

            user.PasswordResetCodeHash = hash;
            user.PasswordResetCodeExpiresAt = DateTime.UtcNow.Add(CodeLifetime);

            try
            {
                await _emailService.SendPasswordResetCodeAsync(user.Email!, user.Name, code, cancellationToken);
                await _dbContext.SaveChangesAsync(cancellationToken);
            }
            catch
            {
                user.PasswordResetCodeHash = previousHash;
                user.PasswordResetCodeExpiresAt = previousExpiry;
                throw;
            }
        }

        public async Task<bool> ValidateResetCodeAsync(string email, string code, CancellationToken cancellationToken = default)
        {
            ArgumentException.ThrowIfNullOrWhiteSpace(email);
            ArgumentException.ThrowIfNullOrWhiteSpace(code);

            var user = await FindUserByEmailAsync(email, cancellationToken);
            if (user == null)
            {
                return false;
            }

            return IsCodeValid(user, code);
        }

        public async Task<bool> ResetPasswordAsync(string email, string code, string newPassword, CancellationToken cancellationToken = default)
        {
            ArgumentException.ThrowIfNullOrWhiteSpace(email);
            ArgumentException.ThrowIfNullOrWhiteSpace(code);
            ArgumentException.ThrowIfNullOrWhiteSpace(newPassword);

            var user = await FindUserByEmailAsync(email, cancellationToken);
            if (user == null)
            {
                return false;
            }

            if (!IsCodeValid(user, code))
            {
                return false;
            }

            user.PassWordHash = _userService.HashPassword(newPassword);
            user.PasswordResetCodeHash = null;
            user.PasswordResetCodeExpiresAt = null;

            await _dbContext.SaveChangesAsync(cancellationToken);
            return true;
        }

        private async Task<User?> FindUserByEmailAsync(string email, CancellationToken cancellationToken)
        {
            var normalizedEmail = email.Trim();
            return await _dbContext.Users.FirstOrDefaultAsync(
                u => u.Email != null && EF.Functions.ILike(u.Email, normalizedEmail),
                cancellationToken);
        }

        private static string GenerateCode()
        {
            var value = RandomNumberGenerator.GetInt32(0, 1_000_000);
            return value.ToString("D6");
        }

        private static string HashCode(string code, string userId)
        {
            var data = Encoding.UTF8.GetBytes($"{code}:{userId}");
            var hashBytes = SHA256.HashData(data);
            return Convert.ToBase64String(hashBytes);
        }

        private static bool IsCodeValid(User user, string code)
        {
            if (string.IsNullOrWhiteSpace(user.PasswordResetCodeHash) ||
                user.PasswordResetCodeExpiresAt == null ||
                user.PasswordResetCodeExpiresAt <= DateTime.UtcNow)
            {
                return false;
            }

            try
            {
                var expectedHash = HashCode(code, user.UserId);
                var stored = Convert.FromBase64String(user.PasswordResetCodeHash);
                var provided = Convert.FromBase64String(expectedHash);
                return CryptographicOperations.FixedTimeEquals(stored, provided);
            }
            catch (FormatException)
            {
                return false;
            }
        }

        public async Task<bool> GenerateAndSendTemporaryPasswordAsync(string email, CancellationToken cancellationToken = default)
        {
            ArgumentException.ThrowIfNullOrWhiteSpace(email);

            var user = await FindUserByEmailAsync(email, cancellationToken);
            if (user == null)
            {
                _logger.LogInformation("Se solicitó contraseña temporal para un correo no registrado: {Email}", email);
                return false;
            }

            if (string.IsNullOrWhiteSpace(user.Email))
            {
                _logger.LogWarning("El usuario {UserId} no tiene correo configurado para restablecer contraseña", user.UserId);
                return false;
            }

            var temporaryPassword = GenerateTemporaryPassword();
            var hashedPassword = _userService.HashPassword(temporaryPassword);

            try
            {
                await _emailService.SendTemporaryPasswordAsync(user.Email!, user.Name, temporaryPassword, cancellationToken);
                user.PassWordHash = hashedPassword;
                user.PasswordResetCodeHash = null;
                user.PasswordResetCodeExpiresAt = null;
                
                await _dbContext.SaveChangesAsync(cancellationToken);
                return true;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error al enviar contraseña temporal al usuario {UserId}", user.UserId);
                return false;
            }
        }

        private static string GenerateTemporaryPassword()
        {
            const string lowercase = "abcdefghijklmnopqrstuvwxyz";
            const string uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
            const string digits = "0123456789";
            const string symbols = "!@#$%^&*-_=+";

            var password = new StringBuilder();
            var random = new Random();

            // Agregar mínimo 1 mayúscula
            password.Append(uppercase[random.Next(uppercase.Length)]);

            // Agregar mínimo 1 símbolo
            password.Append(symbols[random.Next(symbols.Length)]);

            // Agregar caracteres adicionales para llegar a 10 caracteres (8 mínimo + 1 mayúscula + 1 símbolo)
            string allChars = lowercase + uppercase + digits + symbols;
            for (int i = password.Length; i < 10; i++)
            {
                password.Append(allChars[random.Next(allChars.Length)]);
            }

            // Barajar la contraseña para no tener mayúscula y símbolo al principio siempre
            var chars = password.ToString().ToCharArray();
            for (int i = chars.Length - 1; i > 0; i--)
            {
                int randomIndex = random.Next(i + 1);
                (chars[i], chars[randomIndex]) = (chars[randomIndex], chars[i]);
            }

            return new string(chars);
        }
    }
}
