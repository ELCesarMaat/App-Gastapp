using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Globalization;
using System.Linq;
using System.Threading.Tasks;
using CommunityToolkit.Maui.Alerts;
using CommunityToolkit.Maui.Core;
using CommunityToolkit.Maui.Views;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Gastapp.Models;
using Gastapp.Models.Models;
using Gastapp.Pages;
using Gastapp.Pages.Register;
using Gastapp.Popups;
using Gastapp.Services;
using Gastapp.Services.ApiService;
using Gastapp.Services.Navigation;
using Gastapp.Services.UserService;
using Gastapp.Utils;
using Refit;
using Application = Microsoft.Maui.Controls.Application;
using Gastapp.Validators;
using FluentValidation;

namespace Gastapp.ViewModels
{
    public partial class RegisterViewModel : ObservableObject
    {
        private readonly RegisterValidator _validator;
        private PagesUtils _popupUtils = new();
        private readonly IList<ContentView> _pasos;
        private INavigationService _navigationService;
        private IUserService _userService;
        private IApiService _apiService;
        private DateTime _lastExitClick = DateTime.MinValue;

        [ObservableProperty]
        private bool _canContinue = false;

        [ObservableProperty]
        private int _pasoActual = 0;

        [ObservableProperty]
        private bool _puedeRetroceder = false;

        [ObservableProperty]
        private bool _canExitWithButton = false;

        [ObservableProperty]
        private string _email = string.Empty;

        [ObservableProperty]
        private string _confirmEmail = string.Empty;

        [ObservableProperty]
        private string _password = string.Empty;

        [ObservableProperty]
        private string _name = string.Empty;

        [ObservableProperty]
        private string _emailErrorMessage = string.Empty;

        [ObservableProperty]
        private string _confirmEmailErrorMessage = string.Empty;

        [ObservableProperty]
        private string _passwordErrorMessage = string.Empty;

        [ObservableProperty]
        private string _nameErrorMessage = string.Empty;

        [ObservableProperty]
        private bool _emailHasError = false;

        [ObservableProperty]
        private bool _confirmEmailHasError = false;

        [ObservableProperty]
        private bool _passwordHasError = false;

        [ObservableProperty]
        private bool _nameHasError = false;

        [ObservableProperty]
        ObservableCollection<int> _listDays = new ObservableCollection<int>();

        [ObservableProperty]
        private int _selectedDay;

        [ObservableProperty]
        ObservableCollection<string> _listMonths = new ObservableCollection<string>();

        [ObservableProperty]
        private string _selectedMonth;

        [ObservableProperty]
        ObservableCollection<int> _listYears = new ObservableCollection<int>();

        [ObservableProperty]
        private int _selectedYear;

        [ObservableProperty] private bool _isWeekSelected = true;
        [ObservableProperty] private bool _isBiWeekSelected;
        [ObservableProperty] private bool _isMonthSelected;
        [ObservableProperty] private bool _isMonthOrBiWeekSelected;


        [ObservableProperty] private ObservableCollection<DayForWeek> _listForWeek = new();
        [ObservableProperty] private ObservableCollection<int> _listForMonth = new();

        [ObservableProperty] private DayForWeek _selectedItemForWeek;
        [ObservableProperty] private ObservableCollection<int> _selectedItemsForMonthOrBiweek = new();
        [ObservableProperty] private decimal _salary = 0m;
        [ObservableProperty] private decimal _percentSave = 0m;
        [ObservableProperty] private decimal _totalSave = 0m;




        public RegisterViewModel(INavigationService navigationService, IUserService userService, IApiService apiService)
        {
            _validator = new RegisterValidator();
            _pasos = new List<ContentView>
            {
                new RegisterAccount{ BindingContext = this },
                new RegisterName{ BindingContext = this },
                new RegisterBirthDate{ BindingContext = this },
                new RegisterSalary{ BindingContext = this },
            };
            _navigationService = navigationService;
            _userService = userService;
            _apiService = apiService;


            var count = 0;
            foreach (var day in DateTimeFormatInfo.CurrentInfo.DayNames)
            {
                ListForWeek.Add(new DayForWeek()
                {
                    DayName = day,
                    DayNumber = count
                });
                count++;
            }

            for (int i = 1; i <= 31; i++)
            {
                ListForMonth.Add(i);
            }

            var today = DateTime.Now;

            for (int i = 1; i <= 31; i++)
            {
                ListDays.Add(i);
            }

            foreach (var month in DateTimeFormatInfo.CurrentInfo.MonthNames)
            {
                ListMonths.Add(month);
            }

            for (int i = today.Year - 3; i >= 1900; i--)
            {
                ListYears.Add(i);
            }

            SelectedDay = ListDays.First();
            SelectedYear = ListYears.First();
            SelectedMonth = ListMonths.First();

            SelectedItemForWeek = ListForWeek.First();

            // Inicializar el estado del botón
            UpdateCanContinue();
        }

