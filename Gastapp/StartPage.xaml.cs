using CommunityToolkit.Maui.Behaviors;
using CommunityToolkit.Maui.Core;
using Gastapp.ViewModels;

namespace Gastapp
{
    public partial class StartPage : ContentPage
    {
        public StartPage(StartPageViewModel vm)
        {
            InitializeComponent();
            BindingContext = vm;
        }

        protected override void OnAppearing()
        {
            this.Behaviors.Add(new StatusBarBehavior
            {
                StatusBarColor = (Color)Application.Current.Resources["PrimaryGreen"],
                StatusBarStyle = StatusBarStyle.LightContent
            });
            base.OnAppearing();
        }

    }

}
