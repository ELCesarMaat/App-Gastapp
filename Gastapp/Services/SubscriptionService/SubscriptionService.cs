using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Gastapp.Data;
using Gastapp.Models;
using Gastapp.Services.ApiService;
using Gastapp.Utils;
using Microsoft.EntityFrameworkCore;

namespace Gastapp.Services
{
    /// <summary>
    /// Toda la logica de ciclos y totales de las suscripciones.
    ///
    /// Mismo reparto que <see cref="CreditCardService"/>: la base local es la fuente
    /// de verdad, la UI no espera a la red y el servicio devuelve
    /// <see cref="SubscriptionSummary"/> ya masticado (textos, colores y fechas), de
    /// modo que ni el ViewModel ni el XAML tengan que calcular nada.
    ///
    /// El empuje al servidor es fire-and-forget despues de cada SaveChanges, igual que
    /// <c>_ = SyncNewCreditCard(card)</c>: si no hay red, la fila se queda con
    /// IsSynced = false y el SyncAllData del arranque la recoge.
    /// </summary>
    public class SubscriptionService : ISubscriptionService
    {
        private readonly GastappDbContext _db;
        private readonly IApiService _api;

        public SubscriptionService(GastappDbContext db, IApiService api)
        {
            _db = db;
            _api = api;
        }

        public async Task<List<Subscription>> GetAllSubscriptionsAsync()
        {
            return await _db.Subscriptions
                .Where(s => !s.IsDeleted)
                .OrderByDescending(s => s.IsActive)
                .ThenBy(s => s.ServiceName)
                .ToListAsync();
        }

        public async Task<Subscription?> GetSubscriptionByIdAsync(string id)
        {
            return await _db.Subscriptions
                .FirstOrDefaultAsync(s => s.SubscriptionId == id && !s.IsDeleted);
        }

        public async Task<Subscription> CreateSubscriptionAsync(Subscription subscription)
        {
            subscription.IsSynced = false;
            subscription.IsDeleted = false;

            await _db.Subscriptions.AddAsync(subscription);
            await _db.SaveChangesAsync();

            _ = SyncNewSubscription(subscription);

            return subscription;
        }

        public async Task<bool> UpdateSubscriptionAsync(Subscription subscription)
        {
            var existing = await _db.Subscriptions
                .FirstOrDefaultAsync(s => s.SubscriptionId == subscription.SubscriptionId);

            if (existing == null) return false;

            existing.ServiceName = subscription.ServiceName;
            existing.PlanName = subscription.PlanName;
            existing.Amount = subscription.Amount;
            existing.BillingCycle = subscription.BillingCycle;
            existing.FirstChargeDate = subscription.FirstChargeDate;
            existing.PaymentMethod = subscription.PaymentMethod;
            existing.CreditCardId = subscription.CreditCardId;
            existing.CategoryId = subscription.CategoryId;
            existing.IsTrial = subscription.IsTrial;
            existing.TrialEndDate = subscription.TrialEndDate;
            existing.ColorHex = subscription.ColorHex;
            existing.Notes = subscription.Notes;
            existing.IsSynced = false;

            await _db.SaveChangesAsync();

            _ = SyncNewSubscription(existing);

            return true;
        }

        public async Task<bool> DeleteSubscriptionAsync(string id)
        {
            var existing = await _db.Subscriptions.FirstOrDefaultAsync(s => s.SubscriptionId == id);
            if (existing == null) return false;

            // Borrado logico, igual que en tarjetas: se conserva la fila hasta que la
            // purga se la lleve, para que el borrado alcance a viajar al servidor.
            existing.IsDeleted = true;
            existing.DeletedAt = DateTime.UtcNow;
            existing.IsSynced = false;

            await _db.SaveChangesAsync();

            _ = SyncDeleteSubscription(id);

            return true;
        }

