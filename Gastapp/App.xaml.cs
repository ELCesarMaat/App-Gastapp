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
using Gastapp.Services.AppUpdateService;
using Gastapp.Services.Notifications;
using Gastapp.Services.WearService;
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
        private readonly IAppUpdateService _appUpdateService;
        private DateTime _lastActiveDate = DateTime.Today;

        public App(GastappDbContext db, IApiService apiService, IReminderNotificationService reminderNotificationService, IAppUpdateService appUpdateService)
        {
            Current!.UserAppTheme = AppTheme.Light;

            _dbContext = db;
            _api = apiService;
            _reminderNotificationService = reminderNotificationService;
            _appUpdateService = appUpdateService;
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
            _ = CheckForAppUpdate();
            _ = PurgeDeletedLocal.PurgeAsync(_dbContext);
            _ = SincronizarReloj();
        }

        /// <summary>
        /// Deja al reloj al dia y se queda escuchando cambios, para que su tile no
        /// muestre el total de ayer. Si no hay reloj vinculado no hace nada.
        /// </summary>
        private async Task SincronizarReloj()
        {
            var wear = IPlatformApplication.Current?.Services?.GetService<IWearSyncService>();
            if (wear == null)
                return;

            wear.StartWatching();
            await wear.PushTodayAsync();
            await wear.PushCategoriesAsync();
        }

        private async Task CheckForAppUpdate()
        {
            if (Connectivity.Current.NetworkAccess != NetworkAccess.Internet)
                return;

            var latest = await _appUpdateService.CheckForUpdateAsync();
            if (latest is null)
                return;

            var wantsUpdate = await AlertHelper.ShowAlertAsync(
                "Nueva versión disponible",
                $"Hay una nueva versión ({latest.VersionName}) de Gastapp. ¿Descargarla e instalarla ahora?",
                "Actualizar",
                "Después");

            if (!wantsUpdate)
                return;

            try
            {
                await Current!.MainPage.DisplaySnackbar("Descargando actualización...", duration: TimeSpan.FromSeconds(3));
                await _appUpdateService.DownloadAndInstallAsync(latest);
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Fallo la descarga de la actualización: {ex.Message}");
                await Current!.MainPage.DisplaySnackbar("No se pudo descargar la actualización. Intenta más tarde.", duration: TimeSpan.FromSeconds(4));
            }
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
                await PullRemoteSpendings();
            }
            catch (ApiException ex) when (ex.StatusCode == HttpStatusCode.Unauthorized)
            {
                // El servidor confirmó explícitamente que el token ya no es válido
                // (cuenta eliminada, contraseña cambiada, firma inválida, etc).
                // Cualquier otro código (404, 5xx, timeout del túnel) es un problema
                // de conectividad, no una sesión inválida, así que no debe expulsar al usuario.
                Preferences.Remove("token");
                Preferences.Remove("tokenexpiration");
                await Current!.MainPage.DisplaySnackbar("Tu sesión expiró. Inicia sesión de nuevo para sincronizar.",
                    duration: TimeSpan.FromSeconds(4));
                await Shell.Current.GoToAsync("//LoginPage");
            }
            catch (ApiException ex)
            {
                Console.WriteLine($"Refresh token failed with status {ex.StatusCode}: {ex.Message}");
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Refresh token failed: {ex.Message}");
            }
        }

        /// <summary>
        /// Baja los gastos que se crearon en otro dispositivo (el reloj) y que este
        /// telefono todavia no tiene.
        ///
        /// Hasta ahora la sincronizacion era de un solo sentido: el telefono empujaba
        /// sus cambios y solo descargaba todo al iniciar sesion. Con el reloj hay un
        /// segundo origen de gastos, asi que hace falta bajar tambien.
        /// </summary>
        private async Task PullRemoteSpendings()
        {
            try
            {
                var token = Preferences.Get("token", string.Empty);
                if (string.IsNullOrEmpty(token))
                    return;

                var user = await _dbContext.Users.AsNoTracking().FirstOrDefaultAsync();
                if (user == null)
                    return;

                var remotos = await _api.GetSpendings(token);
                if (remotos == null || remotos.Count == 0)
                    return;

                var idsLocales = await _dbContext.Spending
                    .AsNoTracking()
                    .Select(s => s.SpendingId)
                    .ToListAsync();

                var conocidos = idsLocales.ToHashSet();

                // Solo se insertan los que faltan. Nunca se sobrescribe un gasto local:
                // podria tener ediciones sin sincronizar todavia.
                var nuevos = remotos.Where(s => !conocidos.Contains(s.SpendingId)).ToList();
                if (nuevos.Count == 0)
                    return;

                var tarjetas = await _dbContext.CreditCards
                    .AsNoTracking()
                    .Select(cc => cc.CreditCardId)
                    .ToListAsync();

                var tarjetasConocidas = tarjetas.ToHashSet();

                foreach (var s in nuevos)
                {
                    // Misma proteccion que en el login: una referencia a una tarjeta que
                    // no existe en local rompe la llave foranea y tumba todo el guardado.
                    var creditCardId = !string.IsNullOrEmpty(s.CreditCardId) && tarjetasConocidas.Contains(s.CreditCardId)
                        ? s.CreditCardId
                        : null;

                    await _dbContext.Spending.AddAsync(new Spending
                    {
                        SpendingId = s.SpendingId,
                        UserId = user.UserId,
                        CategoryId = s.CategoryId,
                        Title = s.Title,
                        Description = s.Description,
                        Amount = s.Amount,
                        Date = DateTimeUtils.SpendingFromApiToLocal(s.Date),
                        IsSynced = true,
                        IsDeleted = false,
                        DeletedAt = null,
                        IsCreditCard = s.IsCreditCard,
                        CreditCardId = creditCardId,
                        PaymentMethod = s.PaymentMethod,
                        IsMsi = s.IsMsi,
                        TotalInstallments = s.TotalInstallments,
                        CurrentInstallment = s.CurrentInstallment,
                        ParentSpendingId = s.ParentSpendingId,
                        InstallmentMonthlyAmount = s.InstallmentMonthlyAmount
                    });
                }

                await _dbContext.SaveChangesAsync();

                // Avisar a las pantallas abiertas para que se refresquen solas.
                WeakReferenceMessenger.Default.Send(new SpendingChangedMessage(string.Empty));

                Console.WriteLine($"Se bajaron {nuevos.Count} gastos creados en otro dispositivo.");
            }
            catch (Exception ex)
            {
                // No debe impedir que la app arranque.
                Console.WriteLine($"No se pudieron bajar los gastos remotos: {ex.Message}");
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
                var creditCards = await _dbContext.CreditCards.Where(cc => !cc.IsSynced).ToListAsync();

                if (user is null && !spendings.Any() && !categories.Any() && !creditCards.Any())
                    return false;

                UserInfoDto? userInfo = null;

                if (user != null)
                {
                    userInfo = new UserInfoDto
                    {
                        UserId = user.UserId,
                        Name = user.Name,
                        Salary = user.Salary,
                        PercentSave = user.PercentSave,
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
                        IsDeleted = s.IsDeleted,
                        DeletedAt = s.DeletedAt,
                        IsCreditCard = s.IsCreditCard,
                        CreditCardId = s.CreditCardId,
                        PaymentMethod = s.PaymentMethod,
                        IsMsi = s.IsMsi,
                        TotalInstallments = s.TotalInstallments,
                        CurrentInstallment = s.CurrentInstallment,
                        ParentSpendingId = s.ParentSpendingId,
                        InstallmentMonthlyAmount = s.InstallmentMonthlyAmount
                    }).ToList(),
                    Categories = categories.Select(c => new CategoryDto
                    {
                        CategoryId = c.CategoryId,
                        CategoryName = c.CategoryName,
                        IsDefaultCategory = c.IsDefaultCategory,
                        IsSynced = c.IsSynced,
                        UserId = c.UserId,
                    }).ToList(),
                    CreditCards = creditCards.Select(cc => new CreditCardDto
                    {
                        CreditCardId = cc.CreditCardId,
                        UserId = cc.UserId,
                        CardName = cc.CardName,
                        BankName = cc.BankName,
                        LastFourDigits = cc.LastFourDigits,
                        CutOffDay = cc.CutOffDay,
                        PaymentDay = cc.PaymentDay,
                        CreditLimit = cc.CreditLimit,
                        ColorHex = cc.ColorHex,
                        IsSynced = cc.IsSynced,
                        IsDeleted = cc.IsDeleted,
                        DeletedAt = cc.DeletedAt
                    }).ToList()
                }, token);

                if (res)
                {
                    foreach (var spending in spendings)
                        spending.IsSynced = true;

                    foreach (var category in categories)
                        category.IsSynced = true;

                    foreach (var cc in creditCards)
                        cc.IsSynced = true;

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
