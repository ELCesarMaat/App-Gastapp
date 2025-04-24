using CommunityToolkit.Maui;
using Gastapp.Pages;
using Gastapp.Pages.Menu;
using Gastapp.Services;
using Gastapp.ViewModels;
using Microsoft.Extensions.Logging;
using Syncfusion.Maui.Core.Hosting;
using The49.Maui.BottomSheet;

namespace Gastapp
{
    public static class MauiProgram
    {
        public static MauiApp CreateMauiApp()
        { 
            var builder = MauiApp.CreateBuilder();
            builder
                .UseMauiApp<App>()
                .UseMauiCommunityToolkit()
                .UseBottomSheet()
                .ConfigureSyncfusionCore()
                .ConfigureFonts(fonts =>
                {
                    fonts.AddFont("OpenSans-Regular.ttf", "OpenSansRegular");
                    fonts.AddFont("OpenSans-Semibold.ttf", "OpenSansSemibold");
                    fonts.AddFont("Baloo-Regular.ttf", "BalooRegular");
                });

            #region Services

            builder.Services.AddSingleton<INavigationService, NavigationService>();

            #endregion


            #region ViewModels

            builder.Services.AddTransient<StartPageViewModel>();
            builder.Services.AddTransient<RegisterViewModel>();
            builder.Services.AddTransient<SummaryViewModel>();
            builder.Services.AddTransient<MainPageViewModel>();


            #endregion


            #region PagesViews

            builder.Services.AddTransient<StartPage>();
            builder.Services.AddTransient<WizardRegister>();
            builder.Services.AddTransient<MainPage>();
            builder.Services.AddTransient<SummaryPage>();


            #endregion

#if DEBUG
            builder.Logging.AddDebug();
#endif

            return builder.Build();
        }
    }
}
