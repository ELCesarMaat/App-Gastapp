using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using CommunityToolkit.Maui.Behaviors;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Gastapp.BottomSheets;
using Gastapp.Pages.Menu;
using Gastapp.Services;
using Gastapp.Services.Navigation;
using The49.Maui.BottomSheet;

namespace Gastapp.ViewModels
{
    public partial class MainPageViewModel : ObservableObject
    {
        public INavigationService NavigationService;
        public required NewSpendingBottomSheet BottomSheet;
        public bool IsBsOpen = false;

        private readonly SummaryViewModel _summaryVm;
        private readonly NewSpendingViewModel _newSpendingVm;
        private readonly SettingsViewModel _settingsVm;
        private readonly ProfileViewModel _profileVm;
        private SavesViewModel _savesVm;


        private readonly SummaryPage _spendingsPage;
        private readonly SettingsPage _settingsPage;
        private readonly ProfilePage _profilePage;
        private SavesPage _summaryPage;

        [ObservableProperty] private ContentView _currentPage;
        [ObservableProperty] private bool _showNavBar;
        [ObservableProperty] private string _currentPageTitle = string.Empty;
        [ObservableProperty] private string _navBarColor = "#FFFFFF";

        [ObservableProperty] private bool _isSummarySelected;
        [ObservableProperty] private bool _isSavesSelected;
        [ObservableProperty] private bool _isProfileSelected;
        [ObservableProperty] private bool _isSettingsSelected;


        public MainPageViewModel(INavigationService nav, SummaryViewModel summaryVm, NewSpendingViewModel spendingVm,
            SettingsViewModel settingsVm, ProfileViewModel profileVm, SavesViewModel savesVm)
        {
            NavigationService = nav;

            _newSpendingVm = spendingVm;
            _summaryVm = summaryVm;
            _settingsVm = settingsVm;
            _profileVm = profileVm;
            _savesVm = savesVm;
            _savesVm.MainPageVm = this;


            _spendingsPage = new SummaryPage(summaryVm);
            _settingsPage = new SettingsPage(settingsVm);
            _profilePage = new ProfilePage(profileVm);
            _summaryPage = new SavesPage(_savesVm);

            CurrentPage = _spendingsPage;
            IsSummarySelected = true;
        }

        private async Task ShowSummaryPageAsync()
        {
            CurrentPage = _spendingsPage;
            await _summaryVm.GetData();
            ChangeStatusBarColor("#FFFFFF", false);
            CurrentPageTitle = string.Empty;
            ClearButtonSelection();
            IsSummarySelected = true;
        }

        [RelayCommand]
        private async Task SetSpendingsPage()
        {
            await ShowSummaryPageAsync();
        }

        [RelayCommand]
        private async Task SetSummaryPage()
        {
            await ShowSummaryPageAsync();
        }

        [RelayCommand]
        private async Task SetSettingsPage()
        {
            if (IsSettingsSelected)
            {
                await ShowSummaryPageAsync();
                return;
            }

            CurrentPage = _settingsPage;
            //_ = _settingsVm.GetData();
            ChangeStatusBarColor("#FFFFFF");
            CurrentPageTitle = "Ajustes";
            ClearButtonSelection();
            IsSettingsSelected = true;
        }

        [RelayCommand]
        private async Task SetProfilePage()
        {
            if (IsProfileSelected)
            {
                await ShowSummaryPageAsync();
                return;
            }

            await _profileVm.GetUser();
            CurrentPage = _profilePage;
            //_ = _profileVm.GetData();
            ChangeStatusBarColor("#FFFFFF");
            CurrentPageTitle = "Perfil";
            ClearButtonSelection();
            IsProfileSelected = true;
        }

        [RelayCommand]
        private async Task SetSavesPage()
        {
            if (IsSavesSelected)
            {
                await ShowSummaryPageAsync();
                return;
            }

            _summaryPage = new SavesPage(_savesVm);
            CurrentPage = _summaryPage;
            await _savesVm.GetData();

            ChangeStatusBarColor(_savesVm.HealthColor);
            CurrentPageTitle = "Ahorros";

            ClearButtonSelection();
            IsSavesSelected = true;
        }


        [RelayCommand]
        private void OpenBottomSheet()
        {
            if (IsBsOpen)
                return;

            BottomSheet = new NewSpendingBottomSheet(_newSpendingVm);
            _newSpendingVm.MenuSelectedDate = _summaryVm.SelectedDay;
            _ = _newSpendingVm.GetCategories();
            _ = BottomSheet.ShowAsync();
            IsBsOpen = true;
            BottomSheet.Dismissed += BottomSheetOnDismissed;
        }

        private void BottomSheetOnDismissed(object? sender, DismissOrigin e)
        {
            if (CurrentPage is SummaryPage)
                _ = _summaryVm.UpdateSpendings();
            if (CurrentPage is SavesPage && _newSpendingVm.HasNewSpending)
                _ = _savesVm.GetData();
            IsBsOpen = false;
            BottomSheet.Dismissed -= BottomSheetOnDismissed;
        }

        public void ChangeStatusBarColor(string color, bool showNavBar = true)
        {
            var behavior = Shell.Current.CurrentPage.Behaviors.OfType<StatusBarBehavior>().FirstOrDefault();
            if (behavior != null)
            {
                behavior.StatusBarColor = Color.Parse(color);
            }

            NavBarColor = color;
            ShowNavBar = showNavBar;
        }

        private void ClearButtonSelection()
        {
            IsSummarySelected = IsSavesSelected = IsProfileSelected = IsSettingsSelected = false;
        }
    }
}