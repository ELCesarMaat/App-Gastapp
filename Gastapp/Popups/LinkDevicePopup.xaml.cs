using CommunityToolkit.Maui.Views;
using CommunityToolkit.Mvvm.Messaging;
using Gastapp.Messages;

namespace Gastapp.Popups;

/// <summary>
/// Acompaña la vinculación de un reloj.
///
/// Ya no se teclea ningún código: el reloj se lo manda al teléfono por Bluetooth y
/// este llama solo a Device/Link. Aquí solo se espera y se avisa cuando ocurre, así
/// que el popup se puede cerrar en cualquier momento sin romper nada.
/// </summary>
public partial class LinkDevicePopup : Popup
{
    public LinkDevicePopup()
    {
        InitializeComponent();

        WeakReferenceMessenger.Default.Register<WearDeviceLinkedMessage>(this, (_, mensaje) =>
        {
            // Llega desde el servicio de la Data Layer, que corre fuera del hilo de UI.
            MainThread.BeginInvokeOnMainThread(() => MostrarVinculado(mensaje.Value));
        });
    }

    private void MostrarVinculado(string deviceName)
    {
        // Los iconos viven en los recursos de la aplicacion, no en los del popup.
        if (Application.Current?.Resources.TryGetValue("IconCircleCheck", out var icono) == true
            && icono is string glifo)
        {
            BadgeIcon.Text = glifo;
        }

        TitleLabel.Text = "¡Reloj vinculado!";

        MessageLabel.Text = string.IsNullOrWhiteSpace(deviceName)
            ? "Ya puedes registrar gastos desde el reloj."
            : $"{deviceName} ya puede registrar gastos.";

        WaitingRow.IsVisible = false;
        BusyIndicator.IsRunning = false;
    }

    private async void OnCloseClicked(object sender, EventArgs e) => await CloseAsync();

    protected override async Task OnClosed(object? result, bool wasDismissedByTappingOutsideOfPopup, CancellationToken token)
    {
        // Sin esto el popup queda suscrito para siempre y reaccionaria a
        // vinculaciones posteriores estando ya cerrado.
        WeakReferenceMessenger.Default.Unregister<WearDeviceLinkedMessage>(this);

        await base.OnClosed(result, wasDismissedByTappingOutsideOfPopup, token);
    }
}
