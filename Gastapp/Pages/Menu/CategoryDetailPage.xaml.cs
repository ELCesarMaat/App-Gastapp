using Gastapp.ViewModels;

namespace Gastapp.Pages.Menu;

public partial class CategoryDetailPage : ContentPage
{
    public CategoryDetailPage(CategoryDetailViewModel vm)
    {
        InitializeComponent();
        BindingContext = vm;
    }
}
