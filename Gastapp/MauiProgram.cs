using CommunityToolkit.Maui;
using Gastapp.BottomSheets;
using Gastapp.Data;
using Gastapp.Pages;
using Gastapp.Pages.Menu;
using Gastapp.Services;
using Gastapp.Services.ApiService;
using Gastapp.Services.Navigation;
using Gastapp.Services.Notifications;
using Gastapp.Services.SpendingService;
using Gastapp.Services.UserService;
using Gastapp.ViewModels;
using Microsoft.Extensions.Logging;
using Plugin.LocalNotification;
using Refit;
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
                .UseLocalNotification()
                .UseBottomSheet()
                .ConfigureSyncfusionCore()
                .ConfigureFonts(fonts =>
                {
                    fonts.AddFont("OpenSans-Regular.ttf", "OpenSansRegular");
                    fonts.AddFont("OpenSans-Semibold.ttf", "OpenSansSemibold");
                    fonts.AddFont("Baloo-Regular.ttf", "BalooRegular");
                });

            #region Services
            
            builder.Services.AddDbContext<GastappDbContext>();
            builder.Services.AddSingleton<INavigationService, NavigationService>();
            builder.Services.AddSingleton<ISpendingService, SpendingService>();
            builder.Services.AddSingleton<IUserService, UserService>();
            builder.Services.AddSingleton<IReminderNotificationService, ReminderNotificationService>();
            builder.Services.AddRefitClient<IApiService>().ConfigureHttpClient(c =>
            {
                c.Timeout = TimeSpan.FromSeconds(120);
                c.BaseAddress = new Uri("https://app-gastapp.onrender.com/api");
                //c.BaseAddress = new Uri("https://grubworm-cuddly-flamingo.ngrok-free.app/api");

            });


            #endregion


            #region ViewModels

            builder.Services.AddTransient<StartPageViewModel>();
            builder.Services.AddTransient<RegisterViewModel>();
            builder.Services.AddTransient<SummaryViewModel>();
            builder.Services.AddTransient<MainPageViewModel>();
            builder.Services.AddTransient<DetailViewModel>();
            builder.Services.AddTransient<NewSpendingViewModel>();
            //builder.Services.AddTransient<OfflineRegisterViewModel>();
            builder.Services.AddTransient<SettingsViewModel>();
            builder.Services.AddTransient<ProfileViewModel>();
            builder.Services.AddTransient<SavesViewModel>();
            builder.Services.AddTransient<ForgetPasswordViewModel>();
            builder.Services.AddTransient<CategoryDetailViewModel>();


            #endregion


            #region PagesViews

            builder.Services.AddTransient<StartPage>();
            builder.Services.AddTransient<WizardRegister>();
            builder.Services.AddTransient<MainPage>();
            builder.Services.AddTransient<SummaryPage>();
            builder.Services.AddTransient<SpendingDetailPage>();
            //builder.Services.AddTransient<WizardOfflineRegisterPage>();
            builder.Services.AddTransient<SettingsPage>();
            builder.Services.AddTransient<SavesPage>();
            builder.Services.AddTransient<ForgetPasswordPage>();
            builder.Services.AddTransient<CategoryDetailPage>();

            #endregion

            var dbContext = new GastappDbContext();
            dbContext.EnsureSchemaUpToDate();
            dbContext.Dispose();

#if DEBUG
            builder.Logging.AddDebug();
#endif

            return builder.Build();
        }
    }
}
