using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Globalization;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using CommunityToolkit.Maui.Alerts;
using CommunityToolkit.Maui.Core;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Gastapp.Models;
using Gastapp.Services.Navigation;
using Gastapp.Services.Notifications;
using Gastapp.Services.SpendingService;
using Gastapp.Services.UserService;
using Gastapp.Utils;

namespace Gastapp.ViewModels
{
    public partial class SettingsViewModel(INavigationService navService, IUserService userService, ISpendingService spendingService, IReminderNotificationService reminderNotificationService) : ObservableObject
    {
        private readonly INavigationService _navService = navService;
        private readonly IUserService _userService = userService;
        private readonly ISpendingService _spendingService = spendingService;
        private readonly IReminderNotificationService _reminderNotificationService = reminderNotificationService;
        private User _user = new();

        [ObservableProperty] private bool _isWeekSelected;
        [ObservableProperty] private bool _isBiWeekSelected;
        [ObservableProperty] private bool _isMonthSelected;
        [ObservableProperty] private bool _isSaving;
        [ObservableProperty] private string _incomeSummary = string.Empty;
        [ObservableProperty] private string _payDaySummary = string.Empty;
        [ObservableProperty] private string _estimatedSavingsText = string.Empty;
        [ObservableProperty] private string _estimatedSpendableText = string.Empty;
        [ObservableProperty] private string _saveGoalSummary = string.Empty;
        [ObservableProperty] private string _screenSubtitle = string.Empty;
        [ObservableProperty] private DayForWeek? _selectedWeekDay;
        [ObservableProperty] private ObservableCollection<DayForWeek> _listForWeek = [];
        [ObservableProperty] private ObservableCollection<int> _firstDayList = [];
        [ObservableProperty] private ObservableCollection<int> _secondDayList = [];
        [ObservableProperty] private int _selectedFirstDay;
        [ObservableProperty] private int _selectedSecondDay;
        [ObservableProperty] private ObservableCollection<Category> _categories = [];

        [ObservableProperty] private bool _isRemindersEnabled;
        [ObservableProperty] private bool _isSystemNotificationsEnabled;
        [ObservableProperty] private bool _showEnableNotificationsButton;
        [ObservableProperty] private string _notificationsStatusText = string.Empty;
        [ObservableProperty] private bool _isSavingNotifications;
        [ObservableProperty] private ObservableCollection<ReminderFrequencyOption> _reminderFrequencyOptions = [];
        [ObservableProperty] private ReminderFrequencyOption? _selectedReminderFrequencyOption;

        private bool _isInitialized;

        public User User
        {
            get => _user;
            set
            {
                if (SetProperty(ref _user, value))
                {
                    UpdatePreview();
                }
            }
        }

        public async Task EnsureInitialized()
        {
            if (_isInitialized)
                return;

            await Initialize();
            _isInitialized = true;
        }

        private async Task Initialize()
        {
            await GetData();
            InitLists();
            InitReminderFrequencies();
            await LoadReminderSettings();
        }

        private void InitLists()
        {
            ListForWeek.Clear();
            FirstDayList.Clear();
            SecondDayList.Clear();

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

            //SelectedWeekDay = ListForWeek.FirstOrDefault();

            if (User?.IncomeTypeId == 1)
                SelectedWeekDay = ListForWeek.FirstOrDefault(x => x.DayNumber == User.FirstPayDay);

            for (var i = 1; i <= 31; i++)
            {
                FirstDayList.Add(i);
                SecondDayList.Add(i);
            }

            //SelectedFirstDay = FirstDayList.First();
            //SelectedSecondDay = SecondDayList.First();

            if (User?.IncomeTypeId == 2)
            {
                SelectedFirstDay = FirstDayList.FirstOrDefault(x => x == User.FirstPayDay);
                SelectedSecondDay = SecondDayList.FirstOrDefault(x => x == User.SecondPayDay);
            }

            if (User?.IncomeTypeId == 3)
            {
                SelectedFirstDay = FirstDayList.FirstOrDefault(x => x == User.FirstPayDay);
            }

            UpdatePreview();
        }

        public async Task GetData()
        {
            User = await _userService.GetUser() ?? new User();
            IsWeekSelected = IsBiWeekSelected = IsMonthSelected = false;
            switch (User.IncomeTypeId)
            {
                case 1:
                    IsWeekSelected = true;
                    break;
                case 2:
                    IsBiWeekSelected = true;
                    break;
                case 3:
                    IsMonthSelected = true;
                    break;
            }

            UpdatePreview();
        }

        partial void OnSelectedWeekDayChanged(DayForWeek? value)
        {
            UpdatePreview();
        }

        partial void OnSelectedFirstDayChanged(int value)
        {
            UpdatePreview();
        }

        partial void OnSelectedSecondDayChanged(int value)
        {
            UpdatePreview();
        }

        partial void OnIsWeekSelectedChanged(bool value)
        {
            UpdatePreview();
        }

