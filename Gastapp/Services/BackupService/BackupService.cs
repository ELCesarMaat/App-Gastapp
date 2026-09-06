using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading.Tasks;
using CommunityToolkit.Maui.Alerts;
using CommunityToolkit.Maui.Core;
using Gastapp.Data;
using Gastapp.Models;
using Gastapp.Utils;
using Microsoft.EntityFrameworkCore;
using Microsoft.Maui.ApplicationModel.DataTransfer;
using Microsoft.Maui.Devices;
using Microsoft.Maui.Storage;

namespace Gastapp.Services.BackupService
{
    public class GastappJsonBackup
    {
        public string Version { get; set; } = "1.0";
        public DateTime ExportedAt { get; set; } = DateTime.UtcNow;
        public string AppVersion { get; set; } = "1.0.0";
        public List<User> Users { get; set; } = new();
        public List<IncomeType> IncomeTypes { get; set; } = new();
        public List<Category> Categories { get; set; } = new();
        public List<CreditCard> CreditCards { get; set; } = new();
        public List<Spending> Spendings { get; set; } = new();
    }

    public class BackupService(GastappDbContext dbContext) : IBackupService
    {
        private readonly GastappDbContext _dbContext = dbContext;

        public async Task<string?> ExportDatabaseFileAsync()
        {
            try
            {
                // Force WAL checkpoint to ensure all data is in the primary .db file
                try
                {
                    await _dbContext.Database.ExecuteSqlRawAsync("PRAGMA wal_checkpoint(TRUNCATE);");
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"[BackupService] wal_checkpoint warning: {ex.Message}");
                }

                var dbPath = GastappDbContext.GetDatabasePath();
                if (!File.Exists(dbPath))
                {
                    await AlertHelper.ShowAlertAsync("Error", "No se encontró el archivo de base de datos local.", "OK");
                    return null;
                }

                var timestamp = DateTime.Now.ToString("yyyyMMdd_HHmmss");
                var backupFileName = $"gastapp_backup_{timestamp}.db";
                var cacheDir = FileSystem.CacheDirectory;
                var backupFilePath = Path.Combine(cacheDir, backupFileName);

                File.Copy(dbPath, backupFilePath, overwrite: true);

                await Share.Default.RequestAsync(new ShareFileRequest
                {
                    Title = "Exportar Base de Datos Gastapp (.db)",
                    File = new ShareFile(backupFilePath)
                });

                return backupFilePath;
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[BackupService] ExportDatabaseFileAsync error: {ex}");
                await AlertHelper.ShowAlertAsync("Error", $"No se pudo exportar la base de datos: {ex.Message}", "OK");
                return null;
            }
        }

        public async Task<string?> ExportJsonBackupAsync()
        {
            try
            {
                _dbContext.EnsureSchemaUpToDate();

                var users = await _dbContext.Users.AsNoTracking().ToListAsync();
                var incomeTypes = await _dbContext.IncomeTypes.AsNoTracking().ToListAsync();
                var categories = await _dbContext.Categories.AsNoTracking().ToListAsync();
                var creditCards = await _dbContext.CreditCards.AsNoTracking().ToListAsync();
                var spendings = await _dbContext.Spending.AsNoTracking().ToListAsync();

                // Clean navigation properties to avoid circular references during serialization
                foreach (var c in categories) { c.User = null!; c.Spendings = null!; }
                foreach (var cc in creditCards) { cc.User = null!; }
                foreach (var s in spendings) { s.User = null!; s.Category = null!; }
                foreach (var u in users) { u.Categories = null!; u.CreditCards = null!; u.Spendings = null!; u.IncomeType = null!; }

                var backup = new GastappJsonBackup
                {
                    Version = "1.0",
                    ExportedAt = DateTime.UtcNow,
                    AppVersion = AppInfo.Current.VersionString,
                    Users = users,
                    IncomeTypes = incomeTypes,
                    Categories = categories,
                    CreditCards = creditCards,
                    Spendings = spendings
                };

                var options = new JsonSerializerOptions
                {
                    WriteIndented = true,
                    ReferenceHandler = ReferenceHandler.IgnoreCycles,
                    DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
                };

                var jsonString = JsonSerializer.Serialize(backup, options);
                var timestamp = DateTime.Now.ToString("yyyyMMdd_HHmmss");
                var backupFileName = $"gastapp_backup_{timestamp}.json";
                var cacheDir = FileSystem.CacheDirectory;
                var backupFilePath = Path.Combine(cacheDir, backupFileName);

                await File.WriteAllTextAsync(backupFilePath, jsonString);

                await Share.Default.RequestAsync(new ShareFileRequest
                {
                    Title = "Exportar Respaldo JSON Gastapp",
                    File = new ShareFile(backupFilePath)
                });

                return backupFilePath;
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[BackupService] ExportJsonBackupAsync error: {ex}");
                await AlertHelper.ShowAlertAsync("Error", $"No se pudo exportar el respaldo JSON: {ex.Message}", "OK");
                return null;
            }
        }

