using CommunityToolkit.Maui.Views;

namespace Gastapp.Popups;

public partial class AlertPopup : Popup
{
    public AlertPopup(string title, string message, string confirm, string? cancel = null)
    {
        InitializeComponent();

        TitleLabel.Text = title;
        MessageLabel.Text = message;
        ConfirmButton.Text = confirm;

        if (cancel != null)
        {
            CancelButton.IsVisible = true;
            CancelButton.Text = cancel;
        }

        ApplyStyle(title, confirm, cancel != null);
    }

    private void ApplyStyle(string title, string confirmText, bool hasCancelButton)
    {
        bool isDestructive = confirmText.Contains("Eliminar", StringComparison.OrdinalIgnoreCase)
                          || confirmText.Contains("Borrar", StringComparison.OrdinalIgnoreCase)
                          || confirmText.Contains("Quitar", StringComparison.OrdinalIgnoreCase);

        if (hasCancelButton)
        {
            if (isDestructive)
            {
                // Acción destructiva: icono rojo, botón confirmación rojo
                IconCircle.BackgroundColor = Color.FromArgb("#FDECEA");
                IconLabel.Text = "!";
                IconLabel.TextColor = Color.FromArgb("#C62828");
                ConfirmButton.BackgroundColor = Color.FromArgb("#C62828");
            }
            else
            {
                // Confirmación normal: icono verde
                IconCircle.BackgroundColor = Color.FromArgb("#DDF5EF");
                IconLabel.Text = "?";
                IconLabel.TextColor = Color.FromArgb("#126E63");
            }
        }
        else if (title.Contains("Error", StringComparison.OrdinalIgnoreCase))
        {
            // Alerta de error: icono rojo
            IconCircle.BackgroundColor = Color.FromArgb("#FDECEA");
            IconLabel.Text = "!";
            IconLabel.TextColor = Color.FromArgb("#C62828");
        }
        else
        {
            // Info neutral: icono verde
            IconCircle.BackgroundColor = Color.FromArgb("#DDF5EF");
            IconLabel.Text = "i";
            IconLabel.TextColor = Color.FromArgb("#126E63");
        }
    }

    private async void OnConfirmClicked(object sender, EventArgs e)
        => await CloseAsync(true);

    private async void OnCancelClicked(object sender, EventArgs e)
        => await CloseAsync(false);
}
