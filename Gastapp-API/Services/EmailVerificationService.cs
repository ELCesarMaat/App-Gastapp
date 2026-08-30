using System;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Gastapp_API.Data;
using Gastapp_API.Models;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;

namespace Gastapp.Services
{
    public class EmailVerificationService : IEmailVerificationService
    {
        private static readonly TimeSpan CodeLifetime = TimeSpan.FromMinutes(15);
        private const int MaxAttempts = 5;

        private readonly GastappDbContext _dbContext;
        private readonly IEmailService _emailService;
        private readonly ILogger<EmailVerificationService> _logger;

        public EmailVerificationService(
            GastappDbContext dbContext,
            IEmailService emailService,
            ILogger<EmailVerificationService> logger)
        {
            _dbContext = dbContext;
            _emailService = emailService;
            _logger = logger;
        }

        public async Task<EmailVerificationResult> SendVerificationCodeAsync(string email, CancellationToken cancellationToken = default)
        {
            ArgumentException.ThrowIfNullOrWhiteSpace(email);

            var normalized = Normalize(email);

            var alreadyRegistered = await _dbContext.Users
                .AnyAsync(u => u.Email != null && EF.Functions.ILike(u.Email, normalized), cancellationToken);

            if (alreadyRegistered)
                return EmailVerificationResult.EmailAlreadyRegistered;

            // Una sola verificacion viva por correo: se reemplaza la anterior.
            var existing = await _dbContext.EmailVerifications
                .Where(v => EF.Functions.ILike(v.Email, normalized))
                .ToListAsync(cancellationToken);

            if (existing.Count > 0)
                _dbContext.EmailVerifications.RemoveRange(existing);

            var code = GenerateCode();

            var verification = new EmailVerification
            {
                Email = normalized,
                CodeHash = HashCode(code, normalized),
                ExpiresAt = DateTime.UtcNow.Add(CodeLifetime),
                CreatedAt = DateTime.UtcNow
            };

            await _dbContext.EmailVerifications.AddAsync(verification, cancellationToken);

            // Si el correo no sale, no dejamos el registro guardado.
            await _emailService.SendEmailVerificationCodeAsync(normalized, code, cancellationToken);
            await _dbContext.SaveChangesAsync(cancellationToken);

            return EmailVerificationResult.Ok;
        }

        public async Task<EmailVerificationResult> VerifyCodeAsync(string email, string code, CancellationToken cancellationToken = default)
        {
            ArgumentException.ThrowIfNullOrWhiteSpace(email);
            ArgumentException.ThrowIfNullOrWhiteSpace(code);

            var normalized = Normalize(email);

            var verification = await _dbContext.EmailVerifications
                .Where(v => EF.Functions.ILike(v.Email, normalized))
                .OrderByDescending(v => v.CreatedAt)
                .FirstOrDefaultAsync(cancellationToken);

            if (verification == null || verification.ExpiresAt <= DateTime.UtcNow)
                return EmailVerificationResult.InvalidOrExpiredCode;

            if (verification.Attempts >= MaxAttempts)
                return EmailVerificationResult.TooManyAttempts;

            if (!CodeMatches(verification, code, normalized))
            {
                verification.Attempts++;
                await _dbContext.SaveChangesAsync(cancellationToken);
                return EmailVerificationResult.InvalidOrExpiredCode;
            }

            verification.VerifiedAt = DateTime.UtcNow;
            await _dbContext.SaveChangesAsync(cancellationToken);

            return EmailVerificationResult.Ok;
        }

        public async Task<bool> IsEmailVerifiedAsync(string email, CancellationToken cancellationToken = default)
        {
            if (string.IsNullOrWhiteSpace(email))
                return false;

            var normalized = Normalize(email);

            return await _dbContext.EmailVerifications.AnyAsync(
                v => EF.Functions.ILike(v.Email, normalized)
                     && v.VerifiedAt != null
                     && v.ExpiresAt > DateTime.UtcNow,
                cancellationToken);
        }

        public async Task ConsumeVerificationAsync(string email, CancellationToken cancellationToken = default)
        {
            if (string.IsNullOrWhiteSpace(email))
                return;

            var normalized = Normalize(email);

            var records = await _dbContext.EmailVerifications
                .Where(v => EF.Functions.ILike(v.Email, normalized))
                .ToListAsync(cancellationToken);

            if (records.Count == 0)
                return;

            _dbContext.EmailVerifications.RemoveRange(records);
            await _dbContext.SaveChangesAsync(cancellationToken);
        }

        private static string Normalize(string email) => email.Trim();

        private static string GenerateCode() =>
            RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6");

        private static string HashCode(string code, string email)
        {
            var data = Encoding.UTF8.GetBytes($"{code}:{email.ToLowerInvariant()}");
            return Convert.ToBase64String(SHA256.HashData(data));
        }

        private static bool CodeMatches(EmailVerification verification, string code, string email)
        {
            try
            {
                var expected = HashCode(code.Trim(), email);
                return CryptographicOperations.FixedTimeEquals(
                    Convert.FromBase64String(verification.CodeHash),
                    Convert.FromBase64String(expected));
            }
            catch (FormatException)
            {
                return false;
            }
        }
    }
}
