namespace Gastapp_API
{
    public static class DatabaseConnectionHelper
    {
        public static string GetConnectionString()
        {
            var connectionString = Environment.GetEnvironmentVariable("DATABASE_URL");

            if (string.IsNullOrEmpty(connectionString))
            {
                connectionString = "Host=localhost;Port=5432;Username=myuser;Password=mypassword;Database=mydb";
            }
            Console.WriteLine($"Connection String: {connectionString}");
            return connectionString;
        }
    }
}