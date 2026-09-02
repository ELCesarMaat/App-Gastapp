using CommunityToolkit.Maui.Views;
using Gastapp.Models.Models;

namespace Gastapp.Popups;

/// <summary>
/// Captura el codigo de emparejamiento que muestra el reloj y lo envia a la API.
/// Devuelve el dispositivo vinculado, o null si el usuario cancela.
///
/// La validacion se muestra dentro del popup en lugar de abrir un alert encima,
/// para que el usuario no pierda lo que ya escribio.
/// </summary>
public partial class LinkDevicePopup : Popup
{
    private readonly Func<string, Task<LinkDeviceResponse?>> _linkAsync;
    private bool _isBusy;
    private bool _isFormatting;

    /// <param name="linkAsync">
    /// Envia el codigo ya normalizado a la API. Debe devolver el dispositivo si vinculo,
    /// o lanzar una excepcion cuyo mensaje se muestra tal cual en el popup.
    /// </param>
    public LinkDevicePopup(Func<string, Task<LinkDeviceResponse?>> linkAsync)
    {
        InitializeComponent();
        _linkAsync = linkAsync;
    }

    private void OnCodeFocusChanged(object? sender, FocusEventArgs e)
    {
        // Si hay un error visible, el trazo rojo manda sobre el de foco.
        if (ErrorRow.IsVisible)
            return;

        SetFieldStroke(e.IsFocused ? "FieldStrokeFocus" : "FieldStrokeIdle");
    }

    private void SetFieldStroke(string resourceKey)
    {
        if (Card.Resources.TryGetValue(resourceKey, out var brush) && brush is Brush stroke)
            CodeFieldBorder.Stroke = stroke;
    }

    private void OnCodeTextChanged(object? sender, TextChangedEventArgs e)
    {
        if (_isFormatting)
            return;

        HideError();

        // Mayusculas y guion automatico tras el tercer caracter, para que el usuario
        // teclee tal cual lo ve en el reloj sin preocuparse por el formato.
        var limpio = new string((e.NewTextValue ?? string.Empty)
            .ToUpperInvariant()
            .Where(char.IsLetterOrDigit)
            .ToArray());

        if (limpio.Length > 6)
            limpio = limpio[..6];

        var formateado = limpio.Length > 3 ? $"{limpio[..3]}-{limpio[3..]}" : limpio;

        if (formateado == e.NewTextValue)
            return;

        _isFormatting = true;
        CodeEntry.Text = formateado;
        CodeEntry.CursorPosition = formateado.Length;
        _isFormatting = false;
    }

    private async void OnConfirmClicked(object sender, EventArgs e)
    {
        if (_isBusy)
            return;

        var code = new string((CodeEntry.Text ?? string.Empty)
            .Where(char.IsLetterOrDigit)
            .ToArray());

        if (code.Length != 6)
        {
            ShowError("El código tiene 6 caracteres. Revísalo en la pantalla del reloj.");
            return;
        }

        SetBusy(true);
        try
        {
            var device = await _linkAsync(code);
            if (device == null)
            {
                ShowError("Código no válido o expirado. Genera otro en el reloj.");
                return;
            }

            await CloseAsync(device);
        }
        catch (Exception ex)
        {
            ShowError(ex.Message);
        }
        finally
        {
            SetBusy(false);
        }
    }

    private async void OnCancelClicked(object sender, EventArgs e)
    {
        if (_isBusy)
            return;

        await CloseAsync(null);
    }

    private void SetBusy(bool busy)
    {
        _isBusy = busy;
        BusyIndicator.IsVisible = busy;
        BusyIndicator.IsRunning = busy;
        ConfirmButton.IsEnabled = !busy;
        CancelButton.IsEnabled = !busy;

        // El plan gratuito de Render puede tardar casi un minuto en despertar,
        // asi que conviene decirlo en vez de dejar el boton mudo.
        ConfirmButton.Text = busy ? "Vinculando..." : "Vincular";
    }

    private void ShowError(string message)
    {
        ErrorLabel.Text = message;
        ErrorRow.IsVisible = true;
        SetFieldStroke("FieldStrokeError");
    }

    private void HideError()
    {
        if (!ErrorRow.IsVisible)
            return;

        ErrorRow.IsVisible = false;
        SetFieldStroke(CodeEntry.IsFocused ? "FieldStrokeFocus" : "FieldStrokeIdle");
    }
}
