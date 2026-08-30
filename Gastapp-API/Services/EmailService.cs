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
    /// <summary>
    /// Envio por SMTP. Sirve para desarrollo local; en Render los planes gratuitos
    /// bloquean los puertos SMTP, ahi se usa <see cref="ResendEmailService"/>.
    /// </summary>
    public class EmailService : IEmailService
    {
        private readonly EmailSettings _settings;
        private readonly ILogger<EmailService> _logger;

        public EmailService(IOptions<EmailSettings> options, ILogger<EmailService> logger)
        {
            _settings = options.Value;
            _logger = logger;
        }

        public Task SendPasswordResetCodeAsync(string email, string name, string code, CancellationToken cancellationToken = default)
        {
            ArgumentException.ThrowIfNullOrWhiteSpace(email);
            ArgumentException.ThrowIfNullOrWhiteSpace(code);

            return SendAsync(
                email,
                EmailMessages.PasswordResetSubject,
                EmailMessages.BuildPasswordResetBody(name, code),
                cancellationToken);
        }

        public Task SendTemporaryPasswordAsync(string email, string name, string temporaryPassword, CancellationToken cancellationToken = default)
        {
            ArgumentException.ThrowIfNullOrWhiteSpace(email);
            ArgumentException.ThrowIfNullOrWhiteSpace(temporaryPassword);

            return SendAsync(
                email,
                EmailMessages.TemporaryPasswordSubject,
                EmailMessages.BuildTemporaryPasswordBody(name, temporaryPassword),
                cancellationToken);
        }

        public Task SendEmailVerificationCodeAsync(string email, string code, CancellationToken cancellationToken = default)
        {
            ArgumentException.ThrowIfNullOrWhiteSpace(email);
            ArgumentException.ThrowIfNullOrWhiteSpace(code);

            return SendAsync(
                email,
                EmailMessages.EmailVerificationSubject,
                EmailMessages.BuildEmailVerificationBody(code),
                cancellationToken);
        }

        private async Task SendAsync(string to, string subject, string htmlBody, CancellationToken cancellationToken)
        {
            cancellationToken.ThrowIfCancellationRequested();

            if (string.IsNullOrWhiteSpace(_settings.SmtpHost))
                throw new InvalidOperationException("El servidor SMTP no está configurado.");

            if (string.IsNullOrWhiteSpace(_settings.SenderEmail))
                throw new InvalidOperationException("El correo remitente no está configurado.");

            using var message = new MailMessage
            {
                From = new MailAddress(_settings.SenderEmail, _settings.SenderName),
                Subject = subject,
                Body = htmlBody,
                IsBodyHtml = true
            };

            message.To.Add(to);

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
                    "Timeout/cancelación al enviar \"{Subject}\" a {Email}. Host={Host}, Port={Port}, SSL={EnableSsl}, TimeoutMs={TimeoutMs}",
                    subject, to, _settings.SmtpHost, _settings.SmtpPort, _settings.EnableSsl, smtpClient.Timeout);
                throw;
            }
            catch (SmtpException ex)
            {
                _logger.LogError(
                    ex,
                    "Error SMTP al enviar \"{Subject}\" a {Email}. Host={Host}, Port={Port}, SSL={EnableSsl}, StatusCode={StatusCode}",
                    subject, to, _settings.SmtpHost, _settings.SmtpPort, _settings.EnableSsl, ex.StatusCode);
                throw;
            }
            catch (Exception ex)
            {
                _logger.LogError(
                    ex,
                    "No se pudo enviar \"{Subject}\" a {Email}. Host={Host}, Port={Port}, SSL={EnableSsl}",
                    subject, to, _settings.SmtpHost, _settings.SmtpPort, _settings.EnableSsl);
                throw;
            }
        }
    }
}
