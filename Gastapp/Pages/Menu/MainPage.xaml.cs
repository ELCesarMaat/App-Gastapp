using Gastapp.BottomSheets;
using Gastapp.ViewModels;
using The49.Maui.BottomSheet;

namespace Gastapp.Pages.Menu;

public partial class MainPage : ContentPage
{
    private readonly MainPageViewModel _vm;
    public MainPage(MainPageViewModel vm)
    {
        InitializeComponent();
        BindingContext = _vm = vm;
    }

    protected override bool OnBackButtonPressed()
    {
        if (_vm.IsBsOpen)
        {
            _vm.BottomSheet.DismissAsync();
            return true;
        }

        if (_vm.CurrentPage is not SummaryPage)
        {
            _vm.SetSummaryPageCommand.Execute(null);
            return true;
        }

        return base.OnBackButtonPressed();
    }
}