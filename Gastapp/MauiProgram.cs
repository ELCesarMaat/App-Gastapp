using CommunityToolkit.Maui;
using Gastapp.BottomSheets;
using Gastapp.Data;
using Gastapp.Pages;
using Gastapp.Pages.Menu;
using Gastapp.Services;
using Gastapp.Services.ApiService;
using Gastapp.Services.Navigation;
using Gastapp.Services.SpendingService;
using Gastapp.Services.UserService;
using Gastapp.ViewModels;
using Microsoft.Extensions.Logging;
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


            #endregion

            var dbContext = new GastappDbContext();
            dbContext.Database.EnsureCreated();
            DatabaseSeeder.SeedIncomeTypes(dbContext);
            dbContext.Dispose();

#if DEBUG
            builder.Logging.AddDebug();
#endif

            return builder.Build();
        }
    }
}
