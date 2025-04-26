using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.Globalization;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using CommunityToolkit.Maui.Alerts;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Gastapp.Models;
using Gastapp.Pages.OfflineRegister;
using Gastapp.Pages.Register;
using Gastapp.Services.Navigation;

namespace Gastapp.ViewModels
{
    public partial class OfflineRegisterViewModel : ObservableObject
    {
        private readonly IList<ContentView> _pasos;
        private INavigationService _navigationService;
        private DateTime _lastExitClick = DateTime.MinValue;

        [ObservableProperty] private int _pasoActual = 0;

        [ObservableProperty] private bool _puedeRetroceder = false;

        [ObservableProperty] ObservableCollection<int> _listDays = new ObservableCollection<int>();
        [ObservableProperty] private int _selectedDay;

        [ObservableProperty] ObservableCollection<string> _listMonths = new ObservableCollection<string>();

        [ObservableProperty] private string _selectedMonth;

        [ObservableProperty] ObservableCollection<int> _listYears = new ObservableCollection<int>();

        [ObservableProperty] private int _selectedYear;

        [ObservableProperty] private string _name = string.Empty;

        [ObservableProperty] private bool _isWeekSelected = true;
        [ObservableProperty] private bool _isBiWeekSelected;
        [ObservableProperty] private bool _isMonthSelected;
        [ObservableProperty] private bool _isMonthOrBiWeekSelected;


        [ObservableProperty] private ObservableCollection<DayForWeek> _listForWeek = new();
        [ObservableProperty] private ObservableCollection<int> _listForMonth = new();

        [ObservableProperty] private DayForWeek _selectedItemForWeek;
        [ObservableProperty] private ObservableCollection<int> _selectedItemsForMonthOrBiweek = new();

        public OfflineRegisterViewModel(INavigationService navService)
        {
            _navigationService = navService;
            _pasos = new List<ContentView>
            {
                new RegisterName { BindingContext = this },
                new OfflineRegisterSalary { BindingContext = this },
                new RegisterBirthDate { BindingContext = this }
            };
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

        }

       

        partial void OnIsWeekSelectedChanged(bool value)
        {
        }

        partial void OnIsBiWeekSelectedChanged(bool value)
        {
            IsMonthOrBiWeekSelected = IsBiWeekSelected || IsMonthSelected;
        }

        partial void OnIsMonthSelectedChanged(bool value)
        {
            IsMonthOrBiWeekSelected = IsBiWeekSelected || IsMonthSelected;
        }

       

        public async Task MostrarPaso(ContentView contenedor)
        {
            await contenedor.FadeTo(0, 150);
            contenedor.Content = _pasos[PasoActual];
            await contenedor.FadeTo(1, 150);

            PuedeRetroceder = PasoActual > 0;
        }

        [RelayCommand]
        private void Next()
        {
            if (PasoActual < _pasos.Count - 1)
            {
                PasoActual++;
                //ValidateAll();
                ActualizarVista();
            }
            else
            {
                //if validateAll
                Toast.Make("Por favor revise todos los campos antes de continuar").Show();
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
                shell.CurrentPage is WizardOfflineRegisterPage page)
            {
                _ = MostrarPaso(page.FindByName<ContentView>("PasoContainer"));
            }
        }
    }
}