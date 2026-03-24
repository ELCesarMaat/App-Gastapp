namespace Gastapp.Services.Notifications
{
    public interface IReminderNotificationService
    {
        Task ConfigureRecurringRemindersAsync(int frequencyHours);
        Task DisableRemindersAsync();
        Task<bool> AreNotificationsEnabledAsync();
        Task<bool> RequestNotificationPermissionAsync();
        Task OpenAppNotificationSettingsAsync();
        Task<bool> SendTestNotificationAsync();
    }
}