using Gastapp.ViewModels;

namespace Gastapp.Pages;

public partial class WizardRegister : ContentPage
{
	RegisterViewModel _vm;
	public WizardRegister(RegisterViewModel vm)
	{
		InitializeComponent();
		BindingContext = _vm = vm;
		_ = _vm.MostrarPaso(PasoContainer);
	}

    protected override bool OnBackButtonPressed()
    {
		_ = _vm.Previous();
		return true;
    }
}