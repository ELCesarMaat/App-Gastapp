using System.Threading.Tasks;

namespace Gastapp.Services.BackupService
{
    public interface IBackupService
    {
        Task<string?> ExportDatabaseFileAsync();
        Task<string?> ExportJsonBackupAsync();
        Task<bool> PickAndRestoreBackupAsync();
    }
}
