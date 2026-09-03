using CommunityToolkit.Maui;
using Gastapp.BottomSheets;
using Gastapp.Data;
using Gastapp.Pages;
using Gastapp.Pages.Menu;
using Gastapp.Services;
using Gastapp.Services.ApiService;
using Gastapp.Services.AppUpdateService;
using Gastapp.Services.BackupService;
using Gastapp.Services.Navigation;
using Gastapp.Services.Notifications;
using Gastapp.Services.SpendingService;
using Gastapp.Services.UserService;
using Gastapp.Services.WearService;
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
                    fonts.AddFont("fa-solid-900.ttf", "FaSolid");
                });

#if ANDROID
            // Android le pinta un subrayado propio a Entry y Picker. Como en toda la app
            // van dentro de un Border redondeado, ese subrayado se ve como un error.
            Microsoft.Maui.Handlers.EntryHandler.Mapper.AppendToMapping("SinSubrayado", (handler, _) =>
            {
                handler.PlatformView.BackgroundTintList =
                    Android.Content.Res.ColorStateList.ValueOf(Android.Graphics.Color.Transparent);
            });

            Microsoft.Maui.Handlers.PickerHandler.Mapper.AppendToMapping("SinSubrayado", (handler, _) =>
            {
                handler.PlatformView.BackgroundTintList =
                    Android.Content.Res.ColorStateList.ValueOf(Android.Graphics.Color.Transparent);
            });
#endif

            #region Services

            builder.Services.AddDbContext<GastappDbContext>();
            builder.Services.AddSingleton<INavigationService, NavigationService>();
            builder.Services.AddSingleton<ISpendingService, SpendingService>();
            builder.Services.AddSingleton<IUserService, UserService>();
            builder.Services.AddSingleton<IReminderNotificationService, ReminderNotificationService>();
            builder.Services.AddSingleton<ICreditCardService, CreditCardService>();
            builder.Services.AddSingleton<IBackupService, BackupService>();
            builder.Services.AddSingleton<IRegisterDraftService, RegisterDraftService>();
            builder.Services.AddSingleton<IAppUpdateService, AppUpdateService>();

            // Canal Bluetooth con el reloj. Solo hay implementacion en Android; en el
            // resto no se registra y quien lo pida recibe null, que es justo lo
            // correcto: avisar al reloj es cortesia, no un paso obligatorio.
#if ANDROID
            builder.Services.AddSingleton<IWearChannel, Gastapp.Platforms.Android.Wear.WearChannel>();
#endif
            builder.Services.AddSingleton<IWearSyncService, WearSyncService>();
            builder.Services.AddHttpClient("update", c => c.Timeout = TimeSpan.FromMinutes(5));
            builder.Services.AddRefitClient<IApiService>().ConfigureHttpClient(c =>
            {
                c.Timeout = TimeSpan.FromSeconds(120);
//#if DEBUG
                //c.BaseAddress = new Uri("http://10.0.2.2:5118/api");
                //c.BaseAddress = new Uri("https://g72cqh68-7189.usw3.devtunnels.ms/api");
//#else
                c.BaseAddress = new Uri("https://app-gastapp.onrender.com/api");

                //#endif
            }).ConfigurePrimaryHttpMessageHandler(() =>
            {
                return new HttpClientHandler
                {
                    ServerCertificateCustomValidationCallback =
                        HttpClientHandler.DangerousAcceptAnyServerCertificateValidator
                };
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
            builder.Services.AddTransient<CreditCardsViewModel>();


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
            builder.Services.AddTransient<CreditCardsPage>();

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
