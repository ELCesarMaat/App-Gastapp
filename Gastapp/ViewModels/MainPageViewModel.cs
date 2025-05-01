using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
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


        private readonly SummaryPage _summaryPage;
        private readonly SettingsPage _settingsPage;
        private readonly ProfilePage _profilePage;

        [ObservableProperty] private ContentView _currentPage;

        public MainPageViewModel(INavigationService nav, SummaryViewModel summaryVm, NewSpendingViewModel spendingVm, SettingsViewModel settingsVm, ProfileViewModel profileVm)
        {
            NavigationService = nav;

            _newSpendingVm = spendingVm;
            _summaryVm = summaryVm;
            _settingsVm = settingsVm;
            _profileVm = profileVm;


            _summaryPage = new SummaryPage(summaryVm);
            _settingsPage = new SettingsPage(settingsVm);
            _profilePage = new ProfilePage(profileVm);

            CurrentPage = new SummaryPage(_summaryVm);
        }


        [RelayCommand]
        private void SetSummaryPage()
        {
            CurrentPage = _summaryPage;
            //Cambiar esto a alguna funcion que actualice solo lo necesario
            _ = _summaryVm.GetData();
        }

        [RelayCommand]
        private void SetSettingsPage()
        {
            CurrentPage = _settingsPage;
            //_ = _settingsVm.GetData();
        }

        [RelayCommand]
        private void SetProfilePage()
        {
            CurrentPage = _profilePage;
            //_ = _profileVm.GetData();
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
            _ = _summaryVm.UpdateSpendings();
            IsBsOpen = false;
            BottomSheet.Dismissed -= BottomSheetOnDismissed;
        }
    }
}