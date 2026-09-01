using System.Threading;
using System.Threading.Tasks;
using Gastapp.Models.Models;

namespace Gastapp.Services
{
    public interface IAppUpdateService
    {
        Task<AppLatestVersionDto?> GetLatestVersionAsync(CancellationToken cancellationToken);
    }
}
