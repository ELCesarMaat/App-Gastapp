namespace Gastapp.Utils
{
    public static class DateTimeUtils
    {
        public static DateTime SpendingToApiUtc(DateTime date)
        {
            var normalized = date.Kind == DateTimeKind.Unspecified
                ? DateTime.SpecifyKind(date, DateTimeKind.Local)
                : date;

            return normalized.Kind == DateTimeKind.Utc
                ? normalized
                : normalized.ToUniversalTime();
        }

        public static DateTime SpendingFromApiToLocal(DateTime date)
        {
            if (date.Kind == DateTimeKind.Local)
                return date;

            var normalized = date.Kind == DateTimeKind.Unspecified
                ? DateTime.SpecifyKind(date, DateTimeKind.Utc)
                : date;

            return normalized.ToLocalTime();
        }
    }
}
