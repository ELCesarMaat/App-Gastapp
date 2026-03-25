using System;
using System.Collections.ObjectModel;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.DependencyInjection;
using CommunityToolkit.Mvvm.Input;
using CommunityToolkit.Maui.Alerts;
using CommunityToolkit.Maui.Core;
using Gastapp.Models;
using Gastapp.Services.UserService;
using Microsoft.Maui.ApplicationModel;
using System.Globalization;
using System.Threading;

namespace Gastapp.ViewModels
{
    public partial class ProfileViewModel(IUserService userService) : ObservableObject
    {
        private readonly IUserService _userService = userService;

        [ObservableProperty] private User _user = new();
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
        [ObservableProperty] private bool _isWeekSelected;
        [ObservableProperty] private bool _isBiWeekSelected;
        [ObservableProperty] private bool _isMonthSelected;
        [ObservableProperty] private ObservableCollection<DayForWeek> _listForWeek = [];
        [ObservableProperty] private ObservableCollection<int> _firstDayList = [];
        [ObservableProperty] private ObservableCollection<int> _secondDayList = [];
        [ObservableProperty] private DayForWeek? _selectedWeekDay;
        [ObservableProperty] private int _selectedFirstDay;
        [ObservableProperty] private int _selectedSecondDay;
        [ObservableProperty] private string _salaryInput = string.Empty;
        [ObservableProperty] private string _percentSaveInput = string.Empty;
        [ObservableProperty] private bool _isSavingByPercent = true;
        [ObservableProperty] private bool _isSavingByAmount;
        [ObservableProperty] private string _fixedAmountInput = string.Empty;
        [ObservableProperty] private string _computedPercentInfo = string.Empty;
        [ObservableProperty] private bool _isAutoSavingProfile;
        [ObservableProperty] private string _profileSettingsStatusText = "Los cambios se guardan automáticamente.";
        [ObservableProperty] private string _estimatedSavingsText = string.Empty;
        [ObservableProperty] private string _estimatedSpendableText = string.Empty;

        private bool _isInitializingSettings;
        private bool _isAutoSaveInProgress;
        private bool _hasPendingAutoSave;
        private CancellationTokenSource? _autoSaveCts;
        private string _lastSavedSnapshot = string.Empty;
        private bool _isRecalculating;

        public async Task GetUser()
        {
            User = await _userService.GetUser() ?? new User();
            InitializeLists();
            LoadSettingsFromUser();
            UpdateProfileHighlights();
            await UpdateCloudSyncStatusAsync();
        }

        private void InitializeLists()
        {
            if (ListForWeek.Count == 0)
            {
                int count = 0;
                foreach (var day in DateTimeFormatInfo.CurrentInfo.DayNames)
                {
                    ListForWeek.Add(new DayForWeek
                    {
                        DayName = day,
                        DayNumber = count
                    });
                    count++;
                }
            }

            if (FirstDayList.Count == 0)
            {
                for (var i = 1; i <= 31; i++)
                {
                    FirstDayList.Add(i);
                    SecondDayList.Add(i);
                }
            }
        }

        private void LoadSettingsFromUser()
        {
            _isInitializingSettings = true;

            IsWeekSelected = IsBiWeekSelected = IsMonthSelected = false;
            switch (User.IncomeTypeId)
            {
                case 1:
                    IsWeekSelected = true;
                    SelectedWeekDay = ListForWeek.FirstOrDefault(x => x.DayNumber == User.FirstPayDay);
                    break;
                case 2:
                    IsBiWeekSelected = true;
                    SelectedFirstDay = User.FirstPayDay ?? 1;
                    SelectedSecondDay = User.SecondPayDay ?? 15;
                    break;
                case 3:
                    IsMonthSelected = true;
                    SelectedFirstDay = User.FirstPayDay ?? 1;
                    break;
                default:
                    IsMonthSelected = true;
                    SelectedFirstDay = 1;
                    break;
            }

            SalaryInput = User.Salary.ToString("0", CultureInfo.InvariantCulture);
            PercentSaveInput = User.PercentSave.ToString("0.##", CultureInfo.InvariantCulture);
            var initAmount = User.Salary * (User.PercentSave / 100m);
            FixedAmountInput = initAmount > 0 ? initAmount.ToString("0", CultureInfo.InvariantCulture) : string.Empty;
            var savedByPercent = Preferences.Default.Get("SavingsModeIsPercent", true);
            IsSavingByPercent = savedByPercent;
            IsSavingByAmount = !savedByPercent;

            _lastSavedSnapshot = BuildSnapshot();
            UpdateFinancialPreview();
            ProfileSettingsStatusText = "Los cambios se guardan automáticamente.";

            _isInitializingSettings = false;
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
            IncomeSummary = User.Salary > 0 ? $"${User.Salary:N0} por periodo" : "Ingreso pendiente de configurar";
            SaveGoalSummary = User.PercentSave > 0
                ? $"Meta actual: {User.PercentSave:N0}% de ahorro"
                : "Define cuánto quieres separar para ahorrar";
            PayScheduleSummary = BuildPayScheduleSummary();
            BirthDateSummary = User.BirthDate > DateTime.MinValue
                ? User.BirthDate.ToString("dd 'de' MMMM 'de' yyyy", new CultureInfo("es-MX"))
                : "Fecha de nacimiento no disponible";

            UpdateFinancialPreview();
        }

