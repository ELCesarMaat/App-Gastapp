using System;
using Gastapp.Models;
using Gastapp_API.Models;
using Microsoft.EntityFrameworkCore;

namespace Gastapp_API.Data
{
    public class GastappDbContext : DbContext
    {
        public DbSet<User> Users { get; set; } = null!;
        public DbSet<IncomeType> IncomeTypes { get; set; } = null!;
        public DbSet<Category> Categories { get; set; } = null!;
        public DbSet<Spending> Spendings { get; set; } = null!;
        public DbSet<CreditCard> CreditCards { get; set; } = null!;
        public DbSet<EmailVerification> EmailVerifications { get; set; } = null!;
        public DbSet<DeviceAuthorization> DeviceAuthorizations { get; set; } = null!;
        public DbSet<Device> Devices { get; set; } = null!;

        public GastappDbContext(DbContextOptions<GastappDbContext> options) : base(options)
        {
        }

        /// <summary>
        /// Crea las tablas que se agregaron despues del EnsureCreated inicial.
        /// La base de produccion no se creo con migraciones, asi que se completa
        /// aqui de forma idempotente en cada arranque.
        /// </summary>
        public void EnsureSchemaUpToDate()
        {
            Database.ExecuteSqlRaw("""
                CREATE TABLE IF NOT EXISTS "EmailVerifications" (
                    "EmailVerificationId" text NOT NULL,
                    "Email" text NOT NULL,
                    "CodeHash" text NOT NULL,
                    "ExpiresAt" timestamp with time zone NOT NULL,
                    "VerifiedAt" timestamp with time zone NULL,
                    "Attempts" integer NOT NULL DEFAULT 0,
                    "CreatedAt" timestamp with time zone NOT NULL DEFAULT now(),
                    CONSTRAINT "PK_EmailVerifications" PRIMARY KEY ("EmailVerificationId")
                );
                """);

            Database.ExecuteSqlRaw("""
                CREATE INDEX IF NOT EXISTS "IX_EmailVerifications_Email"
                ON "EmailVerifications" ("Email");
                """);

            // Emparejamiento de dispositivos (relojes Wear OS).
            Database.ExecuteSqlRaw("""
                CREATE TABLE IF NOT EXISTS "DeviceAuthorizations" (
                    "DeviceAuthorizationId" text NOT NULL,
                    "DeviceCodeHash" text NOT NULL,
                    "UserCode" text NOT NULL,
                    "UserId" text NULL,
                    "DeviceName" text NOT NULL,
                    "Platform" text NOT NULL DEFAULT 'wearos',
                    "Status" text NOT NULL DEFAULT 'pending',
                    "PollCount" integer NOT NULL DEFAULT 0,
                    "LastPolledAt" timestamp with time zone NULL,
                    "IntervalSeconds" integer NOT NULL DEFAULT 5,
                    "ExpiresAt" timestamp with time zone NOT NULL,
                    "CreatedAt" timestamp with time zone NOT NULL DEFAULT now(),
                    CONSTRAINT "PK_DeviceAuthorizations" PRIMARY KEY ("DeviceAuthorizationId")
                );
                """);

            // Solo puede haber un codigo vivo por valor. Los ya consumidos o expirados
            // no estorban, por eso el indice es parcial.
            Database.ExecuteSqlRaw("""
                CREATE UNIQUE INDEX IF NOT EXISTS "IX_DeviceAuthorizations_UserCode_Pending"
                ON "DeviceAuthorizations" ("UserCode") WHERE "Status" = 'pending';
                """);

            Database.ExecuteSqlRaw("""
                CREATE INDEX IF NOT EXISTS "IX_DeviceAuthorizations_DeviceCodeHash"
                ON "DeviceAuthorizations" ("DeviceCodeHash");
                """);

            Database.ExecuteSqlRaw("""
                CREATE TABLE IF NOT EXISTS "Devices" (
                    "DeviceId" text NOT NULL,
                    "UserId" text NOT NULL,
                    "Name" text NOT NULL,
                    "Platform" text NOT NULL DEFAULT 'wearos',
                    "RefreshTokenHash" text NOT NULL,
                    "Scopes" text NOT NULL DEFAULT 'expenses:write expenses:read_summary',
                    "CreatedAt" timestamp with time zone NOT NULL DEFAULT now(),
                    "LastSeenAt" timestamp with time zone NULL,
                    "RevokedAt" timestamp with time zone NULL,
                    CONSTRAINT "PK_Devices" PRIMARY KEY ("DeviceId")
                );
                """);

            Database.ExecuteSqlRaw("""
                CREATE INDEX IF NOT EXISTS "IX_Devices_UserId" ON "Devices" ("UserId");
                """);

            Database.ExecuteSqlRaw("""
                CREATE INDEX IF NOT EXISTS "IX_Devices_RefreshTokenHash"
                ON "Devices" ("RefreshTokenHash");
                """);

            // Marca de cuando se borro cada registro, para poder purgarlos despues de N dias.
            Database.ExecuteSqlRaw("""
                ALTER TABLE "Spendings" ADD COLUMN IF NOT EXISTS "DeletedAt" timestamp with time zone NULL;
                """);

            Database.ExecuteSqlRaw("""
                ALTER TABLE "CreditCards" ADD COLUMN IF NOT EXISTS "DeletedAt" timestamp with time zone NULL;
                """);

            // Los registros que ya estaban borrados antes de existir esta columna no tienen
            // fecha. Se les pone la de ahora para que reciban el periodo de gracia completo
            // en lugar de purgarse de inmediato.
            Database.ExecuteSqlRaw("""
                UPDATE "Spendings" SET "DeletedAt" = now()
                WHERE "IsDeleted" AND "DeletedAt" IS NULL;
                """);

            Database.ExecuteSqlRaw("""
                UPDATE "CreditCards" SET "DeletedAt" = now()
                WHERE "IsDeleted" AND "DeletedAt" IS NULL;
                """);
        }

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            base.OnModelCreating(modelBuilder);
            modelBuilder.Entity<IncomeType>().HasData(
                new IncomeType { IncomeTypeId = 1, IncomeTypeName = "Semanal" },
                new IncomeType { IncomeTypeId = 2, IncomeTypeName = "Quincenal" },
                new IncomeType { IncomeTypeId = 3, IncomeTypeName = "Mensual" }
            );

            // Configure CreditCard
            modelBuilder.Entity<CreditCard>(entity =>
            {
                entity.HasKey(c => c.CreditCardId);
                entity.HasOne(c => c.User)
                      .WithMany(u => u.CreditCards)
                      .HasForeignKey(c => c.UserId)
                      .OnDelete(DeleteBehavior.Cascade);
            });

            // Configure Spending
            modelBuilder.Entity<Spending>(entity =>
            {
                entity.HasOne(s => s.CreditCard)
                      .WithMany(c => c.Spendings)
                      .HasForeignKey(s => s.CreditCardId)
                      .OnDelete(DeleteBehavior.SetNull);
            });
        }
    }
}