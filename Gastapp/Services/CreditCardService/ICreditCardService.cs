using System.Collections.Generic;
using System.Threading.Tasks;
using Gastapp.Models;

namespace Gastapp.Services
{
    public interface ICreditCardService
    {
        Task<List<CreditCard>> GetAllCreditCardsAsync();
        Task<CreditCard?> GetCreditCardByIdAsync(string id);
        Task<CreditCard> CreateCreditCardAsync(CreditCard card);
        Task<bool> UpdateCreditCardAsync(CreditCard card);
        Task<bool> DeleteCreditCardAsync(string id);
        Task<decimal> GetPendingAmountForCardAsync(string creditCardId);
        Task<List<Spending>> GetPendingSpendingsForCardAsync(string creditCardId);
        Task<CreditCardSummary> GetCardSummaryAsync(string creditCardId);
        Task<List<CreditCardSummary>> GetAllCardSummariesAsync();
        Task<List<Spending>> GetActiveMsiSpendingsAsync(string creditCardId);
        Task<List<Spending>> GetCurrentCycleSpendingsAsync(string creditCardId);
        (DateTime CutOffDate, DateTime PaymentDueDate) CalculateCycleDates(int cutOffDay, int paymentDay, DateTime referenceDate);
        Task<bool> AdjustCardBalanceAsync(string creditCardId, decimal newBalance);
    }
}
