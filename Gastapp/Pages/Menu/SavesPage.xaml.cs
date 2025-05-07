using Gastapp.ViewModels;
using Syncfusion.Maui.Charts;

namespace Gastapp.Pages.Menu;

public partial class SavesPage : ContentView
{
    private SavesViewModel _vm;
    public SavesPage(SavesViewModel vm)
    {
        InitializeComponent();
        BindingContext = _vm = vm;
    }
}