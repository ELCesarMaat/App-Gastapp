using System;
using System.ComponentModel;
using Gastapp.BottomSheets;
using Gastapp.ViewModels;
using Microsoft.Maui.Controls;
using The49.Maui.BottomSheet;

namespace Gastapp.Pages.Menu
{
    public partial class CreditCardsPage : ContentPage
    {
        private readonly CreditCardsViewModel _viewModel;
        private CreditCardFormBottomSheet? _cardFormSheet;

        public CreditCardsPage(CreditCardsViewModel viewModel)
        {
            InitializeComponent();
            _viewModel = viewModel;
            BindingContext = _viewModel;

            // El formulario ya no vive incrustado en la pagina: se abre en su propia
            // hoja. Se sigue el mismo ShowCardForm del ViewModel para no duplicar la
            // logica de cuando abrir y cerrar.
            _viewModel.PropertyChanged += OnViewModelPropertyChanged;
        }

        protected override async void OnAppearing()
        {
            base.OnAppearing();
            await _viewModel.GetData();
        }

        private void OnViewModelPropertyChanged(object? sender, PropertyChangedEventArgs e)
        {
            if (e.PropertyName != nameof(CreditCardsViewModel.ShowCardForm))
                return;

            if (_viewModel.ShowCardForm)
                MostrarFormulario();
            else
                _ = CerrarFormulario();
        }

        private void MostrarFormulario()
        {
            if (_cardFormSheet != null)
                return;

            _cardFormSheet = new CreditCardFormBottomSheet(_viewModel);

            // Cerrar deslizando debe dejar el ViewModel como si se hubiera cancelado,
            // o al reabrir seguiria creyendo que el formulario esta en pantalla.
            _cardFormSheet.Dismissed += (_, _) =>
            {
                _cardFormSheet = null;
                if (_viewModel.ShowCardForm)
                    _viewModel.CloseCardFormCommand.Execute(null);
            };

            _ = _cardFormSheet.ShowAsync();
        }

        private async Task CerrarFormulario()
        {
            if (_cardFormSheet == null)
                return;

            var hoja = _cardFormSheet;
            _cardFormSheet = null;
            await hoja.DismissAsync();
        }

        /// <summary>
        /// El boton fisico de Android no debe tirar el formulario a medio llenar.
        /// </summary>
        protected override bool OnBackButtonPressed()
        {
            if (!_viewModel.HasUnsavedCardData)
                return base.OnBackButtonPressed();

            Dispatcher.Dispatch(async () =>
            {
                if (await _viewModel.ConfirmDiscardCardFormAsync())
                    await Shell.Current.GoToAsync("..");
            });

            return true;
        }
    }
}