        public async Task<bool> PickAndRestoreBackupAsync()
        {
            try
            {
                var customFileType = new FilePickerFileType(
                    new Dictionary<DevicePlatform, IEnumerable<string>>
                    {
                        { DevicePlatform.Android, new[] { "application/octet-stream", "application/json", "application/x-sqlite3", "*/*" } },
                        { DevicePlatform.WinUI, new[] { ".db", ".json", ".sqlite", ".sqlite3" } },
                        { DevicePlatform.iOS, new[] { "public.data", "public.json" } }
                    });

                var pickResult = await FilePicker.Default.PickAsync(new PickOptions
                {
                    PickerTitle = "Selecciona un archivo de respaldo (.db o .json)",
                    FileTypes = customFileType
                });

                if (pickResult == null)
                    return false;

                var fileName = pickResult.FileName ?? "archivo seleccionado";
                var fileInfo = new FileInfo(pickResult.FullPath);
                var fileDetails = fileInfo.Exists
                    ? $"\n\nArchivo: {fileName}\nTamaño: {fileInfo.Length / 1024.0:N0} KB\nModificado: {fileInfo.LastWriteTime:dd/MM/yyyy HH:mm}"
                    : $"\n\nArchivo: {fileName}";

                var confirm = await AlertHelper.ShowAlertAsync(
                    "Restaurar Respaldo",
                    $"¿Deseas restaurar la información desde este archivo?{fileDetails}\n\nEsta acción reemplazará los datos locales actuales en la aplicación.",
                    "Restaurar",
                    "Cancelar");

                if (!confirm)
                    return false;

                var isJson = fileName.EndsWith(".json", StringComparison.OrdinalIgnoreCase);

                if (isJson)
                {
                    return await RestoreFromJsonFileAsync(pickResult.FullPath);
                }
                else
                {
                    return await RestoreFromDatabaseFileAsync(pickResult.FullPath);
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[BackupService] PickAndRestoreBackupAsync error: {ex}");
                await AlertHelper.ShowAlertAsync("Error", $"No se pudo restaurar el respaldo: {ex.Message}", "OK");
                return false;
            }
        }

        private static async Task<bool> IsValidSqliteFileAsync(string path)
        {
            try
            {
                await using var stream = File.OpenRead(path);
                var header = new byte[16];
                var bytesRead = await stream.ReadAsync(header.AsMemory(0, 16));
                if (bytesRead < 16)
                    return false;

                return System.Text.Encoding.ASCII.GetString(header).StartsWith("SQLite format 3", StringComparison.Ordinal);
            }
            catch
            {
                return false;
            }
        }

        private async Task<bool> RestoreFromDatabaseFileAsync(string sourceFilePath)
        {
            try
            {
                if (!await IsValidSqliteFileAsync(sourceFilePath))
                {
                    await AlertHelper.ShowAlertAsync(
                        "Archivo no válido",
                        "El archivo seleccionado no es una base de datos SQLite válida. No se realizó ningún cambio en tu información local.",
                        "Entendido");
                    return false;
                }

                var targetDbPath = GastappDbContext.GetDatabasePath();

                // Close active connections before overwriting
                try
                {
                    var conn = _dbContext.Database.GetDbConnection();
                    if (conn.State == System.Data.ConnectionState.Open)
                        await conn.CloseAsync();
                    Microsoft.Data.Sqlite.SqliteConnection.ClearAllPools();
                }
                catch
                {
                }

                File.Copy(sourceFilePath, targetDbPath, overwrite: true);

                // Re-verify schema
                _dbContext.EnsureSchemaUpToDate();

                var spendingCount = await _dbContext.Spending.CountAsync();
                var cardCount = await _dbContext.CreditCards.CountAsync();
                var categoryCount = await _dbContext.Categories.CountAsync();

                await AlertHelper.ShowAlertAsync(
                    "Restauración Completada",
                    $"Base de datos restaurada correctamente.\n\n• {spendingCount} Gastos\n• {cardCount} Tarjetas de crédito\n• {categoryCount} Categorías",
                    "Aceptar");

                return true;
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[BackupService] RestoreFromDatabaseFileAsync error: {ex}");
                await AlertHelper.ShowAlertAsync("Error", $"Error al restaurar archivo .db: {ex.Message}", "OK");
                return false;
            }
        }

        private async Task<bool> RestoreFromJsonFileAsync(string jsonFilePath)
        {
            try
            {
                var jsonContent = await File.ReadAllTextAsync(jsonFilePath);
                var options = new JsonSerializerOptions
                {
                    PropertyNameCaseInsensitive = true,
                    ReferenceHandler = ReferenceHandler.IgnoreCycles
                };

                var backup = JsonSerializer.Deserialize<GastappJsonBackup>(jsonContent, options);
                if (backup == null)
                {
                    await AlertHelper.ShowAlertAsync("Error", "El archivo JSON no tiene un formato válido de respaldo.", "OK");
                    return false;
                }

                await _dbContext.ResetDatabaseAsync();

                if (backup.IncomeTypes != null && backup.IncomeTypes.Count > 0)
                {
                    foreach (var inc in backup.IncomeTypes)
                    {
                        var exists = await _dbContext.IncomeTypes.AnyAsync(x => x.IncomeTypeId == inc.IncomeTypeId);
                        if (!exists)
                            _dbContext.IncomeTypes.Add(inc);
                    }
                    await _dbContext.SaveChangesAsync();
                }

                if (backup.Users != null && backup.Users.Count > 0)
                {
                    foreach (var u in backup.Users)
                    {
                        u.Categories = null!;
                        u.CreditCards = null!;
                        u.Spendings = null!;
                        _dbContext.Users.Add(u);
                    }
                    await _dbContext.SaveChangesAsync();
                }

                if (backup.Categories != null && backup.Categories.Count > 0)
                {
                    foreach (var cat in backup.Categories)
                    {
                        cat.User = null!;
                        cat.Spendings = null!;
                        _dbContext.Categories.Add(cat);
                    }
                    await _dbContext.SaveChangesAsync();
                }

                if (backup.CreditCards != null && backup.CreditCards.Count > 0)
                {
                    foreach (var card in backup.CreditCards)
                    {
                        card.User = null!;
                        _dbContext.CreditCards.Add(card);
                    }
                    await _dbContext.SaveChangesAsync();
                }

                if (backup.Spendings != null && backup.Spendings.Count > 0)
                {
                    foreach (var sp in backup.Spendings)
                    {
                        sp.User = null!;
                        sp.Category = null!;
                        _dbContext.Spending.Add(sp);
                    }
                    await _dbContext.SaveChangesAsync();
                }

                var spendingCount = backup.Spendings?.Count ?? 0;
                var cardCount = backup.CreditCards?.Count ?? 0;
                var categoryCount = backup.Categories?.Count ?? 0;

                await AlertHelper.ShowAlertAsync(
                    "Restauración Completada",
                    $"Respaldo JSON restaurado correctamente.\n\n• {spendingCount} Gastos\n• {cardCount} Tarjetas de crédito\n• {categoryCount} Categorías",
                    "Aceptar");

                return true;
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[BackupService] RestoreFromJsonFileAsync error: {ex}");
                await AlertHelper.ShowAlertAsync("Error", $"Error al restaurar JSON: {ex.Message}", "OK");
                return false;
            }
        }
    }
}
