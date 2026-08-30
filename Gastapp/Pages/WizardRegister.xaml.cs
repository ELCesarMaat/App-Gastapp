using Gastapp.ViewModels;

namespace Gastapp.Pages;

public partial class WizardRegister : ContentPage
{
	RegisterViewModel _vm;
	private bool _draftRestored;

	public WizardRegister(RegisterViewModel vm)
	{
		InitializeComponent();
		BindingContext = _vm = vm;
	}

	protected override async void OnAppearing()
	{
		base.OnAppearing();

		// Retomar el registro donde se quedo antes de pintar el primer paso.
		if (!_draftRestored)
		{
			_draftRestored = true;
			await _vm.RestoreDraftAsync();
		}

		await _vm.MostrarPaso(PasoContainer);
	}

    protected override bool OnBackButtonPressed()
    {
		_ = _vm.Previous();
		return true;
    }
}