using Gastapp.ViewModels;
using The49.Maui.BottomSheet;

namespace Gastapp.BottomSheets;

public partial class MsiPurchaseBottomSheet : BottomSheet
{
    private readonly CreditCardsViewModel _vm;

    public MsiPurchaseBottomSheet(CreditCardsViewModel vm)
    {
        InitializeComponent();
        BindingContext = _vm = vm;
    }

    private void CloseSheet(object? sender, TappedEventArgs e)
    {
        _ = DismissAsync();
    }

    /// <summary>
    /// El comando valida y agrega; solo cerramos si la compra si entro a la lista.
    /// </summary>
    private void OnAdded(object? sender, EventArgs e)
    {
        if (!_vm.LastMsiPurchaseWasAdded)
            return;

        _ = DismissAsync();
    }
}
