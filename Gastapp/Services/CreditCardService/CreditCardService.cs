using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Gastapp.Data;
using Gastapp.Models;
using Gastapp.Services.ApiService;
using Microsoft.EntityFrameworkCore;

namespace Gastapp.Services
{
    public class CreditCardService : ICreditCardService
    {
        private readonly GastappDbContext _db;
        private readonly IApiService _api;

        public CreditCardService(GastappDbContext db, IApiService api)
        {
            _db = db;
            _api = api;
        }

        public async Task<List<CreditCard>> GetAllCreditCardsAsync()
        {
            return await _db.CreditCards
                .Where(cc => !cc.IsDeleted)
                .OrderBy(cc => cc.CardName)
                .ToListAsync();
        }

        public async Task<CreditCard?> GetCreditCardByIdAsync(string id)
        {
            return await _db.CreditCards
                .FirstOrDefaultAsync(cc => cc.CreditCardId == id && !cc.IsDeleted);
        }

        public async Task<CreditCard> CreateCreditCardAsync(CreditCard card)
        {
            card.IsSynced = false;
            card.IsDeleted = false;
            await _db.CreditCards.AddAsync(card);
            await _db.SaveChangesAsync();

            _ = SyncNewCreditCard(card);

            return card;
        }

        public async Task<bool> UpdateCreditCardAsync(CreditCard card)
        {
            var existing = await _db.CreditCards.FirstOrDefaultAsync(cc => cc.CreditCardId == card.CreditCardId);
            if (existing == null) return false;

            existing.CardName = card.CardName;
            existing.BankName = card.BankName;
            existing.LastFourDigits = card.LastFourDigits;
            existing.CutOffDay = card.CutOffDay;
            existing.PaymentDay = card.PaymentDay;
            existing.CreditLimit = card.CreditLimit;
            existing.ColorHex = card.ColorHex;
            existing.IsSynced = false;

            await _db.SaveChangesAsync();

            _ = SyncNewCreditCard(existing);

            return true;
        }

        public async Task<bool> DeleteCreditCardAsync(string id)
        {
            var existing = await _db.CreditCards.FirstOrDefaultAsync(cc => cc.CreditCardId == id);
            if (existing == null) return false;

            existing.IsDeleted = true;
            existing.DeletedAt = DateTime.UtcNow;
            existing.IsSynced = false;

            await _db.SaveChangesAsync();

            _ = SyncDeleteCreditCard(id);

            return true;
        }

        public async Task<decimal> GetPendingAmountForCardAsync(string creditCardId)
        {
            // Pending amount is: Total Purchases (IsCreditCard = true) minus Total Payments (IsCreditCard = false)
            var purchases = await _db.Spending
                .Where(s => s.CreditCardId == creditCardId && s.IsCreditCard && !s.IsDeleted)
                .SumAsync(s => s.Amount);

            var payments = await _db.Spending
                .Where(s => s.CreditCardId == creditCardId && !s.IsCreditCard && !s.IsDeleted)
                .SumAsync(s => s.Amount);

            return Math.Max(0, purchases - payments);
        }

        public async Task<List<Spending>> GetPendingSpendingsForCardAsync(string creditCardId)
        {
            return await _db.Spending
                .Include(s => s.Category)
                .Where(s => s.CreditCardId == creditCardId && s.IsCreditCard && !s.IsDeleted)
                .OrderByDescending(s => s.Date)
                .ToListAsync();
        }

        public (DateTime CutOffDate, DateTime PaymentDueDate) CalculateCycleDates(int cutOffDay, int paymentDay, DateTime referenceDate)
        {
            var today = referenceDate.Date;

            // El corte y el pago se calculan cada uno por separado, como "la proxima vez
            // que ocurra ese dia del mes". Antes el pago se calculaba a partir del PROXIMO
            // corte, lo cual salta un ciclo entero cuando ya pasamos el dia de corte de este
            // mes pero todavia no llega el dia de pago (ej. hoy 30, corte dia 25, pago dia 15:
            // el pago del 15 de septiembre es el del corte del 25 de agosto que ya paso, no
            // el del corte del 25 de septiembre que todavia ni se genera).
            var cutOffDate = NextOccurrenceOfDay(today, cutOffDay);
            var paymentDueDate = NextOccurrenceOfDay(today, paymentDay);

            return (cutOffDate, paymentDueDate);
        }

