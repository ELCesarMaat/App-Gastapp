using Gastapp.ViewModels;

namespace Gastapp.Pages.Menu;

public partial class MainPage : ContentPage
{
    public MainPage(SummaryViewModel summaryVm)
    {
        InitializeComponent();
        ContentViewContainer.Content = new SummaryPage(summaryVm);
    }
}