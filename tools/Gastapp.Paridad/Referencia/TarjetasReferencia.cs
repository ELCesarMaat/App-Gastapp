using Gastapp.Models;

namespace Gastapp.Paridad.Referencia;

/// <summary>
/// Copia de la logica de Gastapp/Services/CreditCardService/CreditCardService.cs y de
/// los calculos de tarjetas que viven en CreditCardsViewModel y SavesViewModel
/// (tomada el 2026-10-02).
///
/// Cambios respecto al original, y solo estos:
///  - Las consultas a EF Core son LINQ sobre una lista, con los MISMOS filtros.
///  - DateTime.Today y DateTime.Now se reciben como parametro.
///
/// No "arreglar" nada aqui aunque parezca raro: es la referencia contra la que se
/// compara la version de Kotlin.
/// </summary>
public static class TarjetasReferencia
{
    public static decimal GetPendingAmountForCard(string creditCardId, List<Spending> spendings)
    {
        var purchases = spendings
            .Where(s => s.CreditCardId == creditCardId && s.IsCreditCard && !s.IsDeleted)
            .Sum(s => s.Amount);

        var payments = spendings
            .Where(s => s.CreditCardId == creditCardId && !s.IsCreditCard && !s.IsDeleted)
            .Sum(s => s.Amount);

        return Math.Max(0, purchases - payments);
    }

    public static (DateTime CutOffDate, DateTime PaymentDueDate) CalculateCycleDates(int cutOffDay, int paymentDay, DateTime referenceDate)
    {
        var today = referenceDate.Date;
        var cutOffDate = NextOccurrenceOfDay(today, cutOffDay);
        var paymentDueDate = NextOccurrenceOfDay(today, paymentDay);
        return (cutOffDate, paymentDueDate);
    }

    public static (DateTime CutOffDate, DateTime PaymentDueDate) CalculateCycleDatesAsync(CreditCard card, List<Spending> spendings, DateTime referenceDate)
    {
        var today = referenceDate.Date;
        var (cutOffDate, paymentDueDate) = CalculateCycleDates(card.CutOffDay, card.PaymentDay, today);

        var statementCutOff = PreviousOccurrenceOfDay(paymentDueDate.AddDays(-1), card.CutOffDay);

        if (statementCutOff > today)
            return (cutOffDate, paymentDueDate);

        if (IsStatementSettled(card.CreditCardId, spendings, statementCutOff))
            paymentDueDate = NextOccurrenceOfDay(paymentDueDate.AddDays(1), card.PaymentDay);

        return (cutOffDate, paymentDueDate);
    }

    public static bool IsStatementSettled(string creditCardId, List<Spending> spendings, DateTime statementCutOff)
    {
        var cutOffLimit = statementCutOff.Date.AddDays(1);

        var billed = spendings
            .Where(s => s.CreditCardId == creditCardId && s.IsCreditCard && !s.IsDeleted && s.Date < cutOffLimit)
            .Sum(s => s.Amount);

        var paid = spendings
            .Where(s => s.CreditCardId == creditCardId && !s.IsCreditCard && !s.IsDeleted)
            .Sum(s => s.Amount);

        if (billed <= 0m)
            return GetPendingAmountForCard(creditCardId, spendings) <= 0.01m;

        return billed - paid <= 0.01m;
    }

    public static DateTime GetLastCutOffDate(int cutOffDay, DateTime referenceDate) =>
        PreviousOccurrenceOfDay(referenceDate.Date, cutOffDay);

    public static DateTime PreviousOccurrenceOfDay(DateTime reference, int day)
    {
        reference = reference.Date;
        var maxDaysThisMonth = DateTime.DaysInMonth(reference.Year, reference.Month);
        var candidate = new DateTime(reference.Year, reference.Month, Math.Min(day, maxDaysThisMonth));

        if (candidate > reference)
        {
            var previousMonth = reference.AddMonths(-1);
            var maxDaysPreviousMonth = DateTime.DaysInMonth(previousMonth.Year, previousMonth.Month);
            candidate = new DateTime(previousMonth.Year, previousMonth.Month, Math.Min(day, maxDaysPreviousMonth));
        }

        return candidate;
    }

