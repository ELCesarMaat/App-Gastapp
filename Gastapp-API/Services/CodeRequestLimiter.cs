using System;
using System.Collections.Generic;
using System.Linq;

namespace Gastapp.Services
{
    /// <summary>
    /// Respuesta de <see cref="CodeRequestLimiter.TryAcquire"/>. Si se aparto el envio y al
    /// final el correo no salio, se devuelve con <see cref="CodeRequestLimiter.Release"/>.
    /// </summary>
    public readonly record struct CodeRequestDecision(bool Allowed, TimeSpan RetryAfter, string Key, DateTime At);

    /// <summary>
    /// Cuantas veces se puede pedir un codigo por correo (verificacion del registro,
    /// recuperar contrasena y contrasena temporal): uno cada 60 s y como maximo 5 por hora.
    /// Antes no habia limite: cada peticion generaba un codigo nuevo y mandaba un correo,
    /// asi que se podia llenar de correos a cualquiera. La app espera los mismos 60 s antes
    /// de ofrecer "Reenviar codigo".
    ///
    /// Se lleva por correo, exista o no la cuenta: asi la respuesta no revela si un correo
    /// esta registrado. Vive en memoria, como los intentos de vincular un reloj
    /// (DeviceAuthService): un reinicio lo limpia, lo que a lo mucho regala unos correos.
    /// </summary>
    public static class CodeRequestLimiter
    {
        public static readonly TimeSpan Cooldown = TimeSpan.FromSeconds(60);
        private const int MaxPerWindow = 5;
        private static readonly TimeSpan Window = TimeSpan.FromHours(1);
        private static readonly TimeSpan SweepEvery = TimeSpan.FromMinutes(10);

        // Envios de la ultima hora por proposito y correo, del mas viejo al mas nuevo.
        private static readonly Dictionary<string, List<DateTime>> Requests = new();
        private static readonly object Lock = new();
        private static DateTime _lastSweep = DateTime.MinValue;

        /// <summary>Aparta un envio para el correo si todavia se puede; si no, dice cuanto falta.</summary>
        public static CodeRequestDecision TryAcquire(string purpose, string email)
        {
            var key = $"{purpose}:{email.Trim().ToLowerInvariant()}";
            var now = DateTime.UtcNow;

            lock (Lock)
            {
                SweepIfDue(now);

                if (!Requests.TryGetValue(key, out var sent))
                {
                    sent = new List<DateTime>();
                    Requests[key] = sent;
                }

                sent.RemoveAll(t => now - t >= Window);

                if (sent.Count > 0 && now - sent[^1] < Cooldown)
                    return new CodeRequestDecision(false, Cooldown - (now - sent[^1]), key, now);

                if (sent.Count >= MaxPerWindow)
                    return new CodeRequestDecision(false, sent[0] + Window - now, key, now);

                sent.Add(now);
                return new CodeRequestDecision(true, TimeSpan.Zero, key, now);
            }
        }

        /// <summary>El correo no salio (error del servicio o correo ya registrado): el intento no cuenta.</summary>
        public static void Release(CodeRequestDecision decision)
        {
            if (!decision.Allowed)
                return;

            lock (Lock)
            {
                if (!Requests.TryGetValue(decision.Key, out var sent))
                    return;

                sent.Remove(decision.At);
                if (sent.Count == 0)
                    Requests.Remove(decision.Key);
            }
        }

        /// <summary>Lo que ve el usuario cuando todavia no puede pedir otro codigo.</summary>
        public static string Message(TimeSpan retryAfter)
        {
            var seconds = SecondsToWait(retryAfter);
            if (seconds <= 90)
                return $"Espera {seconds} segundos para pedir otro código.";

            var minutes = (int)Math.Ceiling(retryAfter.TotalMinutes);
            return $"Ya pediste varios códigos. Intenta de nuevo en {minutes} minutos.";
        }

        /// <summary>Para el encabezado Retry-After.</summary>
        public static int SecondsToWait(TimeSpan retryAfter) => Math.Max(1, (int)Math.Ceiling(retryAfter.TotalSeconds));

        /// <summary>Quita los correos sin envios en la ultima hora, para que el diccionario no crezca sin fin.</summary>
        private static void SweepIfDue(DateTime now)
        {
            if (now - _lastSweep < SweepEvery)
                return;

            _lastSweep = now;
            var stale = Requests
                .Where(pair => pair.Value.Count == 0 || now - pair.Value[^1] >= Window)
                .Select(pair => pair.Key)
                .ToList();

            foreach (var key in stale)
                Requests.Remove(key);
        }
    }
}
