using System.Linq;
using System.Net;
using CommunityToolkit.Maui.Alerts;
using CommunityToolkit.Maui.Core;
using CommunityToolkit.Mvvm.Messaging;
using Gastapp.Data;
using Gastapp.Messages;
using Gastapp.Models;
using Gastapp.Models.Models;
using Gastapp.Pages.Menu;
using Gastapp.Services.ApiService;
using Gastapp.Services.Notifications;
using Gastapp.Utils;
using Microsoft.EntityFrameworkCore;
using Microsoft.Maui.Networking;
using Refit;
using Syncfusion.Licensing;
using Toast = CommunityToolkit.Maui.Alerts.Toast;

namespace Gastapp
{
    public partial class App : Application
    {
        private readonly GastappDbContext _dbContext;
        private readonly IApiService _api;
        private readonly IReminderNotificationService _reminderNotificationService;
        private DateTime _lastActiveDate = DateTime.Today;

        public App(GastappDbContext db, IApiService apiService, IReminderNotificationService reminderNotificationService)
        {
            Current!.UserAppTheme = AppTheme.Light;

            _dbContext = db;
            _api = apiService;
            _reminderNotificationService = reminderNotificationService;
            InitializeComponent();
            SyncfusionLicenseProvider.RegisterLicense(
                "Ngo9BigBOggjHTQxAR8/V1NNaF5cXmBCf1FpRmJGdld5fUVHYVZUTXxaS00DNHVRdkdmWXtcc3VRQmRYUEJyXUVWYUA=");
            MainPage = new AppShell();
            //_ = CheckUser();
        }

        protected override void OnStart()
        {
            base.OnStart();
            _lastActiveDate = DateTime.Today;
            _ = CheckUser();
        }

        protected override void OnResume()
        {
            base.OnResume();
            var today = DateTime.Today;
            if (today != _lastActiveDate)
            {
                _lastActiveDate = today;
                WeakReferenceMessenger.Default.Send(new DayChangedMessage(today));
            }
        }

        private async Task CheckUser()
        {
            var localUser = await _dbContext.Users.AsNoTracking().FirstOrDefaultAsync();
            var hasLocalSession = localUser != null;

            var tokenExpiration = DateTime.TryParse(Preferences.Get("tokenexpiration", string.Empty), out var value)
                ? value
                : DateTime.UnixEpoch;

            var token = Preferences.Get("token", string.Empty);
            var hasInternet = Connectivity.Current.NetworkAccess == NetworkAccess.Internet;
            var hasValidToken = !string.IsNullOrEmpty(token) && tokenExpiration >= DateTime.Now;

            if (hasLocalSession)
            {
                var remindersEnabled = Preferences.Get("reminders_enabled", true);
                var reminderFrequencyHours = Preferences.Get("reminder_frequency_hours", 4);

                if (remindersEnabled)
                    _ = _reminderNotificationService.ConfigureRecurringRemindersAsync(reminderFrequencyHours);
                else
                    _ = _reminderNotificationService.DisableRemindersAsync();

                await Shell.Current.GoToAsync("//MainPage");

                if (!hasInternet)
                    return;

                if (hasValidToken)
                {
                    _ = RefreshToken(token);
                }
                else if (tokenExpiration != DateTime.UnixEpoch || !string.IsNullOrWhiteSpace(token))
                {
                    await Current!.MainPage.DisplaySnackbar("Estás usando la app con datos locales. Inicia sesión de nuevo para volver a sincronizar.",
                        duration: TimeSpan.FromSeconds(5));
                }

                return;
            }

            if (!hasInternet)
            {
                await Current!.MainPage.DisplaySnackbar("Necesitas conexión para iniciar sesión por primera vez.",
                    duration: TimeSpan.FromSeconds(4));
                return;
            }

            if (!hasValidToken)
            {
                if (tokenExpiration != DateTime.UnixEpoch)
                {
                    await Current!.MainPage.DisplaySnackbar("Su sesión ha caducado, vuelva a iniciar sesión.",
                        duration: TimeSpan.FromSeconds(5));
                    Preferences.Remove("tokenexpiration");
                }

                return;
            }

            await RefreshToken(token);
        }

        private async Task RefreshToken(string token)
        {
            try
            {
                var newToken = await _api.RefreshToken(token);
                Preferences.Set("token", newToken.TokenValue);
                Preferences.Set("tokenexpiration", newToken.TokenExpiration.ToString());
                await SyncData();
            }
            catch (ApiException ex)
            {
                if (ex.StatusCode is HttpStatusCode.Unauthorized or HttpStatusCode.NotFound)
                    await Shell.Current.GoToAsync("//LoginPage");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Refresh token failed: {ex.Message}");
            }
        }

        private async Task<bool> SyncData()
        {
            try
            {
                var token = Preferences.Get("token", string.Empty);
                if (string.IsNullOrEmpty(token))
                {
                    Console.WriteLine("Token no disponible.");
                    return false;
                }

                var user = await _dbContext.Users.FirstOrDefaultAsync(c => !c.IsSynced);
                var spendings = await _dbContext.Spending.Where(s => !s.IsSynced).ToListAsync();
                var categories = await _dbContext.Categories.Where(c => !c.IsSynced).ToListAsync();

                if (user is null && !spendings.Any() && !categories.Any())
                    return false;

                UserInfoDto? userInfo = null;

                if (user != null)
                {
                    userInfo = new UserInfoDto
                    {
                        UserId = user.UserId,
                        Name = user.Name,
                        FirstPayDay = user.FirstPayDay,
                        SecondPayDay = user.SecondPayDay,
                        WeekPayDay = user.WeekPayDay,
                        IncomeTypeId = user.IncomeTypeId,
                        BirthDate = user.BirthDate,
                    };
                }

                var res = await _api.SyncAllData(new SyncDataDto
                {
                    User = userInfo,
                    Spendings = spendings.Select(s => new SpendingDto
                    {
                        Amount = s.Amount,
                        CategoryId = s.CategoryId,
                        Date = DateTimeUtils.SpendingToApiUtc(s.Date),
                        Description = s.Description,
                        IsSynced = s.IsSynced,
                        SpendingId = s.SpendingId,
                        Title = s.Title,
                        UserId = s.UserId,
                    }).ToList(),
                    Categories = categories.Select(c => new CategoryDto
                    {
                        CategoryId = c.CategoryId,
                        CategoryName = c.CategoryName,
                        IsDefaultCategory = c.IsDefaultCategory,
                        IsSynced = c.IsSynced,
                        UserId = c.UserId,
                    }).ToList()
                }, token);

                if (res)
                {
                    foreach (var spending in spendings)
                        spending.IsSynced = true;

                    foreach (var category in categories)
                        category.IsSynced = true;

                    if (user != null)
                        user.IsSynced = true;

                    await _dbContext.SaveChangesAsync();
                }

                await Toast.Make("Se sincronizó la información", ToastDuration.Long).Show();
                return res;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Sync failed: {ex.Message}\n{ex.StackTrace}");
                return false;
            }
        }
    }
}
