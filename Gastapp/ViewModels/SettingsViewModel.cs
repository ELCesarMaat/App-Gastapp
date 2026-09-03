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
using Gastapp.Pages.Menu;
using Gastapp.Services.Navigation;
using Gastapp.Services.Notifications;
using Gastapp.Services.SpendingService;
using Gastapp.Services.UserService;
using Gastapp.Services.WearService;
using Gastapp.Utils;
using Microsoft.Maui.ApplicationModel;
using System.Threading;
using Gastapp.Services;
using Gastapp.Services.BackupService;
using CommunityToolkit.Mvvm.Messaging;
using Gastapp.Messages;
using Gastapp.Models.Models;
using Gastapp.Popups;
using Gastapp.Services.ApiService;
using CommunityToolkit.Maui.Views;
using Refit;

namespace Gastapp.ViewModels
{
    public partial class SettingsViewModel(
        INavigationService navService,
        IUserService userService,
        ISpendingService spendingService,
        IReminderNotificationService reminderNotificationService,
        ICreditCardService creditCardService,
        IBackupService backupService,
        IApiService apiService,
        IWearChannel? wearChannel = null) : ObservableObject
    {
        private readonly INavigationService _navService = navService;
        private readonly IUserService _userService = userService;
        private readonly ISpendingService _spendingService = spendingService;
        private readonly IReminderNotificationService _reminderNotificationService = reminderNotificationService;
        private readonly ICreditCardService _creditCardService = creditCardService;
        private readonly IBackupService _backupService = backupService;
        private readonly IApiService _apiService = apiService;

        // null fuera de Android: no hay canal con el reloj y no pasa nada.
        private readonly IWearChannel? _wearChannel = wearChannel;
        private User _user = new();

        [ObservableProperty] private bool _isExporting;
        [ObservableProperty] private bool _isRestoring;

        public bool IsBackupBusy => IsExporting || IsRestoring;
        public string BackupStatusText => IsExporting
            ? "Generando tu copia de seguridad..."
            : IsRestoring
                ? "Restaurando tu información, no cierres la app..."
                : string.Empty;

#if DEBUG
        public bool IsBackupToolsVisible => true;
#else
        public bool IsBackupToolsVisible => false;
#endif

        partial void OnIsExportingChanged(bool value)
        {
            OnPropertyChanged(nameof(IsBackupBusy));
            OnPropertyChanged(nameof(BackupStatusText));
        }

        partial void OnIsRestoringChanged(bool value)
        {
            OnPropertyChanged(nameof(IsBackupBusy));
            OnPropertyChanged(nameof(BackupStatusText));
        }
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

        [ObservableProperty] private string _creditCardsSummaryText = string.Empty;

        [ObservableProperty] private ObservableCollection<DeviceDto> _linkedDevices = [];
        [ObservableProperty] private bool _isLoadingDevices;
        [ObservableProperty] private string _devicesSummaryText = string.Empty;

        public bool HasLinkedDevices => LinkedDevices.Count > 0;

        partial void OnLinkedDevicesChanged(ObservableCollection<DeviceDto> value)
        {
            OnPropertyChanged(nameof(HasLinkedDevices));
        }

        private bool _isInitialized;
        private Task? _initializationTask;
        private bool _isLoadingReminderSettings;
        private CancellationTokenSource? _reminderAutoSaveCts;

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

            // Si ya hay una carga en vuelo se espera esa, en vez de arrancar otra.
            _initializationTask ??= RunInitializationAsync();
            await _initializationTask;
        }

        private async Task RunInitializationAsync()
        {
            try
            {
                await Initialize();
                EscucharCambiosDeDispositivos();
                _isInitialized = true;
            }
            finally
            {
                // Si fallo, el siguiente intento vuelve a empezar en vez de quedarse
                // con la tarea rota en cache.
                _initializationTask = null;
            }
        }

