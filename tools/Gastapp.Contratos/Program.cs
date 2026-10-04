using System.Text.Json;
using Gastapp.Models;
using Gastapp.Models.Models;

// Fixtures de contrato del API (ver el .csproj). Todo es determinista salvo el desfase
// con el que se escribe TokenExpiration: es un DateTime Local (DateTime.Now en el API),
// asi que sale con el desfase de la maquina; el instante es siempre el mismo.

var modo = args.Length > 0 ? args[0] : "todo";
var carpeta = args.Length > 1
    ? args[1]
    : Path.Combine("Gastapp.Android", "core", "src", "test", "resources", "contratos");
Directory.CreateDirectory(carpeta);

// La configuracion de ASP.NET Core para MVC: camelCase, sin distinguir mayusculas al leer
// y numeros que pueden venir como texto.
var web = new JsonSerializerOptions(JsonSerializerDefaults.Web) { WriteIndented = true };

if (modo is "todo" or "generar")
    Generar();

if (modo is "todo" or "verificar")
    Environment.ExitCode = Verificar() ? 0 : 1;

return;

// ---------------------------------------------------------------------------
// Respuestas del API
// ---------------------------------------------------------------------------
void Generar()
{
    const string userId = "8d3c0f2e-1a5b-4c7d-9e0f-123456789abc";

    // Como salen de Npgsql: timestamptz -> Kind Utc, con microsegundos.
    DateTime Utc(int y, int m, int d, int h, int min, int s, int micro = 0) =>
        new DateTime(y, m, d, h, min, s, DateTimeKind.Utc).AddTicks(micro * 10L);

    // Columna date -> Kind Unspecified (fechas de calendario de suscripciones).
    DateTime Calendario(int y, int m, int d) => new DateTime(y, m, d, 0, 0, 0, DateTimeKind.Unspecified);

    // DateTime.Now del API: Kind Local. Mismo instante en cualquier maquina.
    var expiracion = DateTime.SpecifyKind(Utc(2026, 11, 1, 18, 30, 0, 123456).ToLocalTime(), DateTimeKind.Local);

    var usuario = new User
    {
        UserId = userId,
        Salary = 12500.00m,
        PercentSave = 15.5m,
        Name = "Ana Prueba",
        Email = "ana@gastapp.dev",
        PassWordHash = "no-viaja",
        PasswordResetCodeHash = "hash-del-reset",
        PasswordResetCodeExpiresAt = null,
        // CreateUser la guarda a las 00:00 UTC (asi la manda MAUI).
        BirthDate = Utc(1995, 6, 15, 0, 0, 0),
        IncomeTypeId = 2,
        FirstPayDay = 1,
        SecondPayDay = 16,
        WeekPayDay = null,
        IsSynced = true,
        // El login hace Include(u => u.IncomeType): viaja anidado.
        IncomeType = new IncomeType { IncomeTypeId = 2, IncomeTypeName = "Quincenal" },
    };

    var categorias = new List<CategoryDto>
    {
        new() { CategoryId = "cat-default", UserId = userId, CategoryName = "Sin categoria", IsDefaultCategory = true, IsSynced = true },
        new() { CategoryId = "cat-comida", UserId = userId, CategoryName = "Comida", IsSynced = true },
    };

    var tarjetas = new List<CreditCardDto>
    {
        new()
        {
            CreditCardId = "card-oro", UserId = userId, CardName = "Oro", BankName = "Banco Uno", LastFourDigits = "1234",
            CutOffDay = 10, PaymentDay = 30, CreditLimit = 20000.00m, ColorHex = "#126E63", IsSynced = true,
        },
        // El login tambien manda las borradas: sus gastos siguen apuntando a ellas.
        new()
        {
            CreditCardId = "card-vieja", UserId = userId, CardName = "Vieja", BankName = "Banco Dos", LastFourDigits = null,
            CutOffDay = 31, PaymentDay = 20, CreditLimit = 0m, ColorHex = "#C62828", IsSynced = true,
            IsDeleted = true, DeletedAt = Utc(2026, 9, 20, 15, 0, 0),
        },
    };

    var gastos = new List<SpendingDto>
    {
        new()
        {
            SpendingId = "sp-tacos", CategoryId = "cat-comida", UserId = userId, Title = "Tacos", Description = "Con salsa",
            Amount = 123.45m, Date = Utc(2026, 10, 2, 20, 15, 33, 123456), IsSynced = true,
        },
        new()
        {
            SpendingId = "sp-msi", CategoryId = "cat-default", UserId = userId, Title = "Pantalla", Description = null,
            Amount = 3000.00m, Date = Utc(2026, 9, 28, 1, 30, 0), IsSynced = true, IsCreditCard = true,
            CreditCardId = "card-oro", PaymentMethod = "CreditCard", IsMsi = true, TotalInstallments = 3,
            CurrentInstallment = 1, InstallmentMonthlyAmount = 1000.00m,
        },
        new()
        {
            SpendingId = "sp-borrado", CategoryId = "cat-comida", UserId = userId, Title = "Cafe", Amount = 0.10m,
            Date = Utc(2026, 9, 30, 14, 0, 0), IsSynced = true, IsDeleted = true, DeletedAt = Utc(2026, 10, 1, 12, 0, 0, 500000),
        },
        // Apunta a una categoria y a una tarjeta que no vinieron: el telefono anula la
        // tarjeta y manda el gasto a "Sin categoria".
        new()
        {
            SpendingId = "sp-colgante", CategoryId = "cat-que-no-existe", UserId = userId, Title = "Colgante",
            Amount = 50m, Date = Utc(2026, 10, 1, 6, 0, 0), IsSynced = true, IsCreditCard = true,
            CreditCardId = "card-que-no-existe", PaymentMethod = "CreditCard",
        },
    };

    var suscripciones = new List<SubscriptionDto>
    {
        new()
        {
            SubscriptionId = "sub-netflix", UserId = userId, ServiceName = "Netflix", PlanName = "Estandar", Amount = 219.00m,
            BillingCycle = "Monthly", FirstChargeDate = Calendario(2026, 1, 31), PaymentMethod = "CreditCard",
            CreditCardId = "card-oro", CategoryId = "cat-comida", IsActive = true, IsTrial = false, TrialEndDate = null,
            ColorHex = "#C62828", Notes = null, LastChargeRegisteredAt = Utc(2026, 9, 30, 18, 0, 0), IsSynced = true,
        },
        new()
        {
            SubscriptionId = "sub-prueba", UserId = userId, ServiceName = "Nube", Amount = 49.90m, BillingCycle = "Yearly",
            FirstChargeDate = Calendario(2026, 2, 28), PaymentMethod = "Debit", CreditCardId = "card-que-no-existe",
            CategoryId = "cat-que-no-existe", IsActive = true, IsTrial = true, TrialEndDate = Calendario(2026, 10, 15),
            ColorHex = "#7C3AED", Notes = "Prueba gratis", IsSynced = true,
        },
    };

    var tipos = new List<IncomeType>
    {
        new() { IncomeTypeId = 1, IncomeTypeName = "Semanal" },
        new() { IncomeTypeId = 2, IncomeTypeName = "Quincenal" },
        new() { IncomeTypeId = 3, IncomeTypeName = "Mensual" },
    };

    Guardar("login.json", new AllUserData
    {
        User = usuario,
        Categories = categorias,
        Spendings = gastos,
        CreditCards = tarjetas,
        Subscriptions = suscripciones,
        Incomes = tipos,
        Token = "eyJ.token.login",
        TokenExpiration = expiracion,
    });

    Guardar("refresh_token.json", new Token { TokenValue = "eyJ.token.nuevo", TokenExpiration = expiracion });

    Guardar("create_user_response.json", new CreateUserResponse
    {
        UserId = userId,
        Token = "eyJ.token.registro",
        TokenExpiration = expiracion,
    });

    // GetSpendings: solo los vigentes, IsSynced = true. El del reloj trae Description
    // con la firma del reloj y la categoria por defecto del servidor.
    Guardar("get_spendings.json", new List<SpendingDto>
    {
        gastos[0],
        new()
        {
            SpendingId = "sp-reloj", CategoryId = "cat-default", UserId = userId, Title = "Cafe", Description = "Reloj: cafe 45",
            Amount = 45m, Date = Utc(2026, 10, 2, 16, 5, 0), IsSynced = true,
        },
    });

    Guardar("latest_version.json", new AppLatestVersionDto
    {
        VersionCode = 200,
        VersionName = "2.0.0",
        ApkUrl = "https://github.com/ELCesarMaat/App-Gastapp/releases/download/v2.0.0/gastapp.apk",
        ReleaseNotes = "Version nativa",
        PublishedAt = Utc(2026, 10, 20, 17, 0, 0),
    });

    Guardar("devices.json", new List<DeviceDto>
    {
        new() { DeviceId = "dev-1", Name = "Pixel Watch", Platform = "wearos", CreatedAt = Utc(2026, 8, 1, 10, 0, 0), LastSeenAt = Utc(2026, 10, 2, 9, 0, 0, 250000) },
        new() { DeviceId = "dev-2", Name = "Reloj viejo", Platform = "wearos", CreatedAt = Utc(2026, 1, 5, 10, 0, 0), LastSeenAt = null },
    });

    // PasswordReset/temporary responde un objeto anonimo.
    Guardar("temporary_password.json", new { message = "Se ha enviado una contraseña temporal a tu correo." });
}

