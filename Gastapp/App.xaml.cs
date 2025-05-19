using System.Net;
using Gastapp.Data;
using Gastapp.Models;
using Gastapp.Pages.Menu;
using Gastapp.Services.ApiService;
using Refit;
using Syncfusion.Licensing;

namespace Gastapp
{
    public partial class App : Application
    {
        private readonly GastappDbContext _dbContext;
        private readonly IApiService _api;

        public App(GastappDbContext db, IApiService apiService)
        {
            _dbContext = db;
            _api = apiService;
            InitializeComponent();
            SyncfusionLicenseProvider.RegisterLicense(
                "Ngo9BigBOggjHTQxAR8/V1NNaF5cXmBCf1FpRmJGdld5fUVHYVZUTXxaS00DNHVRdkdmWXtcc3VRQmRYUEJyXUVWYUA=");
            MainPage = new AppShell();
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
                return;



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
            }
            catch (ApiException ex)
            {
                if(ex.StatusCode is HttpStatusCode.Unauthorized or HttpStatusCode.NotFound)
                    await Shell.Current.GoToAsync("//LoginPage");
            }
        }

        private async Task SyncData()
        {
            //Nuevas categorias
            var newCategories = _dbContext.Categories
                .Where(c => !c.IsSynced)
                .Select(c => new CategoryDto()
                {
                    CategoryName = c.CategoryName,
                    CategoryId = c.CategoryId,
                    IsSynced = c.IsSynced,
                    UserId = c.UserId
                })
                .ToList();

            //// Preparas los DTOs para enviar al API
            //var newSpendingsDto = _dbContext.Spending
            //    .Where(s => !s.IsSynced && !s.IsDeleted)
            //    .Select(s => new SpendingDto
            //    {
            //        SpendingId = s.SpendingId,
            //        CategoryId = s.CategoryId,
            //        UserId = s.UserId,
            //        Title = s.Title,
            //        Description = s.Description,
            //        Amount = s.Amount,
            //        IsSynced = s.IsSynced,
            //        IsDeleted = s.IsDeleted,
            //        Date = s.Date
            //    })
            //    .ToList();


            try
            {
                var token = Preferences.Get("token", string.Empty);
                var res = await _api.SyncNewCategories(newCategories, token);

                if (res)
                {
                    // Ahora sí, actualizas las entidades rastreadas
                    var entitiesToUpdate = _dbContext.Categories
                        .Where(s => !s.IsSynced)
                        .ToList();

                    foreach (var category in entitiesToUpdate)
                    {
                        category.IsSynced = true;
                    }

                    await _dbContext.SaveChangesAsync();
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Sync failed: {ex.Message}");
            }
        }
    }
}