using Gastapp.ViewModels;

namespace Gastapp.Pages.Menu;

public partial class ProfilePage : ContentView
{
    private ProfileViewModel _vm;
    public ProfilePage(ProfileViewModel vm)
    {
        InitializeComponent();
        BindingContext = _vm = vm;
    }
}