        public async Task<bool> SetActiveStateAsync(string id, bool isActive)
        {
            var existing = await _db.Subscriptions
                .FirstOrDefaultAsync(s => s.SubscriptionId == id && !s.IsDeleted);

            if (existing == null) return false;

            existing.IsActive = isActive;
            existing.IsSynced = false;

            await _db.SaveChangesAsync();

            _ = SyncNewSubscription(existing);

            return true;
        }

        public async Task<bool> MarkChargeRegisteredAsync(string id, DateTime chargedAt)
        {
            var existing = await _db.Subscriptions
                .FirstOrDefaultAsync(s => s.SubscriptionId == id && !s.IsDeleted);

            if (existing == null) return false;

            existing.LastChargeRegisteredAt = chargedAt;
            existing.IsSynced = false;

            await _db.SaveChangesAsync();

            _ = SyncNewSubscription(existing);

            return true;
        }

        /// <summary>
        /// Cuenta ciclos completos desde el primer cobro hasta rebasar la fecha dada.
        ///
        /// El candidato siempre se calcula desde el ancla (<c>anchor.AddMonths(n * meses)</c>)
        /// y nunca encadenando AddMonths sobre el resultado anterior: si el ancla cae 31
        /// y un mes lo recorta a 30, encadenar perderia el dia 31 para siempre.
        /// </summary>
        public DateTime CalculateNextChargeDate(DateTime firstChargeDate, string billingCycle, DateTime referenceDate)
        {
            var anchor = firstChargeDate.Date;
            var today = referenceDate.Date;

            // El primer cobro todavia no llega: ese es el proximo.
            if (anchor >= today)
                return anchor;

            if (billingCycle == SubscriptionBillingCycles.Weekly)
            {
                var elapsedWeeks = (int)Math.Floor((today - anchor).TotalDays / 7d);
                var weekly = anchor.AddDays(elapsedWeeks * 7);

                while (weekly < today)
                {
                    elapsedWeeks++;
                    weekly = anchor.AddDays(elapsedWeeks * 7);
                }

                return weekly;
            }

            var monthsPerCycle = GetMonthsPerCycle(billingCycle);
            var elapsedMonths = ((today.Year - anchor.Year) * 12) + today.Month - anchor.Month;
            var cycles = Math.Max(0, elapsedMonths / monthsPerCycle);

            var candidate = anchor.AddMonths(cycles * monthsPerCycle);
            while (candidate < today)
            {
                cycles++;
                candidate = anchor.AddMonths(cycles * monthsPerCycle);
            }

            return candidate;
        }

        /// <summary>
        /// El espejo de <see cref="CalculateNextChargeDate"/> hacia atras. Mismo
        /// cuidado: el candidato se calcula siempre desde el ancla, nunca restando
        /// meses al resultado anterior.
        /// </summary>
        public DateTime CalculatePreviousChargeDate(DateTime firstChargeDate, string billingCycle, DateTime referenceDate)
        {
            var anchor = firstChargeDate.Date;
            var today = referenceDate.Date;

            // Todavia no ocurre ni el primer cobro: no hay periodo anterior.
            if (anchor >= today)
                return anchor;

            if (billingCycle == SubscriptionBillingCycles.Weekly)
            {
                var elapsedWeeks = (int)Math.Floor((today - anchor).TotalDays / 7d);
                return anchor.AddDays(elapsedWeeks * 7);
            }

            var monthsPerCycle = GetMonthsPerCycle(billingCycle);
            var elapsedMonths = ((today.Year - anchor.Year) * 12) + today.Month - anchor.Month;
            var cycles = Math.Max(0, elapsedMonths / monthsPerCycle);

            var candidate = anchor.AddMonths(cycles * monthsPerCycle);
            while (candidate > today && cycles > 0)
            {
                cycles--;
                candidate = anchor.AddMonths(cycles * monthsPerCycle);
            }

            return candidate;
        }

