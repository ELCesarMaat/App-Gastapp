using System.Threading;
using System.Threading.Tasks;

namespace Gastapp.Services
{
    public interface IPasswordResetService
    {
        Task RequestPasswordResetAsync(string email, CancellationToken cancellationToken = default);
        Task<bool> ValidateResetCodeAsync(string email, string code, CancellationToken cancellationToken = default);
        Task<bool> ResetPasswordAsync(string email, string code, string newPassword, CancellationToken cancellationToken = default);
    }
}
