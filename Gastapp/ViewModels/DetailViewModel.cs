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
using Gastapp.Services.UserService;

namespace Gastapp.ViewModels
{
    [QueryProperty(nameof(SpendingId), "spendingId")]
    public partial class DetailViewModel(INavigationService navService, ISpendingService spendingService, IUserService userService) : ObservableObject
    {
        private bool _isSubscribed;

        public readonly INavigationService NavigationService = navService;
        public readonly ISpendingService SpendingService = spendingService;
        public readonly IUserService UserService = userService;
        
        [ObservableProperty] Spending _spending = new();
        [ObservableProperty] private string _spendingId = string.Empty;
        [ObservableProperty] private string _amountText = "$0.00";
        [ObservableProperty] private string _categoryText = "Sin categoría";
        [ObservableProperty] private string _descriptionText = "Sin descripción";
        [ObservableProperty] private string _longDateText = string.Empty;
        [ObservableProperty] private string _timeText = string.Empty;
        [ObservableProperty] private string _headerSubtitle = "Revisa los datos del movimiento y verifica cuándo se registró.";

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
            EnsureSubscriptions();

            var spending = await SpendingService.GetSpendingByIdAsync(SpendingId);
            if (spending == null)
            {
                await Microsoft.Maui.Controls.Application.Current.MainPage.DisplayAlert("Aviso", "El gasto ya no existe.", "OK");
                await NavigationService.GoBackAsync();
                return;
            }

            Spending = spending;
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
            HeaderSubtitle = $"{CategoryText} • {Spending.Date:dd/MM/yyyy HH:mm}";

            OnPropertyChanged(nameof(Spending));
        }

        private void EnsureSubscriptions()
        {
            if (_isSubscribed)
                return;

            Microsoft.Maui.Controls.MessagingCenter.Subscribe<object, string>(this, NewSpendingViewModel.SpendingsChangedMessage, async (_, spendingId) =>
            {
                if (string.IsNullOrWhiteSpace(SpendingId))
                    return;

                if (string.IsNullOrWhiteSpace(spendingId) || spendingId == SpendingId)
                {
                    await GetData();
                }
            });

            _isSubscribed = true;
        }

        [RelayCommand]
        public async Task EditSpending()
        {
            // Abrir el bottom sheet de edición directamente
            var spendingService = SpendingService;
            var userService = UserService;
            if (userService == null)
            {
                await Microsoft.Maui.Controls.Application.Current.MainPage.DisplayAlert("Error", "No se pudo abrir el editor de gasto.", "OK");
                return;
            }

            var vm = new Gastapp.ViewModels.NewSpendingViewModel(spendingService, userService);
            await vm.GetCategories();
            vm.LoadForEdit(Spending);
            var bottomSheet = new Gastapp.BottomSheets.NewSpendingBottomSheet(vm);
            await bottomSheet.ShowAsync();
        }
    }
}