void Guardar(string nombre, object contenido)
{
    var ruta = Path.Combine(carpeta, nombre);
    File.WriteAllText(ruta, JsonSerializer.Serialize(contenido, contenido.GetType(), web) + "\n");
    Console.WriteLine($"generado   {nombre}");
}

// ---------------------------------------------------------------------------
// Lo que manda la app nativa: C# tiene que entenderlo como lo entendia de MAUI
// ---------------------------------------------------------------------------
bool Verificar()
{
    var fallas = new List<string>();

    void Igual<T>(string que, T esperado, T real)
    {
        if (!EqualityComparer<T>.Default.Equals(esperado, real))
            fallas.Add($"{que}: se esperaba {esperado}, llego {real}");
    }

    var sync = Leer<SyncDataDto>("sync_all_data_request.json");
    if (sync != null)
    {
        var u = sync.User!;
        Igual("user.salary", 12500.00m, u.Salary);
        Igual("user.percentSave", 15.5m, u.PercentSave);
        // SyncAllData hace SpecifyKind(Utc) sin mover la hora: tiene que llegar sin zona.
        Igual("user.birthDate", new DateTime(1995, 6, 15), u.BirthDate);
        Igual("user.birthDate.Kind", DateTimeKind.Unspecified, u.BirthDate.Kind);
        Igual("user.isSynced", false, u.IsSynced);

        var gasto = sync.Spendings.Single(s => s.SpendingId == "sp-1");
        Igual("spending.amount", 123.45m, gasto.Amount);
        Igual("spending.date", new DateTime(2026, 10, 2, 20, 15, 0, DateTimeKind.Utc), gasto.Date);
        Igual("spending.date.Kind", DateTimeKind.Utc, gasto.Date.Kind);
        Igual("spending.installmentMonthlyAmount", 41.15m, gasto.InstallmentMonthlyAmount);
        Igual("spending.isSynced", false, gasto.IsSynced);

        var borrado = sync.Spendings.Single(s => s.SpendingId == "sp-2");
        Igual("deleted.amount", 0.10m, borrado.Amount);
        Igual("deleted.isDeleted", true, borrado.IsDeleted);
        Igual("deleted.deletedAt", new DateTime(2026, 10, 1, 12, 0, 0, DateTimeKind.Utc), borrado.DeletedAt);
        Igual("deleted.deletedAt.Kind", DateTimeKind.Utc, borrado.DeletedAt?.Kind);

        var tarjeta = sync.CreditCards.Single();
        Igual("card.creditLimit", 20000.00m, tarjeta.CreditLimit);
        Igual("card.cutOffDay", 10, tarjeta.CutOffDay);

        var sub = sync.Subscriptions.Single();
        // Fechas de calendario: sin zona, a las 00:00 (NormalizeCalendarDate las deja igual).
        Igual("sub.firstChargeDate", new DateTime(2026, 1, 31), sub.FirstChargeDate);
        Igual("sub.firstChargeDate.Kind", DateTimeKind.Unspecified, sub.FirstChargeDate.Kind);
        Igual("sub.trialEndDate", new DateTime(2026, 10, 15), sub.TrialEndDate);
        Igual("sub.lastChargeRegisteredAt", new DateTime(2026, 9, 30, 18, 0, 0, DateTimeKind.Utc), sub.LastChargeRegisteredAt);
        Igual("sub.lastChargeRegisteredAt.Kind", DateTimeKind.Utc, sub.LastChargeRegisteredAt?.Kind);
        Igual("sub.amount", 219.00m, sub.Amount);

        Igual("categories", 1, sync.Categories.Count);
    }

    var alta = Leer<CreateUserModel>("create_user_request.json");
    if (alta != null)
    {
        // CreateUser guarda BirthDate en timestamptz SIN normalizar: Npgsql rechaza un
        // DateTime Unspecified ahi. Tiene que llegar en UTC.
        Igual("createUser.birthDate", new DateTime(1995, 6, 15, 0, 0, 0, DateTimeKind.Utc), alta.BirthDate);
        Igual("createUser.birthDate.Kind", DateTimeKind.Utc, alta.BirthDate.Kind);
        Igual("createUser.salary", 12500.00m, alta.Salary);
        Igual("createUser.percentSave", 15.5m, alta.PercentSave);
        Igual("createUser.incomeTypeId", 2, alta.IncomeTypeId);
        Igual("createUser.weekPayDay", null, alta.WeekPayDay);
    }

    foreach (var falla in fallas)
        Console.WriteLine($"FALLA      {falla}");

    var ok = fallas.Count == 0 && sync != null && alta != null;
    Console.WriteLine(ok ? "verificado los cuerpos que manda la app nativa" : "la verificacion fallo");
    return ok;
}

T? Leer<T>(string nombre) where T : class
{
    var ruta = Path.Combine(carpeta, nombre);
    if (!File.Exists(ruta))
    {
        Console.WriteLine($"FALTA      {nombre} (golden de ContractRequestTest en :core)");
        return null;
    }
    return JsonSerializer.Deserialize<T>(File.ReadAllText(ruta), web);
}
