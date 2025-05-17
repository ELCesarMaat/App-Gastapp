using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Gastapp.BottomSheets;
using Gastapp.Data;
using Gastapp.Models;
using Gastapp.Pages;
using Gastapp.Popups;
using Gastapp.Services;
using Gastapp.Services.ApiService;
using Gastapp.Services.Navigation;
using Gastapp.Services.UserService;
using Gastapp.Utils;
using Refit;
using The49.Maui.BottomSheet;

namespace Gastapp.ViewModels
{
    public partial class StartPageViewModel : ObservableObject
    {
        public INavigationService _navigationService;
        public LoginBottomSheet LoginBottomSheet;
        private readonly IApiService _apiService;
        private readonly IUserService _userService;
        private readonly PagesUtils _dialogs = new();
        private GastappDbContext _db;

        [ObservableProperty] private bool _isBottomSheetOpen;
        [ObservableProperty] private string _email;
        [ObservableProperty] private string _password;
        [ObservableProperty] private string _errorMessage;

        public StartPageViewModel(INavigationService navigationService, IApiService apiService, IUserService userService, GastappDbContext db)
        {
            _navigationService = navigationService;
            _apiService = apiService;
            _userService = userService;
            _db = db;
        }

        partial void OnEmailChanged(string value)
        {
            ErrorMessage = string.Empty;
        }

        partial void OnPasswordChanged(string value)
        {
            ErrorMessage = string.Empty;
        }

        [RelayCommand]
        public async Task GoToRegister()
        {
            await _navigationService.GoToAsync("WizardRegister");
        }

        [RelayCommand]
        public async Task GoToLogin()
        {
            if (IsBottomSheetOpen)
                return;
            LoginBottomSheet = new LoginBottomSheet(this);
            LoginBottomSheet.Dismissed += LoginBottomSheetOnDismissed;
            await LoginBottomSheet.ShowAsync();
            IsBottomSheetOpen = true;
        }

        [RelayCommand]
        private async Task Login()
        {
           
            _dialogs.ShowPopup(new LoadingPopup());
            try
            {
                var res = await _apiService.Login(new LoginModel
                {
                    Email = Email,
                    Password = Password
                });
                await _userService.AddUserData(res);
                if (IsBottomSheetOpen)
                    await LoginBottomSheet.DismissAsync();

                await _navigationService.GoToAsync("//MainPage");
            }
            catch (Exception ex)
            {
                ErrorMessage = ex.Message;
            }

            
            await _dialogs.ClosePopup();

        }

        private void LoginBottomSheetOnDismissed(object? sender, DismissOrigin e)
        {
            IsBottomSheetOpen = false;
            LoginBottomSheet.Dismissed -= LoginBottomSheetOnDismissed;
        }


        //[RelayCommand]
        //public async Task GoToOfflineRegister()
        //{
        //    await _navigationService.GoToAsync(nameof(WizardOfflineRegisterPage));
        //}

        [RelayCommand]
        public async Task GoToMainPage()
        {
            await _navigationService.GoToAsync("//MainPage");
        }
    }
}