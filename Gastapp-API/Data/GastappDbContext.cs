using System;
using Gastapp.Models;
using Microsoft.EntityFrameworkCore;

namespace Gastapp_API.Data
{
    public class GastappDbContext : DbContext
    {
        public DbSet<User> Users { get; set; } = null!;
        public DbSet<IncomeType> IncomeTypes { get; set; } = null!;
        public DbSet<Category> Categories { get; set; } = null!;
        public DbSet<Spending> Spendings { get; set; } = null!;

        public GastappDbContext(DbContextOptions<GastappDbContext> options) : base(options)
        {
        }

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            base.OnModelCreating(modelBuilder);
            modelBuilder.Entity<IncomeType>().HasData(
                new IncomeType { IncomeTypeId = 1, IncomeTypeName = "Semanal" },
                new IncomeType { IncomeTypeId = 2, IncomeTypeName = "Quincenal" },
                new IncomeType { IncomeTypeId = 3, IncomeTypeName = "Mensual" }
            );
        }
    }
}