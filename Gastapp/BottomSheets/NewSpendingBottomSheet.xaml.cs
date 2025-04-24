using Gastapp.ViewModels;
using The49.Maui.BottomSheet;

namespace Gastapp.BottomSheets;

public partial class NewSpendingBottomSheet : BottomSheet
{
	public NewSpendingBottomSheet(NewSpendingViewModel vm)
	{
		InitializeComponent();
        BindingContext = vm;
    }

    private void CloseBottomSheet(object? sender, EventArgs e)
    {
        _ = DismissAsync();
    }
}