        // Método para actualizar el estado de CanContinue basado en el paso actual
        private void UpdateCanContinue()
        {
            switch (PasoActual)
            {
                case 0: // RegisterAccount
                    var accountResult = _validator.Validate(this, options =>
                        options.IncludeProperties(nameof(Email), nameof(ConfirmEmail), nameof(Password)));
                    CanContinue = accountResult.IsValid;
                    break;
                case 1: // RegisterName
                    var nameResult = _validator.Validate(this, options =>
                        options.IncludeProperties(nameof(Name)));
                    CanContinue = nameResult.IsValid;
                    break;
                case 2: // RegisterBirthDate
                    var birthDateResult = _validator.Validate(this, options =>
                        options.IncludeProperties(nameof(SelectedDay), nameof(SelectedMonth), nameof(SelectedYear)));
                    CanContinue = birthDateResult.IsValid;
                    break;
                case 3: // RegisterSalary
                    var salaryResult = _validator.Validate(this, options =>
                        options.IncludeProperties(nameof(Salary), nameof(PercentSave)));

                    // También validamos que al menos un tipo de ingreso esté seleccionado
                    var incomeTypeSelected = IsWeekSelected || IsBiWeekSelected || IsMonthSelected;

                    CanContinue = salaryResult.IsValid && incomeTypeSelected;
                    break;
                default:
                    CanContinue = false;
                    break;
            }
        }

        partial void OnEmailChanged(string value)
        {
            ValidateEmail();
            UpdateCanContinue();
        }

        partial void OnConfirmEmailChanged(string value)
        {
            ValidateConfirmEmail();
            UpdateCanContinue();
        }

        partial void OnPasswordChanged(string value)
        {
            ValidatePassword();
            UpdateCanContinue();
        }

        partial void OnNameChanged(string value)
        {
            ValidateName();
            UpdateCanContinue();
        }

        partial void OnSelectedDayChanged(int value)
        {
            UpdateCanContinue();
        }

        partial void OnSelectedMonthChanged(string value)
        {
            var monthNumber = ListMonths.IndexOf(value) + 1;
            var prevDay = SelectedDay;

            var maxDay = DateTime.DaysInMonth(SelectedYear, monthNumber);

            ListDays.Clear();
            for (int d = 1; d <= maxDay; d++)
                ListDays.Add(d);

            SelectedDay = prevDay <= maxDay
                ? prevDay
                : maxDay;

            if (SelectedDay == prevDay)
                OnPropertyChanged(nameof(SelectedDay));

            UpdateCanContinue();
        }

        partial void OnSelectedYearChanged(int value)
        {
            UpdateCanContinue();
        }

        partial void OnIsBiWeekSelectedChanged(bool value)
        {
            IsMonthOrBiWeekSelected = IsBiWeekSelected || IsMonthSelected;
            UpdateCanContinue();
        }

        partial void OnIsMonthSelectedChanged(bool value)
        {
            IsMonthOrBiWeekSelected = IsBiWeekSelected || IsMonthSelected;
            UpdateCanContinue();
        }

        partial void OnIsWeekSelectedChanged(bool value)
        {
            UpdateCanContinue();
        }

        partial void OnSalaryChanged(decimal value)
        {
            TotalSave = Salary * (PercentSave / 100);
            UpdateCanContinue();
        }

        partial void OnPercentSaveChanged(decimal value)
        {
            if (value > 99)
                PercentSave = 99;
            if (value < 0)
                PercentSave = 0;
            TotalSave = Salary * (PercentSave / 100);
            UpdateCanContinue();
        }

        partial void OnSelectedItemsForMonthOrBiweekChanged(ObservableCollection<int> value)
        {
            UpdateCanContinue();
        }

        partial void OnSelectedItemForWeekChanged(DayForWeek value)
        {
            UpdateCanContinue();
        }

        partial void OnPasoActualChanged(int value)
        {
            // Actualizar el estado del botón cuando cambia el paso
            UpdateCanContinue();
        }

        public async Task MostrarPaso(ContentView contenedor)
        {
            await contenedor.FadeTo(0, 150);
            contenedor.Content = _pasos[PasoActual];
            await contenedor.FadeTo(1, 150);

            PuedeRetroceder = PasoActual > 0;

            // Actualizar el estado del botón al cambiar de paso
            UpdateCanContinue();
        }

        [RelayCommand]
        private async void Next()
        {
            if (PasoActual < _pasos.Count - 1)
            {
                PasoActual++;
                ActualizarVista();
            }
            else
            {
                if (!ValidateAll())
                    await Toast.Make("Por favor revise todos los campos antes de continuar").Show();
                else
                {
                    await SaveUser();
                }
            }
        }

        [RelayCommand]
        public async Task Previous()
        {
            if (PasoActual > 0)
            {
                PasoActual--;
                ActualizarVista();
            }
            else
            {
                await CheckExit();
            }
        }

