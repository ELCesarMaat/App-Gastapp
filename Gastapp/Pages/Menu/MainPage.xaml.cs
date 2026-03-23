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
        protected override async void OnAppearing()
        {
            base.OnAppearing();
            await _vm.RefreshSummaryAsync();
        }


    private async void ChangePageClick(object? sender, EventArgs e)
    {
        await ContentViewContainer.ScaleTo(1.025, 80);

        await ContentViewContainer.ScaleTo(1, 80);
    }
}