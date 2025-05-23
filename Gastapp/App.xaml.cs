using System.Linq;
using System.Net;
using Android.Widget;
using CommunityToolkit.Maui.Alerts;
using CommunityToolkit.Maui.Core;
using Gastapp.Data;
using Gastapp.Models;
using Gastapp.Models.Models;
using Gastapp.Pages.Menu;
using Gastapp.Services.ApiService;
using Microsoft.EntityFrameworkCore;
using Refit;
using Syncfusion.Licensing;
using Toast = CommunityToolkit.Maui.Alerts.Toast;

namespace Gastapp
{
    public partial class App : Application
    {
        private readonly GastappDbContext _dbContext;
        private readonly IApiService _api;

        public App(GastappDbContext db, IApiService apiService)
        {
            Current!.UserAppTheme = AppTheme.Light;

            _dbContext = db;
            _api = apiService;
            InitializeComponent();
            SyncfusionLicenseProvider.RegisterLicense(
                "Ngo9BigBOggjHTQxAR8/V1NNaF5cXmBCf1FpRmJGdld5fUVHYVZUTXxaS00DNHVRdkdmWXtcc3VRQmRYUEJyXUVWYUA=");
            MainPage = new AppShell();
            //_ = CheckUser();
        }

        protected override void OnStart()
        {
            base.OnStart();
            _ = CheckUser();
        }

        private async Task CheckUser()
        {
            var tokenExpiration = DateTime.TryParse(Preferences.Get("tokenexpiration", string.Empty), out var value)
                ? value
                : DateTime.UnixEpoch;

            var token = Preferences.Get("token", string.Empty);

            var today = DateTime.Now;
            if (tokenExpiration < today || string.IsNullOrEmpty(token))
            {
                if (tokenExpiration != DateTime.UnixEpoch)
                    await Current!.MainPage.DisplaySnackbar("Su sesion ha caducado, vuelva a iniciar sesion",
                        duration: TimeSpan.FromMinutes(5));
                return;
            }


            _ = RefreshToken(token);


            var user = _dbContext.Users.FirstOrDefault();
            if (user != null)
            {
                await Shell.Current.GoToAsync("//MainPage");
                //SyncData();
            }
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
                        Date = s.Date,
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