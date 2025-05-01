using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using CommunityToolkit.Maui.Alerts;
using CommunityToolkit.Maui.Core;
using CommunityToolkit.Maui.Core.Extensions;
using CommunityToolkit.Maui.Core.Views;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Gastapp.Models;
using Gastapp.Pages.Menu;
using Gastapp.Services;
using Gastapp.Services.Navigation;
using Gastapp.Services.SpendingService;
using Gastapp.Services.UserService;
using Syncfusion.Maui.Calendar;

namespace Gastapp.ViewModels
{
    public partial class SummaryViewModel : ObservableObject
    {
        public INavigationService NavigationService;
        public ISpendingService SpendingService;
        public IUserService UserService;

        private CalendarDateRange? _calendarRange;

        [ObservableProperty] private ObservableCollection<Spending> _spendings = new();

        [ObservableProperty] private ObservableCollection<DateTime> _days = new();
        [ObservableProperty] private DateTime _selectedDay;
        [ObservableProperty] private bool _isEmptyList = true;

        [ObservableProperty] private decimal _totalAmount = 0;

        [ObservableProperty] private User? _user = new();

        [ObservableProperty] private DateTime _todayDate = DateTime.Now;

        [ObservableProperty] private bool _isCalendarVisible;

        [ObservableProperty] private decimal _totalPeriodAmount;


        public SummaryViewModel(INavigationService navigationService, ISpendingService spendingService,
            IUserService userService)
        {
            NavigationService = navigationService;
            SpendingService = spendingService;
            UserService = userService;

            _spendings.CollectionChanged += SpendingsOnCollectionChanged;
            _ = GetData();
        }

        public async Task GetData()
        {
            await Task.WhenAll(UpdateSpendings(), GetDays(), GetUserInfo());
        }

        public async Task UpdateSpendings()
        {
            var newSpendings = (await SpendingService.GetSpendingListByDateAsync(SelectedDay))
                .ToObservableCollection();

            var toRemove = Spendings
                .Where(old => !newSpendings.Any(nw => nw.SpendingId == old.SpendingId))
                .ToList();

            foreach (var old in toRemove)
                Spendings.Remove(old);

            var toAdd = newSpendings
                .Where(nw => !Spendings.Any(old => old.SpendingId == nw.SpendingId))
                .ToList();

            foreach (var nw in toAdd)
                Spendings.Add(nw);
        }

        public async Task GetDays()
        {
            var dayList = await SpendingService.GetDaysWithSpendings(); // List<DateTime>
            Days.Clear();
            foreach (var day in dayList)
            {
                Days.Add(day);
            }

            SelectedDay = Days.First();
        }

        public async Task GetUserInfo()
        {
            var user = await UserService.GetUser();
            if (user != null)
            {
                User = user;
            }
            OnPropertyChanged(nameof(User));
        }


        partial void OnSelectedDayChanged(DateTime value)
        {
            _ = UpdateSpendings();
        }

        private void SpendingsOnCollectionChanged(object? sender, NotifyCollectionChangedEventArgs e)
        {
            TotalAmount = Spendings.Sum(s => s.Amount);
            IsEmptyList = Spendings.Count == 0;
        }

        [RelayCommand]
        public void OnSpendingClicked(Spending? item)
        {
            if (item == null)
                return;
            NavigationService.GoToAsync(nameof(SpendingDetailPage) + $"?spendingId={item.SpendingId}");
        }

        [RelayCommand]
        public async Task DeleteSpending(Spending item)
        {
            var response = await Application.Current!.MainPage!.DisplayAlert("¿Deseas eliminar gasto?",
                "Tu resumen podria verse afectado", "Eliminar", "Cancelar");
            if (!response)
                return;
            await SpendingService.RemoveSpendingById(item.SpendingId);
            Spendings.Remove(item);
        }

        [RelayCommand]
        private void ShowTotalToast()
        {
            Toast.Make($"Total ${TotalAmount:#,0.##}", ToastDuration.Short).Show();
        }

        [RelayCommand]
        private async Task ToggleCalendarVisible()
        {
            IsCalendarVisible = !IsCalendarVisible;
            if (IsCalendarVisible)
                GetRangeDays();
            else
                await GetDays();
        }

        [RelayCommand]
        private void CalendarSelectionChange(CalendarSelectionChangedEventArgs arg)
        {
            if (arg.NewValue is not CalendarDateRange)
                return;
            _calendarRange = (CalendarDateRange)arg.NewValue;

            GetRangeDays();
        }

        private async Task GetRangeDays()
        {
            if (_calendarRange == null)
                return;
            var startDate = _calendarRange.StartDate;
            var endDate = _calendarRange.EndDate;

            if (startDate == null || endDate == null)
                return;

            Days.Clear();
            for (var i = endDate.Value; i >= startDate.Value; i = i.AddDays(-1))
            {
                Days.Add(i);
            }

            SelectedDay = Days.First();
            TotalPeriodAmount = await SpendingService.GetTotalAmountByPeriod(startDate, endDate);
        }
    }
}