        public bool CountsTowardTotals(Subscription subscription, DateTime referenceDate)
        {
            if (!subscription.IsActive)
                return false;

            // En prueba gratis no sale dinero todavia, asi que no puede sumar al
            // gasto mensual. Es lo que promete el formulario al activar el switch.
            var trialEnd = subscription.TrialEndDate?.Date;
            var enPrueba = subscription.IsTrial && trialEnd.HasValue && trialEnd.Value >= referenceDate.Date;

            return !enPrueba;
        }

        public decimal GetMonthlyEquivalent(decimal amount, string billingCycle)
        {
            if (amount <= 0) return 0m;

            return billingCycle switch
            {
                // 52 semanas repartidas en 12 meses, no 4 semanas por mes: cuatro
                // semanas se quedan cortas casi un mes entero al año.
                SubscriptionBillingCycles.Weekly => amount * 52m / 12m,
                SubscriptionBillingCycles.Quarterly => amount / 3m,
                SubscriptionBillingCycles.Semiannual => amount / 6m,
                SubscriptionBillingCycles.Yearly => amount / 12m,
                _ => amount
            };
        }

        public string GetBillingCycleDisplayName(string billingCycle) => billingCycle switch
        {
            SubscriptionBillingCycles.Weekly => "Semanal",
            SubscriptionBillingCycles.Quarterly => "Trimestral",
            SubscriptionBillingCycles.Semiannual => "Semestral",
            SubscriptionBillingCycles.Yearly => "Anual",
            _ => "Mensual"
        };

        public string GetBillingCycleSuffix(string billingCycle) => billingCycle switch
        {
            SubscriptionBillingCycles.Weekly => "a la semana",
            SubscriptionBillingCycles.Quarterly => "cada 3 meses",
            SubscriptionBillingCycles.Semiannual => "cada 6 meses",
            SubscriptionBillingCycles.Yearly => "al año",
            _ => "al mes"
        };

        public async Task<SubscriptionSummary> GetSubscriptionSummaryAsync(string id)
        {
            var subscription = await GetSubscriptionByIdAsync(id);
            if (subscription == null) return new SubscriptionSummary();

            var monthlyTotal = await GetTotalMonthlyCostAsync();
            var cardNames = await GetCardNamesAsync();
            var categoryNames = await GetCategoryNamesAsync();

            return BuildSummary(subscription, monthlyTotal, cardNames, categoryNames);
        }

        public async Task<List<SubscriptionSummary>> GetAllSubscriptionSummariesAsync()
        {
            var subscriptions = await GetAllSubscriptionsAsync();

            // El peso de cada suscripcion se mide contra el total de las que cuentan,
            // asi que el total se calcula una sola vez y no una por fila. Los nombres
            // de tarjetas y categorias se bajan igual, de un solo golpe.
            var today = DateTime.Today;
            var monthlyTotal = subscriptions
                .Where(s => CountsTowardTotals(s, today))
                .Sum(s => GetMonthlyEquivalent(s.Amount, s.BillingCycle));

            var cardNames = await GetCardNamesAsync();
            var categoryNames = await GetCategoryNamesAsync();

            return subscriptions
                .Select(s => BuildSummary(s, monthlyTotal, cardNames, categoryNames))
                .ToList();
        }

