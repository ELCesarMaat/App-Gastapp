using System.Threading.Tasks;
using Gastapp.Models.Models;

namespace Gastapp.Services.AppUpdateService
{
    public interface IAppUpdateService
    {
        Task<AppLatestVersionDto?> CheckForUpdateAsync();

        Task DownloadAndInstallAsync(AppLatestVersionDto latest);
    }
}
