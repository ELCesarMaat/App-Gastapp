//using System;
//using System.Collections.Generic;
//using System.Collections.ObjectModel;
//using System.Collections.Specialized;
//using System.Globalization;
//using System.Linq;
//using System.Text;
//using System.Threading.Tasks;
//using CommunityToolkit.Maui.Alerts;
//using CommunityToolkit.Maui.Core;
//using CommunityToolkit.Mvvm.ComponentModel;
//using CommunityToolkit.Mvvm.Input;
//using Gastapp.Pages.OfflineRegister;
//using Gastapp.Pages.Register;
//using Gastapp.Services.Navigation;
//using Gastapp.Services.UserService;
//using Gastapp.Models;
//namespace Gastapp.ViewModels
//{
//    public partial class OfflineRegisterViewModel : ObservableObject
//    {
//        private readonly IList<ContentView> _pasos;
//        private INavigationService _navigationService;
//        private IUserService _userService;
//        private DateTime _lastExitClick = DateTime.MinValue;

//        [ObservableProperty] private decimal _salary = 0m;

//        [ObservableProperty] private int _pasoActual = 0;

//        [ObservableProperty] private bool _puedeRetroceder = false;

//        [ObservableProperty] ObservableCollection<int> _listDays = new ObservableCollection<int>();
//        [ObservableProperty] private int _selectedDay;

//        [ObservableProperty] ObservableCollection<string> _listMonths = new ObservableCollection<string>();

//        [ObservableProperty] private string _selectedMonth;

//        [ObservableProperty] ObservableCollection<int> _listYears = new ObservableCollection<int>();

//        [ObservableProperty] private int _selectedYear;

//        [ObservableProperty] private string _name = string.Empty;

//        [ObservableProperty] private bool _isWeekSelected = true;
//        [ObservableProperty] private bool _isBiWeekSelected;
//        [ObservableProperty] private bool _isMonthSelected;
//        [ObservableProperty] private bool _isMonthOrBiWeekSelected;


//        [ObservableProperty] private ObservableCollection<DayForWeek> _listForWeek = new();
//        [ObservableProperty] private ObservableCollection<int> _listForMonth = new();

//        [ObservableProperty] private DayForWeek _selectedItemForWeek;
//        [ObservableProperty] private ObservableCollection<int> _selectedItemsForMonthOrBiweek = new();

//        public OfflineRegisterViewModel(INavigationService navService, IUserService userService)
//        {
//            _navigationService = navService;
//            _userService = userService;
//            _pasos = new List<ContentView>
//            {
//                new RegisterName { BindingContext = this },
//                new OfflineRegisterSalary { BindingContext = this },
//                new RegisterBirthDate { BindingContext = this }
//            };
//            var count = 0;
//            foreach (var day in DateTimeFormatInfo.CurrentInfo.DayNames)
//            {
//                ListForWeek.Add(new DayForWeek()
//                {
//                    DayName = day,
//                    DayNumber = count
//                });
//                count++;
//            }

//            for (int i = 1; i <= 31; i++)
//            {
//                ListForMonth.Add(i);
//            }

//            var today = DateTime.Now;

//            for (int i = 1; i <= 31; i++)
//            {
//                ListDays.Add(i);
//            }

//            foreach (var month in DateTimeFormatInfo.CurrentInfo.MonthNames)
//            {
//                ListMonths.Add(month);
//            }

//            for (int i = today.Year - 3; i >= 1900; i--)
//            {
//                ListYears.Add(i);
//            }

//            SelectedDay = ListDays.First();
//            SelectedYear = ListYears.First();
//            SelectedMonth = ListMonths.First();
//        }


//        partial void OnNameChanged(string value)
//        {
//            //Toast.Make(value, ToastDuration.Long).Show();
//        }


//        partial void OnSelectedDayChanged(int value)
//        {
//        }


//        partial void OnSelectedMonthChanged(string value)
//        {
//            var monthNumber = ListMonths.IndexOf(value) + 1;
//            var prevDay = SelectedDay;

