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
                EnableSsl = _settings.EnableSsl,
                Timeout = _settings.TimeoutMs > 0 ? _settings.TimeoutMs : 30000
            };

            if (!string.IsNullOrWhiteSpace(_settings.SmtpUser))
            {
                smtpClient.Credentials = new NetworkCredential(_settings.SmtpUser, _settings.SmtpPassword);
            }

            try
            {
                await smtpClient.SendMailAsync(message, cancellationToken);
            }
            catch (OperationCanceledException ex)
            {
                _logger.LogError(
                    ex,
                    "Timeout/cancelación al enviar código a {Email}. Host={Host}, Port={Port}, SSL={EnableSsl}, TimeoutMs={TimeoutMs}",
                    email,
                    _settings.SmtpHost,
                    _settings.SmtpPort,
                    _settings.EnableSsl,
                    smtpClient.Timeout);
                throw;
            }
            catch (SmtpException ex)
            {
                _logger.LogError(
                    ex,
                    "Error SMTP al enviar código a {Email}. Host={Host}, Port={Port}, SSL={EnableSsl}, StatusCode={StatusCode}",
                    email,
                    _settings.SmtpHost,
                    _settings.SmtpPort,
                    _settings.EnableSsl,
                    ex.StatusCode);
                throw;
            }
            catch (Exception ex)
            {
                _logger.LogError(
                    ex,
                    "No se pudo enviar el código de restablecimiento a {Email}. Host={Host}, Port={Port}, SSL={EnableSsl}",
                    email,
                    _settings.SmtpHost,
                    _settings.SmtpPort,
                    _settings.EnableSsl);
                throw;
            }
        }

        public async Task SendTemporaryPasswordAsync(string email, string name, string temporaryPassword, CancellationToken cancellationToken = default)
        {
            ArgumentException.ThrowIfNullOrWhiteSpace(email);
            ArgumentException.ThrowIfNullOrWhiteSpace(temporaryPassword);

            cancellationToken.ThrowIfCancellationRequested();

            if (string.IsNullOrWhiteSpace(_settings.SmtpHost))
            {
                throw new InvalidOperationException("El servidor SMTP no está configurado.");
            }

            if (string.IsNullOrWhiteSpace(_settings.SenderEmail))
            {
                throw new InvalidOperationException("El correo remitente no está configurado.");
            }

            var body = $@"Hola {name},<br><br>
Has solicitado restablecer tu contraseña en Gastapp. Tu nueva contraseña temporal es:<br><br>
<strong>{temporaryPassword}</strong><br><br>
Utiliza esta contraseña para acceder a tu cuenta. Te recomendamos cambiarla por una más segura después de iniciar sesión.<br><br>
Si no solicitaste este cambio, puedes contactarnos inmediatamente para asegurar la seguridad de tu cuenta.<br><br>
Equipo Gastapp";

            using var message = new MailMessage
            {
                From = new MailAddress(_settings.SenderEmail, _settings.SenderName),
                Subject = "Tu nueva contraseña temporal - Gastapp",
                Body = body,
                IsBodyHtml = true
            };

            message.To.Add(email);

            using var smtpClient = new SmtpClient(_settings.SmtpHost, _settings.SmtpPort)
            {
                EnableSsl = _settings.EnableSsl,
                Timeout = _settings.TimeoutMs > 0 ? _settings.TimeoutMs : 30000
            };

            if (!string.IsNullOrWhiteSpace(_settings.SmtpUser))
            {
                smtpClient.Credentials = new NetworkCredential(_settings.SmtpUser, _settings.SmtpPassword);
            }

            try
            {
                await smtpClient.SendMailAsync(message, cancellationToken);
            }
            catch (OperationCanceledException ex)
            {
                _logger.LogError(
                    ex,
                    "Timeout/cancelación al enviar contraseña temporal a {Email}. Host={Host}, Port={Port}, SSL={EnableSsl}, TimeoutMs={TimeoutMs}",
                    email,
                    _settings.SmtpHost,
                    _settings.SmtpPort,
                    _settings.EnableSsl,
                    smtpClient.Timeout);
                throw;
            }
            catch (SmtpException ex)
            {
                _logger.LogError(
                    ex,
                    "Error SMTP al enviar contraseña temporal a {Email}. Host={Host}, Port={Port}, SSL={EnableSsl}, StatusCode={StatusCode}",
                    email,
                    _settings.SmtpHost,
                    _settings.SmtpPort,
                    _settings.EnableSsl,
                    ex.StatusCode);
                throw;
            }
            catch (Exception ex)
            {
                _logger.LogError(
                    ex,
                    "No se pudo enviar la contraseña temporal a {Email}. Host={Host}, Port={Port}, SSL={EnableSsl}",
                    email,
                    _settings.SmtpHost,
                    _settings.SmtpPort,
                    _settings.EnableSsl);
                throw;
            }
        }
    }
}
