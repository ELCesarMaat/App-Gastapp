using System;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Gastapp.Popups;
using Gastapp.Services;
using Gastapp.Services.UserService;
using Gastapp.Utils;
using Gastapp.Services.Navigation;

namespace Gastapp.ViewModels;

public partial class ForgetPasswordViewModel : ObservableObject
{
    readonly PagesUtils pagesUtils;
    readonly IUserService _userService;
    readonly INavigationService _navigationService;

    [ObservableProperty]
    private string _email = string.Empty;

    [ObservableProperty]
    private bool _isEmailEnabled = true;

    [ObservableProperty]
    private string _statusMessage = string.Empty;

    [ObservableProperty]
    private bool _showStatusMessage;

    [ObservableProperty]
    private string _statusMessageColor = "#C62828";

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(CanRequestTemporaryPassword))]
    private bool _isProcessing;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(ShowEmailRequestSection))]
    [NotifyPropertyChangedFor(nameof(ShowSuccessSection))]
    private bool _passwordSent;

    public bool ShowEmailRequestSection => !PasswordSent;

    public bool ShowSuccessSection => PasswordSent;

    public bool CanRequestTemporaryPassword => !IsProcessing;

    public ForgetPasswordViewModel(IUserService userService, INavigationService navigationService)
    {
        pagesUtils = new PagesUtils();
        _userService = userService;
        _navigationService = navigationService;
    }

    [RelayCommand]
    public async Task GoBack()
    {
        ResetForm();
        await _navigationService.GoBackAsync();
    }

    [RelayCommand]
    public async Task RequestTemporaryPassword()
    {
        ShowStatusMessage = false;
        StatusMessageColor = "#C62828";

        if (string.IsNullOrWhiteSpace(Email))
        {
            StatusMessage = "Ingresa tu correo para recibir una contraseña temporal.";
            ShowStatusMessage = true;
            return;
        }

        IsProcessing = true;
        PasswordSent = false;

        try
        {
            var result = await _userService.GenerateTemporaryPassword(Email);

            if (result)
            {
                PasswordSent = true;
                IsEmailEnabled = false;
                StatusMessageColor = "#14804A";
                StatusMessage = "Se ha enviado una contraseña temporal a tu correo. Revisa tu bandeja de entrada (a veces puede llegar a spam). Usa esa contraseña para iniciar sesión.";
            }
            else
            {
                StatusMessageColor = "#C62828";
                StatusMessage = "No pudimos procesar tu solicitud. Verifica que el correo esté registrado e intenta de nuevo.";
            }

            ShowStatusMessage = true;
        }
        catch (Exception ex)
        {
            StatusMessageColor = "#C62828";
            StatusMessage = $"Error: {ex.Message}. Por favor intenta de nuevo.";
            ShowStatusMessage = true;
        }
        finally
        {
            IsProcessing = false;
        }
    }

    [RelayCommand]
    public async Task GoToLogin()
    {
        ResetForm();
        await _navigationService.GoToAsync("//StartPage");
    }

    private void ResetForm()
    {
        Email = string.Empty;
        StatusMessage = string.Empty;
        ShowStatusMessage = false;
        StatusMessageColor = "#C62828";
        IsEmailEnabled = true;
        PasswordSent = false;
    }

}