//            var maxDay = DateTime.DaysInMonth(SelectedYear, monthNumber);

//            ListDays.Clear();
//            for (int d = 1; d <= maxDay; d++)
//                ListDays.Add(d);

//            SelectedDay = prevDay <= maxDay
//                ? prevDay
//                : maxDay;

//            if (SelectedDay == prevDay)
//                OnPropertyChanged(nameof(SelectedDay));
//        }

//        partial void OnSelectedYearChanged(int value)
//        {
//        }

//        partial void OnSelectedItemForWeekChanged(DayForWeek value)
//        {
//        }

//        partial void OnIsWeekSelectedChanged(bool value)
//        {
//        }

//        partial void OnIsBiWeekSelectedChanged(bool value)
//        {
//            IsMonthOrBiWeekSelected = IsBiWeekSelected || IsMonthSelected;
//        }

//        partial void OnIsMonthSelectedChanged(bool value)
//        {
//            IsMonthOrBiWeekSelected = IsBiWeekSelected || IsMonthSelected;
//        }


//        public async Task MostrarPaso(ContentView contenedor)
//        {
//            await contenedor.FadeTo(0, 150);
//            contenedor.Content = _pasos[PasoActual];
//            await contenedor.FadeTo(1, 150);

//            PuedeRetroceder = PasoActual > 0;
//        }

//        [RelayCommand]
//        private async Task Next()
//        {
//            if (PasoActual < _pasos.Count - 1)
//            {
//                PasoActual++;
//                //ValidateAll();
//                ActualizarVista();
//            }
//            else
//            {
//                //if validateAll
//                await SaveUser();
//                //Toast.Make("Por favor revise todos los campos antes de continuar").Show();
//            }
//        }

//        [RelayCommand]
//        public async Task Previous()
//        {
//            if (PasoActual > 0)
//            {
//                PasoActual--;
//                ActualizarVista();
//            }
//            else
//            {
//                await CheckExit();
//            }
//        }

//        private async Task CheckExit()
//        {
//            if ((DateTime.Now - _lastExitClick).TotalMilliseconds < 2000)
//            {
//                await _navigationService.GoBackAsync();
//            }
//            else
//            {
//                await Toast.Make("Presione nuevamente para salir del registro").Show();

//                _lastExitClick = DateTime.Now;
//            }
//        }

//        private void ActualizarVista()
//        {
//            if (Application.Current?.MainPage is Shell shell &&
//                shell.CurrentPage is WizardOfflineRegisterPage page)
//            {
//                _ = MostrarPaso(page.FindByName<ContentView>("PasoContainer"));
//            }
//        }

//        private async Task SaveUser()
//        {
//            int payType = 0;
//            int? firstPayDay = null;
//            int? secondPayDay = null;
//            if (IsWeekSelected)
//            {
//                payType = 1;
//                firstPayDay = SelectedItemForWeek.DayNumber;
//            }

//            else if (IsBiWeekSelected)
//            {
//                payType = 2;
//                firstPayDay = SelectedItemsForMonthOrBiweek.Min();
//                secondPayDay = SelectedItemsForMonthOrBiweek.Max();
//            }

//            else if (IsMonthSelected)
//            {
//                payType = 3;
//                firstPayDay = SelectedItemsForMonthOrBiweek.Min();
//            }


//            IncomeType? type = await _userService.GetIncomeTypeById(payType);

//            var user = new User
//            {
//                LocalUserId = Guid.NewGuid().ToString(),
//                Name = Name,
//                BirthDate = new DateTime(SelectedYear, ListMonths.IndexOf(SelectedMonth) + 1, SelectedDay),
//                Salary = Salary,
//                IncomeTypeId = type.IncomeTypeId,
//                FirstPayDay = firstPayDay,
//                SecondPayDay = secondPayDay
//            };
//            var res = await _userService.CreateNewUser(user);
//            if (res != null)
//            {
//                await _navigationService.GoToAsync("//MainPage");
//            }
//        }
//    }
//}