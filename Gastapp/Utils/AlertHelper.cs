using CommunityToolkit.Maui.Views;
using Gastapp.Popups;

namespace Gastapp.Utils;

public static class AlertHelper
{
    /// <summary>
    /// Muestra un popup de alerta personalizado que reemplaza a DisplayAlert.
    /// Si se proporciona <paramref name="cancel"/>, el popup tendrá dos botones (confirmación + cancelar).
    /// Retorna true si el usuario presionó el botón principal, false si canceló.
    /// </summary>
    public static async Task<bool> ShowAlertAsync(
        string title,
        string message,
        string accept,
        string? cancel = null)
    {
        var mainPage = Application.Current?.MainPage;
        if (mainPage == null)
            return false;

        var popup = new AlertPopup(title, message, accept, cancel);
        var result = await mainPage.ShowPopupAsync(popup);
        return result is true;
    }
}