        public async Task<List<UpcomingCharge>> GetUpcomingChargesAsync(int daysAhead = 45)
        {
            var today = DateTime.Today;
            var limit = today.AddDays(Math.Max(1, daysAhead));

            var subscriptions = await GetAllSubscriptionsAsync();
            var cardNames = await GetCardNamesAsync();

            var charges = new List<UpcomingCharge>();

            foreach (var subscription in subscriptions.Where(s => s.IsActive))
            {
                // Una prueba en curso no cobra nada: el primer cargo real cae el dia
                // que termina la prueba, no en el ciclo que marcaria el calendario.
                var reference = subscription.IsTrial && subscription.TrialEndDate.HasValue && subscription.TrialEndDate.Value.Date > today
                    ? subscription.TrialEndDate.Value.Date
                    : today;

                var next = CalculateNextChargeDate(subscription.FirstChargeDate, subscription.BillingCycle, reference);

                // Se listan todas las repeticiones que caben en la ventana, no solo la
                // primera: un semanal aporta varias y un anual puede no aportar ninguna.
                var guard = 0;
                while (next <= limit && guard++ < 60)
                {
                    charges.Add(BuildUpcomingCharge(subscription, next, today, cardNames));
                    next = CalculateNextChargeDate(subscription.FirstChargeDate, subscription.BillingCycle, next.AddDays(1));
                }
            }

            return charges.OrderBy(c => c.Date).ThenBy(c => c.ServiceName).ToList();
        }

        // ---------- Armado de los objetos que consume la UI ----------

        private SubscriptionSummary BuildSummary(
            Subscription subscription,
            decimal monthlyTotal,
            IReadOnlyDictionary<string, string> cardNames,
            IReadOnlyDictionary<string, string> categoryNames)
        {
            var today = DateTime.Today;

            var monthlyEquivalent = GetMonthlyEquivalent(subscription.Amount, subscription.BillingCycle);

            var trialEnd = subscription.TrialEndDate?.Date;
            var isTrialActive = subscription.IsTrial && trialEnd.HasValue && trialEnd.Value >= today;
            var daysUntilTrialEnds = isTrialActive ? (trialEnd!.Value - today).Days : 0;

            var reference = isTrialActive ? trialEnd!.Value : today;
            var nextCharge = CalculateNextChargeDate(subscription.FirstChargeDate, subscription.BillingCycle, reference);
            var daysUntilCharge = (nextCharge - today).Days;

            GetChargeStatus(subscription.IsActive, daysUntilCharge, nextCharge, out var chargeStatusText, out var chargeStatusColor);
            GetGradientForColor(subscription.ColorHex, out var gradientStart, out var gradientEnd);

            var linkedCardName = subscription.PaymentMethod == SubscriptionPaymentMethods.CreditCard
                && !string.IsNullOrWhiteSpace(subscription.CreditCardId)
                && cardNames.TryGetValue(subscription.CreditCardId, out var name)
                    ? name
                    : string.Empty;

            var categoryName = !string.IsNullOrWhiteSpace(subscription.CategoryId)
                && categoryNames.TryGetValue(subscription.CategoryId, out var category)
                    ? category
                    : string.Empty;

            var cuentaEnTotales = CountsTowardTotals(subscription, today);

            var share = monthlyTotal > 0 && cuentaEnTotales
                ? Math.Clamp((double)(monthlyEquivalent / monthlyTotal), 0.0, 1.0)
                : 0.0;

            string shareText;
            if (!subscription.IsActive)
                shareText = "No cuenta en tus totales mientras esté pausada";
            else if (isTrialActive)
                shareText = "No cuenta en tus totales mientras dure la prueba";
            else
                shareText = $"{share * 100:F0}% de tu gasto mensual en suscripciones";

            // El periodo en curso arranca en el ultimo cobro que ya ocurrio: si el
            // registro cayo despues de esa fecha, el cobro de este periodo ya esta
            // contabilizado y volver a registrarlo duplicaria el gasto.
            var cicloActual = CalculatePreviousChargeDate(subscription.FirstChargeDate, subscription.BillingCycle, today);
            var ultimoRegistro = subscription.LastChargeRegisteredAt?.Date;
            var cicloYaCobrado = ultimoRegistro.HasValue && ultimoRegistro.Value >= cicloActual && cicloActual <= today;

            string statusBadgeText;
            string statusBadgeColor;

            if (!subscription.IsActive)
            {
                statusBadgeText = "Pausada";
                statusBadgeColor = "#6E6E6E";
            }
            else if (isTrialActive)
            {
                statusBadgeText = "Prueba gratis";
                statusBadgeColor = "#D97706";
            }
            else
            {
                statusBadgeText = "Activa";
                statusBadgeColor = "#126E63";
            }

            string trialStatusText;
            if (!subscription.IsTrial || !trialEnd.HasValue)
                trialStatusText = string.Empty;
            else if (!isTrialActive)
                trialStatusText = $"La prueba terminó el {trialEnd.Value:dd/MMM}, ya se está cobrando.";
            else if (daysUntilTrialEnds == 0)
                trialStatusText = $"La prueba termina hoy: mañana empieza el cobro de ${subscription.Amount:N2}.";
            else
                trialStatusText = $"Prueba gratis: te quedan {daysUntilTrialEnds} día{(daysUntilTrialEnds == 1 ? "" : "s")} (hasta el {trialEnd.Value:dd/MMM}).";

            return new SubscriptionSummary
            {
                Subscription = subscription,
                Amount = subscription.Amount,
                MonthlyEquivalent = monthlyEquivalent,
                YearlyEquivalent = monthlyEquivalent * 12m,
                NextChargeDate = nextCharge,
                DaysUntilCharge = daysUntilCharge,
                ChargeStatusText = chargeStatusText,
                ChargeStatusColor = chargeStatusColor,
                BillingCycleText = GetBillingCycleDisplayName(subscription.BillingCycle),
                PriceCycleText = $"${subscription.Amount:N2} {GetBillingCycleSuffix(subscription.BillingCycle)}",
                PaymentMethodText = GetPaymentMethodDisplayName(subscription.PaymentMethod),
                LinkedCardName = linkedCardName,
                HasLinkedCard = !string.IsNullOrWhiteSpace(linkedCardName),
                CategoryName = categoryName,
                IsActive = subscription.IsActive,
                CountsTowardTotals = cuentaEnTotales,
                ToggleStateActionText = subscription.IsActive ? "Pausar" : "Reanudar",
                IsCurrentCycleCharged = cicloYaCobrado,
                LastChargeText = cicloYaCobrado
                    ? $"Ya registraste el cobro de este periodo el {ultimoRegistro!.Value:dd/MMM}."
                    : string.Empty,
                Notes = subscription.Notes ?? string.Empty,
                HasNotes = !string.IsNullOrWhiteSpace(subscription.Notes),
                StatusBadgeText = statusBadgeText,
                StatusBadgeColor = statusBadgeColor,
                IsTrialActive = isTrialActive,
                DaysUntilTrialEnds = daysUntilTrialEnds,
                TrialStatusText = trialStatusText,
                ShareOfMonthlyRatio = share,
                ShareOfMonthlyText = shareText,
                CardBackgroundGradientStart = gradientStart,
                CardBackgroundGradientEnd = gradientEnd,
                UpcomingCharges = BuildNextCharges(subscription, nextCharge, today, cardNames, count: 3)
            };
        }

