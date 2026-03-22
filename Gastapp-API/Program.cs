using System.Text;
using Gastapp.Models;
using Gastapp_API;
using Gastapp_API.Data;
using Gastapp.Services;
using Microsoft.EntityFrameworkCore;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;
using Gastapp_API.Models;
using Npgsql;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container.
var jwtSettingsSection = builder.Configuration.GetSection("JwtSettings");
var jwtIssuer = Environment.GetEnvironmentVariable("JWT_ISSUER")
    ?? jwtSettingsSection["Issuer"]
    ?? throw new InvalidOperationException("JWT issuer not configured. Set JWT_ISSUER or JwtSettings:Issuer.");
var jwtAudience = Environment.GetEnvironmentVariable("JWT_AUDIENCE")
    ?? jwtSettingsSection["Audience"]
    ?? throw new InvalidOperationException("JWT audience not configured. Set JWT_AUDIENCE or JwtSettings:Audience.");
var jwtSecret = Environment.GetEnvironmentVariable("JWT_SECRET")
    ?? jwtSettingsSection["Secret"]
    ?? throw new InvalidOperationException("JWT secret not configured. Set JWT_SECRET or JwtSettings:Secret.");
var jwtExpiryInDays = int.TryParse(Environment.GetEnvironmentVariable("JWT_EXPIRY_IN_DAYS"), out var expiryInDays)
    ? expiryInDays
    : int.TryParse(jwtSettingsSection["ExpiryInDays"], out var configuredExpiryInDays)
        ? configuredExpiryInDays
        : 7;

builder.Services.AddAuthentication(options =>
{
    options.DefaultAuthenticateScheme = JwtBearerDefaults.AuthenticationScheme;
    options.DefaultChallengeScheme = JwtBearerDefaults.AuthenticationScheme;
})
    .AddJwtBearer(options =>
    {
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidateAudience = true,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            ValidIssuer = jwtIssuer,
            ValidAudience = jwtAudience,
            IssuerSigningKey = new SymmetricSecurityKey(
                Encoding.UTF8.GetBytes(jwtSecret))
        };
    });
builder.Services.AddControllers();
// Learn more about configuring Swagger/OpenAPI at https://aka.ms/aspnetcore/swashbuckle
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

builder.Services.AddSwaggerGen(c =>
{
    c.SwaggerDoc("v1", new() { Title = "Gastapp API", Version = "v1" });

    // Configurar JWT en Swagger
    c.AddSecurityDefinition("Bearer", new OpenApiSecurityScheme
    {
        Description = "JWT Authorization header using the Bearer scheme. Example: \"Bearer {token}\"",
        Name = "Authorization",
        In = ParameterLocation.Header,
        Type = SecuritySchemeType.ApiKey,
        Scheme = "Bearer"
    });

    c.AddSecurityRequirement(new OpenApiSecurityRequirement
    {
        {
            new OpenApiSecurityScheme
            {
                Reference = new OpenApiReference
                {
                    Type = ReferenceType.SecurityScheme,
                    Id = "Bearer"
                },
                Scheme = "oauth2",
                Name = "Bearer",
                In = ParameterLocation.Header
            },
            Array.Empty<string>()
        }
    });
});

string connectionString;

var envDatabaseUrl = Environment.GetEnvironmentVariable("DATABASE_URL");
if (string.IsNullOrWhiteSpace(envDatabaseUrl))
{
    envDatabaseUrl = Environment.GetEnvironmentVariable("GASTAPP_DB_RENDER");
}

if (string.IsNullOrWhiteSpace(envDatabaseUrl))
{
    throw new InvalidOperationException("Database URL not configured. Set DATABASE_URL or GASTAPP_DB_RENDER.");
}

if (envDatabaseUrl.StartsWith("postgresql://", StringComparison.OrdinalIgnoreCase) ||
    envDatabaseUrl.StartsWith("postgres://", StringComparison.OrdinalIgnoreCase))
{
    var uri = new Uri(envDatabaseUrl);
    var userInfo = uri.UserInfo.Split(':', 2);

    if (userInfo.Length != 2)
    {
        throw new InvalidOperationException("Invalid PostgreSQL URL format. Expected user and password in the URL.");
    }

    var builderConnection = new NpgsqlConnectionStringBuilder
    {
        Host = uri.Host,
        Port = uri.IsDefaultPort || uri.Port <= 0 ? 5432 : uri.Port,
        Username = Uri.UnescapeDataString(userInfo[0]),
        Password = Uri.UnescapeDataString(userInfo[1]),
        Database = uri.AbsolutePath.Trim('/'),
        SslMode = SslMode.Require,
    };

    connectionString = builderConnection.ConnectionString;
}
else
{
    connectionString = envDatabaseUrl;
}

builder.Services.AddDbContext<GastappDbContext>(options =>
    options.UseNpgsql(connectionString));

builder.Services.Configure<JwtSettings>(options =>
{
    options.Issuer = jwtIssuer;
    options.Audience = jwtAudience;
    options.Secret = jwtSecret;
    options.ExpiryInDays = jwtExpiryInDays;
});


builder.Services.AddScoped<IUserService, UserService>();

// Configurar EmailSettings desde variables de entorno
builder.Services.Configure<EmailSettings>(options =>
{
    options.SenderName = Environment.GetEnvironmentVariable("EMAIL_SENDER_NAME") ?? "Gastapp";
    options.SenderEmail = Environment.GetEnvironmentVariable("EMAIL_SENDER_EMAIL") 
        ?? throw new InvalidOperationException("La variable de entorno EMAIL_SENDER_EMAIL es requerida");
    options.SmtpHost = Environment.GetEnvironmentVariable("EMAIL_SMTP_HOST") 
        ?? throw new InvalidOperationException("La variable de entorno EMAIL_SMTP_HOST es requerida");
    options.SmtpPort = int.TryParse(Environment.GetEnvironmentVariable("EMAIL_SMTP_PORT"), out var port) ? port : 587;
    options.SmtpUser = Environment.GetEnvironmentVariable("EMAIL_SMTP_USER") 
        ?? throw new InvalidOperationException("La variable de entorno EMAIL_SMTP_USER es requerida");
    options.SmtpPassword = Environment.GetEnvironmentVariable("EMAIL_SMTP_PASSWORD") 
        ?? throw new InvalidOperationException("La variable de entorno EMAIL_SMTP_PASSWORD es requerida");
    options.EnableSsl = bool.TryParse(Environment.GetEnvironmentVariable("EMAIL_ENABLE_SSL"), out var ssl) ? ssl : true;
    options.TimeoutMs = int.TryParse(Environment.GetEnvironmentVariable("EMAIL_SMTP_TIMEOUT_MS"), out var timeoutMs)
        ? timeoutMs
        : 30000;
});

builder.Services.AddScoped<IEmailService, EmailService>();
builder.Services.AddScoped<IPasswordResetService, PasswordResetService>();
var app = builder.Build();


// Configure the HTTP request pipeline.
if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();