        public async Task<(DateTime CutOffDate, DateTime PaymentDueDate)> CalculateCycleDatesAsync(CreditCard card, DateTime referenceDate)
        {
            var today = referenceDate.Date;
            var (cutOffDate, paymentDueDate) = CalculateCycleDates(card.CutOffDay, card.PaymentDay, today);

            // La fecha limite liquida el estado de cuenta del ultimo corte que ocurrio ANTES
            // de esa fecha de pago. Si ese corte ya quedo cubierto con los pagos registrados,
            // la fecha limite vigente pasa a ser la del ciclo siguiente.
            var statementCutOff = PreviousOccurrenceOfDay(paymentDueDate.AddDays(-1), card.CutOffDay);

            // Si el corte todavia no llega, el estado de cuenta ni siquiera se ha generado:
            // no hay nada que dar por pagado.
            if (statementCutOff > today)
                return (cutOffDate, paymentDueDate);

            if (await IsStatementSettledAsync(card.CreditCardId, statementCutOff))
                paymentDueDate = NextOccurrenceOfDay(paymentDueDate.AddDays(1), card.PaymentDay);

            return (cutOffDate, paymentDueDate);
        }

        // Un corte se considera cubierto cuando lo facturado hasta esa fecha (compras con
        // IsCreditCard = true) es menor o igual a todo lo abonado a la tarjeta (pagos con
        // IsCreditCard = false). Es un acumulado: los pagos previos ya descontaron las
        // compras previas, asi que la resta refleja lo que queda del corte vigente.
        private async Task<bool> IsStatementSettledAsync(string creditCardId, DateTime statementCutOff)
        {
            var cutOffLimit = statementCutOff.Date.AddDays(1);

            var billed = await _db.Spending
                .Where(s => s.CreditCardId == creditCardId && s.IsCreditCard && !s.IsDeleted && s.Date < cutOffLimit)
                .SumAsync(s => s.Amount);

            var paid = await _db.Spending
                .Where(s => s.CreditCardId == creditCardId && !s.IsCreditCard && !s.IsDeleted)
                .SumAsync(s => s.Amount);

            // Tolerancia de un centavo para no arrastrar redondeos.
            return billed - paid <= 0.01m;
        }

        private static DateTime PreviousOccurrenceOfDay(DateTime reference, int day)
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

        private static DateTime NextOccurrenceOfDay(DateTime today, int day)
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

        public async Task<List<Spending>> GetActiveMsiSpendingsAsync(string creditCardId)
        {
            return await _db.Spending
                .Include(s => s.Category)
                .Where(s => s.CreditCardId == creditCardId && s.IsCreditCard && s.IsMsi && !s.IsDeleted)
                .OrderByDescending(s => s.Date)
                .ToListAsync();
        }

        public async Task<List<Spending>> GetCurrentCycleSpendingsAsync(string creditCardId)
        {
            var card = await _db.CreditCards.FirstOrDefaultAsync(cc => cc.CreditCardId == creditCardId);
            if (card == null) return [];

            var (nextCutOff, _) = CalculateCycleDates(card.CutOffDay, card.PaymentDay, DateTime.Today);
            var cycleStartDate = nextCutOff.AddMonths(-1);

            return await _db.Spending
                .Include(s => s.Category)
                .Where(s => s.CreditCardId == creditCardId && s.IsCreditCard && !s.IsDeleted && s.Date >= cycleStartDate && s.Date <= nextCutOff.AddDays(1))
                .OrderByDescending(s => s.Date)
                .ToListAsync();
        }

