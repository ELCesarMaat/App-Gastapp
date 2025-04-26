using Gastapp.Pages;
using Gastapp.Pages.Menu;
using Gastapp.Pages.OfflineRegister;
using Gastapp.Pages.Register;

namespace Gastapp
{
    public partial class AppShell : Shell
    {
        public AppShell()
        {
            InitializeComponent();

            Routing.RegisterRoute("WizardRegister", typeof(WizardRegister));
            Routing.RegisterRoute(nameof(SpendingDetailPage), typeof(SpendingDetailPage));
            Routing.RegisterRoute(nameof(WizardOfflineRegisterPage), typeof(WizardOfflineRegisterPage));


        }
    }
}
