using System;
using System.Linq;
using System.Threading.Tasks;
using Microsoft.EntityFrameworkCore;

namespace Gastapp.Data
{
    // Limpieza de la base local: borra en definitivo los registros que quedaron marcados
    // como borrados hace mas de N dias. Solo toca filas ya sincronizadas, para no perder
    // un borrado que el servidor todavia no conoce.
    public static class PurgeDeletedLocal
    {
        public const int RetentionDays = 30;

        public static async Task<int> PurgeAsync(GastappDbContext db)
        {
            try
            {
                var cutoff = DateTime.UtcNow.AddDays(-RetentionDays);

                // Los gastos van PRIMERO: una tarjeta borrada puede tener gastos que aun la
                // referencian y la llave foranea truena si se borra la tarjeta antes.
                var spendings = await db.Spending
                    .Where(s => s.IsDeleted && s.IsSynced && s.DeletedAt != null && s.DeletedAt < cutoff)
                    .ExecuteDeleteAsync();

                // Solo tarjetas sin ningun gasto que las referencie.
                var cards = await db.CreditCards
                    .Where(cc => cc.IsDeleted && cc.IsSynced && cc.DeletedAt != null && cc.DeletedAt < cutoff
                                 && !db.Spending.Any(s => s.CreditCardId == cc.CreditCardId))
                    .ExecuteDeleteAsync();

                var total = spendings + cards;
                if (total > 0)
                    Console.WriteLine($"Purga local: {spendings} gastos y {cards} tarjetas eliminados.");

                return total;
            }
            catch (Exception ex)
            {
                // Nunca debe impedir que la app arranque.
                Console.WriteLine($"Purga local fallida: {ex.Message}");
                return 0;
            }
        }
    }
}
