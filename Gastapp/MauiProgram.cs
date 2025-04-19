using CommunityToolkit.Maui;
using Gastapp.Pages;
using Gastapp.Services;
using Gastapp.ViewModels;
using Microsoft.Extensions.Logging;
using Microsoft.Maui.LifecycleEvents;

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
                .ConfigureFonts(fonts =>
                {
                    fonts.AddFont("OpenSans-Regular.ttf", "OpenSansRegular");
                    fonts.AddFont("OpenSans-Semibold.ttf", "OpenSansSemibold");
                }).ConfigureLifecycleEvents(events =>
                {
#if ANDROID
                events.AddAndroid(android => android.OnCreate((activity, bundle) => MakeStatusBarTranslucent(activity)));

                static void MakeStatusBarTranslucent(Android.App.Activity activity)
                {
                    activity.Window.SetFlags(Android.Views.WindowManagerFlags.LayoutNoLimits, Android.Views.WindowManagerFlags.LayoutNoLimits);

                    activity.Window.ClearFlags(Android.Views.WindowManagerFlags.TranslucentStatus);

                    activity.Window.SetStatusBarColor(Android.Graphics.Color.Transparent);
                }
#endif
                });

            #region Services

            builder.Services.AddSingleton<INavigationService, NavigationService>();

            #endregion


            #region ViewModels

            builder.Services.AddTransient<MainPageViewModel>();
            builder.Services.AddTransient<RegisterViewModel>();

            #endregion


            #region PagesViews

            builder.Services.AddTransient<MainPage>();
            builder.Services.AddTransient<WizardRegister>();

            #endregion

#if DEBUG
            builder.Logging.AddDebug();
#endif

            return builder.Build();
        }
    }
}
