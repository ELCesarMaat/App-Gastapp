using System;
using Gastapp.ViewModels;
using Microsoft.Maui.Controls;

namespace Gastapp.Pages.Menu
{
    public partial class CreditCardsPage : ContentPage
    {
        private readonly CreditCardsViewModel _viewModel;

        public CreditCardsPage(CreditCardsViewModel viewModel)
        {
            InitializeComponent();
            _viewModel = viewModel;
            BindingContext = _viewModel;
        }

        protected override async void OnAppearing()
        {
            base.OnAppearing();
            await _viewModel.GetData();
        }
    }
}
