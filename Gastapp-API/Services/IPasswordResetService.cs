using System.Threading;
using System.Threading.Tasks;

namespace Gastapp.Services
{
    public enum PasswordResetCodeResult
    {
        Ok,
        InvalidOrExpired,
        /// <summary>Se fallo el codigo demasiadas veces: se invalido y hay que pedir otro.</summary>
        TooManyAttempts
    }

    public interface IPasswordResetService
    {
        Task RequestPasswordResetAsync(string email, CancellationToken cancellationToken = default);
        Task<PasswordResetCodeResult> ValidateResetCodeAsync(string email, string code, CancellationToken cancellationToken = default);
        Task<PasswordResetCodeResult> ResetPasswordAsync(string email, string code, string newPassword, CancellationToken cancellationToken = default);
        Task<bool> GenerateAndSendTemporaryPasswordAsync(string email, CancellationToken cancellationToken = default);
    }
}
