using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Gastapp.Models;
using Microsoft.EntityFrameworkCore;

namespace Gastapp.Data
{
    public class GastappDbContext : DbContext
    {
        public DbSet<Spending> Spending { get; set; }
        public DbSet<Category> Categories { get; set; }

        private string _dbPath;

        public GastappDbContext()
        {
            var folder = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            _dbPath = Path.Combine(folder, "gastapp.db");
        }

        protected override void OnConfiguring(DbContextOptionsBuilder optionsBuilder)
        {
            optionsBuilder.UseSqlite($"Filename={_dbPath}");
            base.OnConfiguring(optionsBuilder);
        }

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            modelBuilder.Entity<Spending>(entity =>
            {
                entity.HasKey(e => e.SpendingId).HasName("PK_Spending");

                //entity.HasIndex(e => e.CategoryId, "IX_CategoryId");

                entity.Property(e => e.Amount)
                    .HasDefaultValue(0m)
                    .HasColumnType("decimal(18, 0)");
                entity.Property(e => e.Description).HasMaxLength(255);
                entity.Property(e => e.Date).HasColumnType("datetime");
                entity.Property(e => e.Title).HasMaxLength(50);

                //entity.HasOne(d => d.Category)
                //    .WithMany(p => p.Spendings)
                //    .HasForeignKey(d => d.CategoryId)
                //    .HasConstraintName("FK_Spending_Category");
            });

            modelBuilder.Entity<Category>(entity =>
            {
                entity.HasKey(e => e.CategoryId).HasName("PK_Category");
                entity.HasIndex(e => e.UserId, "IX_UserId");

                entity.Property(e => e.CategoryName).HasMaxLength(100);

                //entity.HasMany(c => c.Spendings)
                //    .WithOne(s => s.Category)
                //    .HasForeignKey(s => s.CategoryId);
            });

            base.OnModelCreating(modelBuilder);
        }
    }
}