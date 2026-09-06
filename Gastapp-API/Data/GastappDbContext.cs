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
        public DbSet<Subscription> Subscriptions { get; set; } = null!;
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

            // Suscripciones y membresias.
            //
            // FirstChargeDate y TrialEndDate son `date`, no `timestamp with time zone`,
            // a proposito: son fechas de calendario. Guardarlas como timestamptz obliga
            // a convertir de zona horaria y una fecha de las 00:00 puede terminar
            // corriendose un dia, y con ella todos los cobros que se calculan desde el
            // ancla. LastChargeRegisteredAt y DeletedAt si son instantes reales.
            Database.ExecuteSqlRaw("""
                CREATE TABLE IF NOT EXISTS "Subscriptions" (
                    "SubscriptionId" text NOT NULL,
                    "UserId" text NOT NULL,
                    "ServiceName" text NOT NULL,
                    "PlanName" text NULL,
                    "Amount" numeric NOT NULL DEFAULT 0,
                    "BillingCycle" text NOT NULL DEFAULT 'Monthly',
                    "FirstChargeDate" date NOT NULL,
                    "PaymentMethod" text NOT NULL DEFAULT 'Cash',
                    "CreditCardId" text NULL,
                    "CategoryId" text NULL,
                    "IsActive" boolean NOT NULL DEFAULT TRUE,
                    "IsTrial" boolean NOT NULL DEFAULT FALSE,
                    "TrialEndDate" date NULL,
                    "ColorHex" text NOT NULL DEFAULT '#7C3AED',
                    "Notes" text NULL,
                    "LastChargeRegisteredAt" timestamp with time zone NULL,
                    "IsSynced" boolean NOT NULL DEFAULT FALSE,
                    "IsDeleted" boolean NOT NULL DEFAULT FALSE,
                    "DeletedAt" timestamp with time zone NULL,
                    CONSTRAINT "PK_Subscriptions" PRIMARY KEY ("SubscriptionId"),
                    CONSTRAINT "FK_Subscriptions_Users_UserId"
                        FOREIGN KEY ("UserId") REFERENCES "Users" ("UserId") ON DELETE CASCADE,
                    CONSTRAINT "FK_Subscriptions_CreditCards_CreditCardId"
                        FOREIGN KEY ("CreditCardId") REFERENCES "CreditCards" ("CreditCardId") ON DELETE SET NULL,
                    CONSTRAINT "FK_Subscriptions_Categories_CategoryId"
                        FOREIGN KEY ("CategoryId") REFERENCES "Categories" ("CategoryId") ON DELETE SET NULL
                );
                """);

            Database.ExecuteSqlRaw("""
                CREATE INDEX IF NOT EXISTS "IX_Subscriptions_UserId"
                ON "Subscriptions" ("UserId");
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

            // Configure Subscription
            modelBuilder.Entity<Subscription>(entity =>
            {
                entity.HasKey(s => s.SubscriptionId);

                // Fechas de calendario: `date`, no timestamptz. Ver el comentario del
                // CREATE TABLE en EnsureSchemaUpToDate.
                entity.Property(s => s.FirstChargeDate).HasColumnType("date");
                entity.Property(s => s.TrialEndDate).HasColumnType("date");

                // Sin coleccion inversa en User: la propiedad no existe en el modelo
                // compartido, justamente para que la app y el API declaren la relacion
                // cada uno por su lado.
                entity.HasOne(s => s.User)
                      .WithMany()
                      .HasForeignKey(s => s.UserId)
                      .OnDelete(DeleteBehavior.Cascade);

                // Borrar la tarjeta o la categoria no se lleva la suscripcion: el
                // servicio se sigue pagando, solo cambia de donde sale el dinero.
                entity.HasOne(s => s.CreditCard)
                      .WithMany()
                      .HasForeignKey(s => s.CreditCardId)
                      .OnDelete(DeleteBehavior.SetNull);

                entity.HasOne(s => s.Category)
                      .WithMany()
                      .HasForeignKey(s => s.CategoryId)
                      .OnDelete(DeleteBehavior.SetNull);
            });
        }
    }
}