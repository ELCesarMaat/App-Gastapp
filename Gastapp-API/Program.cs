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
var jwtSettings = builder.Configuration.GetSection("JwtSettings");
var secretKey = jwtSettings["Secret"];

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
            ValidIssuer = jwtSettings["Issuer"],
            ValidAudience = jwtSettings["Audience"],
            IssuerSigningKey = new SymmetricSecurityKey(
                Encoding.UTF8.GetBytes(secretKey!))
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

builder.Services.Configure<JwtSettings>(
    builder.Configuration.GetSection("JwtSettings"));


builder.Services.AddScoped<IUserService, UserService>();

// Configurar EmailSettings desde appsettings.json y variables de entorno (solo credenciales)
builder.Services.Configure<EmailSettings>(options =>
{
    var emailSettings = builder.Configuration.GetSection("EmailSettings");
    options.SenderName = emailSettings["SenderName"] ?? "Gastapp";
    options.SenderEmail = Environment.GetEnvironmentVariable("EMAIL_SENDER_EMAIL") 
        ?? emailSettings["SenderEmail"] 
        ?? throw new InvalidOperationException("EMAIL_SENDER_EMAIL no está configurada");
    options.SmtpHost = emailSettings["SmtpHost"] ?? throw new InvalidOperationException("SmtpHost no está configurado en appsettings");
    options.SmtpPort = int.TryParse(emailSettings["SmtpPort"], out var port) ? port : 587;
    options.SmtpUser = emailSettings["SmtpUser"] ?? throw new InvalidOperationException("SmtpUser no está configurado en appsettings");
    options.SmtpPassword = Environment.GetEnvironmentVariable("EMAIL_SMTP_PASSWORD") 
        ?? emailSettings["SmtpPassword"] 
        ?? throw new InvalidOperationException("EMAIL_SMTP_PASSWORD no está configurada");
    options.EnableSsl = bool.TryParse(emailSettings["EnableSsl"], out var ssl) ? ssl : true;
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