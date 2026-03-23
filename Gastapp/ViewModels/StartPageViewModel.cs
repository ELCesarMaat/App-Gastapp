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
using Microsoft.Maui.ApplicationModel;
using Refit;
using The49.Maui.BottomSheet;

namespace Gastapp.ViewModels
{
    public partial class StartPageViewModel(INavigationService navigationService, IApiService apiService, IUserService userService, GastappDbContext db) : ObservableObject
    {
        public INavigationService NavigationService = navigationService;
        public LoginBottomSheet? LoginBottomSheet;
        private readonly IApiService _apiService = apiService;
        private readonly IUserService _userService = userService;
        private readonly PagesUtils _dialogs = new();
        private GastappDbContext _db = db;

        [ObservableProperty] private bool _isBottomSheetOpen;
        [ObservableProperty] private string _email = string.Empty;
        [ObservableProperty] private string _password = string.Empty;
        [ObservableProperty] private string _errorMessage = string.Empty;
        [ObservableProperty] private bool _isPasswordHidden = true;
        [ObservableProperty] private bool _hasLoginError;

        partial void OnEmailChanged(string value)
        {
            ErrorMessage = string.Empty;
            HasLoginError = false;
        }

        partial void OnPasswordChanged(string value)
        {
            ErrorMessage = string.Empty;
            HasLoginError = false;
        }

        partial void OnErrorMessageChanged(string value)
        {
            HasLoginError = !string.IsNullOrWhiteSpace(value);
        }

        [RelayCommand]
        public async Task GoToRegister()
        {
            await NavigationService.GoToAsync("WizardRegister");
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
            if (string.IsNullOrWhiteSpace(Email) || string.IsNullOrWhiteSpace(Password))
            {
                ErrorMessage = "Ingresa tu correo y contraseña para continuar.";
                return;
            }
           
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
                    await LoginBottomSheet!.DismissAsync();

                await NavigationService.GoToAsync("//MainPage");
            }
            catch (HttpRequestException httpEx)
            {
                ErrorMessage = "Error de conexión. Verifica tu conexión a internet e intenta de nuevo.";
            }
            catch (ApiException apiEx) when (apiEx.StatusCode == System.Net.HttpStatusCode.BadRequest)
            {
                // Intentar extraer el mensaje de error del API
                try
                {
                    ErrorMessage = apiEx.Content;
                }
                catch
                {
                    ErrorMessage = "Credenciales inválidas. Verifica tu correo y contraseña.";
                }
            }
            catch (ApiException apiEx)
            {
                ErrorMessage = $"Error del servidor: {apiEx.StatusCode}. Intenta más tarde.";
            }
            catch (Exception ex)
            {
                ErrorMessage = ex.Message ?? "Ocurrió un error inesperado.";
            }

            await _dialogs.ClosePopup();
        }

        [RelayCommand]
        private void TogglePasswordVisibility()
        {
            IsPasswordHidden = !IsPasswordHidden;
        }

        [RelayCommand]
        private async Task GoToPrivacyNotice()
        {
            await Launcher.Default.OpenAsync("https://www.privacypolicies.com/live/063d06df-a5ce-42a4-9513-86839a3aa87d");
        }

        private void LoginBottomSheetOnDismissed(object? sender, DismissOrigin e)
        {
            IsBottomSheetOpen = false;
            if (LoginBottomSheet is not null)
                LoginBottomSheet.Dismissed -= LoginBottomSheetOnDismissed;
        }


        //[RelayCommand]
        //public async Task GoToOfflineRegister()
        //{
        //    await NavigationService.GoToAsync(nameof(WizardOfflineRegisterPage));
        //}

        [RelayCommand]
        public async Task GoToMainPage()
        {
            await NavigationService.GoToAsync("//MainPage");
        }

        [RelayCommand]
        public async Task GoToForgotPassword()
        {
            await NavigationService.GoToAsync(nameof(ForgetPasswordPage));
        }
    }
}