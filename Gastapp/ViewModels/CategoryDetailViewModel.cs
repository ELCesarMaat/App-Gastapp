using System.Collections.ObjectModel;
using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using CommunityToolkit.Mvvm.Messaging;
using Gastapp.Messages;
using Gastapp.Models;
using Gastapp.Pages.Menu;
using Gastapp.Services.Navigation;
using Gastapp.Services.SpendingService;
using Gastapp.Utils;

namespace Gastapp.ViewModels
{
    [QueryProperty(nameof(Category), "Category")]
    [QueryProperty(nameof(PeriodStart), "PeriodStart")]
    [QueryProperty(nameof(PeriodEnd), "PeriodEnd")]
    public partial class CategoryDetailViewModel(INavigationService navigationService, ISpendingService spendingService) : ObservableObject
    {
        private readonly INavigationService _navigationService = navigationService;
        private readonly ISpendingService _spendingService = spendingService;

        [ObservableProperty] private CategoryResume? _category;
        [ObservableProperty] private DateTime _periodStart;
        [ObservableProperty] private DateTime _periodEnd;
        [ObservableProperty] private ObservableCollection<SpendingGroup> _spendingsGroup = [];
        [ObservableProperty] private bool _isEmpty = true;
        [ObservableProperty] private decimal _totalAmount;
        [ObservableProperty] private string _periodLabel = string.Empty;
        [ObservableProperty] private string _spendingCountText = "Sin movimientos";

        public bool IsNotEmpty => !IsEmpty;

        partial void OnIsEmptyChanged(bool value) => OnPropertyChanged(nameof(IsNotEmpty));

        partial void OnCategoryChanged(CategoryResume? value) => TryLoad();
        partial void OnPeriodStartChanged(DateTime value) => TryLoad();
        partial void OnPeriodEndChanged(DateTime value) => TryLoad();

        private void TryLoad()
        {
            if (Category is null || PeriodStart == default || PeriodEnd == default)
                return;
            _ = LoadDataAsync();
        }

        private async Task LoadDataAsync()
        {
            if (Category is null) return;

            var spendings = await _spendingService.GetSpendingsByCategoryAndPeriod(
                Category.CategoryId, PeriodStart, PeriodEnd);

            TotalAmount = spendings.Sum(s => s.Amount);

            var grouped = spendings
                .GroupBy(s => s.Date.Date)
                .OrderByDescending(g => g.Key)
                .Select(g => new SpendingGroup(
                    g.Key.ToString("dddd d 'de' MMMM", CultureInfo.GetCultureInfo("es-MX")),
                    new ObservableCollection<Spending>(g.OrderByDescending(s => s.Date).ToList())))
                .ToList();

            SpendingsGroup = new ObservableCollection<SpendingGroup>(grouped);
            IsEmpty = SpendingsGroup.Count == 0;

            var count = spendings.Count;
            SpendingCountText = count switch
            {
                0 => "Sin movimientos",
                1 => "1 movimiento",
                _ => $"{count} movimientos"
            };

            PeriodLabel = PeriodStart.Date == PeriodEnd.Date
                ? PeriodStart.ToString("d 'de' MMMM", CultureInfo.GetCultureInfo("es-MX"))
                : $"{PeriodStart:dd MMM} – {PeriodEnd:dd MMM}";
        }

        [RelayCommand]
        private async Task GoBack() => await _navigationService.GoBackAsync();

        [RelayCommand]
        private async Task SpendingClicked(Spending? item)
        {
            if (item == null) return;
            await _navigationService.GoToAsync(nameof(SpendingDetailPage) + $"?spendingId={item.SpendingId}");
        }

        [RelayCommand]
        private async Task DeleteSpending(Spending item)
        {
            var confirm = await AlertHelper.ShowAlertAsync(
                "¿Deseas eliminar este gasto?",
                "El resumen del periodo se actualizará.",
                "Eliminar", "Cancelar");
            if (!confirm) return;

            await _spendingService.RemoveSpendingById(item.SpendingId);
            WeakReferenceMessenger.Default.Send(new SpendingChangedMessage(item.SpendingId));
            await LoadDataAsync();
        }
    }
}
