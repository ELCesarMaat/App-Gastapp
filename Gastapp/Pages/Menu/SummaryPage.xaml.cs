using Gastapp.ViewModels;

namespace Gastapp.Pages.Menu;

public partial class SummaryPage : ContentView
{
    public SummaryPage(SummaryViewModel vm)
    {
        InitializeComponent();
        BindingContext = vm;
    }


    private void ItemsView_OnRemainingItemsThresholdReached(object? sender, EventArgs e)
    {
    }

    private async void SelectFirstItem(object? sender, TappedEventArgs e)
    {
        DaysCollectionView.ScrollTo(0, animate:false);
    }
}

