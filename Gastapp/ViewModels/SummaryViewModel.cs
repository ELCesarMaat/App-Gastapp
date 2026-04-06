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
using CommunityToolkit.Mvvm.Messaging;
using Gastapp.Messages;
using Gastapp.Models;
using Gastapp.Pages.Menu;
using Gastapp.Services;
using Gastapp.Services.Navigation;
using Gastapp.Services.SpendingService;
using Gastapp.Services.UserService;
using Gastapp.Utils;
using Syncfusion.Maui.Calendar;

namespace Gastapp.ViewModels
{
    public partial class SummaryViewModel(INavigationService navigationService, ISpendingService spendingService, IUserService userService) : ObservableObject
    {
        public INavigationService NavigationService = navigationService;
        public ISpendingService SpendingService = spendingService;
        public IUserService UserService = userService;

        private CalendarDateRange? _calendarRange;

        [ObservableProperty] private ObservableCollection<Spending> _spendings = [];
        [ObservableProperty] private ObservableCollection<SpendingGroup> _spendingsGroup = [];
        [ObservableProperty] private ObservableCollection<DayItem> _days = [];
        [ObservableProperty] private DayItem? _selectedDay;
        [ObservableProperty] private bool _isEmptyList = true;
        [ObservableProperty] private decimal _totalAmount = 0;
        [ObservableProperty] private User? _user = new();
        [ObservableProperty] private DateTime _todayDate = DateTime.Now;
        [ObservableProperty] private bool _isCalendarVisible;
        [ObservableProperty] private decimal _totalPeriodAmount;
        [ObservableProperty] private string _selectedDateLabel = string.Empty;
        [ObservableProperty] private string _selectedDateCaption = "Selecciona un día para revisar tus movimientos.";
        [ObservableProperty] private string _spendingCountText = "0 movimientos";
        [ObservableProperty] private string _calendarToggleText = "Ver calendario";
        [ObservableProperty] private string _periodSummaryText = "Sin movimientos registrados aún.";
        [ObservableProperty] private string _activePeriodLabel = string.Empty;
        [ObservableProperty] private bool _canGoToNewerPeriod;
        [ObservableProperty] private int _periodOffset;
        private bool _isInitialized;

        private void EnsureInitialized()
        {
            if (_isInitialized)
                return;

            _spendings.CollectionChanged += SpendingsOnCollectionChanged;
            WeakReferenceMessenger.Default.Register<SpendingChangedMessage>(this, (_, _) =>
            {
                _ = GetDays();
            });

            WeakReferenceMessenger.Default.Register<DayChangedMessage>(this, (_, msg) =>
            {
                TodayDate = msg.Value;
                PeriodOffset = 0;
                _ = GetDays();
            });

            _isInitialized = true;
        }

        public async Task GetData()
        {
            EnsureInitialized();
            await GetUserInfo();
            await GetDays();
        }

        public async Task UpdateSpendings()
        {
            EnsureInitialized();
            var refreshed = await SpendingService.GetSpendingListByDateAsync(SelectedDay?.Date ?? DateTime.Today);
            Spendings.Clear();
            foreach (var spending in refreshed)
            {
                Spendings.Add(spending);
            }

            GroupSpendings();
            UpdateSummaryHeader();
        }

        private void GroupSpendings()
        {
            var grouped = Spendings.GroupBy(s => s.Category?.CategoryName ?? "Sin categoría")
                .Select(g => new SpendingGroup(g.Key, new ObservableCollection<Spending>(g)))
                .ToList();
            SpendingsGroup.Clear();
            foreach (var group in grouped)
            {
                SpendingsGroup.Add(group);
            }
        }

