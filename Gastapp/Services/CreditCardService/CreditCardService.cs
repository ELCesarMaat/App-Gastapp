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
            // All active credit card purchases for this card
            return await _db.Spending
                .Where(s => s.CreditCardId == creditCardId && s.IsCreditCard && !s.IsDeleted)
                .OrderBy(s => s.Date)
                .ToListAsync();
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
