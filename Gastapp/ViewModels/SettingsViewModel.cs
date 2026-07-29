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
using Microsoft.Maui.ApplicationModel;
using System.Threading;
using Gastapp.Services;

namespace Gastapp.ViewModels
{
    public partial class SettingsViewModel(
        INavigationService navService,
        IUserService userService,
        ISpendingService spendingService,
        IReminderNotificationService reminderNotificationService,
        ICreditCardService creditCardService) : ObservableObject
    {
        private readonly INavigationService _navService = navService;
        private readonly IUserService _userService = userService;
        private readonly ISpendingService _spendingService = spendingService;
        private readonly IReminderNotificationService _reminderNotificationService = reminderNotificationService;
        private readonly ICreditCardService _creditCardService = creditCardService;
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

        [ObservableProperty] private ObservableCollection<CreditCard> _creditCards = [];
        [ObservableProperty] private string _newCardName = string.Empty;
        [ObservableProperty] private string _newBankName = string.Empty;
        [ObservableProperty] private string _newLastFourDigits = string.Empty;
        [ObservableProperty] private int _newCutOffDay = 15;
        [ObservableProperty] private int _newPaymentDay = 5;
        [ObservableProperty] private bool _showNewCardForm;
        [ObservableProperty] private ObservableCollection<int> _daysOfMonth = [];
        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(CardFormTitle))]
        [NotifyPropertyChangedFor(nameof(SaveCardButtonText))]
        private bool _isEditingCard;
        [ObservableProperty] private string _editingCardId = string.Empty;

        public string CardFormTitle => IsEditingCard ? "Editar Tarjeta" : "Nueva Tarjeta";
        public string SaveCardButtonText => IsEditingCard ? "Guardar Cambios" : "Guardar";

        private bool _isInitialized;
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

            await Initialize();
            _isInitialized = true;
        }

        private async Task Initialize()
        {
            await GetData();
            InitLists();
            InitReminderFrequencies();
            await LoadReminderSettings();

            CreditCards = new(await _creditCardService.GetAllCreditCardsAsync());
            DaysOfMonth.Clear();
            for (int i = 1; i <= 31; i++) DaysOfMonth.Add(i);
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

        [ObservableProperty] private bool _showAddCardButton = true;

        [RelayCommand]
        public void ToggleNewCardForm()
        {
            ShowNewCardForm = !ShowNewCardForm;
            ShowAddCardButton = !ShowNewCardForm;
            if (ShowNewCardForm)
            {
                NewCardName = string.Empty;
                NewBankName = string.Empty;
                NewLastFourDigits = string.Empty;
                NewCutOffDay = 15;
                NewPaymentDay = 5;
                IsEditingCard = false;
                EditingCardId = string.Empty;
            }
        }

        [RelayCommand]
        public void ToggleEditCardForm(CreditCard card)
        {
            if (card == null) return;

            IsEditingCard = true;
            EditingCardId = card.CreditCardId;
            NewCardName = card.CardName;
            NewBankName = card.BankName;
            NewLastFourDigits = card.LastFourDigits ?? string.Empty;
            NewCutOffDay = card.CutOffDay;
            NewPaymentDay = card.PaymentDay;

            ShowNewCardForm = true;
            ShowAddCardButton = false;
        }

        [RelayCommand]
        public async Task AddCreditCard()
        {
            try
            {
                var cardName = NewCardName?.Trim() ?? string.Empty;
                var bankName = NewBankName?.Trim() ?? string.Empty;
                var lastFour = NewLastFourDigits?.Trim() ?? string.Empty;

                if (string.IsNullOrWhiteSpace(cardName))
                {
                    await AlertHelper.ShowAlertAsync("Error", "Ingresa el nombre de la tarjeta (Ej. Oro).", "OK");
                    return;
                }
                if (string.IsNullOrWhiteSpace(bankName))
                {
                    await AlertHelper.ShowAlertAsync("Error", "Ingresa el banco emisor (Ej. BBVA).", "OK");
                    return;
                }
                if (NewCutOffDay < 1 || NewCutOffDay > 31 || NewPaymentDay < 1 || NewPaymentDay > 31)
                {
                    await AlertHelper.ShowAlertAsync("Error", "Selecciona días válidos de corte y pago.", "OK");
                    return;
                }

                var user = await _userService.GetUser();
                if (user == null || string.IsNullOrWhiteSpace(user.UserId))
                {
                    await AlertHelper.ShowAlertAsync("Error", "No se pudo obtener el usuario.", "OK");
                    return;
                }

                var card = new CreditCard
                {
                    CardName = cardName,
                    BankName = bankName,
                    LastFourDigits = string.IsNullOrEmpty(lastFour) ? null : lastFour,
                    CutOffDay = NewCutOffDay,
                    PaymentDay = NewPaymentDay,
                    UserId = user.UserId
                };

                if (IsEditingCard && !string.IsNullOrEmpty(EditingCardId))
                {
                    card.CreditCardId = EditingCardId;
                    var success = await _creditCardService.UpdateCreditCardAsync(card);
                    if (success)
                    {
                        var existingCard = CreditCards.FirstOrDefault(cc => cc.CreditCardId == EditingCardId);
                        if (existingCard != null)
                        {
                            int index = CreditCards.IndexOf(existingCard);
                            if (index != -1)
                            {
                                CreditCards[index] = card;
                            }
                        }
                        ToggleNewCardForm();
                        await Toast.Make("Tarjeta de crédito modificada.", ToastDuration.Short).Show();
                    }
                    else
                    {
                        await AlertHelper.ShowAlertAsync("Error", "No se pudo guardar la tarjeta.", "OK");
                    }
                }
                else
                {
                    await _creditCardService.CreateCreditCardAsync(card);
                    CreditCards.Add(card);

                    ToggleNewCardForm();
                    await Toast.Make("Tarjeta de crédito agregada.", ToastDuration.Short).Show();
                }
            }
            catch (Exception ex)
            {
                await AlertHelper.ShowAlertAsync("Error", "No se pudo guardar la tarjeta de crédito.", "OK");
                System.Diagnostics.Debug.WriteLine(ex);
            }
        }

        [RelayCommand]
        public async Task DeleteCreditCard(CreditCard card)
        {
            if (card == null) return;

            var confirm = await AlertHelper.ShowAlertAsync(
                "Eliminar tarjeta",
                $"¿Seguro que deseas eliminar la tarjeta '{card.CardName}'?\nLos gastos ya hechos con esta tarjeta se conservarán pero no estarán vinculados a ella.",
                "Eliminar", "Cancelar");

            if (!confirm) return;

            var success = await _creditCardService.DeleteCreditCardAsync(card.CreditCardId);
            if (success)
            {
                CreditCards.Remove(card);
                await Toast.Make("Tarjeta de crédito eliminada.", ToastDuration.Short).Show();
            }
            else
            {
                await AlertHelper.ShowAlertAsync("Error", "No se pudo eliminar la tarjeta.", "OK");
            }
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