        private void UpdateFinancialPreview()
        {
            var salary = ParseDecimal(SalaryInput);
            var percent = ParseDecimal(PercentSaveInput);
            var estimatedSavings = salary * (percent / 100m);
            var estimatedSpendable = salary - estimatedSavings;

            IncomeSummary = salary > 0 ? $"${salary:N0} por periodo" : "Ingreso pendiente de configurar";
            SaveGoalSummary = percent > 0
                ? $"Meta actual: {percent:N0}% de ahorro"
                : "Define cuánto quieres separar para ahorrar";
            PayScheduleSummary = BuildCurrentPayScheduleSummary();
            EstimatedSavingsText = $"Ahorrarías ${estimatedSavings:N0} por periodo";
            EstimatedSpendableText = $"Tendrías ${estimatedSpendable:N0} disponibles para gastar";
            ComputedPercentInfo = IsSavingByAmount && percent > 0
                ? $"Equivale al {percent:N1}% de tu sueldo"
                : string.Empty;
        }

        private static decimal ParseDecimal(string? value)
        {
            if (string.IsNullOrWhiteSpace(value))
                return 0m;

            var normalized = value.Trim().Replace(',', '.');
            return decimal.TryParse(normalized, NumberStyles.Number, CultureInfo.InvariantCulture, out var number)
                ? number
                : 0m;
        }

        private string BuildCurrentPayScheduleSummary()
        {
            if (IsWeekSelected)
            {
                return SelectedWeekDay is null
                    ? "Selecciona el día en que recibes tu pago semanal."
                    : $"Recibes tu pago cada {SelectedWeekDay.DayName.ToLowerInvariant()}.";
            }

            if (IsBiWeekSelected)
            {
                return SelectedFirstDay > 0 && SelectedSecondDay > 0
                    ? $"Tus pagos quincenales llegan los días {SelectedFirstDay} y {SelectedSecondDay}."
                    : "Selecciona ambos días de pago para tu esquema quincenal.";
            }

            if (IsMonthSelected)
            {
                return SelectedFirstDay > 0
                    ? $"Tu pago mensual llega el día {SelectedFirstDay}."
                    : "Selecciona el día de pago mensual.";
            }

            return "Frecuencia de pago no disponible";
        }

        private string BuildSnapshot()
        {
            var incomeType = IsWeekSelected ? 1 : IsBiWeekSelected ? 2 : 3;
            var firstPayDay = IsWeekSelected
                ? SelectedWeekDay?.DayNumber ?? -1
                : SelectedFirstDay;
            var secondPayDay = IsBiWeekSelected ? SelectedSecondDay : -1;

            return $"{incomeType}|{firstPayDay}|{secondPayDay}|{SalaryInput}|{PercentSaveInput}";
        }

