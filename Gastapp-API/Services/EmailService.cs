using System;
using System.Net;
using System.Net.Mail;
using System.Threading;
using System.Threading.Tasks;
using Gastapp_API.Models;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace Gastapp.Services
{
    public class EmailService : IEmailService
    {
        private readonly EmailSettings _settings;
        private readonly ILogger<EmailService> _logger;

        public EmailService(IOptions<EmailSettings> options, ILogger<EmailService> logger)
        {
            _settings = options.Value;
            _logger = logger;
        }

        public async Task SendPasswordResetCodeAsync(string email, string name, string code, CancellationToken cancellationToken = default)
        {
            ArgumentException.ThrowIfNullOrWhiteSpace(email);
            ArgumentException.ThrowIfNullOrWhiteSpace(code);

            cancellationToken.ThrowIfCancellationRequested();

            if (string.IsNullOrWhiteSpace(_settings.SmtpHost))
            {
                throw new InvalidOperationException("El servidor SMTP no está configurado.");
            }

            if (string.IsNullOrWhiteSpace(_settings.SenderEmail))
            {
                throw new InvalidOperationException("El correo remitente no está configurado.");
            }

            using var message = new MailMessage
            {
                From = new MailAddress(_settings.SenderEmail, _settings.SenderName),
                Subject = "Código para restablecer tu contraseña",
                Body = $"Hola {name},\n\nTu código para restablecer la contraseña es: {code}.\nEste código expira en 15 minutos.\n\nSi no solicitaste este cambio, puedes ignorar este correo.\n\nEquipo Gastapp",
                IsBodyHtml = false
            };

            message.To.Add(email);

            using var smtpClient = new SmtpClient(_settings.SmtpHost, _settings.SmtpPort)
            {
                EnableSsl = _settings.EnableSsl
            };

            if (!string.IsNullOrWhiteSpace(_settings.SmtpUser))
            {
                smtpClient.Credentials = new NetworkCredential(_settings.SmtpUser, _settings.SmtpPassword);
            }

            try
            {
                await smtpClient.SendMailAsync(message, cancellationToken);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "No se pudo enviar el código de restablecimiento a {Email}", email);
                throw;
            }
        }
    }
}
