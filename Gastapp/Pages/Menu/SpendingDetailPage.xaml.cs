using Gastapp.ViewModels;

namespace Gastapp.Pages.Menu;

public partial class SpendingDetailPage : ContentPage
{
	public SpendingDetailPage(DetailViewModel vm)
	{
		InitializeComponent();
        BindingContext = vm;
    }
}