        public async Task<CreditCardSummary> GetCardSummaryAsync(string creditCardId)
        {
            var card = await _db.CreditCards.FirstOrDefaultAsync(cc => cc.CreditCardId == creditCardId && !cc.IsDeleted);
            if (card == null) return new CreditCardSummary();

            var totalDebt = await GetPendingAmountForCardAsync(creditCardId);
            var creditLimit = card.CreditLimit;
            var availableCredit = creditLimit > 0 ? Math.Max(0, creditLimit - totalDebt) : 0;
            var usagePercentage = creditLimit > 0 ? (double)(totalDebt / creditLimit) * 100 : 0;

            var (nextCutOff, nextPayment) = await CalculateCycleDatesAsync(card, DateTime.Today);
            var today = DateTime.Today;
            var daysUntilCutOff = (nextCutOff.Date - today).Days;
            var daysUntilPayment = (nextPayment.Date - today).Days;

            string paymentStatusText;
            string paymentStatusColor;

            if (daysUntilPayment < 0)
            {
                paymentStatusText = "Vencido";
                paymentStatusColor = "#C62828";
            }
            else if (daysUntilPayment == 0)
            {
                paymentStatusText = "Vence hoy";
                paymentStatusColor = "#C62828";
            }
            else if (daysUntilPayment <= 3)
            {
                paymentStatusText = $"Vence en {daysUntilPayment} día{(daysUntilPayment == 1 ? "" : "s")}";
                paymentStatusColor = "#D97706";
            }
            else
            {
                paymentStatusText = $"Vence en {daysUntilPayment} días ({nextPayment:dd/MMM})";
                paymentStatusColor = "#126E63";
            }

            string cutOffStatusText = daysUntilCutOff == 0
                ? "Corta hoy"
                : $"Corte en {daysUntilCutOff} día{(daysUntilCutOff == 1 ? "" : "s")} ({nextCutOff:dd/MMM})";

            string usageStatusColor;
            if (usagePercentage >= 80) usageStatusColor = "#C62828";
            else if (usagePercentage >= 50) usageStatusColor = "#D97706";
            else usageStatusColor = "#126E63";

            var currentCycleSpendings = await GetCurrentCycleSpendingsAsync(creditCardId);
            var activeMsiSpendings = await GetActiveMsiSpendingsAsync(creditCardId);

            decimal futureMsiDebt = 0;
            foreach (var msi in activeMsiSpendings)
            {
                var remainingMonths = Math.Max(0, msi.TotalInstallments - msi.CurrentInstallment);
                var monthlyAmount = msi.InstallmentMonthlyAmount > 0 ? msi.InstallmentMonthlyAmount : (msi.Amount / Math.Max(1, msi.TotalInstallments));
                futureMsiDebt += remainingMonths * monthlyAmount;
            }

            GetGradientForColor(card.ColorHex, out var gradStart, out var gradEnd);

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
                CutOffStatusText = cutOffStatusText,
                PaymentStatusText = paymentStatusText,
                PaymentStatusColor = paymentStatusColor,
                UsageStatusColor = usageStatusColor,
                CardBackgroundGradientStart = gradStart,
                CardBackgroundGradientEnd = gradEnd,
                CurrentCycleSpendings = currentCycleSpendings,
                ActiveMsiSpendings = activeMsiSpendings
            };
        }

        public async Task<List<CreditCardSummary>> GetAllCardSummariesAsync()
        {
            var cards = await GetAllCreditCardsAsync();
            var list = new List<CreditCardSummary>();
            foreach (var card in cards)
            {
                list.Add(await GetCardSummaryAsync(card.CreditCardId));
            }
            return list;
        }