        partial void OnIsBiWeekSelectedChanged(bool value)
        {
            UpdatePreview();
        }

        partial void OnIsMonthSelectedChanged(bool value)
        {
            UpdatePreview();
        }

        private void InitReminderFrequencies()
        {
            ReminderFrequencyOptions.Clear();
            ReminderFrequencyOptions.Add(new ReminderFrequencyOption { Hours = 2, Label = "Cada 2 horas" });
            ReminderFrequencyOptions.Add(new ReminderFrequencyOption { Hours = 4, Label = "Cada 4 horas" });
            ReminderFrequencyOptions.Add(new ReminderFrequencyOption { Hours = 6, Label = "Cada 6 horas" });
            ReminderFrequencyOptions.Add(new ReminderFrequencyOption { Hours = 8, Label = "Cada 8 horas" });
            ReminderFrequencyOptions.Add(new ReminderFrequencyOption { Hours = 12, Label = "Cada 12 horas" });
            ReminderFrequencyOptions.Add(new ReminderFrequencyOption { Hours = 24, Label = "Cada 24 horas" });
        }

        private async Task LoadReminderSettings()
        {
            IsRemindersEnabled = Preferences.Get("reminders_enabled", true);
            var savedFrequency = Preferences.Get("reminder_frequency_hours", 4);
            SelectedReminderFrequencyOption = ReminderFrequencyOptions.FirstOrDefault(x => x.Hours == savedFrequency)
                ?? ReminderFrequencyOptions.FirstOrDefault(x => x.Hours == 4)
                ?? ReminderFrequencyOptions.FirstOrDefault();

            await RefreshNotificationPermissionState();
        }

        private async Task RefreshNotificationPermissionState()
        {
            IsSystemNotificationsEnabled = await _reminderNotificationService.AreNotificationsEnabledAsync();

            if (!IsSystemNotificationsEnabled)
            {
                NotificationsStatusText = "Las notificaciones están desactivadas en tu dispositivo.";
                ShowEnableNotificationsButton = true;
                return;
            }

            ShowEnableNotificationsButton = false;
            if (!IsRemindersEnabled)
            {
                NotificationsStatusText = "Los recordatorios están apagados para esta app.";
                return;
            }

            var hours = SelectedReminderFrequencyOption?.Hours ?? 4;
            NotificationsStatusText = $"Recibirás recordatorios aproximadamente cada {hours} horas.";
        }

        private void UpdatePreview()
        {
            ScreenSubtitle = "Ajusta tus ingresos y objetivo de ahorro para que la app calcule mejor tus límites de gasto.";

            var salary = User?.Salary ?? 0m;
            var percent = User?.PercentSave ?? 0m;
            var estimatedSavings = salary * (percent / 100m);
            var estimatedSpendable = salary - estimatedSavings;

            IncomeSummary = salary > 0 ? $"${salary:N2} por periodo" : "Sin sueldo configurado";
            EstimatedSavingsText = $"Ahorrarías ${estimatedSavings:N2} por periodo";
            EstimatedSpendableText = $"Tendrías ${estimatedSpendable:N2} disponibles para gastar";
            SaveGoalSummary = percent > 0 ? $"Objetivo actual: {percent:N0}% de ahorro" : "Define cuánto quieres separar para ahorrar";

            if (IsWeekSelected)
            {
                PayDaySummary = SelectedWeekDay is null
                    ? "Selecciona el día en que recibes tu pago semanal."
                    : $"Recibes tu pago cada {SelectedWeekDay.DayName.ToLowerInvariant()}.";
                return;
            }

            if (IsBiWeekSelected)
            {
                PayDaySummary = SelectedFirstDay > 0 && SelectedSecondDay > 0
                    ? $"Tus pagos quincenales llegan los días {SelectedFirstDay} y {SelectedSecondDay}."
                    : "Selecciona ambos días de pago para tu esquema quincenal.";
                return;
            }

            if (IsMonthSelected)
            {
                PayDaySummary = SelectedFirstDay > 0
                    ? $"Tu pago mensual llega el día {SelectedFirstDay}."
                    : "Selecciona el día de pago mensual.";
                return;
            }

            PayDaySummary = "Selecciona tu frecuencia de pago.";
        }

        private async Task<bool> ValidateBeforeSave()
        {
            if (User is null)
            {
                await Toast.Make("No se pudo cargar tu configuración actual.", ToastDuration.Short).Show();
                return false;
            }

            if (User.Salary <= 0)
            {
                await Toast.Make("Ingresa un sueldo mayor a 0.", ToastDuration.Short).Show();
                return false;
            }

            if (User.PercentSave < 0 || User.PercentSave > 99)
            {
                await Toast.Make("El porcentaje de ahorro debe estar entre 0 y 99.", ToastDuration.Short).Show();
                return false;
            }

            if (IsWeekSelected && SelectedWeekDay is null)
            {
                await Toast.Make("Selecciona el día de tu pago semanal.", ToastDuration.Short).Show();
                return false;
            }

            if (IsBiWeekSelected)
            {
                if (SelectedFirstDay <= 0 || SelectedSecondDay <= 0)
                {
                    await Toast.Make("Selecciona tus dos días de pago quincenal.", ToastDuration.Short).Show();
                    return false;
                }

                if (SelectedFirstDay == SelectedSecondDay)
                {
                    await Toast.Make("Los días de pago quincenal deben ser distintos.", ToastDuration.Short).Show();
                    return false;
                }
            }

            if (IsMonthSelected && SelectedFirstDay <= 0)
            {
                await Toast.Make("Selecciona tu día de pago mensual.", ToastDuration.Short).Show();
                return false;
            }

            return true;
        }

