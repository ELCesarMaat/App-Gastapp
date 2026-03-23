using System;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Gastapp.Services.UserService;
using Gastapp.Utils;
using Gastapp.Services.Navigation;

namespace Gastapp.ViewModels;

public partial class ForgetPasswordViewModel(IUserService userService, INavigationService navigationService) : ObservableObject
{
    readonly PagesUtils pagesUtils = new PagesUtils();
    readonly IUserService _userService = userService;
    readonly INavigationService _navigationService = navigationService;

    [ObservableProperty]
    private string _email = string.Empty;

    [ObservableProperty]
    private string _verificationCode = string.Empty;

    [ObservableProperty]
    private string _newPassword = string.Empty;

    [ObservableProperty]
    private string _confirmPassword = string.Empty;

    [ObservableProperty]
    private string _statusMessage = string.Empty;

    [ObservableProperty]
    private bool _showStatusMessage;

    [ObservableProperty]
    private string _statusMessageColor = "#C62828";

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(CanSubmit))]
    private bool _isProcessing;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(ShowStep1))]
    [NotifyPropertyChangedFor(nameof(ShowStep2))]
    [NotifyPropertyChangedFor(nameof(ShowStep3))]
    [NotifyPropertyChangedFor(nameof(ShowSuccessSection))]
    private int _currentStep = 1;

    public bool ShowStep1 => CurrentStep == 1;
    public bool ShowStep2 => CurrentStep == 2;
    public bool ShowStep3 => CurrentStep == 3;
    public bool ShowSuccessSection => CurrentStep == 4;

    public bool CanSubmit => !IsProcessing;



    [RelayCommand]
    public async Task GoBack()
    {
        ResetForm();
        await _navigationService.GoBackAsync();
    }

    [RelayCommand]
    public async Task SendVerificationCode()
    {
        ShowStatusMessage = false;
        StatusMessageColor = "#C62828";

        if (string.IsNullOrWhiteSpace(Email))
        {
            StatusMessage = "Ingresa tu correo para recibir el código de verificación.";
            ShowStatusMessage = true;
            return;
        }

        IsProcessing = true;

        try
        {
            var result = await _userService.PasswordResetRequest(Email);

            if (result)
            {
                CurrentStep = 2;
            }
            else
            {
                StatusMessage = "No pudimos enviar el código. Verifica que el correo esté registrado e intenta de nuevo.";
                ShowStatusMessage = true;
            }
        }
        catch (Exception)
        {
            StatusMessage = "Error al enviar el código. Por favor intenta de nuevo.";
            ShowStatusMessage = true;
        }
        finally
        {
            IsProcessing = false;
        }
    }

    [RelayCommand]
    public async Task VerifyCode()
    {
        ShowStatusMessage = false;
        StatusMessageColor = "#C62828";

        if (string.IsNullOrWhiteSpace(VerificationCode))
        {
            StatusMessage = "Ingresa el código de verificación que recibiste en tu correo.";
            ShowStatusMessage = true;
            return;
        }

        IsProcessing = true;

        try
        {
            var result = await _userService.PasswordResetVerify(Email, VerificationCode);

            if (result)
            {
                CurrentStep = 3;
            }
            else
            {
                StatusMessage = "Código inválido o expirado. Verifica el código e intenta de nuevo.";
                ShowStatusMessage = true;
            }
        }
        catch (Exception)
        {
            StatusMessage = "Error al verificar el código. Por favor intenta de nuevo.";
            ShowStatusMessage = true;
        }
        finally
        {
            IsProcessing = false;
        }
    }

    [RelayCommand]
    public async Task ResetPassword()
    {
        ShowStatusMessage = false;
        StatusMessageColor = "#C62828";

        if (string.IsNullOrWhiteSpace(NewPassword))
        {
            StatusMessage = "Ingresa tu nueva contraseña.";
            ShowStatusMessage = true;
            return;
        }

        if (NewPassword.Length < 6)
        {
            StatusMessage = "La contraseña debe tener al menos 6 caracteres.";
            ShowStatusMessage = true;
            return;
        }

        if (NewPassword != ConfirmPassword)
        {
            StatusMessage = "Las contraseñas no coinciden.";
            ShowStatusMessage = true;
            return;
        }

        IsProcessing = true;

        try
        {
            var result = await _userService.PasswordResetConfirm(Email, VerificationCode, NewPassword);

            if (result)
            {
                CurrentStep = 4;
            }
            else
            {
                StatusMessage = "No se pudo cambiar la contraseña. El código puede haber expirado.";
                ShowStatusMessage = true;
            }
        }
        catch (Exception)
        {
            StatusMessage = "Error al cambiar la contraseña. Por favor intenta de nuevo.";
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
        await _navigationService.GoBackAsync();
    }

    private void ResetForm()
    {
        Email = string.Empty;
        VerificationCode = string.Empty;
        NewPassword = string.Empty;
        ConfirmPassword = string.Empty;
        StatusMessage = string.Empty;
        ShowStatusMessage = false;
        StatusMessageColor = "#C62828";
        CurrentStep = 1;
        IsProcessing = false;
    }

}
