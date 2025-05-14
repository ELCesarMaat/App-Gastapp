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
    }
}
