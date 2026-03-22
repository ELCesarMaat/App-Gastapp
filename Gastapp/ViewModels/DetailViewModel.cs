using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Gastapp.Models;
using Gastapp.Services.Navigation;
using Gastapp.Services.SpendingService;

namespace Gastapp.ViewModels
{
    [QueryProperty(nameof(SpendingId), "spendingId")]
    public partial class DetailViewModel : ObservableObject
    {
        public readonly INavigationService NavigationService;
        [ObservableProperty] Spending _spending = new();
        public ISpendingService SpendingService;

        [ObservableProperty] private string _spendingId = string.Empty;
        [ObservableProperty] private string _amountText = "$0.00";
        [ObservableProperty] private string _categoryText = "Sin categoría";
        [ObservableProperty] private string _descriptionText = "Sin descripción";
        [ObservableProperty] private string _longDateText = string.Empty;
        [ObservableProperty] private string _timeText = string.Empty;
        [ObservableProperty] private string _headerSubtitle = "Revisa los datos del movimiento y verifica cuándo se registró.";

        public DetailViewModel(INavigationService navService, ISpendingService spendingService)
        {
            NavigationService = navService;
            SpendingService = spendingService;
        }

        partial void OnSpendingIdChanged(string value)
        {
            _ = GetData();
        }

        [RelayCommand]
        private async Task GoBack()
        {
            await NavigationService.GoBackAsync();
        }

        private async Task GetData()
        {
            Spending = await SpendingService.GetSpendingByIdAsync(SpendingId);
            if (string.IsNullOrEmpty(Spending.Description))
            {
                Spending.Description = "*SIN DESCRIPCION*";
            }

            CategoryText = Spending.Category?.CategoryName ?? "Sin categoría";
            DescriptionText = string.Equals(Spending.Description, "*SIN DESCRIPCION*", StringComparison.Ordinal)
                ? "No agregaste una descripción para este gasto."
                : Spending.Description;
            AmountText = $"-${Spending.Amount:N2}";
            LongDateText = Spending.Date.ToString("dddd dd 'de' MMMM", System.Globalization.CultureInfo.GetCultureInfo("es-MX"));
            TimeText = Spending.Date.ToString("hh:mm tt", System.Globalization.CultureInfo.GetCultureInfo("es-MX"));

            OnPropertyChanged(nameof(Spending));
        }

    }
}
