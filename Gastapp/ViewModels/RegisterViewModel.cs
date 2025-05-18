using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.ComponentModel.DataAnnotations;
using System.Globalization;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;
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

namespace Gastapp.ViewModels
{
    public partial class RegisterViewModel : ObservableObject
    {
        private PagesUtils _popupUtils = new();
        private readonly IList<ContentView> _pasos;
        private INavigationService _navigationService;
        private IUserService _userService;
        private IApiService _apiService;
        private DateTime _lastExitClick = DateTime.MinValue;

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

        }

        partial void OnEmailChanged(string value)
        {
            ValidateEmail();
        }

        partial void OnConfirmEmailChanged(string value)
        {
            ValidateConfirmEmail();
        }

        partial void OnPasswordChanged(string value)
        {
            ValidatePassword();
        }

        partial void OnNameChanged(string value)
        {
            ValidateName();
        }

        partial void OnIsBiWeekSelectedChanged(bool value)
        {
            IsMonthOrBiWeekSelected = IsBiWeekSelected || IsMonthSelected;
        }

        partial void OnIsMonthSelectedChanged(bool value)
        {
            IsMonthOrBiWeekSelected = IsBiWeekSelected || IsMonthSelected;
        }

        partial void OnPercentSaveChanged(decimal value)
        {
            if (value > 99)
                PercentSave = 99;
            if (value < 0)
                PercentSave = 0;
            TotalSave = Salary * (PercentSave / 100);
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
        }


        public async Task MostrarPaso(ContentView contenedor)
        {
            await contenedor.FadeTo(0, 150);
            contenedor.Content = _pasos[PasoActual];
            await contenedor.FadeTo(1, 150);

            PuedeRetroceder = PasoActual > 0;
        }

        [RelayCommand]
        private async void Next()
        {

            if (PasoActual < _pasos.Count - 1)
            {
                PasoActual++;
                ValidateAll();
                ActualizarVista();
            }
            else
            {
                if(!ValidateAll())
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
            if((DateTime.Now - _lastExitClick).TotalMilliseconds < 2000)
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
        #region ValidationFunctions

        public bool ValidateAll()
        {
            return ValidateEmail() & ValidatePassword() & ValidateConfirmEmail()
                & ValidateName();
        }
       
        private bool ValidateEmail()
        {
            if (!Regex.IsMatch(Email, "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"))
            {
                EmailErrorMessage = "Ingrese un correo valido";
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
            if (!string.Equals(Email, ConfirmEmail))
            {
                ConfirmEmailErrorMessage = "Los correos no coinciden";
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
            if (Password.Length < 6)
            {
                PasswordErrorMessage = "La contraseña debe ser mayor de 6 caracteres";
                PasswordHasError = true;
            }

            else if (Password.Length > 20)
            {
                PasswordErrorMessage = "La contraseña no puede ser tan larga";
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
            if(Name.Length < 2)
            {
                NameErrorMessage = "Ingrese un nombre valido";
                NameHasError = true;
            }
            else
            {
                NameHasError = false;
            }

            return !NameHasError;
        }

        #endregion

    }
}
