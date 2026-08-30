using System;
using System.Text.Json;
using System.Threading.Tasks;
using Microsoft.Maui.Storage;

namespace Gastapp.Services
{
    /// <summary>
    /// Lo que el usuario lleva capturado en el wizard de registro.
    /// La contrasena NO va aqui: se guarda aparte en SecureStorage.
    /// </summary>
    public class RegisterDraft
    {
        public int Step { get; set; }
        public string Email { get; set; } = string.Empty;
        public bool EmailVerified { get; set; }
        public string Name { get; set; } = string.Empty;
        public int BirthDay { get; set; }
        public string BirthMonth { get; set; } = string.Empty;
        public int BirthYear { get; set; }
        public int IncomeTypeId { get; set; }
        public int? FirstPayDay { get; set; }
        public int? SecondPayDay { get; set; }
        public decimal Salary { get; set; }
        public decimal PercentSave { get; set; }
        public DateTime SavedAt { get; set; } = DateTime.UtcNow;
    }

    /// <summary>
    /// Conserva el avance del registro para que el usuario no tenga que empezar
    /// de cero si cierra la app, sobre todo entre confirmar el correo y capturar
    /// sus datos (que es donde ya invirtio tiempo y recibio un codigo).
    /// </summary>
    public interface IRegisterDraftService
    {
        Task<RegisterDraft?> LoadAsync();
        Task SaveAsync(RegisterDraft draft, string password);
        Task<string> GetPasswordAsync();
        Task ClearAsync();
    }

    public class RegisterDraftService : IRegisterDraftService
    {
        private const string DraftKey = "register_draft";
        private const string PasswordKey = "register_draft_password";

        /// <summary>Pasado este tiempo el borrador se descarta: el codigo ya expiro hace mucho.</summary>
        private static readonly TimeSpan DraftLifetime = TimeSpan.FromDays(2);

        public async Task<RegisterDraft?> LoadAsync()
        {
            try
            {
                var json = Preferences.Get(DraftKey, string.Empty);
                if (string.IsNullOrWhiteSpace(json))
                    return null;

                var draft = JsonSerializer.Deserialize<RegisterDraft>(json);
                if (draft == null)
                    return null;

                if (DateTime.UtcNow - draft.SavedAt > DraftLifetime)
                {
                    await ClearAsync();
                    return null;
                }

                return draft;
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[RegisterDraft] load error: {ex.Message}");
                return null;
            }
        }

        public async Task SaveAsync(RegisterDraft draft, string password)
        {
            try
            {
                draft.SavedAt = DateTime.UtcNow;
                Preferences.Set(DraftKey, JsonSerializer.Serialize(draft));

                if (string.IsNullOrEmpty(password))
                    SecureStorage.Default.Remove(PasswordKey);
                else
                    await SecureStorage.Default.SetAsync(PasswordKey, password);
            }
            catch (Exception ex)
            {
                // Que no se pueda guardar el avance no debe romper el registro.
                System.Diagnostics.Debug.WriteLine($"[RegisterDraft] save error: {ex.Message}");
            }
        }

        public async Task<string> GetPasswordAsync()
        {
            try
            {
                return await SecureStorage.Default.GetAsync(PasswordKey) ?? string.Empty;
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[RegisterDraft] password read error: {ex.Message}");
                return string.Empty;
            }
        }

        public Task ClearAsync()
        {
            try
            {
                Preferences.Remove(DraftKey);
                SecureStorage.Default.Remove(PasswordKey);
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[RegisterDraft] clear error: {ex.Message}");
            }

            return Task.CompletedTask;
        }
    }
}