        private bool ValidateFinancialSettings(bool showToast, out string validationMessage)
        {
            validationMessage = string.Empty;
            var salary = ParseDecimal(SalaryInput);
            var percent = ParseDecimal(PercentSaveInput);

            if (salary <= 0)
            {
                validationMessage = "Ingresa un sueldo mayor a 0 para guardar automáticamente.";
            }
            else if (percent < 0 || percent > 99)
            {
                validationMessage = IsSavingByAmount
                    ? "La cantidad a ahorrar no puede superar el 99% de tu sueldo."
                    : "El porcentaje de ahorro debe estar entre 0 y 99.";
            }
            else if (IsWeekSelected && SelectedWeekDay is null)
            {
                validationMessage = "Selecciona el día de tu pago semanal.";
            }
            else if (IsBiWeekSelected && (SelectedFirstDay <= 0 || SelectedSecondDay <= 0))
            {
                validationMessage = "Selecciona tus dos días de pago quincenal.";
            }
            else if (IsBiWeekSelected && SelectedFirstDay == SelectedSecondDay)
            {
                validationMessage = "Los días de pago quincenal deben ser distintos.";
            }
            else if (IsMonthSelected && SelectedFirstDay <= 0)
            {
                validationMessage = "Selecciona tu día de pago mensual.";
            }

            if (!string.IsNullOrWhiteSpace(validationMessage) && showToast)
            {
                _ = Toast.Make(validationMessage, ToastDuration.Short).Show();
            }

            return string.IsNullOrWhiteSpace(validationMessage);
        }

        [RelayCommand]
        private void SelectFrequency(string type)
        {
            switch (type)
            {
                case "week": IsWeekSelected = true; break;
                case "biweek": IsBiWeekSelected = true; break;
                case "month": IsMonthSelected = true; break;
            }
        }

        [RelayCommand]
        private void SelectSavingsMode(string mode)
        {
            _isRecalculating = true;
            var salary = ParseDecimal(SalaryInput);

            if (mode == "percent")
            {
                // Recalculate percent from the current fixed amount so precision is preserved
                var amount = ParseDecimal(FixedAmountInput);
                var percent = salary > 0 ? Math.Round((amount / salary) * 100m, 4) : 0m;
                PercentSaveInput = percent > 0 ? percent.ToString("0.##", CultureInfo.InvariantCulture) : "0";

                IsSavingByPercent = true;
                IsSavingByAmount = false;
            }
            else
            {
                // Recalculate fixed amount from the current percent
                var percent = ParseDecimal(PercentSaveInput);
                var amount = salary * (percent / 100m);
                FixedAmountInput = amount > 0 ? amount.ToString("0", CultureInfo.InvariantCulture) : string.Empty;

                IsSavingByPercent = false;
                IsSavingByAmount = true;
            }

            Preferences.Default.Set("SavingsModeIsPercent", mode == "percent");
            _isRecalculating = false;
            UpdateFinancialPreview();
        }

        private void QueueAutoSaveProfileSettings()
        {
            if (_isInitializingSettings)
                return;

            UpdateFinancialPreview();
            ProfileSettingsStatusText = "Guardando automáticamente...";

            _autoSaveCts?.Cancel();
            _autoSaveCts = new CancellationTokenSource();
            var token = _autoSaveCts.Token;

            _ = Task.Run(async () =>
            {
                try
                {
                    await Task.Delay(700, token);
                    if (token.IsCancellationRequested)
                        return;

                    await MainThread.InvokeOnMainThreadAsync(SaveProfileSettingsAutomaticallyAsync);
                }
                catch (TaskCanceledException)
                {
                }
            }, token);
        }

