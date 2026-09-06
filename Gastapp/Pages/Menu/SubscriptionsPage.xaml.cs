using System;
using System.ComponentModel;
using System.Threading.Tasks;
using Gastapp.BottomSheets;
using Gastapp.ViewModels;
using Microsoft.Maui.Controls;
using The49.Maui.BottomSheet;

namespace Gastapp.Pages.Menu
{
    public partial class SubscriptionsPage : ContentPage
    {
        private readonly SubscriptionsViewModel _viewModel;
        private SubscriptionFormBottomSheet? _subscriptionFormSheet;

        public SubscriptionsPage(SubscriptionsViewModel viewModel)
        {
            InitializeComponent();
            _viewModel = viewModel;
            BindingContext = _viewModel;

            // El formulario se abre en su propia hoja, igual que el de tarjetas. Se
            // sigue el ShowSubscriptionForm del ViewModel para no duplicar la logica
            // de cuando abrir y cerrar.
            _viewModel.PropertyChanged += OnViewModelPropertyChanged;
        }

        protected override async void OnAppearing()
        {
            base.OnAppearing();
            await _viewModel.GetData();
        }

        private void OnViewModelPropertyChanged(object? sender, PropertyChangedEventArgs e)
        {
            if (e.PropertyName != nameof(SubscriptionsViewModel.ShowSubscriptionForm))
                return;

            if (_viewModel.ShowSubscriptionForm)
                MostrarFormulario();
            else
                _ = CerrarFormulario();
        }

        private void MostrarFormulario()
        {
            if (_subscriptionFormSheet != null)
                return;

            _subscriptionFormSheet = new SubscriptionFormBottomSheet(_viewModel);

            // Cerrar deslizando debe dejar el ViewModel como si se hubiera cancelado,
            // o al reabrir seguiria creyendo que el formulario esta en pantalla.
            _subscriptionFormSheet.Dismissed += (_, _) =>
            {
                _subscriptionFormSheet = null;
                if (_viewModel.ShowSubscriptionForm)
                    _viewModel.CloseSubscriptionFormCommand.Execute(null);
            };

            _ = _subscriptionFormSheet.ShowAsync();
        }

        private async Task CerrarFormulario()
        {
            if (_subscriptionFormSheet == null)
                return;

            var hoja = _subscriptionFormSheet;
            _subscriptionFormSheet = null;
            await hoja.DismissAsync();
        }

        /// <summary>
        /// El boton fisico de Android no debe tirar el formulario a medio llenar.
        /// </summary>
        protected override bool OnBackButtonPressed()
        {
            if (!_viewModel.HasUnsavedSubscriptionData)
                return base.OnBackButtonPressed();

            Dispatcher.Dispatch(async () =>
            {
                if (await _viewModel.ConfirmDiscardSubscriptionFormAsync())
                    await Shell.Current.GoToAsync("..");
            });

            return true;
        }
    }
}
