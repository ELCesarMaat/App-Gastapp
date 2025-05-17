using Gastapp.ViewModels;
using The49.Maui.BottomSheet;

namespace Gastapp.BottomSheets;

public partial class LoginBottomSheet : BottomSheet
{
	public LoginBottomSheet(StartPageViewModel vm)
	{
		InitializeComponent();
        BindingContext = vm;
    }
}