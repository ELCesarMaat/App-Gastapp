using Plugin.LocalNotification;

namespace Gastapp.Services.Notifications
{
    public class ReminderNotificationService : IReminderNotificationService
    {
        private const int ReminderBaseId = 6100;
        private const int TestNotificationId = 7099;

        private static readonly string[] ReminderMessages =
        [
            "Tip de ahorro: guarda al menos el 10% de cualquier ingreso extra.",
            "Tip rapido: revisar tus gastos 2 minutos al dia evita fugas de dinero.",
            "Idea util: separa tus gastos fijos de los variables para ajustar mejor tu presupuesto.",
            "Recordatorio: pequenos gastos diarios tambien cuentan; registralos para ver el impacto real.",
            "Tip practico: antes de comprar algo, espera 24 horas y decide con calma.",
            "Tip inteligente: define un tope semanal para gastos hormiga y respetalo."
        ];

        public async Task ConfigureRecurringRemindersAsync(int frequencyHours)
        {
            if (DeviceInfo.Platform != DevicePlatform.Android && DeviceInfo.Platform != DevicePlatform.iOS)
                return;

            try
            {
                frequencyHours = Math.Clamp(frequencyHours, 1, 24);

                var notificationsEnabled = await AreNotificationsEnabledAsync();
                if (!notificationsEnabled)
                {
                    notificationsEnabled = await RequestNotificationPermissionAsync();
                }

                if (!notificationsEnabled)
                    return;

                var reminderIds = Enumerable.Range(ReminderBaseId, ReminderMessages.Length).ToArray();
                LocalNotificationCenter.Current.Cancel(reminderIds);

                var firstReminderTime = DateTime.Now.AddMinutes(5);
                var wheelInterval = TimeSpan.FromHours(frequencyHours * ReminderMessages.Length);

                for (var i = 0; i < ReminderMessages.Length; i++)
                {
                    var notifyAt = firstReminderTime.AddHours(i * frequencyHours);
                    var request = new NotificationRequest
                    {
                        NotificationId = ReminderBaseId + i,
                        Title = "Gastapp te acompana",
                        Description = $"{ReminderMessages[i]} Recuerda registrar tus gastos de hoy.",
                        ReturningData = "savings-reminder",
                        Schedule = new NotificationRequestSchedule
                        {
                            NotifyTime = notifyAt,
                            RepeatType = NotificationRepeat.TimeInterval,
                            NotifyRepeatInterval = wheelInterval
                        }
                    };

                    await LocalNotificationCenter.Current.Show(request);
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"No se pudieron programar recordatorios: {ex.Message}");
            }
        }

        public Task DisableRemindersAsync()
        {
            var reminderIds = Enumerable.Range(ReminderBaseId, ReminderMessages.Length).ToArray();
            LocalNotificationCenter.Current.Cancel(reminderIds);
            return Task.CompletedTask;
        }

        public Task<bool> AreNotificationsEnabledAsync()
        {
            return LocalNotificationCenter.Current.AreNotificationsEnabled();
        }

        public Task<bool> RequestNotificationPermissionAsync()
        {
            return LocalNotificationCenter.Current.RequestNotificationPermission(new NotificationPermission
            {
                AskPermission = true
            });
        }

        public Task OpenAppNotificationSettingsAsync()
        {
            AppInfo.Current.ShowSettingsUI();
            return Task.CompletedTask;
        }

        public async Task<bool> SendTestNotificationAsync()
        {
            if (DeviceInfo.Platform != DevicePlatform.Android && DeviceInfo.Platform != DevicePlatform.iOS)
                return false;

            try
            {
                var notificationsEnabled = await AreNotificationsEnabledAsync();
                if (!notificationsEnabled)
                {
                    notificationsEnabled = await RequestNotificationPermissionAsync();
                }

                if (!notificationsEnabled)
                    return false;

                LocalNotificationCenter.Current.Cancel([TestNotificationId]);

                await LocalNotificationCenter.Current.Show(new NotificationRequest
                {
                    NotificationId = TestNotificationId,
                    Title = "Prueba de recordatorio",
                    Description = "Este es un recordatorio de prueba. Si hiciste un gasto, registralo en Gastapp.",
                    ReturningData = "test-reminder"
                });

                return true;
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"No se pudo enviar notificacion de prueba: {ex.Message}");
                return false;
            }
        }
    }
}