        /// <summary>Las siguientes N repeticiones de una misma suscripcion.</summary>
        private List<UpcomingCharge> BuildNextCharges(
            Subscription subscription,
            DateTime firstNext,
            DateTime today,
            IReadOnlyDictionary<string, string> cardNames,
            int count)
        {
            var charges = new List<UpcomingCharge>();
            var next = firstNext;

            for (var i = 0; i < count; i++)
            {
                charges.Add(BuildUpcomingCharge(subscription, next, today, cardNames));
                next = CalculateNextChargeDate(subscription.FirstChargeDate, subscription.BillingCycle, next.AddDays(1));
            }

            return charges;
        }

        private UpcomingCharge BuildUpcomingCharge(
            Subscription subscription,
            DateTime date,
            DateTime today,
            IReadOnlyDictionary<string, string> cardNames)
        {
            var daysUntil = (date.Date - today).Days;

            string whenText = daysUntil switch
            {
                < 0 => "Ya pasó",
                0 => "Hoy",
                1 => "Mañana",
                _ => $"En {daysUntil} días"
            };

            string statusColor = daysUntil switch
            {
                <= 0 => "#C62828",
                <= 3 => "#D97706",
                _ => "#126E63"
            };

            var isCard = subscription.PaymentMethod == SubscriptionPaymentMethods.CreditCard;
            var cardName = isCard
                && !string.IsNullOrWhiteSpace(subscription.CreditCardId)
                && cardNames.TryGetValue(subscription.CreditCardId, out var name)
                    ? name
                    : string.Empty;

            return new UpcomingCharge
            {
                SubscriptionId = subscription.SubscriptionId,
                ServiceName = subscription.ServiceName,
                PlanName = subscription.PlanName,
                Amount = subscription.Amount,
                Date = date.Date,
                DaysUntil = daysUntil,
                ColorHex = subscription.ColorHex,
                WhenText = whenText,
                DateText = date.ToString("dd/MMM"),
                StatusColor = statusColor,
                IsChargedToCard = isCard,
                PaymentSourceText = string.IsNullOrWhiteSpace(cardName)
                    ? GetPaymentMethodDisplayName(subscription.PaymentMethod)
                    : cardName
            };
        }