        private async Task CheckExit()
        {
            if ((DateTime.Now - _lastExitClick).TotalMilliseconds < 2000)
            {
                await _navigationService.GoBackAsync();
            }
            else
            {
                await Toast.Make("Presione nuevamente para salir del registro").Show();

                _lastExitClick = DateTime.Now;
            }
        }

        private void ActualizarVista()
        {
            if (Application.Current?.MainPage is Shell shell &&
                shell.CurrentPage is WizardRegister page)
            {
                _ = MostrarPaso(page.FindByName<ContentView>("PasoContainer"));
            }
        }

        private async Task SaveUser()
        {
            _popupUtils.ShowPopup(new LoadingPopup());
            int payType = 0;
            int? firstPayDay = null;
            int? secondPayDay = null;
            if (IsWeekSelected)
            {
                payType = 1;
                firstPayDay = SelectedItemForWeek.DayNumber;
            }

            else if (IsBiWeekSelected)
            {
                payType = 2;
                firstPayDay = SelectedItemsForMonthOrBiweek.Min();
                secondPayDay = SelectedItemsForMonthOrBiweek.Max();
            }

            else if (IsMonthSelected)
            {
                payType = 3;
                firstPayDay = SelectedItemsForMonthOrBiweek.Min();
            }



            var user = new CreateUserModel
            var user = new User
            {
                Name = Name,
                BirthDate = DateTime.SpecifyKind(new DateTime(SelectedYear, ListMonths.IndexOf(SelectedMonth) + 1, SelectedDay), DateTimeKind.Utc),
                Salary = Salary,
                IncomeTypeId = payType,
                FirstPayDay = firstPayDay,
                SecondPayDay = secondPayDay,
                Password = Password,
                Email = Email,
                PercentSave = PercentSave
            };

            try
            {
                var res = await _apiService.CreateUser(user);
                user.UserId = res.UserId;
                Preferences.Set("token", res.Token);
                await _userService.CreateNewUser(user, res.Token);

                await Toast.Make($"Bienvenido {user.Name}").Show();
                await _navigationService.GoToAsync("//MainPage");
            }
            catch (Exception e)
            {
                await Toast.Make($"Token: {e.Message}", ToastDuration.Long).Show();
            }
            await _popupUtils.ClosePopup();
        }

        public bool ValidateAll()
        {
            var result = _validator.Validate(this);

            // Limpia todos los mensajes de error anteriores
            EmailHasError = ConfirmEmailHasError = PasswordHasError = NameHasError = false;

            // Si hay errores, asigna los mensajes de error a las propiedades correspondientes
            if (!result.IsValid)
            {
                foreach (var error in result.Errors)
                {
                    switch (error.PropertyName)
                    {
                        case nameof(Email):
                            EmailErrorMessage = error.ErrorMessage;
                            EmailHasError = true;
                            break;
                        case nameof(ConfirmEmail):
                            ConfirmEmailErrorMessage = error.ErrorMessage;
                            ConfirmEmailHasError = true;
                            break;
                        case nameof(Password):
                            PasswordErrorMessage = error.ErrorMessage;
                            PasswordHasError = true;
                            break;
                        case nameof(Name):
                            NameErrorMessage = error.ErrorMessage;
                            NameHasError = true;
                            break;
                    }
                }
            }

            return result.IsValid;
        }

        private bool ValidateEmail()
        {
            var result = _validator.Validate(this, options =>
                options.IncludeProperties(nameof(Email)));

            if (!result.IsValid)
            {
                EmailErrorMessage = result.Errors.First().ErrorMessage;
                EmailHasError = true;
            }
            else
            {
                EmailHasError = false;
            }

            return !EmailHasError;
        }

        private bool ValidateConfirmEmail()
        {
            var result = _validator.Validate(this, options =>
                options.IncludeProperties(nameof(ConfirmEmail)));

            if (!result.IsValid)
            {
                ConfirmEmailErrorMessage = result.Errors.First().ErrorMessage;
                ConfirmEmailHasError = true;
            }
            else
            {
                ConfirmEmailHasError = false;
            }

            return !ConfirmEmailHasError;
        }

        private bool ValidatePassword()
        {
            var result = _validator.Validate(this, options =>
                options.IncludeProperties(nameof(Password)));

            if (!result.IsValid)
            {
                PasswordErrorMessage = result.Errors.First().ErrorMessage;
                PasswordHasError = true;
            }
            else
            {
                PasswordHasError = false;
            }

            return !PasswordHasError;
        }

        private bool ValidateName()
        {
            var result = _validator.Validate(this, options =>
                options.IncludeProperties(nameof(Name)));

            if (!result.IsValid)
            {
                NameErrorMessage = result.Errors.First().ErrorMessage;
                NameHasError = true;
            }
            else
            {
                NameHasError = false;
            }

            return !NameHasError;
        }
    }
}