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
    }
}