        private async Task SaveProfileSettingsAutomaticallyAsync()
        {
            if (_isAutoSaveInProgress)
            {
                _hasPendingAutoSave = true;
                return;
            }

            if (!ValidateFinancialSettings(false, out var validationMessage))
            {
                ProfileSettingsStatusText = validationMessage;
                return;
            }

            var snapshot = BuildSnapshot();
            if (snapshot == _lastSavedSnapshot)
            {
                ProfileSettingsStatusText = "Tus cambios ya están guardados.";
                return;
            }

            _isAutoSaveInProgress = true;
            IsAutoSavingProfile = true;

            try
            {
                var currentUser = User;
                currentUser.Salary = ParseDecimal(SalaryInput);
                currentUser.PercentSave = ParseDecimal(PercentSaveInput);

                if (IsWeekSelected)
                {
                    currentUser.IncomeTypeId = 1;
                    currentUser.FirstPayDay = SelectedWeekDay?.DayNumber;
                    currentUser.SecondPayDay = null;
                }
                else if (IsBiWeekSelected)
                {
                    currentUser.IncomeTypeId = 2;
                    currentUser.FirstPayDay = SelectedFirstDay;
                    currentUser.SecondPayDay = SelectedSecondDay;
                }
                else
                {
                    currentUser.IncomeTypeId = 3;
                    currentUser.FirstPayDay = SelectedFirstDay;
                    currentUser.SecondPayDay = null;
                }

                var updatedUser = await _userService.UpdateUserPayInfo(currentUser);
                if (updatedUser is null)
                {
                    ProfileSettingsStatusText = "No se pudo guardar automáticamente. Revisa tu conexión e inténtalo de nuevo.";
                    return;
                }

                User = updatedUser;
                _lastSavedSnapshot = snapshot;
                ProfileSettingsStatusText = "Cambios guardados automáticamente.";
                UpdateProfileHighlights();
                await UpdateCloudSyncStatusAsync();
            }
            finally
            {
                IsAutoSavingProfile = false;
                _isAutoSaveInProgress = false;
            }

            if (_hasPendingAutoSave)
            {
                _hasPendingAutoSave = false;
                await SaveProfileSettingsAutomaticallyAsync();
            }
        }

        partial void OnSelectedWeekDayChanged(DayForWeek? value)
        {
            QueueAutoSaveProfileSettings();
        }

        partial void OnSelectedFirstDayChanged(int value)
        {
            QueueAutoSaveProfileSettings();
        }

        partial void OnSelectedSecondDayChanged(int value)
        {
            QueueAutoSaveProfileSettings();
        }

        partial void OnIsWeekSelectedChanged(bool value)
        {
            if (value)
            {
                IsBiWeekSelected = false;
                IsMonthSelected = false;
            }

            QueueAutoSaveProfileSettings();
        }

        partial void OnIsBiWeekSelectedChanged(bool value)
        {
            if (value)
            {
                IsWeekSelected = false;
                IsMonthSelected = false;
            }

            QueueAutoSaveProfileSettings();
        }

        partial void OnIsMonthSelectedChanged(bool value)
        {
            if (value)
            {
                IsWeekSelected = false;
                IsBiWeekSelected = false;
            }

            QueueAutoSaveProfileSettings();
        }

        partial void OnSalaryInputChanged(string value)
        {
            if (_isRecalculating || _isInitializingSettings) return;

            _isRecalculating = true;
            var salary = ParseDecimal(value);
            if (IsSavingByAmount)
            {
                var amount = ParseDecimal(FixedAmountInput);
                var percent = salary > 0 ? Math.Round((amount / salary) * 100m, 4) : 0m;
                PercentSaveInput = percent > 0 ? percent.ToString("0.##", CultureInfo.InvariantCulture) : "0";
            }
            else
            {
                var percent = ParseDecimal(PercentSaveInput);
                var amount = salary * (percent / 100m);
                FixedAmountInput = amount > 0 ? amount.ToString("0", CultureInfo.InvariantCulture) : string.Empty;
            }
            _isRecalculating = false;

            QueueAutoSaveProfileSettings();
        }

        partial void OnPercentSaveInputChanged(string value)
        {
            if (_isRecalculating || _isInitializingSettings) return;

            if (IsSavingByPercent)
            {
                _isRecalculating = true;
                var salary = ParseDecimal(SalaryInput);
                var percent = ParseDecimal(value);
                var amount = salary * (percent / 100m);
                FixedAmountInput = amount > 0 ? amount.ToString("0", CultureInfo.InvariantCulture) : string.Empty;
                _isRecalculating = false;
            }

            QueueAutoSaveProfileSettings();
        }

        partial void OnFixedAmountInputChanged(string value)
        {
            if (_isRecalculating || _isInitializingSettings) return;

            if (IsSavingByAmount)
            {
                _isRecalculating = true;
                var salary = ParseDecimal(SalaryInput);
                var percent = salary > 0 ? Math.Round((ParseDecimal(value) / salary) * 100m, 4) : 0m;
                PercentSaveInput = percent > 0 ? percent.ToString("0.##", CultureInfo.InvariantCulture) : "0";
                _isRecalculating = false;
            }

            QueueAutoSaveProfileSettings();
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

    }
}
