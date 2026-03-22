using System.Threading;
using System.Threading.Tasks;

namespace Gastapp.Services
{
    public interface IEmailService
    {
        Task SendPasswordResetCodeAsync(string email, string name, string code, CancellationToken cancellationToken = default);
        Task SendTemporaryPasswordAsync(string email, string name, string temporaryPassword, CancellationToken cancellationToken = default);
    }
}
