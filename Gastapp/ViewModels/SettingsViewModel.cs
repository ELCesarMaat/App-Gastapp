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
using Gastapp.Services.SpendingService;
using Gastapp.Services.UserService;

namespace Gastapp.ViewModels
{
    public partial class SettingsViewModel(INavigationService navService, IUserService userService, ISpendingService spendingService) : ObservableObject
    {
        private readonly INavigationService _navService = navService;
        private readonly IUserService _userService = userService;
        private readonly ISpendingService _spendingService = spendingService;
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