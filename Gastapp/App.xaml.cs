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

        public App(GastappDbContext db)
        {
            _dbContext = db;
            InitializeComponent();
            SyncfusionLicenseProvider.RegisterLicense(
                "Ngo9BigBOggjHTQxAR8/V1NNaF5cXmBCf1FpRmJGdld5fUVHYVZUTXxaS00DNHVRdkdmWXtcc3VRQmRYUEJyXUVWYUA=");
            MainPage = new AppShell();
            CheckUser();
        }

        private async Task CheckUser()
        {
            var user = _dbContext.Users.FirstOrDefault();
            if (user != null)
            {
                Shell.Current.GoToAsync("//MainPage");
                SyncData();
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

            var api = RestService.For<IApiService>("https://grubworm-cuddly-flamingo.ngrok-free.app/api");

            try
            {
                var res = await api.SyncNewCategories(newCategories);

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