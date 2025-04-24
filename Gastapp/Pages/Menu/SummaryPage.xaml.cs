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
}