        public async Task<bool> AdjustCardBalanceAsync(string creditCardId, decimal newBalance)
        {
            try
            {
                var card = await _db.CreditCards.FirstOrDefaultAsync(cc => cc.CreditCardId == creditCardId && !cc.IsDeleted);
                if (card == null) return false;

                var currentBalance = await GetPendingAmountForCardAsync(creditCardId);
                var diff = newBalance - currentBalance;

                if (Math.Abs(diff) < 0.001m)
                    return true;

                var defaultCategory = await _db.Categories
                    .FirstOrDefaultAsync(c => c.UserId == card.UserId && c.IsDefaultCategory)
                    ?? await _db.Categories.FirstOrDefaultAsync(c => c.UserId == card.UserId);

                if (defaultCategory == null)
                {
                    defaultCategory = new Category
                    {
                        CategoryName = "Sin categoria",
                        UserId = card.UserId,
                        IsDefaultCategory = true,
                        IsSynced = false
                    };
                    await _db.Categories.AddAsync(defaultCategory);
                    await _db.SaveChangesAsync();
                }

                if (diff > 0)
                {
                    var spending = new Spending
                    {
                        Title = $"Ajuste de saldo - {card.CardName}",
                        Description = $"Ajuste de saldo de ${currentBalance:N2} a ${newBalance:N2}",
                        Amount = diff,
                        CategoryId = defaultCategory.CategoryId,
                        Category = defaultCategory,
                        Date = DateTime.Now,
                        UserId = card.UserId,
                        IsCreditCard = true,
                        CreditCardId = card.CreditCardId,
                        PaymentMethod = "CreditCard",
                        IsSynced = false
                    };
                    await _db.Spending.AddAsync(spending);
                }
                else
                {
                    var payment = new Spending
                    {
                        Title = $"Ajuste de saldo (Abono) - {card.CardName}",
                        Description = $"Ajuste de saldo de ${currentBalance:N2} a ${newBalance:N2}",
                        Amount = Math.Abs(diff),
                        CategoryId = defaultCategory.CategoryId,
                        Category = defaultCategory,
                        Date = DateTime.Now,
                        UserId = card.UserId,
                        IsCreditCard = false,
                        CreditCardId = card.CreditCardId,
                        PaymentMethod = "Transfer",
                        IsSynced = false
                    };
                    await _db.Spending.AddAsync(payment);
                }

                await _db.SaveChangesAsync();
                return true;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error adjusting card balance: {ex.Message}");
                return false;
            }
        }

        private static void GetGradientForColor(string? hex, out string start, out string end)
        {
            var clean = (hex ?? "#126E63").ToUpperInvariant().Trim();
            switch (clean)
            {
                case "#1A73E8": // Blue
                    start = "#1A73E8";
                    end = "#0D47A1";
                    break;
                case "#D97706": // Amber / Gold
                    start = "#D97706";
                    end = "#78350F";
                    break;
                case "#7C3AED": // Purple
                    start = "#7C3AED";
                    end = "#4C1D95";
                    break;
                case "#1F2937": // Dark / Black
                    start = "#374151";
                    end = "#111827";
                    break;
                case "#E11D48": // Rose / Red
                    start = "#E11D48";
                    end = "#881337";
                    break;
                default: // Emerald / Green
                    start = "#126E63";
                    end = "#0B534A";
                    break;
            }
        }

        private async Task<bool> SyncNewCreditCard(CreditCard card)
        {
            try
            {
                var token = Microsoft.Maui.Storage.Preferences.Get("token", string.Empty);
                if (string.IsNullOrWhiteSpace(token)) return false;

                var dto = new CreditCardDto
                {
                    CreditCardId = card.CreditCardId,
                    UserId = card.UserId,
                    CardName = card.CardName,
                    BankName = card.BankName,
                    LastFourDigits = card.LastFourDigits,
                    CutOffDay = card.CutOffDay,
                    PaymentDay = card.PaymentDay,
                    CreditLimit = card.CreditLimit,
                    ColorHex = card.ColorHex,
                    IsSynced = false,
                    IsDeleted = card.IsDeleted
                };

                var success = await _api.CreateCreditCard(dto, token);
                if (success)
                {
                    var existing = await _db.CreditCards.FirstOrDefaultAsync(cc => cc.CreditCardId == card.CreditCardId);
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
                Console.WriteLine($"Error syncing credit card: {ex.Message}");
                return false;
            }
        }

        private async Task<bool> SyncDeleteCreditCard(string creditCardId)
        {
            try
            {
                var token = Microsoft.Maui.Storage.Preferences.Get("token", string.Empty);
                if (string.IsNullOrWhiteSpace(token)) return false;

                var success = await _api.DeleteCreditCard(creditCardId, token);
                if (success)
                {
                    var existing = await _db.CreditCards.FirstOrDefaultAsync(cc => cc.CreditCardId == creditCardId);
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
                Console.WriteLine($"Error deleting credit card sync: {ex.Message}");
                return false;
            }
        }
    }
}
