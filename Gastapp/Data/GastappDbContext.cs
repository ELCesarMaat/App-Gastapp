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

        private string _dbPath;

        public GastappDbContext()
        {
            var folder = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            _dbPath = Path.Combine(folder, "gastapp.db");
        }

        protected override void OnConfiguring(DbContextOptionsBuilder optionsBuilder)
        {
            optionsBuilder.UseSqlite($"Filename={_dbPath}");
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
                entity.Property(e => e.IsSynced).HasDefaultValue(false);
                entity.Property(e => e.IsDeleted).HasDefaultValue(false);

                entity.HasOne(c => c.User)
                      .WithMany(u => u.CreditCards)
                      .HasForeignKey(c => c.UserId)
                      .OnDelete(DeleteBehavior.Cascade);
            });
        }
        public void DeleteDatabase()
        {
            Database.EnsureDeleted();
            Database.EnsureCreated();
            ChangeTracker.Clear();
            Preferences.Clear();
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
                    IsSynced INTEGER NOT NULL DEFAULT 0,
                    IsDeleted INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (UserId) REFERENCES Users (UserId) ON DELETE CASCADE
                );
            ");

            // Ensure Spending columns for CreditCard support exist
            using var checkSpendingCmd = connection.CreateCommand();
            checkSpendingCmd.CommandText = "PRAGMA table_info('Spending');";
            var hasIsCreditCardColumn = false;
            var hasCreditCardIdColumn = false;
            using (var reader = checkSpendingCmd.ExecuteReader())
            {
                while (reader.Read())
                {
                    var columnName = reader[1]?.ToString();
                    if (string.Equals(columnName, "IsCreditCard", StringComparison.OrdinalIgnoreCase))
                    {
                        hasIsCreditCardColumn = true;
                    }
                    else if (string.Equals(columnName, "CreditCardId", StringComparison.OrdinalIgnoreCase))
                    {
                        hasCreditCardIdColumn = true;
                    }
                }
            }

            if (!hasIsCreditCardColumn)
            {
                Database.ExecuteSqlRaw("ALTER TABLE Spending ADD COLUMN IsCreditCard INTEGER NOT NULL DEFAULT 0;");
            }
            if (!hasCreditCardIdColumn)
            {
                Database.ExecuteSqlRaw("ALTER TABLE Spending ADD COLUMN CreditCardId TEXT NULL;");
            }
        }

    }
}
