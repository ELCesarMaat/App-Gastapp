using Gastapp.ViewModels;
using The49.Maui.BottomSheet;

namespace Gastapp.BottomSheets;

public partial class NewSpendingBottomSheet : BottomSheet
{
    private readonly NewSpendingViewModel _vm;
	public NewSpendingBottomSheet(NewSpendingViewModel vm)
	{
		InitializeComponent();
        BindingContext = _vm = vm;
    }

    private void CloseBottomSheet(object? sender, EventArgs e)
    {
        _ = DismissAsync();
    }

    private void SaveSpending(object? sender, TappedEventArgs e)
    {
        _ = _vm.SaveSpending();
        _ = DismissAsync();
    }
}