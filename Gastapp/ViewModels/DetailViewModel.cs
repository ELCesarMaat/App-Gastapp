using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using CommunityToolkit.Maui.Alerts;
using CommunityToolkit.Maui.Core;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using CommunityToolkit.Mvvm.Messaging;
using Gastapp.Messages;
using Gastapp.Models;
using Gastapp.Services.Navigation;
using Gastapp.Services.SpendingService;
using Gastapp.Services.UserService;
using Gastapp.Utils;
using The49.Maui.BottomSheet;

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
        [ObservableProperty] private string _descriptionText = string.Empty;
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
                await AlertHelper.ShowAlertAsync("Aviso", "El gasto ya no existe.", "OK");
                await NavigationService.GoBackAsync();
                return;
            }

            Spending = spending;
            CategoryText = Spending.Category?.CategoryName ?? "Sin categoría";
            DescriptionText = " - ";
            if (Spending.Description is not null && !string.IsNullOrEmpty(Spending.Description))
                DescriptionText = Spending.Description;

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

            WeakReferenceMessenger.Default.Register<SpendingChangedMessage>(this, (_, message) =>
            {
                if (string.IsNullOrWhiteSpace(SpendingId))
                    return;

                _ = GetData();
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
                await AlertHelper.ShowAlertAsync("Error", "No se pudo abrir el editor de gasto.", "OK");
                return;
            }

            var vm = new Gastapp.ViewModels.NewSpendingViewModel(spendingService, userService);
            await vm.GetCategories();
            vm.LoadForEdit(Spending);
            var bottomSheet = new Gastapp.BottomSheets.NewSpendingBottomSheet(vm);

            var dismissedTcs = new TaskCompletionSource();
            void OnDismissed(object? _, DismissOrigin __) => dismissedTcs.TrySetResult();
            bottomSheet.Dismissed += OnDismissed;

            await bottomSheet.ShowAsync();
            await dismissedTcs.Task;

            bottomSheet.Dismissed -= OnDismissed;

            if (vm.HasNewSpending)
            {
                await GetData();
                await Toast.Make("Cambios guardados", ToastDuration.Short).Show();
            }
        }
    }
}