        public async Task GetDays()
        {
            var previousSelectedDate = SelectedDay?.Date ?? DateTime.Today;
            var dayList = await SpendingService.GetAllPeriodDays(PeriodOffset);
            Days.Clear();
            foreach (var day in dayList)
            {
                Days.Add(day);
            }

            CanGoToNewerPeriod = PeriodOffset > 0;
            if (Days.Count > 0)
            {
                var periodStart = Days.Last().Date;
                var periodEnd = Days.First().Date;
                ActivePeriodLabel = periodStart.Date == periodEnd.Date
                    ? $"{periodStart:dd MMM yyyy}"
                    : $"{periodStart:dd MMM} - {periodEnd:dd MMM}";
            }
            else
            {
                ActivePeriodLabel = "Sin periodo";
            }

            if (Days.Count == 0)
            {
                SelectedDay = null;
                Spendings.Clear();
                GroupSpendings();
                UpdateSummaryHeader();
                return;
            }

            SelectedDay = Days.FirstOrDefault(d => d.Date == previousSelectedDate) ?? Days.First();

            await UpdateSpendings();
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


        partial void OnSelectedDayChanged(DayItem? value)
        {
            UpdateSummaryHeader();
            _ = UpdateSpendings();
        }

        private void SpendingsOnCollectionChanged(object? sender, NotifyCollectionChangedEventArgs e)
        {
            TotalAmount = Spendings.Sum(s => s.Amount);
            IsEmptyList = Spendings.Count == 0;
            GroupSpendings();
            UpdateSummaryHeader();

        }

        partial void OnIsCalendarVisibleChanged(bool value)
        {
            CalendarToggleText = value ? "Ocultar calendario" : "Ver calendario";
            UpdateSummaryHeader();
        }

        private void UpdateSummaryHeader()
        {
            SelectedDateLabel = (SelectedDay?.Date ?? DateTime.Today).ToString("dddd dd MMMM");

            SpendingCountText = Spendings.Count switch
            {
                0 => "Sin movimientos",
                1 => "1 movimiento",
                _ => $"{Spendings.Count} movimientos"
            };

            if (IsCalendarVisible && _calendarRange is not null && _calendarRange.StartDate is not null && _calendarRange.EndDate is not null)
            {
                PeriodSummaryText = $"Periodo del {_calendarRange.StartDate.Value:dd MMM} al {_calendarRange.EndDate.Value:dd MMM}";
                SelectedDateCaption = $"Total del periodo: ${TotalPeriodAmount:N2}";
                return;
            }

            PeriodSummaryText = IsEmptyList
                ? "No hay gastos para la fecha seleccionada."
                : "Desliza un gasto para eliminarlo o toca una tarjeta para ver el detalle.";
            SelectedDateCaption = "Consulta el total del día y cambia rápido entre fechas con movimiento.";
        }

        [RelayCommand]
        public async Task SetTodayDate()
        {
            var today = DateTime.Today;

            if (PeriodOffset != 0)
            {
                if (IsCalendarVisible)
                    IsCalendarVisible = false;

                _calendarRange = null;
                PeriodOffset = 0;
                await GetDays();
            }

            SelectedDay = Days.FirstOrDefault(d => d.Date == today) ?? Days.FirstOrDefault();
        }

        [RelayCommand]
        public async Task OnSpendingClicked(Spending? item)
        {
            if (item == null)
                return;

            await NavigationService.GoToAsync(nameof(SpendingDetailPage) + $"?spendingId={item.SpendingId}");
        }

        [RelayCommand]
        public async Task DeleteSpending(Spending item)
        {
            var response = await AlertHelper.ShowAlertAsync("¿Deseas eliminar gasto?",
                "Tu resumen podria verse afectado", "Eliminar", "Cancelar");
            if (!response)
                return;
            await SpendingService.RemoveSpendingById(item.SpendingId);
            Spendings.Remove(item);
            WeakReferenceMessenger.Default.Send(new SpendingChangedMessage(item.SpendingId));
        }

        [RelayCommand]
        private async Task ShowTotalToast()
        {
            await Toast.Make($"Total ${TotalAmount:#,0.##}", ToastDuration.Short).Show();
        }

        [RelayCommand]
        private async Task ToggleCalendarVisible()
        {
            IsCalendarVisible = !IsCalendarVisible;
            if (IsCalendarVisible)
                await GetRangeDays();
            else
                await GetDays();
        }

        [RelayCommand]
        private async Task PreviousPeriod()
        {
            if (IsCalendarVisible)
                IsCalendarVisible = false;

            _calendarRange = null;
            PeriodOffset++;
            await GetDays();
        }

        [RelayCommand]
        private async Task NextPeriod()
        {
            if (PeriodOffset == 0)
                return;

            if (IsCalendarVisible)
                IsCalendarVisible = false;

            _calendarRange = null;
            PeriodOffset--;
            await GetDays();
        }

        [RelayCommand]
        private async Task CalendarSelectionChange(CalendarSelectionChangedEventArgs arg)
        {
            if (arg.NewValue is not CalendarDateRange)
                return;
            _calendarRange = (CalendarDateRange)arg.NewValue;

            await GetRangeDays();
        }

        private async Task GetRangeDays()
        {
            if (_calendarRange == null)
                return;
            var startDate = _calendarRange.StartDate;
            var endDate = _calendarRange.EndDate;

            if (startDate == null || endDate == null)
                return;

            // Collect all dates in range
            var rangeDates = new List<DateTime>();
            for (var i = endDate.Value.Date; i >= startDate.Value.Date; i = i.AddDays(-1))
                rangeDates.Add(i);

            // Get dates that have spendings in this range
            var daysWithSpendings = await SpendingService.GetDaysWithSpendings();
            var set = daysWithSpendings.Select(d => d.Date).ToHashSet();

            Days.Clear();
            foreach (var d in rangeDates)
                Days.Add(new DayItem { Date = d, HasSpendings = set.Contains(d) });

            TotalPeriodAmount = await SpendingService.GetTotalAmountByPeriod(startDate, endDate);
            if (Days.Count == 0)
            {
                SelectedDay = null;
                Spendings.Clear();
                UpdateSummaryHeader();
                return;
            }

            SelectedDay = Days.First();
            UpdateSummaryHeader();
        }
    }
}