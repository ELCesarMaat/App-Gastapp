using System;
using System.IO;
using Gastapp.Models;
using Microsoft.EntityFrameworkCore;

namespace Gastapp.Data
{
    public class GastappDbContext : DbContext
    {
        public DbSet<User> Users { get; set; }
        public DbSet<IncomeType> IncomeTypes { get; set; }
        public DbSet<Category> Categories { get; set; }
        public DbSet<Spending> Spending { get; set; }
        public DbSet<CreditCard> CreditCards { get; set; }

        public static string GetDatabasePath()
        {
#if ANDROID
            try
            {
                var context = Android.App.Application.Context;
                var dbFile = context.GetDatabasePath("gastapp.db");
                if (dbFile != null)
                {
                    if (dbFile.ParentFile != null && !dbFile.ParentFile.Exists())
                    {
                        dbFile.ParentFile.Mkdirs();
                    }
                    return dbFile.AbsolutePath;
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[GastappDbContext] Context.GetDatabasePath error: {ex.Message}");
            }
#endif
            string folder;
            try
            {
                folder = Microsoft.Maui.Storage.FileSystem.AppDataDirectory;
            }
            catch
            {
                folder = Environment.GetFolderPath(Environment.SpecialFolder.Personal);
            }

            if (string.IsNullOrEmpty(folder))
            {
                folder = Environment.GetFolderPath(Environment.SpecialFolder.Personal);
            }

            if (!string.IsNullOrEmpty(folder) && !Directory.Exists(folder))
            {
                Directory.CreateDirectory(folder);
            }

            return Path.Combine(folder, "gastapp.db");
        }

        public GastappDbContext()
        {
        }

        protected override void OnConfiguring(DbContextOptionsBuilder optionsBuilder)
        {
            var dbPath = GetDatabasePath();
            var dir = Path.GetDirectoryName(dbPath);
            if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir))
            {
                Directory.CreateDirectory(dir);
            }

            var connectionStringBuilder = new Microsoft.Data.Sqlite.SqliteConnectionStringBuilder
            {
                DataSource = dbPath,
                Mode = Microsoft.Data.Sqlite.SqliteOpenMode.ReadWriteCreate
            };

            optionsBuilder.UseSqlite(connectionStringBuilder.ConnectionString);
        }

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            // User
            modelBuilder.Entity<User>(entity =>
            {
                entity.HasKey(e => e.UserId);
                entity.Property(e => e.UserId).HasMaxLength(100).IsRequired(true);

                entity.Property(e => e.Name).HasMaxLength(100);
                entity.Property(e => e.Email).HasMaxLength(100).IsRequired(false);
                entity.Property(e => e.FirstPayDay).IsRequired(false);
                entity.Property(e => e.SecondPayDay).IsRequired(false);
                entity.Property(e => e.WeekPayDay).IsRequired(false);

                entity.HasOne(u => u.IncomeType)
                      .WithMany()
                      .HasForeignKey(u => u.IncomeTypeId)
                      .OnDelete(DeleteBehavior.Restrict);
            });

            // IncomeType
            modelBuilder.Entity<IncomeType>(entity =>
            {
                entity.HasKey(e => e.IncomeTypeId);
                entity.Property(e => e.IncomeTypeName).HasMaxLength(50);
            });

            // Category
            modelBuilder.Entity<Category>(entity =>
            {
                entity.HasKey(e => e.CategoryId);
                entity.Property(e => e.CategoryName).HasMaxLength(100);
                entity.Property(e => e.IsDefaultCategory).HasDefaultValue(false);
                entity.Property(e => e.IsSynced).HasDefaultValue(false);

                entity.HasOne(c => c.User)
                      .WithMany(u => u.Categories)
                      .HasForeignKey(c => c.UserId) // maps to LocalUserId
                      .OnDelete(DeleteBehavior.Cascade);
            });

            // Spending
            modelBuilder.Entity<Spending>(entity =>
            {
                entity.HasKey(e => e.SpendingId);

                entity.Property(e => e.Title).HasMaxLength(50);
                entity.Property(e => e.Description).HasMaxLength(255);
                entity.Property(e => e.Amount).HasDefaultValue(0m);
                entity.Property(e => e.IsSynced).HasDefaultValue(false);
                entity.Property(e => e.Date).HasColumnType("datetime");
                entity.Property(e => e.PaymentMethod).HasMaxLength(50).HasDefaultValue("Cash");
                entity.Property(e => e.IsMsi).HasDefaultValue(false);
                entity.Property(e => e.TotalInstallments).HasDefaultValue(1);
                entity.Property(e => e.CurrentInstallment).HasDefaultValue(1);
                entity.Property(e => e.InstallmentMonthlyAmount).HasDefaultValue(0m);

                entity.HasOne(s => s.Category)
                      .WithMany(c => c.Spendings)
                      .HasForeignKey(s => s.CategoryId)
                      .OnDelete(DeleteBehavior.Cascade);

                entity.HasOne(s => s.User)
                    .WithMany(c => c.Spendings)
                    .HasForeignKey(s => s.UserId)
                    .OnDelete(DeleteBehavior.Cascade);
            });

