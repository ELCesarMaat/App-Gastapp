using Gastapp.Pages;
using Gastapp.Pages.Menu;

namespace Gastapp
{
    public partial class AppShell : Shell
    {
        public AppShell()
        {
            InitializeComponent();

            Routing.RegisterRoute("WizardRegister", typeof(WizardRegister));
            Routing.RegisterRoute(nameof(SpendingDetailPage), typeof(SpendingDetailPage));
            Routing.RegisterRoute(nameof(ForgetPasswordPage), typeof(ForgetPasswordPage));
            Routing.RegisterRoute(nameof(CategoryDetailPage), typeof(CategoryDetailPage));
            //Routing.RegisterRoute(nameof(WizardOfflineRegisterPage), typeof(WizardOfflineRegisterPage));


        }
    }
}
