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

        private readonly SummaryPage _summaryPage;
        private readonly SummaryViewModel _summaryVm;
        private readonly NewSpendingViewModel _newSpendingVm;


        private readonly SettingsPage _settingsPage;

        [ObservableProperty] private ContentView _currentPage;

        public MainPageViewModel(INavigationService nav, SummaryViewModel summaryVm, NewSpendingViewModel spendingVm)
        {
            NavigationService = nav;

            _newSpendingVm = spendingVm;

            _summaryVm = summaryVm;
            _summaryPage = new SummaryPage(summaryVm);

            _settingsPage = new SettingsPage();

            CurrentPage = new SummaryPage(_summaryVm);
        }


        [RelayCommand]
        private void SetSummaryPage()
        {
            CurrentPage = _summaryPage;
        }

        [RelayCommand]
        private void SetSettingsPage()
        {
            CurrentPage = _settingsPage;
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