        partial void OnSelectedReminderFrequencyOptionChanged(ReminderFrequencyOption? value)
        {
            if (IsSystemNotificationsEnabled && IsRemindersEnabled && value != null)
            {
                NotificationsStatusText = $"Recibirás recordatorios aproximadamente cada {value.Hours} horas.";
            }
        }

        [RelayCommand]
        private async Task SaveReminderSettings()
        {
            if (IsSavingNotifications)
                return;

            IsSavingNotifications = true;
            var selectedHours = SelectedReminderFrequencyOption?.Hours ?? 4;

            if (!IsRemindersEnabled)
            {
                Preferences.Set("reminders_enabled", false);
                Preferences.Set("reminder_frequency_hours", selectedHours);
                await _reminderNotificationService.DisableRemindersAsync();
                await RefreshNotificationPermissionState();
                IsSavingNotifications = false;
                await Toast.Make("Recordatorios desactivados.", ToastDuration.Short).Show();
                return;
            }

            var notificationsEnabled = await _reminderNotificationService.AreNotificationsEnabledAsync();
            if (!notificationsEnabled)
            {
                notificationsEnabled = await _reminderNotificationService.RequestNotificationPermissionAsync();
            }

            notificationsEnabled = notificationsEnabled && await _reminderNotificationService.AreNotificationsEnabledAsync();
            if (!notificationsEnabled)
            {
                IsSavingNotifications = false;
                await RefreshNotificationPermissionState();
                await Toast.Make("No se pudo activar. Debes habilitar notificaciones en permisos del dispositivo.", ToastDuration.Long).Show();
                return;
            }

            Preferences.Set("reminders_enabled", true);
            Preferences.Set("reminder_frequency_hours", selectedHours);
            await _reminderNotificationService.ConfigureRecurringRemindersAsync(selectedHours);
            await RefreshNotificationPermissionState();
            IsSavingNotifications = false;
            await Toast.Make("Frecuencia de recordatorios actualizada.", ToastDuration.Short).Show();
        }

        [RelayCommand]
        private async Task EnableSystemNotifications()
        {
            var notificationsEnabled = await _reminderNotificationService.AreNotificationsEnabledAsync();
            if (!notificationsEnabled)
            {
                notificationsEnabled = await _reminderNotificationService.RequestNotificationPermissionAsync();
            }

            notificationsEnabled = notificationsEnabled && await _reminderNotificationService.AreNotificationsEnabledAsync();
            if (notificationsEnabled)
            {
                await SaveReminderSettings();
                return;
            }

            var openSettings = await AlertHelper.ShowAlertAsync(
                "Notificaciones desactivadas",
                "El permiso está denegado o desactivado. ¿Quieres abrir los ajustes de la app para habilitar notificaciones?",
                "Abrir ajustes",
                "Cancelar");

            if (openSettings)
                await _reminderNotificationService.OpenAppNotificationSettingsAsync();

            await RefreshNotificationPermissionState();
        }

        [RelayCommand]
        private async Task SendTestNotification()
        {
            var sent = await _reminderNotificationService.SendTestNotificationAsync();
            if (sent)
            {
                await Toast.Make("Notificación de prueba enviada.", ToastDuration.Short).Show();
                return;
            }

            await Toast.Make("No se pudo enviar la notificación. Revisa permisos de notificación.", ToastDuration.Short).Show();
            await RefreshNotificationPermissionState();
        }

        [RelayCommand]
        private async Task SaveChanges()
        {
            if (IsSaving)
                return;

            if (!await ValidateBeforeSave())
                return;

            var currentUser = User;

            IsSaving = true;

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
            else if (IsMonthSelected)
            {
                currentUser.IncomeTypeId = 3;
                currentUser.FirstPayDay = SelectedFirstDay;
                currentUser.SecondPayDay = null;
            }

            var updatedUser = await _userService.UpdateUserPayInfo(currentUser);
            IsSaving = false;

            if (updatedUser is null)
            {
                await Toast.Make("No se pudieron guardar los cambios. Intenta nuevamente.", ToastDuration.Short).Show();
                return;
            }

            User = updatedUser;
            UpdatePreview();
            await Toast.Make("Tus ajustes se guardaron correctamente.", ToastDuration.Short).Show();
        }
    }
}