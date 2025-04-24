using Syncfusion.Licensing;

namespace Gastapp
{
    public partial class App : Application
    {
        public App()
        {
            InitializeComponent();
            SyncfusionLicenseProvider.RegisterLicense("Ngo9BigBOggjHTQxAR8/V1NNaF5cXmBCf1FpRmJGdld5fUVHYVZUTXxaS00DNHVRdkdmWXtcc3VRQmRYUEJyXUVWYUA=");
            MainPage = new AppShell();
        }
    }
}
