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

        [ObservableProperty] private int _spendingId;

        public DetailViewModel(INavigationService navService, ISpendingService spendingService)
        {
            NavigationService = navService;
            SpendingService = spendingService;
        }

        partial void OnSpendingIdChanged(int value)
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
        }

    }
}
