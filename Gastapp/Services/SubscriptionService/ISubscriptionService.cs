using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using Gastapp.Models;

namespace Gastapp.Services
{
    public interface ISubscriptionService
    {
        Task<List<Subscription>> GetAllSubscriptionsAsync();
        Task<Subscription?> GetSubscriptionByIdAsync(string id);
        Task<Subscription> CreateSubscriptionAsync(Subscription subscription);
        Task<bool> UpdateSubscriptionAsync(Subscription subscription);
        Task<bool> DeleteSubscriptionAsync(string id);

        /// <summary>Pausa o reanuda sin borrar: una pausada deja de contar en los totales.</summary>
        Task<bool> SetActiveStateAsync(string id, bool isActive);

        /// <summary>Deja constancia de que el cobro de este ciclo ya se registro.</summary>
        Task<bool> MarkChargeRegisteredAsync(string id, DateTime chargedAt);

        Task<SubscriptionSummary> GetSubscriptionSummaryAsync(string id);
        Task<List<SubscriptionSummary>> GetAllSubscriptionSummariesAsync();

        /// <summary>Todos los cobros que caen dentro de los proximos N dias, ya ordenados.</summary>
        Task<List<UpcomingCharge>> GetUpcomingChargesAsync(int daysAhead = 45);

        /// <summary>
        /// El proximo cobro, contando ciclos completos desde la fecha del primero.
        /// Es aritmetica pura de calendario, sin tocar la base.
        /// </summary>
        DateTime CalculateNextChargeDate(DateTime firstChargeDate, string billingCycle, DateTime referenceDate);

        /// <summary>
        /// El ultimo cobro que ya ocurrio. Marca donde empieza el periodo en curso, y
        /// con eso se sabe si el cobro de este periodo ya quedo registrado.
        /// </summary>
        DateTime CalculatePreviousChargeDate(DateTime firstChargeDate, string billingCycle, DateTime referenceDate);

        /// <summary>
        /// Si la suscripcion entra en el gasto mensual y anual. Una pausada no cuenta,
        /// y una en prueba gratis tampoco: todavia no sale dinero.
        /// </summary>
        bool CountsTowardTotals(Subscription subscription, DateTime referenceDate);

        /// <summary>El costo llevado a mes, para poder sumar ciclos distintos entre si.</summary>
        decimal GetMonthlyEquivalent(decimal amount, string billingCycle);

        /// <summary>"Mensual", "Anual"... el texto que ve el usuario.</summary>
        string GetBillingCycleDisplayName(string billingCycle);

        /// <summary>"al mes", "al año"... para armar el precio con su periodicidad.</summary>
        string GetBillingCycleSuffix(string billingCycle);
    }
}