        /// <summary>
        /// El reloj avisa por Bluetooth cuando se desvincula el mismo. Sin esto, la
        /// lista de dispositivos seguiria mostrandolo hasta que el usuario recargara
        /// la pantalla a mano.
        /// </summary>
        private void EscucharCambiosDeDispositivos()
        {
            WeakReferenceMessenger.Default.Register<DevicesChangedMessage>(this, (_, _) =>
            {
                MainThread.BeginInvokeOnMainThread(async () => await RefreshDevices());
            });
        }

        private async Task Initialize()
        {
            // Todo esto es local (SQLite y Preferences): llena la pantalla de inmediato.
            await GetData();
            InitLists();
            InitReminderFrequencies();
            await LoadReminderSettings();
            await RefreshCreditCardsSummary();

            // La lista de dispositivos si sale a la red, y es lo unico de esta pantalla
            // que lo hace. Va sin esperar para que un API dormido no retrase al resto;
            // mientras tanto la seccion muestra su propio indicador de carga.
            _ = RefreshDevices();
        }

        private async Task RefreshCreditCardsSummary()
        {
            var cards = await _creditCardService.GetAllCreditCardsAsync();
            CreditCardsSummaryText = cards.Count switch
            {
                0 => "Aún no tienes tarjetas registradas.",
                1 => "Tienes 1 tarjeta registrada.",
                _ => $"Tienes {cards.Count} tarjetas registradas."
            };
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
            _isLoadingReminderSettings = true;

            IsRemindersEnabled = Preferences.Get("reminders_enabled", true);
            var savedFrequency = Preferences.Get("reminder_frequency_hours", 4);
            SelectedReminderFrequencyOption = ReminderFrequencyOptions.FirstOrDefault(x => x.Hours == savedFrequency)
                ?? ReminderFrequencyOptions.FirstOrDefault(x => x.Hours == 4)
                ?? ReminderFrequencyOptions.FirstOrDefault();

            await RefreshNotificationPermissionState();
            _isLoadingReminderSettings = false;
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
            ScreenSubtitle = "Administra recordatorios y acciones de cuenta en un solo lugar.";

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

        private void QueueReminderAutoSave()
        {
            if (_isLoadingReminderSettings)
                return;

            _reminderAutoSaveCts?.Cancel();
            _reminderAutoSaveCts = new CancellationTokenSource();
            var token = _reminderAutoSaveCts.Token;

            _ = Task.Run(async () =>
            {
                try
                {
                    await Task.Delay(450, token);
                    if (token.IsCancellationRequested)
                        return;

                    await MainThread.InvokeOnMainThreadAsync(SaveReminderSettings);
                }
                catch (TaskCanceledException)
                {
                }
            }, token);
        }

        partial void OnIsRemindersEnabledChanged(bool value)
        {
            QueueReminderAutoSave();
        }

        partial void OnSelectedReminderFrequencyOptionChanged(ReminderFrequencyOption? value)
        {
            if (IsSystemNotificationsEnabled && IsRemindersEnabled && value != null)
            {
                NotificationsStatusText = $"Recibirás recordatorios aproximadamente cada {value.Hours} horas.";
            }

            QueueReminderAutoSave();
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
        private void Logout()
        {
            _userService.ClearLocalSession();
            if (Application.Current is not null)
                Application.Current.MainPage = new AppShell();
        }

        [RelayCommand]
        private async Task OpenCreditCardsPage()
        {
            await _navService.GoToAsync(nameof(CreditCardsPage));
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

        [RelayCommand]
        private async Task ExportDatabase()
        {
            if (IsExporting) return;
            IsExporting = true;
            try
            {
                var path = await _backupService.ExportDatabaseFileAsync();
                if (!string.IsNullOrEmpty(path))
                {
                    await Toast.Make("Copia de seguridad SQLite (.db) generada.", ToastDuration.Short).Show();
                }
            }
            finally
            {
                IsExporting = false;
            }
        }

        [RelayCommand]
        private async Task ExportJson()
        {
            if (IsExporting) return;
            IsExporting = true;
            try
            {
                var path = await _backupService.ExportJsonBackupAsync();
                if (!string.IsNullOrEmpty(path))
                {
                    await Toast.Make("Respaldo JSON generado.", ToastDuration.Short).Show();
                }
            }
            finally
            {
                IsExporting = false;
            }
        }


        // ---- Dispositivos vinculados (relojes Wear OS) ----

        private async Task RefreshDevices()
        {
            var token = Preferences.Get("token", string.Empty);
            if (string.IsNullOrWhiteSpace(token))
            {
                LinkedDevices = [];
                DevicesSummaryText = "Inicia sesión para administrar tus dispositivos.";
                return;
            }

            IsLoadingDevices = true;
            try
            {
                var devices = await _apiService.GetDevices(token);
                LinkedDevices = new ObservableCollection<DeviceDto>(devices);
                DevicesSummaryText = devices.Count switch
                {
                    0 => "Aún no tienes ningún reloj vinculado.",
                    1 => "Tienes 1 dispositivo vinculado.",
                    _ => $"Tienes {devices.Count} dispositivos vinculados."
                };
            }
            catch (Exception)
            {
                // Sin conexion no se puede saber; no vale la pena molestar al usuario aqui.
                DevicesSummaryText = "No se pudo consultar tus dispositivos. Revisa tu conexión.";
            }
            finally
            {
                IsLoadingDevices = false;
            }
        }

        [RelayCommand]
        private async Task LinkDevice()
        {
            var token = Preferences.Get("token", string.Empty);
            if (string.IsNullOrWhiteSpace(token))
            {
                await AlertHelper.ShowAlertAsync("Sesión requerida",
                    "Inicia sesión para vincular un reloj.", "Entendido");
                return;
            }

            var mainPage = Application.Current?.MainPage;
            if (mainPage == null)
                return;

            // Ya no se teclea ningun codigo: el reloj se lo manda al telefono por
            // Bluetooth y GastappWearListenerService llama a Device/Link por su cuenta.
            // Este popup solo acompaña la espera; se puede cerrar cuando se quiera.
            await mainPage.ShowPopupAsync(new LinkDevicePopup());
        }

        [RelayCommand]
        private async Task RevokeDevice(DeviceDto device)
        {
            if (device == null)
                return;

            var token = Preferences.Get("token", string.Empty);
            if (string.IsNullOrWhiteSpace(token))
                return;

            var confirm = await AlertHelper.ShowAlertAsync(
                "Quitar dispositivo",
                $"{device.Name} dejará de poder registrar gastos. Para volver a usarlo tendrás que vincularlo de nuevo.",
                "Quitar",
                "Cancelar");

            if (!confirm)
                return;

            try
            {
                await _apiService.RevokeDevice(new RevokeDeviceRequest { DeviceId = device.DeviceId }, token);

                // Avisar al reloj por Bluetooth para que suelte la sesion al instante.
                // Antes solo se enteraba cuando una llamada suya al API rebotaba un
                // 401, asi que podia pasarse horas creyendose vinculado.
                if (_wearChannel != null)
                    await _wearChannel.NotifyDeviceRevokedAsync(device.DeviceId);

                await RefreshDevices();
                await Toast.Make("Dispositivo desvinculado.", ToastDuration.Short).Show();
            }
            catch (Exception)
            {
                await AlertHelper.ShowAlertAsync("Error",
                    "No se pudo desvincular el dispositivo. Revisa tu conexión.", "OK");
            }
        }

        [RelayCommand]
        private async Task ReloadDevices() => await RefreshDevices();

        [RelayCommand]
        private async Task RestoreBackup()
        {
            if (IsRestoring) return;
            IsRestoring = true;
            try
            {
                var success = await _backupService.PickAndRestoreBackupAsync();
                if (success)
                {
                    _isInitialized = false;
                    await EnsureInitialized();
                    WeakReferenceMessenger.Default.Send(new SpendingChangedMessage(string.Empty));
                    await Toast.Make("Datos restaurados correctamente.", ToastDuration.Short).Show();
                }
            }
            finally
            {
                IsRestoring = false;
            }
        }
    }
}