        private static void GetChargeStatus(bool isActive, int daysUntilCharge, DateTime nextCharge, out string text, out string color)
        {
            if (!isActive)
            {
                text = "Pausada · sin cobros";
                color = "#6E6E6E";
                return;
            }

            if (daysUntilCharge < 0)
            {
                text = "Cobro pendiente de registrar";
                color = "#C62828";
            }
            else if (daysUntilCharge == 0)
            {
                text = "Se cobra hoy";
                color = "#C62828";
            }
            else if (daysUntilCharge == 1)
            {
                text = "Se cobra mañana";
                color = "#D97706";
            }
            else if (daysUntilCharge <= 3)
            {
                text = $"Se cobra en {daysUntilCharge} días";
                color = "#D97706";
            }
            else
            {
                text = $"Se cobra en {daysUntilCharge} días ({nextCharge:dd/MMM})";
                color = "#126E63";
            }
        }

        // Mismas etiquetas que DetailViewModel para que el detalle del gasto y la
        // suscripcion que lo genero digan lo mismo.
        private static string GetPaymentMethodDisplayName(string paymentMethod) => paymentMethod switch
        {
            SubscriptionPaymentMethods.CreditCard => "Tarjeta de crédito",
            SubscriptionPaymentMethods.Debit => "Tarjeta de débito",
            SubscriptionPaymentMethods.Transfer => "Transferencia bancaria",
            _ => "Efectivo"
        };

        private async Task<decimal> GetTotalMonthlyCostAsync()
        {
            var today = DateTime.Today;
            var subscriptions = await GetAllSubscriptionsAsync();

            return subscriptions
                .Where(s => CountsTowardTotals(s, today))
                .Sum(s => GetMonthlyEquivalent(s.Amount, s.BillingCycle));
        }

        private async Task<Dictionary<string, string>> GetCardNamesAsync()
        {
            return await _db.CreditCards
                .Where(c => !c.IsDeleted)
                .ToDictionaryAsync(c => c.CreditCardId, c => c.CardName);
        }

        private async Task<Dictionary<string, string>> GetCategoryNamesAsync()
        {
            return await _db.Categories
                .ToDictionaryAsync(c => c.CategoryId, c => c.CategoryName);
        }

        private static int GetMonthsPerCycle(string billingCycle) => billingCycle switch
        {
            SubscriptionBillingCycles.Quarterly => 3,
            SubscriptionBillingCycles.Semiannual => 6,
            SubscriptionBillingCycles.Yearly => 12,
            _ => 1
        };

