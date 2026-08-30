using System;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Threading;
using System.Threading.Tasks;
using Gastapp_API.Models;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace Gastapp.Services
{
    /// <summary>
    /// Envia correo por la API HTTPS de Resend en lugar de SMTP.
    /// Render bloquea los puertos SMTP (25, 465 y 587) en los planes gratuitos,
    /// asi que el envio tiene que salir por HTTPS.
    /// </summary>
    public class ResendEmailService : IEmailService
    {
        private const string SendEndpoint = "https://api.resend.com/emails";

        private readonly HttpClient _httpClient;
        private readonly EmailSettings _settings;
        private readonly ILogger<ResendEmailService> _logger;
        private readonly string _apiKey;

        public ResendEmailService(
            HttpClient httpClient,
            IOptions<EmailSettings> options,
            ILogger<ResendEmailService> logger)
        {
            _httpClient = httpClient;
            _settings = options.Value;
            _logger = logger;
            _apiKey = Environment.GetEnvironmentVariable("RESEND_API_KEY") ?? string.Empty;
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

        private async Task SendAsync(string to, string subject, string html, CancellationToken cancellationToken)
        {
            if (string.IsNullOrWhiteSpace(_apiKey))
                throw new InvalidOperationException("RESEND_API_KEY no está configurada.");

            if (string.IsNullOrWhiteSpace(_settings.SenderEmail))
                throw new InvalidOperationException("El correo remitente no está configurado.");

            var from = string.IsNullOrWhiteSpace(_settings.SenderName)
                ? _settings.SenderEmail
                : $"{_settings.SenderName} <{_settings.SenderEmail}>";

            using var request = new HttpRequestMessage(HttpMethod.Post, SendEndpoint)
            {
                Content = JsonContent.Create(new
                {
                    from,
                    to = new[] { to },
                    subject,
                    html
                })
            };
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _apiKey);

            try
            {
                using var response = await _httpClient.SendAsync(request, cancellationToken);

                if (!response.IsSuccessStatusCode)
                {
                    var detail = await response.Content.ReadAsStringAsync(cancellationToken);
                    _logger.LogError(
                        "Resend rechazó el envío a {Email}. Status={Status}. Respuesta: {Detail}",
                        to,
                        (int)response.StatusCode,
                        detail);

                    throw new InvalidOperationException($"Resend respondió {(int)response.StatusCode}: {detail}");
                }
            }
            catch (HttpRequestException ex)
            {
                _logger.LogError(ex, "No se pudo contactar a Resend para enviar a {Email}.", to);
                throw;
            }
        }
    }
}
