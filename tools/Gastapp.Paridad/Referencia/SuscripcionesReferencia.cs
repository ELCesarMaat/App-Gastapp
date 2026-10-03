using Gastapp.Models;

namespace Gastapp.Paridad.Referencia;

/// <summary>
/// Copia de la logica de Gastapp/Services/SubscriptionService/SubscriptionService.cs
/// (tomada el 2026-10-02).
///
/// Cambios respecto al original, y solo estos:
///  - DateTime.Today se recibe como parametro.
///  - Las listas y diccionarios llegan ya cargados en vez de leerse de EF Core.
///  - Los textos que llevan una fecha formateada (dependen de la cultura) no se copian.
/// </summary>
public static class SuscripcionesReferencia
{
    public static DateTime CalculateNextChargeDate(DateTime firstChargeDate, string billingCycle, DateTime referenceDate)
    {
        var anchor = firstChargeDate.Date;
        var today = referenceDate.Date;

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

    public static DateTime CalculatePreviousChargeDate(DateTime firstChargeDate, string billingCycle, DateTime referenceDate)
    {
        var anchor = firstChargeDate.Date;
        var today = referenceDate.Date;

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

    public static bool CountsTowardTotals(Subscription subscription, DateTime referenceDate)
    {
        if (!subscription.IsActive)
            return false;

        var trialEnd = subscription.TrialEndDate?.Date;
        var enPrueba = subscription.IsTrial && trialEnd.HasValue && trialEnd.Value >= referenceDate.Date;

        return !enPrueba;
    }

    public static decimal GetMonthlyEquivalent(decimal amount, string billingCycle)
    {
        if (amount <= 0) return 0m;

        return billingCycle switch
        {
            SubscriptionBillingCycles.Weekly => amount * 52m / 12m,
            SubscriptionBillingCycles.Quarterly => amount / 3m,
            SubscriptionBillingCycles.Semiannual => amount / 6m,
            SubscriptionBillingCycles.Yearly => amount / 12m,
            _ => amount
        };
    }

    public static string GetBillingCycleDisplayName(string billingCycle) => billingCycle switch
    {
        SubscriptionBillingCycles.Weekly => "Semanal",
        SubscriptionBillingCycles.Quarterly => "Trimestral",
        SubscriptionBillingCycles.Semiannual => "Semestral",
        SubscriptionBillingCycles.Yearly => "Anual",
        _ => "Mensual"
    };

    public static string GetBillingCycleSuffix(string billingCycle) => billingCycle switch
    {
        SubscriptionBillingCycles.Weekly => "a la semana",
        SubscriptionBillingCycles.Quarterly => "cada 3 meses",
        SubscriptionBillingCycles.Semiannual => "cada 6 meses",
        SubscriptionBillingCycles.Yearly => "al año",
        _ => "al mes"
    };

    public static string GetPaymentMethodDisplayName(string paymentMethod) => paymentMethod switch
    {
        SubscriptionPaymentMethods.CreditCard => "Tarjeta de crédito",
        SubscriptionPaymentMethods.Debit => "Tarjeta de débito",
        SubscriptionPaymentMethods.Transfer => "Transferencia bancaria",
        _ => "Efectivo"
    };

    /// <summary>Lo que necesita la comparacion de BuildSummary.</summary>
    public sealed record ResumenCalculado(
        decimal MonthlyEquivalent,
        decimal YearlyEquivalent,
        DateTime NextChargeDate,
        int DaysUntilCharge,
        string ChargeStatusText,
        string ChargeStatusColor,
        bool IsTrialActive,
        int DaysUntilTrialEnds,
        bool CountsTowardTotals,
        double ShareOfMonthlyRatio,
        string ShareOfMonthlyText,
        bool IsCurrentCycleCharged,
        string StatusBadgeText,
        string StatusBadgeColor,
        List<DateTime> NextCharges);

    public static ResumenCalculado BuildSummary(Subscription subscription, decimal monthlyTotal, DateTime today)
    {
        today = today.Date;

        var monthlyEquivalent = GetMonthlyEquivalent(subscription.Amount, subscription.BillingCycle);

        var trialEnd = subscription.TrialEndDate?.Date;
        var isTrialActive = subscription.IsTrial && trialEnd.HasValue && trialEnd.Value >= today;
        var daysUntilTrialEnds = isTrialActive ? (trialEnd!.Value - today).Days : 0;

        var reference = isTrialActive ? trialEnd!.Value : today;
        var nextCharge = CalculateNextChargeDate(subscription.FirstChargeDate, subscription.BillingCycle, reference);
        var daysUntilCharge = (nextCharge - today).Days;

        GetChargeStatus(subscription.IsActive, daysUntilCharge, out var chargeStatusText, out var chargeStatusColor);

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

        // BuildNextCharges(count: 3)
        var nextCharges = new List<DateTime>();
        var next = nextCharge;
        for (var i = 0; i < 3; i++)
        {
            nextCharges.Add(next.Date);
            next = CalculateNextChargeDate(subscription.FirstChargeDate, subscription.BillingCycle, next.AddDays(1));
        }

        return new ResumenCalculado(
            monthlyEquivalent,
            monthlyEquivalent * 12m,
            nextCharge,
            daysUntilCharge,
            chargeStatusText,
            chargeStatusColor,
            isTrialActive,
            daysUntilTrialEnds,
            cuentaEnTotales,
            share,
            shareText,
            cicloYaCobrado,
            statusBadgeText,
            statusBadgeColor,
            nextCharges);
    }

    /// <summary>GetChargeStatus sin el texto que lleva la fecha (dd/MMM).</summary>
    public static void GetChargeStatus(bool isActive, int daysUntilCharge, out string text, out string color)
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
            text = $"Se cobra en {daysUntilCharge} días (fecha)";
            color = "#126E63";
        }
    }