        // Mismos seis colores que las tarjetas, para que las dos secciones se vean
        // como parte de la misma app.
        private static void GetGradientForColor(string? hex, out string start, out string end)
        {
            var clean = (hex ?? "#7C3AED").ToUpperInvariant().Trim();
            switch (clean)
            {
                case "#126E63": // Esmeralda / Verde
                    start = "#126E63";
                    end = "#0B534A";
                    break;
                case "#1A73E8": // Azul Real
                    start = "#1A73E8";
                    end = "#0D47A1";
                    break;
                case "#D97706": // Oro / Ambar
                    start = "#D97706";
                    end = "#78350F";
                    break;
                case "#1F2937": // Grafito / Negro
                    start = "#374151";
                    end = "#111827";
                    break;
                case "#E11D48": // Rubi / Rojo
                    start = "#E11D48";
                    end = "#881337";
                    break;
                default: // Purpura / Violeta
                    start = "#7C3AED";
                    end = "#4C1D95";
                    break;
            }
        }

        // ---------- Empuje al servidor ----------

        private static SubscriptionDto ToDto(Subscription subscription) => new()
        {
            SubscriptionId = subscription.SubscriptionId,
            UserId = subscription.UserId,
            ServiceName = subscription.ServiceName,
            PlanName = subscription.PlanName,
            Amount = subscription.Amount,
            BillingCycle = subscription.BillingCycle,
            // Las fechas ancla son de calendario y viajan tal cual: convertirlas a UTC
            // correria el dia del cobro. Solo LastChargeRegisteredAt es un instante.
            FirstChargeDate = subscription.FirstChargeDate.Date,
            PaymentMethod = subscription.PaymentMethod,
            CreditCardId = subscription.CreditCardId,
            CategoryId = subscription.CategoryId,
            IsActive = subscription.IsActive,
            IsTrial = subscription.IsTrial,
            TrialEndDate = subscription.TrialEndDate?.Date,
            ColorHex = subscription.ColorHex,
            Notes = subscription.Notes,
            LastChargeRegisteredAt = subscription.LastChargeRegisteredAt.HasValue
                ? DateTimeUtils.SpendingToApiUtc(subscription.LastChargeRegisteredAt.Value)
                : null,
            IsSynced = false,
            IsDeleted = subscription.IsDeleted,
            DeletedAt = subscription.DeletedAt
        };

        private async Task<bool> SyncNewSubscription(Subscription subscription)
        {
            try
            {
                var token = Microsoft.Maui.Storage.Preferences.Get("token", string.Empty);
                if (string.IsNullOrWhiteSpace(token)) return false;

                var success = await _api.CreateSubscription(ToDto(subscription), token);
                if (success)
                {
                    var existing = await _db.Subscriptions
                        .FirstOrDefaultAsync(s => s.SubscriptionId == subscription.SubscriptionId);

                    if (existing != null)
                    {
                        existing.IsSynced = true;
                        await _db.SaveChangesAsync();
                    }
                }

                return success;
            }
            catch (Exception ex)
            {
                // Sin red la fila se queda con IsSynced = false y el SyncAllData del
                // arranque la recoge. No hay nada que reintentar aqui.
                Console.WriteLine($"Error syncing subscription: {ex.Message}");
                return false;
            }
        }

        private async Task<bool> SyncDeleteSubscription(string subscriptionId)
        {
            try
            {
                var token = Microsoft.Maui.Storage.Preferences.Get("token", string.Empty);
                if (string.IsNullOrWhiteSpace(token)) return false;

                var success = await _api.DeleteSubscription(subscriptionId, token);
                if (success)
                {
                    var existing = await _db.Subscriptions
                        .FirstOrDefaultAsync(s => s.SubscriptionId == subscriptionId);

                    if (existing != null)
                    {
                        existing.IsSynced = true;
                        await _db.SaveChangesAsync();
                    }
                }

                return success;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error deleting subscription sync: {ex.Message}");
                return false;
            }
        }
    }
}
