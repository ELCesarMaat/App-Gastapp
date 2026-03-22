using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.DependencyInjection;
using CommunityToolkit.Mvvm.Input;
using Gastapp.Models;
using Gastapp.Services.UserService;
using System.Globalization;

namespace Gastapp.ViewModels
{
    public partial class ProfileViewModel : ObservableObject
    {
        [ObservableProperty] private User _user = new();
        private readonly IUserService _userService;

        [ObservableProperty] private string _displayName = "Tu perfil";
        [ObservableProperty] private string _initials = "GU";
        [ObservableProperty] private string _profileSubtitle = "Mantén tus datos actualizados para recibir recomendaciones más precisas.";
        [ObservableProperty] private string _emailSummary = "Sin correo registrado";
        [ObservableProperty] private string _incomeSummary = "Ingreso pendiente de configurar";
        [ObservableProperty] private string _saveGoalSummary = "Objetivo de ahorro pendiente";
        [ObservableProperty] private string _payScheduleSummary = "Frecuencia de pago no disponible";
        [ObservableProperty] private string _birthDateSummary = "Fecha de nacimiento no disponible";
        [ObservableProperty] private bool _isCloudSyncComplete = true;
        [ObservableProperty] private string _cloudSyncTitle = "Toda tu información está sincronizada en la nube";
        [ObservableProperty] private string _cloudSyncMessage = "No hay cambios pendientes por subir desde este dispositivo.";
        [ObservableProperty] private string _cloudSyncDetail = "";
        [ObservableProperty] private string _cloudSyncBadgeText = "En la nube";
        [ObservableProperty] private string _cloudSyncCardBackground = "#F5FBF8";
        [ObservableProperty] private string _cloudSyncCardStroke = "#D8E4E0";
        [ObservableProperty] private string _cloudSyncBadgeBackground = "#DDF5EF";
        [ObservableProperty] private string _cloudSyncBadgeTextColor = "#126E63";

        public ProfileViewModel(IUserService userService)
        {
            _userService = userService;
            _ = GetUser();
        }

        public async Task GetUser()
        {
            User = await _userService.GetUser() ?? new User();
            UpdateProfileHighlights();
            await UpdateCloudSyncStatusAsync();
        }

        private async Task UpdateCloudSyncStatusAsync()
        {
            var syncStatus = await _userService.GetCloudSyncStatusAsync();

            IsCloudSyncComplete = syncStatus.IsComplete;

            if (IsCloudSyncComplete)
            {
                CloudSyncTitle = "Toda tu información está sincronizada en la nube";
                CloudSyncMessage = "No hay cambios pendientes por subir desde este dispositivo.";
                CloudSyncDetail = "Tus gastos, categorías y datos de perfil ya están respaldados en línea.";
                CloudSyncBadgeText = "En la nube";
                CloudSyncCardBackground = "#F5FBF8";
                CloudSyncCardStroke = "#D8E4E0";
                CloudSyncBadgeBackground = "#DDF5EF";
                CloudSyncBadgeTextColor = "#126E63";
                return;
            }

            CloudSyncTitle = "Sincronización pendiente";
            CloudSyncMessage = $"Faltan de sincronizar {syncStatus.TotalPendingItems} elemento{(syncStatus.TotalPendingItems == 1 ? string.Empty : "s")}.";
            CloudSyncDetail = string.IsNullOrWhiteSpace(syncStatus.Breakdown)
                ? "Se volverá a sincronizar automáticamente cuando vuelva a tener conexión."
                : $"{syncStatus.Breakdown} Se volverá a sincronizar automáticamente cuando vuelva a tener conexión.";
            CloudSyncBadgeText = "Pendiente";
            CloudSyncCardBackground = "#FFF8EE";
            CloudSyncCardStroke = "#F3D6A4";
            CloudSyncBadgeBackground = "#FCE7C3";
            CloudSyncBadgeTextColor = "#8A5A00";
        }

        private void UpdateProfileHighlights()
        {
            var safeName = string.IsNullOrWhiteSpace(User.Name) ? "Tu perfil" : User.Name.Trim();
            DisplayName = safeName;

            var parts = safeName.Split(' ', StringSplitOptions.RemoveEmptyEntries);
            Initials = string.Concat(parts.Take(2).Select(part => char.ToUpperInvariant(part[0])));
            if (string.IsNullOrWhiteSpace(Initials))
                Initials = "GU";

            EmailSummary = string.IsNullOrWhiteSpace(User.Email) ? "Sin correo registrado" : User.Email;
            IncomeSummary = User.Salary > 0 ? $"${User.Salary:N2} por periodo" : "Ingreso pendiente de configurar";
            SaveGoalSummary = User.PercentSave > 0
                ? $"Meta actual: {User.PercentSave:N0}% de ahorro"
                : "Define cuánto quieres separar para ahorrar";
            PayScheduleSummary = BuildPayScheduleSummary();
            BirthDateSummary = User.BirthDate > DateTime.MinValue
                ? User.BirthDate.ToString("dd 'de' MMMM 'de' yyyy", new CultureInfo("es-MX"))
                : "Fecha de nacimiento no disponible";
        }

        private string BuildPayScheduleSummary()
        {
            return User.IncomeTypeId switch
            {
                1 when User.FirstPayDay is not null => $"Pago semanal cada {CultureInfo.CurrentCulture.DateTimeFormat.DayNames[User.FirstPayDay.Value]}",
                2 when User.FirstPayDay is not null && User.SecondPayDay is not null => $"Pagos quincenales los días {User.FirstPayDay} y {User.SecondPayDay}",
                3 when User.FirstPayDay is not null => $"Pago mensual el día {User.FirstPayDay}",
                _ => "Frecuencia de pago no disponible"
            };
        }

        [RelayCommand]
        private void Logout()
        {
            _userService.ClearLocalSession();
            if (Application.Current is not null)
                Application.Current.MainPage = new AppShell();

        }
    }
}