    public sealed record CobroCalculado(int Indice, DateTime Date, int DaysUntil, string WhenText, string StatusColor);

    /// <summary>GetUpcomingChargesAsync. El indice es la posicion de la suscripcion en la lista de entrada.</summary>
    public static List<CobroCalculado> GetUpcomingCharges(List<Subscription> subscriptions, DateTime today, int daysAhead = 45)
    {
        today = today.Date;
        var limit = today.AddDays(Math.Max(1, daysAhead));

        var charges = new List<(Subscription Sub, int Indice, DateTime Date)>();

        // GetAllSubscriptionsAsync filtra los borrados y ordena por activas y nombre.
        var ordered = subscriptions
            .Select((s, i) => (Sub: s, Indice: i))
            .Where(x => !x.Sub.IsDeleted)
            .OrderByDescending(x => x.Sub.IsActive)
            .ThenBy(x => x.Sub.ServiceName)
            .ToList();

        foreach (var (subscription, indice) in ordered.Where(x => x.Sub.IsActive))
        {
            var reference = subscription.IsTrial && subscription.TrialEndDate.HasValue && subscription.TrialEndDate.Value.Date > today
                ? subscription.TrialEndDate.Value.Date
                : today;

            var next = CalculateNextChargeDate(subscription.FirstChargeDate, subscription.BillingCycle, reference);

            var guard = 0;
            while (next <= limit && guard++ < 60)
            {
                charges.Add((subscription, indice, next.Date));
                next = CalculateNextChargeDate(subscription.FirstChargeDate, subscription.BillingCycle, next.AddDays(1));
            }
        }

        return charges
            .OrderBy(c => c.Date)
            .ThenBy(c => c.Sub.ServiceName)
            .Select(c =>
            {
                var daysUntil = (c.Date - today).Days;
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
                return new CobroCalculado(c.Indice, c.Date, daysUntil, whenText, statusColor);
            })
            .ToList();
    }

    private static int GetMonthsPerCycle(string billingCycle) => billingCycle switch
    {
        SubscriptionBillingCycles.Quarterly => 3,
        SubscriptionBillingCycles.Semiannual => 6,
        SubscriptionBillingCycles.Yearly => 12,
        _ => 1
    };
}
