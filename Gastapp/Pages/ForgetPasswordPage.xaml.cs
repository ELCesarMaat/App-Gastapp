using Gastapp.ViewModels;

namespace Gastapp.Pages;

public partial class ForgetPasswordPage : ContentPage
{
	readonly ForgetPasswordViewModel _vm;
	public ForgetPasswordPage(ForgetPasswordViewModel viewModel)
	{
		InitializeComponent();
        BindingContext = _vm = viewModel;
    }
}