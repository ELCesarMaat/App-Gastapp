using Gastapp.ViewModels;

namespace Gastapp.Pages.Menu;

public partial class SettingsPage : ContentView
{
    private SettingsViewModel _vm;
	public SettingsPage(SettingsViewModel vm)
	{
		InitializeComponent();
		BindingContext = _vm = vm;
    }
}