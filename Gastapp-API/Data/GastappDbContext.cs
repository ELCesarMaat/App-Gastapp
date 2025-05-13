using Gastapp.Models;
using Microsoft.EntityFrameworkCore;

namespace Gastapp_API.Data
{
    public class GastappDbContext : DbContext
    {
        public DbSet<User> Users { get; set; }
        public DbSet<Spending> Spending { get; set; }
        public DbSet<Category> Categories { get; set; }
        public GastappDbContext(DbContextOptions<GastappDbContext> options) : base(options)
        {

        }
       
        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            
        }
    }
}
