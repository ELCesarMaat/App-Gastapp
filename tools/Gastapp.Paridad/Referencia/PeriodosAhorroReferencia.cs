namespace Gastapp.Paridad.Referencia;

/// <summary>
/// Copia de los periodos de pago (GetPeriodBounds y sus ayudantes en
/// Gastapp/Services/SpendingService/SpendingService.cs) y de los calculos de ahorro,
/// MSI y perfil que viven en SavesViewModel, NewSpendingViewModel y ProfileViewModel
/// (tomada el 2026-10-02).
///
/// Unico cambio: GetPeriodBounds recibe los campos del usuario sueltos en vez de User.
/// </summary>
public static class PeriodosAhorroReferencia
{
    // ------------------------------------------------------------- Periodos

    /// <summary>
    /// Tipo de ingreso: 1 semanal, 2 quincenal, 3 mensual. En el semanal, el dia de pago
    /// es FirstPayDay con la numeracion de .NET (0 = domingo ... 6 = sabado).
    /// </summary>
    public static (DateTime Start, DateTime End) GetPeriodBounds(int incomeTypeId, int? firstPayDay, int? secondPayDay, DateTime referenceDate, int periodOffset)
    {
        var firstDay = firstPayDay ?? 1;
        var secondDay = secondPayDay ?? 1;

        DateTime start;
        switch (incomeTypeId)
        {
            case 1:
                start = GetLastWeeklyPayDate(referenceDate, firstDay);
                break;
            case 2:
                start = GetLastBiweeklyPayDate(referenceDate, firstDay, secondDay);
                break;
            case 3:
            default:
                start = GetLastMonthlyPayDate(referenceDate, firstDay);
                break;
        }

        var end = referenceDate.Date;

        for (var i = 0; i < periodOffset; i++)
        {
            end = start.AddDays(-1);
            switch (incomeTypeId)
            {
                case 1:
                    start = start.AddDays(-7);
                    break;
                case 2:
                    start = GetLastBiweeklyPayDate(end, firstDay, secondDay);
                    break;
                case 3:
                default:
                    start = GetLastMonthlyPayDate(end, firstDay);
                    break;
            }
        }

        return (start.Date, end.Date);
    }

    private static DateTime GetLastWeeklyPayDate(DateTime referenceDate, int payDay)
    {
        var safePayDay = Math.Clamp(payDay, 0, 6);
        var referenceIndex = (int)referenceDate.DayOfWeek;
        var startOffset = referenceIndex >= safePayDay
            ? referenceIndex - safePayDay
            : 7 - (safePayDay - referenceIndex);
        return referenceDate.Date.AddDays(-startOffset);
    }

    private static DateTime GetLastMonthlyPayDate(DateTime referenceDate, int payDay)
    {
        var safeCurrentDay = Math.Clamp(payDay, 1, DateTime.DaysInMonth(referenceDate.Year, referenceDate.Month));
        if (referenceDate.Day >= safeCurrentDay)
            return new DateTime(referenceDate.Year, referenceDate.Month, safeCurrentDay);

        var previousMonth = referenceDate.AddMonths(-1);
        var safePreviousDay = Math.Clamp(payDay, 1, DateTime.DaysInMonth(previousMonth.Year, previousMonth.Month));
        return new DateTime(previousMonth.Year, previousMonth.Month, safePreviousDay);
    }

