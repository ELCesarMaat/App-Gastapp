using Gastapp.Utils;
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

    private async void SaveSpending(object? sender, TappedEventArgs e)
    {
        try
        {
            var saved = await _vm.SaveSpending();
            if (saved)
            {
                await DismissAsync();
            }
        }
        catch (Exception ex)
        {
            await AlertHelper.ShowAlertAsync("Error", "Ocurrió un error al guardar el gasto.", "OK");
            System.Diagnostics.Debug.WriteLine(ex);
        }
    }
}