            // CreditCard
            modelBuilder.Entity<CreditCard>(entity =>
            {
                entity.HasKey(e => e.CreditCardId);
                entity.Property(e => e.CreditCardId).HasMaxLength(100).IsRequired(true);
                entity.Property(e => e.CardName).HasMaxLength(100).IsRequired(true);
                entity.Property(e => e.BankName).HasMaxLength(100).IsRequired(true);
                entity.Property(e => e.LastFourDigits).HasMaxLength(4).IsRequired(false);
                entity.Property(e => e.CutOffDay).IsRequired(true);
                entity.Property(e => e.PaymentDay).IsRequired(true);
                entity.Property(e => e.CreditLimit).HasDefaultValue(0m);
                entity.Property(e => e.ColorHex).HasMaxLength(20).HasDefaultValue("#126E63");
                entity.Property(e => e.IsSynced).HasDefaultValue(false);
                entity.Property(e => e.IsDeleted).HasDefaultValue(false);

                entity.HasOne(c => c.User)
                      .WithMany(u => u.CreditCards)
                      .HasForeignKey(c => c.UserId)
                      .OnDelete(DeleteBehavior.Cascade);
            });
        }
        public async Task ResetDatabaseAsync()
        {
            ChangeTracker.Clear();
            try
            {
                EnsureSchemaUpToDate();
                await Spending.ExecuteDeleteAsync();
                await Categories.ExecuteDeleteAsync();
                await CreditCards.ExecuteDeleteAsync();
                await Users.ExecuteDeleteAsync();
                await IncomeTypes.ExecuteDeleteAsync();
            }
            catch
            {
                try
                {
                    var connection = Database.GetDbConnection();
                    if (connection.State != System.Data.ConnectionState.Open)
                        await connection.OpenAsync();

                    using var cmd = connection.CreateCommand();
                    cmd.CommandText = @"
                        DELETE FROM Spending;
                        DELETE FROM Categories;
                        DELETE FROM CreditCards;
                        DELETE FROM Users;
                        DELETE FROM IncomeTypes;
                    ";
                    await cmd.ExecuteNonQueryAsync();
                }
                catch
                {
                    EnsureSchemaUpToDate();
                }
            }
            finally
            {
                ChangeTracker.Clear();
            }
        }

        public void DeleteDatabase()
        {
            ChangeTracker.Clear();
            try
            {
                EnsureSchemaUpToDate();
                Spending.ExecuteDelete();
                Categories.ExecuteDelete();
                CreditCards.ExecuteDelete();
                Users.ExecuteDelete();
                IncomeTypes.ExecuteDelete();
            }
            catch
            {
                try
                {
                    var connection = Database.GetDbConnection();
                    if (connection.State != System.Data.ConnectionState.Open)
                        connection.Open();

                    using var cmd = connection.CreateCommand();
                    cmd.CommandText = @"
                        DELETE FROM Spending;
                        DELETE FROM Categories;
                        DELETE FROM CreditCards;
                        DELETE FROM Users;
                        DELETE FROM IncomeTypes;
                    ";
                    cmd.ExecuteNonQuery();
                }
                catch
                {
                    EnsureSchemaUpToDate();
                }
            }
            finally
            {
                ChangeTracker.Clear();
                Preferences.Clear();
            }
        }

        public void EnsureSchemaUpToDate()
        {
            Database.EnsureCreated();

            var connection = Database.GetDbConnection();
            if (connection.State != System.Data.ConnectionState.Open)
                connection.Open();

            using var checkCommand = connection.CreateCommand();
            checkCommand.CommandText = "PRAGMA table_info('Categories');";

            var hasIsDefaultCategoryColumn = false;
            using (var reader = checkCommand.ExecuteReader())
            {
                while (reader.Read())
                {
                    var columnName = reader[1]?.ToString();
                    if (string.Equals(columnName, "IsDefaultCategory", StringComparison.OrdinalIgnoreCase))
                    {
                        hasIsDefaultCategoryColumn = true;
                        break;
                    }
                }
            }

            if (!hasIsDefaultCategoryColumn)
            {
                Database.ExecuteSqlRaw("ALTER TABLE Categories ADD COLUMN IsDefaultCategory INTEGER NOT NULL DEFAULT 0;");
                Database.ExecuteSqlRaw(@"
                    UPDATE Categories
                    SET IsDefaultCategory = 1,
                        CategoryName = 'Sin categoria'
                    WHERE UPPER(CategoryName) = 'SIN CATEGORIA'
                       OR UPPER(CategoryName) = 'SIN CATEGORÍA';
                ");
            }

            // Ensure CreditCards table exists
            Database.ExecuteSqlRaw(@"
                CREATE TABLE IF NOT EXISTS CreditCards (
                    CreditCardId TEXT PRIMARY KEY NOT NULL,
                    UserId TEXT NOT NULL,
                    CardName TEXT NOT NULL,
                    BankName TEXT NOT NULL,
                    LastFourDigits TEXT NULL,
                    CutOffDay INTEGER NOT NULL,
                    PaymentDay INTEGER NOT NULL,
                    CreditLimit REAL NOT NULL DEFAULT 0,
                    ColorHex TEXT NOT NULL DEFAULT '#126E63',
                    IsSynced INTEGER NOT NULL DEFAULT 0,
                    IsDeleted INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (UserId) REFERENCES Users (UserId) ON DELETE CASCADE
                );
            ");

            // Check columns in CreditCards
            using var checkCardCmd = connection.CreateCommand();
            checkCardCmd.CommandText = "PRAGMA table_info('CreditCards');";
            var hasCreditLimitColumn = false;
            var hasColorHexColumn = false;
            using (var reader = checkCardCmd.ExecuteReader())
            {
                while (reader.Read())
                {
                    var columnName = reader[1]?.ToString();
                    if (string.Equals(columnName, "CreditLimit", StringComparison.OrdinalIgnoreCase))
                        hasCreditLimitColumn = true;
                    else if (string.Equals(columnName, "ColorHex", StringComparison.OrdinalIgnoreCase))
                        hasColorHexColumn = true;
                }
            }

            if (!hasCreditLimitColumn)
                Database.ExecuteSqlRaw("ALTER TABLE CreditCards ADD COLUMN CreditLimit REAL NOT NULL DEFAULT 0;");
            if (!hasColorHexColumn)
                Database.ExecuteSqlRaw("ALTER TABLE CreditCards ADD COLUMN ColorHex TEXT NOT NULL DEFAULT '#126E63';");

            // Ensure Spending columns for CreditCard & Payment Methods & MSI support exist
            using var checkSpendingCmd = connection.CreateCommand();
            checkSpendingCmd.CommandText = "PRAGMA table_info('Spending');";
            var hasIsCreditCardColumn = false;
            var hasCreditCardIdColumn = false;
            var hasPaymentMethodColumn = false;
            var hasIsMsiColumn = false;
            var hasTotalInstallmentsColumn = false;
            var hasCurrentInstallmentColumn = false;
            var hasParentSpendingIdColumn = false;
            var hasInstallmentMonthlyAmountColumn = false;

            using (var reader = checkSpendingCmd.ExecuteReader())
            {
                while (reader.Read())
                {
                    var columnName = reader[1]?.ToString();
                    if (string.Equals(columnName, "IsCreditCard", StringComparison.OrdinalIgnoreCase))
                        hasIsCreditCardColumn = true;
                    else if (string.Equals(columnName, "CreditCardId", StringComparison.OrdinalIgnoreCase))
                        hasCreditCardIdColumn = true;
                    else if (string.Equals(columnName, "PaymentMethod", StringComparison.OrdinalIgnoreCase))
                        hasPaymentMethodColumn = true;
                    else if (string.Equals(columnName, "IsMsi", StringComparison.OrdinalIgnoreCase))
                        hasIsMsiColumn = true;
                    else if (string.Equals(columnName, "TotalInstallments", StringComparison.OrdinalIgnoreCase))
                        hasTotalInstallmentsColumn = true;
                    else if (string.Equals(columnName, "CurrentInstallment", StringComparison.OrdinalIgnoreCase))
                        hasCurrentInstallmentColumn = true;
                    else if (string.Equals(columnName, "ParentSpendingId", StringComparison.OrdinalIgnoreCase))
                        hasParentSpendingIdColumn = true;
                    else if (string.Equals(columnName, "InstallmentMonthlyAmount", StringComparison.OrdinalIgnoreCase))
                        hasInstallmentMonthlyAmountColumn = true;
                }
            }

            if (!hasIsCreditCardColumn)
                Database.ExecuteSqlRaw("ALTER TABLE Spending ADD COLUMN IsCreditCard INTEGER NOT NULL DEFAULT 0;");
            if (!hasCreditCardIdColumn)
                Database.ExecuteSqlRaw("ALTER TABLE Spending ADD COLUMN CreditCardId TEXT NULL;");
            if (!hasPaymentMethodColumn)
                Database.ExecuteSqlRaw("ALTER TABLE Spending ADD COLUMN PaymentMethod TEXT NOT NULL DEFAULT 'Cash';");
            if (!hasIsMsiColumn)
                Database.ExecuteSqlRaw("ALTER TABLE Spending ADD COLUMN IsMsi INTEGER NOT NULL DEFAULT 0;");
            if (!hasTotalInstallmentsColumn)
                Database.ExecuteSqlRaw("ALTER TABLE Spending ADD COLUMN TotalInstallments INTEGER NOT NULL DEFAULT 1;");
            if (!hasCurrentInstallmentColumn)
                Database.ExecuteSqlRaw("ALTER TABLE Spending ADD COLUMN CurrentInstallment INTEGER NOT NULL DEFAULT 1;");
            if (!hasParentSpendingIdColumn)
                Database.ExecuteSqlRaw("ALTER TABLE Spending ADD COLUMN ParentSpendingId TEXT NULL;");
            if (!hasInstallmentMonthlyAmountColumn)
                Database.ExecuteSqlRaw("ALTER TABLE Spending ADD COLUMN InstallmentMonthlyAmount REAL NOT NULL DEFAULT 0;");
        }

    }
}