    private static DateTime GetLastBiweeklyPayDate(DateTime referenceDate, int firstPayDay, int secondPayDay)
    {
        var currentYear = referenceDate.Year;
        var currentMonth = referenceDate.Month;

        var safeFirstDay = Math.Clamp(firstPayDay, 1, DateTime.DaysInMonth(currentYear, currentMonth));
        var safeSecondDay = Math.Clamp(secondPayDay, 1, DateTime.DaysInMonth(currentYear, currentMonth));

        var firstDate = new DateTime(currentYear, currentMonth, safeFirstDay);
        var secondDate = new DateTime(currentYear, currentMonth, safeSecondDay);

        if (firstDate > secondDate)
        {
            var temp = firstDate;
            firstDate = secondDate;
            secondDate = temp;
        }

        if (referenceDate.Date >= secondDate.Date)
            return secondDate.Date;

        if (referenceDate.Date >= firstDate.Date)
            return firstDate.Date;

        var previousMonth = referenceDate.AddMonths(-1);
        var prevFirstDay = Math.Clamp(firstPayDay, 1, DateTime.DaysInMonth(previousMonth.Year, previousMonth.Month));
        var prevSecondDay = Math.Clamp(secondPayDay, 1, DateTime.DaysInMonth(previousMonth.Year, previousMonth.Month));

        var prevFirstDate = new DateTime(previousMonth.Year, previousMonth.Month, prevFirstDay);
        var prevSecondDate = new DateTime(previousMonth.Year, previousMonth.Month, prevSecondDay);

        return prevFirstDate > prevSecondDate ? prevFirstDate.Date : prevSecondDate.Date;
    }

    // --------------------------------------------------------------- Ahorro

    public sealed record SaludCalculada(decimal MaxTotalSpending, decimal Percent, decimal ProgressPercent, string HealthText, string HealthColor, decimal RemainingBudget, decimal DailyAverage, decimal SavedOrExceeded, bool Exceeded);

    /// <summary>SavesViewModel.GetData + CheckHealth + propiedades derivadas.</summary>
    public static SaludCalculada Salud(decimal salary, decimal percentSave, decimal totalSpending, int periodDayCount)
    {
        var maxTotalSpending = salary * (100 - percentSave) / 100;

        var percent = maxTotalSpending > 0
            ? Math.Round(totalSpending / maxTotalSpending * 100, 2)
            : 0;
        var progressPercent = Math.Min(percent, 100);

        string healthText;
        string healthColor;
        switch (percent)
        {
            case >= 100:
                healthText = "Critica";
                healthColor = "#C62828";
                break;
            case >= 90:
                healthText = "Ajustada";
                healthColor = "#D97706";
                break;
            case >= 80:
                healthText = "Estable";
                healthColor = "#B68A12";
                break;
            default:
                healthText = "Saludable";
                healthColor = "#E7F7F0";
                break;
        }

        var dayCount = periodDayCount > 0 ? periodDayCount : 1;
        var dailyAverage = dayCount > 0 ? Math.Round(totalSpending / dayCount, 2) : 0;

        var savingsBalance = salary - totalSpending;
        var exceeded = savingsBalance < 0;

        return new SaludCalculada(
            maxTotalSpending,
            percent,
            progressPercent,
            healthText,
            healthColor,
            maxTotalSpending - totalSpending,
            dailyAverage,
            Math.Abs(savingsBalance),
            exceeded);
    }

    /// <summary>Porcentaje de cada categoria en SavesViewModel.GetData.</summary>
    public static List<decimal> PorcentajesCategorias(List<decimal> amounts)
    {
        var total = amounts.Sum();
        return amounts
            .Select(a => total > 0 ? Math.Round(a / total * 100, 1) : 0)
            .ToList();
    }

    // ---------------------------------------------------------- MSI y perfil

    /// <summary>NewSpendingViewModel: mensualidad de una compra a MSI.</summary>
    public static decimal MensualidadMsi(decimal amount, int selectedInstallments)
    {
        var totalInstallments = Math.Max(1, selectedInstallments);
        return Math.Round(amount / totalInstallments, 2);
    }

    /// <summary>ProfileViewModel: porcentaje de ahorro a partir de un monto fijo.</summary>
    public static decimal PorcentajeDesdeMonto(decimal amount, decimal salary) =>
        salary > 0 ? Math.Round((amount / salary) * 100m, 4) : 0m;

    /// <summary>ProfileViewModel: monto de ahorro a partir de un porcentaje.</summary>
    public static decimal MontoDesdePorcentaje(decimal salary, decimal percent) =>
        salary * (percent / 100m);
}
