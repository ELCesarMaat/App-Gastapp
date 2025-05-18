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
        }
        public void DeleteDatabase()
        {
            Database.EnsureDeleted();
            Database.EnsureCreated();

        }

    }
}