    public static DateTime NextOccurrenceOfDay(DateTime today, int day)
    {
        var maxDaysThisMonth = DateTime.DaysInMonth(today.Year, today.Month);
        var candidate = new DateTime(today.Year, today.Month, Math.Min(day, maxDaysThisMonth));

        if (candidate < today)
        {
            var nextMonth = today.AddMonths(1);
            var maxDaysNextMonth = DateTime.DaysInMonth(nextMonth.Year, nextMonth.Month);
            candidate = new DateTime(nextMonth.Year, nextMonth.Month, Math.Min(day, maxDaysNextMonth));
        }

        return candidate;
    }

    public static List<Spending> GetActiveMsiSpendings(string creditCardId, List<Spending> spendings) =>
        spendings
            .Where(s => s.CreditCardId == creditCardId && s.IsCreditCard && s.IsMsi && !s.IsDeleted)
            .OrderByDescending(s => s.Date)
            .ToList();

    public static List<Spending> GetCurrentCycleSpendings(CreditCard card, List<Spending> spendings, DateTime today)
    {
        var (nextCutOff, _) = CalculateCycleDates(card.CutOffDay, card.PaymentDay, today);
        var cycleStartDate = nextCutOff.AddMonths(-1);

        return spendings
            .Where(s => s.CreditCardId == card.CreditCardId && s.IsCreditCard && !s.IsDeleted && s.Date >= cycleStartDate && s.Date <= nextCutOff.AddDays(1))
            .OrderByDescending(s => s.Date)
            .ToList();
    }

    /// <summary>GetCardSummaryAsync sin los textos con fecha (dependen de la cultura).</summary>
    public static CreditCardSummary GetCardSummary(CreditCard card, List<Spending> spendings, DateTime today)
    {
        today = today.Date;
        var totalDebt = GetPendingAmountForCard(card.CreditCardId, spendings);
        var creditLimit = card.CreditLimit;
        var availableCredit = creditLimit > 0 ? Math.Max(0, creditLimit - totalDebt) : 0;
        var usagePercentage = creditLimit > 0 ? (double)(totalDebt / creditLimit) * 100 : 0;

        var (nextCutOff, nextPayment) = CalculateCycleDatesAsync(card, spendings, today);
        var daysUntilCutOff = (nextCutOff.Date - today).Days;
        var daysUntilPayment = (nextPayment.Date - today).Days;

        string paymentStatusColor;
        if (daysUntilPayment < 0) paymentStatusColor = "#C62828";
        else if (daysUntilPayment == 0) paymentStatusColor = "#C62828";
        else if (daysUntilPayment <= 3) paymentStatusColor = "#D97706";
        else paymentStatusColor = "#126E63";

        string usageStatusColor;
        if (usagePercentage >= 80) usageStatusColor = "#C62828";
        else if (usagePercentage >= 50) usageStatusColor = "#D97706";
        else usageStatusColor = "#126E63";

        var currentCycleSpendings = GetCurrentCycleSpendings(card, spendings, today);
        var activeMsiSpendings = GetActiveMsiSpendings(card.CreditCardId, spendings);

        decimal futureMsiDebt = 0;
        foreach (var msi in activeMsiSpendings)
        {
            var remainingMonths = Math.Max(0, msi.TotalInstallments - msi.CurrentInstallment);
            var monthlyAmount = msi.InstallmentMonthlyAmount > 0 ? msi.InstallmentMonthlyAmount : (msi.Amount / Math.Max(1, msi.TotalInstallments));
            futureMsiDebt += remainingMonths * monthlyAmount;
        }

        return new CreditCardSummary
        {
            Card = card,
            CreditLimit = creditLimit,
            TotalDebt = totalDebt,
            AvailableCredit = availableCredit,
            UsagePercentage = Math.Min(usagePercentage, 100),
            CurrentCycleAmount = currentCycleSpendings.Sum(s => s.Amount),
            TotalMsiRemainingDebt = futureMsiDebt,
            ActiveMsiCount = activeMsiSpendings.Count,
            NextCutOffDate = nextCutOff,
            NextPaymentDueDate = nextPayment,
            DaysUntilCutOff = daysUntilCutOff,
            DaysUntilPayment = daysUntilPayment,
            PaymentStatusColor = paymentStatusColor,
            UsageStatusColor = usageStatusColor,
        };
    }

