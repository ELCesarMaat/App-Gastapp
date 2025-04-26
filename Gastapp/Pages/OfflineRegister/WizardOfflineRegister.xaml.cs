using Gastapp.ViewModels;

namespace Gastapp.Pages.OfflineRegister;

public partial class WizardOfflineRegisterPage : ContentPage
{
    private readonly OfflineRegisterViewModel _vm;
    public WizardOfflineRegisterPage(OfflineRegisterViewModel vm)
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