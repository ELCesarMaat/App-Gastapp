using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Gastapp.Models;
using Microsoft.Maui.ApplicationModel;
using Microsoft.Maui.Devices;
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

        public async Task ScheduleCreditCardRemindersAsync(List<CreditCardSummary> cardSummaries)
        {
            if (DeviceInfo.Platform != DevicePlatform.Android && DeviceInfo.Platform != DevicePlatform.iOS)
                return;

            try
            {
                var enabled = await AreNotificationsEnabledAsync();
                if (!enabled) return;

                // Cancel existing credit card notifications (range 8000 - 8999)
                var existingIds = Enumerable.Range(8000, 100).ToArray();
                LocalNotificationCenter.Current.Cancel(existingIds);

                int notifId = 8000;
                var now = DateTime.Now;

                foreach (var summary in cardSummaries)
                {
                    if (summary.Card == null || summary.Card.IsDeleted) continue;

                    // 1. Cutoff Reminder (2 days before cutoff at 9:00 AM)
                    var cutOffTarget = summary.NextCutOffDate.Date.AddDays(-2).AddHours(9);
                    if (cutOffTarget > now)
                    {
                        await LocalNotificationCenter.Current.Show(new NotificationRequest
                        {
                            NotificationId = notifId++,
                            Title = $"Próximo corte: {summary.Card.CardName}",
                            Description = $"Tu tarjeta {summary.Card.BankName} corta el {summary.NextCutOffDate:dd 'de' MMMM}. Revisa tus compras para cerrar tu ciclo.",
                            ReturningData = $"card-cutoff-{summary.Card.CreditCardId}",
                            Schedule = new NotificationRequestSchedule { NotifyTime = cutOffTarget }
                        });
                    }

                    // 2. Payment Reminder (3 days before payment at 9:00 AM)
                    var paymentWarnTarget = summary.NextPaymentDueDate.Date.AddDays(-3).AddHours(9);
                    if (paymentWarnTarget > now)
                    {
                        var amountText = summary.TotalDebt > 0 ? $" Saldo a pagar: ${summary.TotalDebt:N2}" : string.Empty;
                        await LocalNotificationCenter.Current.Show(new NotificationRequest
                        {
                            NotificationId = notifId++,
                            Title = $"Fecha límite de pago: {summary.Card.CardName}",
                            Description = $"Tu pago vence el {summary.NextPaymentDueDate:dd 'de' MMMM}.{amountText} Paga a tiempo para no generar intereses.",
                            ReturningData = $"card-payment-{summary.Card.CreditCardId}",
                            Schedule = new NotificationRequestSchedule { NotifyTime = paymentWarnTarget }
                        });
                    }

                    // 3. Payment Day Reminder (Day of payment at 8:30 AM)
                    var paymentDayTarget = summary.NextPaymentDueDate.Date.AddHours(8).AddMinutes(30);
                    if (paymentDayTarget > now && summary.TotalDebt > 0)
                    {
                        await LocalNotificationCenter.Current.Show(new NotificationRequest
                        {
                            NotificationId = notifId++,
                            Title = $"¡Hoy vence tu tarjeta {summary.Card.CardName}!",
                            Description = $"Hoy es la fecha límite de pago para {summary.Card.BankName}. Saldo pendiente: ${summary.TotalDebt:N2}.",
                            ReturningData = $"card-payment-today-{summary.Card.CreditCardId}",
                            Schedule = new NotificationRequestSchedule { NotifyTime = paymentDayTarget }
                        });
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Error programando recordatorios de tarjeta: {ex.Message}");
            }
        }
    }
}