    /// <summary>
    /// AdjustCardBalanceAsync: solo la decision de que movimiento se crea.
    /// Devuelve null cuando no hay que crear nada.
    /// </summary>
    public static (bool IsCreditCard, decimal Amount, string PaymentMethod)? AdjustCardBalance(decimal currentBalance, decimal newBalance)
    {
        var diff = newBalance - currentBalance;
        if (Math.Abs(diff) < 0.001m)
            return null;

        return diff > 0
            ? (true, diff, "CreditCard")
            : (false, Math.Abs(diff), "Transfer");
    }

    /// <summary>PayCard (CreditCardsViewModel): monto que se propone al abrir el pago.</summary>
    public static decimal SuggestedPayment(CreditCardSummary summary) =>
        summary.CurrentCycleAmount > 0 ? summary.CurrentCycleAmount : summary.TotalDebt;

    /// <summary>Color de "Tarjetas por pagar" en Ahorros (SavesViewModel). Ojo: ahi el umbral es 5, no 3.</summary>
    public static string SavesPendingColor(int daysUntil)
    {
        if (daysUntil < 0) return "#C62828";
        if (daysUntil <= 5) return "#D97706";
        return "#126E63";
    }

    /// <summary>
    /// Alta de una tarjeta que ya venia en uso (SaveCard en CreditCardsViewModel): los
    /// gastos que se registran para que la deuda cuadre con lo que reporto el usuario.
    /// </summary>
    public static List<(string Title, decimal Amount, DateTime Date, bool IsMsi, decimal Monthly, int Current, int Total)> InUseCardMovements(
        string cardName,
        int cutOffDay,
        decimal totalUsed,
        decimal currentCycleDebt,
        bool saldoYaCortado,
        List<(string Title, decimal MonthlyAmount, int PaidInstallments, int TotalInstallments)> msiPurchases,
        DateTime now)
    {
        var result = new List<(string, decimal, DateTime, bool, decimal, int, int)>();

        // CollectMsiPurchasesForSave + PendingMsiPurchase.RemainingAmount
        decimal Remaining((string Title, decimal MonthlyAmount, int PaidInstallments, int TotalInstallments) p) =>
            p.MonthlyAmount * Math.Max(0, p.TotalInstallments - p.PaidInstallments);

        var msiOutstanding = msiPurchases.Sum(Remaining);
        var cashDebt = Math.Max(0, totalUsed - msiOutstanding);
        var fechaDelSaldoYaCortado = GetLastCutOffDate(cutOffDay, now);

        if (cashDebt > 0)
        {
            var cycleAmount = Math.Min(currentCycleDebt, cashDebt);

            if (cycleAmount > 0 && cycleAmount < cashDebt)
            {
                result.Add(($"Saldo corte actual - {cardName}", cycleAmount, fechaDelSaldoYaCortado, false, 0m, 1, 1));
                result.Add(($"Saldo acumulado previo - {cardName}", cashDebt - cycleAmount, now.AddMonths(-2), false, 0m, 1, 1));
            }
            else
            {
                result.Add(($"Saldo de contado - {cardName}", cashDebt, saldoYaCortado ? fechaDelSaldoYaCortado : now, false, 0m, 1, 1));
            }
        }

        foreach (var msi in msiPurchases)
        {
            result.Add((
                string.IsNullOrWhiteSpace(msi.Title) ? $"Compra MSI previa - {cardName}" : msi.Title,
                Remaining(msi),
                now,
                true,
                msi.MonthlyAmount,
                msi.PaidInstallments,
                msi.TotalInstallments));
        }

        return result;
    }
}
