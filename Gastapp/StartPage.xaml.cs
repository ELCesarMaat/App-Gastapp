using CommunityToolkit.Maui.Behaviors;
using CommunityToolkit.Maui.Core;
using Gastapp.ViewModels;

namespace Gastapp
{
    public partial class StartPage : ContentPage
    {
        private StartPageViewModel _vm;
        public StartPage(StartPageViewModel vm)
        {
            InitializeComponent();
            BindingContext = _vm = vm;
        }

        protected override bool OnBackButtonPressed()
        {
            if (_vm.IsBottomSheetOpen)
            {
                _ = _vm.LoginBottomSheet?.DismissAsync();
                return true;
            }

            return base.OnBackButtonPressed();
        }

    }

}
