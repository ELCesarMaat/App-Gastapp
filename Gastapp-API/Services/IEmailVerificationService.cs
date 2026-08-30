using System.Threading;
using System.Threading.Tasks;

namespace Gastapp.Services
{
    public enum EmailVerificationResult
    {
        Ok,
        EmailAlreadyRegistered,
        InvalidOrExpiredCode,
        TooManyAttempts
    }

    public interface IEmailVerificationService
    {
        Task<EmailVerificationResult> SendVerificationCodeAsync(string email, CancellationToken cancellationToken = default);

        Task<EmailVerificationResult> VerifyCodeAsync(string email, string code, CancellationToken cancellationToken = default);

        /// <summary>True si el correo tiene una verificacion vigente y confirmada.</summary>
        Task<bool> IsEmailVerifiedAsync(string email, CancellationToken cancellationToken = default);

        /// <summary>Se llama al crear la cuenta para que el codigo no se reutilice.</summary>
        Task ConsumeVerificationAsync(string email, CancellationToken cancellationToken = default);
    }
}
