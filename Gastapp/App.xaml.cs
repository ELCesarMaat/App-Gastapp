using Gastapp.Data;
using Gastapp.Pages.Menu;
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

        private void CheckUser()
        {
            var user = _dbContext.Users.FirstOrDefault();
            if (user != null)
            {
                Shell.Current.GoToAsync("//MainPage");
            }

        }
    }
}
