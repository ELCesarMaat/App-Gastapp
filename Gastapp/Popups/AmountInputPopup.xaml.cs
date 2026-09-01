using System.Globalization;
using System.Windows.Input;
using CommunityToolkit.Maui.Views;

namespace Gastapp.Popups;

/// <summary>
/// Fila informativa del bloque de contexto del popup (ej. "Saldo pendiente" -> "$1,200.00").
/// </summary>
public sealed class AmountContextRow
{
    public AmountContextRow(string label, string value, bool isHighlighted = false)
    {
        Label = label;
        Value = value;
        IsHighlighted = isHighlighted;
    }

    public string Label { get; }

    public string Value { get; }

    /// <summary>Resalta el valor (verde y mas grande) para el dato principal de la fila.</summary>
    public bool IsHighlighted { get; }
}

/// <summary>
/// Chip de monto rapido; al tocarlo rellena el campo de captura.
/// </summary>
public sealed class AmountQuickOption
{
    public AmountQuickOption(string label, decimal amount)
    {
        Label = label;
        Amount = amount;
    }

    public string Label { get; }

    public decimal Amount { get; }

    public string DisplayAmount => $"${Amount:N2}";

    /// <summary>Lo inyecta el popup al construirse; el chip lo ejecuta al ser tocado.</summary>
    public ICommand? SelectCommand { get; internal set; }
}

/// <summary>
/// Popup reutilizable para capturar un monto con contexto, montos rapidos y validacion en linea.
/// Se muestra con mainPage.ShowPopupAsync(popup) y devuelve el decimal capturado, o null si se cancela.
/// </summary>
public partial class AmountInputPopup : Popup
{
    private readonly bool _allowZero;

    public AmountInputPopup(
        string title,
        string? subtitle,
        string iconResourceKey,
        string fieldCaption,
        string confirmText,
        string cancelText = "Cancelar",
        decimal? initialAmount = null,
        IReadOnlyList<AmountContextRow>? contextRows = null,
        IReadOnlyList<AmountQuickOption>? quickOptions = null,
        bool allowZero = false)
    {
        InitializeComponent();

        _allowZero = allowZero;

        ContextRows = contextRows ?? [];
        QuickOptions = quickOptions ?? [];
        SelectQuickAmountCommand = new Command<AmountQuickOption>(OnQuickAmountSelected);

        // Cada chip ejecuta el comando del popup al ser tocado
        foreach (var option in QuickOptions)
            option.SelectCommand = SelectQuickAmountCommand;

        BindingContext = this;

        IconLabel.Text = ResolveIcon(iconResourceKey);
        TitleLabel.Text = title;
        SubtitleLabel.Text = subtitle ?? string.Empty;
        SubtitleLabel.IsVisible = !string.IsNullOrWhiteSpace(subtitle);
        FieldCaption.Text = fieldCaption;
        ConfirmButton.Text = confirmText;
        CancelButton.Text = cancelText;

        ContextBlock.IsVisible = ContextRows.Count > 0;
        QuickOptionsBlock.IsVisible = QuickOptions.Count > 0;

        // Solo se presugiere el monto cuando aporta algo; si es 0 se deja el placeholder
        if (initialAmount is > 0m)
            AmountEntry.Text = initialAmount.Value.ToString("F2", CultureInfo.CurrentCulture);
    }

    public IReadOnlyList<AmountContextRow> ContextRows { get; }

    public IReadOnlyList<AmountQuickOption> QuickOptions { get; }

    public ICommand SelectQuickAmountCommand { get; }

    private void OnQuickAmountSelected(AmountQuickOption? option)
    {
        if (option is null) return;

        AmountEntry.Text = option.Amount.ToString("F2", CultureInfo.CurrentCulture);
        ClearError();
    }

    private async void OnConfirmClicked(object sender, EventArgs e)
    {
        if (!TryParseAmount(AmountEntry.Text, out var amount))
        {
            ShowError("Ingresa un monto válido.");
            return;
        }

        if (amount < 0m)
        {
            ShowError("El monto no puede ser negativo.");
            return;
        }

        if (!_allowZero && amount == 0m)
        {
            ShowError("El monto debe ser mayor a $0.00.");
            return;
        }

        await CloseAsync(amount);
    }

    private async void OnCancelClicked(object sender, EventArgs e)
        => await CloseAsync(null);

    private void OnAmountTextChanged(object sender, TextChangedEventArgs e)
        => ClearError();

    private void OnAmountCompleted(object sender, EventArgs e)
        => OnConfirmClicked(sender, e);

    private void OnAmountFocused(object sender, FocusEventArgs e)
    {
        if (!ErrorRow.IsVisible)
            SetFieldStroke("FieldStrokeFocus");

        // Selecciona el monto sugerido para reemplazarlo de un solo tecleo
        if (!string.IsNullOrEmpty(AmountEntry.Text))
        {
            AmountEntry.CursorPosition = 0;
            AmountEntry.SelectionLength = AmountEntry.Text.Length;
        }
    }

    private void OnAmountUnfocused(object sender, FocusEventArgs e)
    {
        if (!ErrorRow.IsVisible)
            SetFieldStroke("FieldStrokeIdle");
    }

    private void ShowError(string message)
    {
        ErrorLabel.Text = message;
        ErrorRow.IsVisible = true;
        SetFieldStroke("FieldStrokeError");
        AmountEntry.Focus();
    }

    private void ClearError()
    {
        if (!ErrorRow.IsVisible) return;

        ErrorRow.IsVisible = false;
        SetFieldStroke(AmountEntry.IsFocused ? "FieldStrokeFocus" : "FieldStrokeIdle");
    }

    private void SetFieldStroke(string resourceKey)
    {
        if (Card.Resources.TryGetValue(resourceKey, out var resource) && resource is Brush stroke)
            AmountFrame.Stroke = stroke;
    }

    /// <summary>Obtiene el glifo de Font Awesome desde Icons.xaml por su llave de recurso.</summary>
    private static string ResolveIcon(string resourceKey)
    {
        if (Application.Current?.Resources.TryGetValue(resourceKey, out var resource) == true
            && resource is string glyph)
        {
            return glyph;
        }

        return string.Empty;
    }

    /// <summary>Acepta el monto con o sin simbolo, separadores de miles y punto o coma decimal.</summary>
    private static bool TryParseAmount(string? input, out decimal amount)
    {
        amount = 0m;

        if (string.IsNullOrWhiteSpace(input)) return false;

        var cleaned = new string(input.Where(c => !char.IsWhiteSpace(c) && c != '$').ToArray());

        if (cleaned.Length == 0) return false;

        return decimal.TryParse(cleaned, NumberStyles.Number, CultureInfo.CurrentCulture, out amount)
            || decimal.TryParse(cleaned, NumberStyles.Number, CultureInfo.InvariantCulture, out amount);
    }
}
