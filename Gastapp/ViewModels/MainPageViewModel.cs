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


        private readonly SummaryPage _summaryPage;
        private readonly SettingsPage _settingsPage;
        private readonly ProfilePage _profilePage;
        private SavesPage _savesPage;

        [ObservableProperty] private ContentView _currentPage;
        [ObservableProperty] private bool _showNavBar;
        [ObservableProperty] private string _currentPageTitle;
        [ObservableProperty] private string _navBarColor = "#FFFFFF";

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


            _summaryPage = new SummaryPage(summaryVm);
            _settingsPage = new SettingsPage(settingsVm);
            _profilePage = new ProfilePage(profileVm);
            _savesPage = new SavesPage(_savesVm);

            CurrentPage = new SummaryPage(_summaryVm);
        }


        [RelayCommand]
        private void SetSummaryPage()
        {
            CurrentPage = _summaryPage;
            //Cambiar esto a alguna funcion que actualice solo lo necesario
            _ = _summaryVm.GetData();
            ChangeStatusBarColor("#66E99D", false);
        }

        [RelayCommand]
        private void SetSettingsPage()
        {
            CurrentPage = _settingsPage;
            //_ = _settingsVm.GetData();
            ChangeStatusBarColor("#66E99D");
            CurrentPageTitle = "Ajustes";
        }

        [RelayCommand]
        private void SetProfilePage()
        {
            CurrentPage = _profilePage;
            //_ = _profileVm.GetData();
            ChangeStatusBarColor("#FFFFFF");
            CurrentPageTitle = "Ajustes";
        }

        [RelayCommand]
        private void SetSavesPage()
        {
            _savesPage = new SavesPage(_savesVm);
            CurrentPage = _savesPage;
            _ = _savesVm.GetData();
            //ChangeStatusBarColor("#61EFFF");
            CurrentPageTitle = "Resumen";
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
            if (CurrentPage is SavesPage)
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
    }
}