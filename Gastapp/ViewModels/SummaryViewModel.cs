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

namespace Gastapp.ViewModels
{
	public partial class SummaryViewModel : ObservableObject
	{
		public INavigationService NavigationService;
		public ISpendingService SpendingService;

		[ObservableProperty] 
		private ObservableCollection<Spending> _spendings = new();

        [ObservableProperty] private ObservableCollection<DateTime> _days = new();
        [ObservableProperty] private DateTime _selectedDay;
        [ObservableProperty] private bool _isEmptyList;

		[ObservableProperty] private decimal _totalAmount = 0;

		public SummaryViewModel(INavigationService navigationService, ISpendingService spendingService)
		{
			NavigationService = navigationService;
			SpendingService = spendingService;

			_spendings.CollectionChanged += SpendingsOnCollectionChanged;
			_ = GetData();
		}

        public async Task GetData()
        {
            await Task.WhenAll(UpdateSpendings(), GetDays());
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

            Days = new ObservableCollection<DateTime>(dayList);
            SelectedDay = Days.First();
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
            var response = await Application.Current!.MainPage!.DisplayAlert("¿Deseas eliminar gasto?", "Tu resumen podria verse afectado", "Eliminar", "Cancelar");
            if (!response)
                return;
            await SpendingService.RemoveSpendingById(item.SpendingId);
			Spendings.Remove(item);
		}
	}
}