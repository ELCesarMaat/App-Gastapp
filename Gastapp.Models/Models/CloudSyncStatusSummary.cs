namespace Gastapp.Models
{
    public class CloudSyncStatusSummary
    {
        public bool IsComplete { get; set; }
        public int TotalPendingItems { get; set; }
        public int PendingUserChanges { get; set; }
        public int PendingCategories { get; set; }
        public int PendingDeletedSpendings { get; set; }
        public int PendingActiveSpendings { get; set; }
        public string Breakdown { get; set; } = string.Empty;
    }
}