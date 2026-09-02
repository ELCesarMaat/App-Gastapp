using System;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Gastapp_API.Data;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;

namespace Gastapp.Services
{
    // Borra de forma definitiva los registros que quedaron marcados como borrados hace
    // mas de N dias. El periodo de gracia existe para que un dispositivo que estuvo sin
    // conexion alcance a sincronizar antes de que la fila desaparezca del servidor.
    public class PurgeDeletedService : IPurgeDeletedService
    {
        public const int DefaultRetentionDays = 30;

        private readonly GastappDbContext _db;
        private readonly ILogger<PurgeDeletedService> _logger;

        public PurgeDeletedService(GastappDbContext db, ILogger<PurgeDeletedService> logger)
        {
            _db = db;
            _logger = logger;
        }

        private static int RetentionDays
        {
            get
            {
                var raw = Environment.GetEnvironmentVariable("PURGE_DELETED_AFTER_DAYS");
                return int.TryParse(raw, out var days) && days > 0 ? days : DefaultRetentionDays;
            }
        }

        public async Task<PurgeResult> PurgeAsync(CancellationToken cancellationToken)
        {
            var cutoff = DateTime.UtcNow.AddDays(-RetentionDays);

            // Los gastos van PRIMERO. Una tarjeta borrada puede tener gastos que todavia la
            // referencian; si se borrara la tarjeta antes, la llave foranea truena.
            var spendings = await _db.Spendings
                .Where(s => s.IsDeleted && s.DeletedAt != null && s.DeletedAt < cutoff)
                .ExecuteDeleteAsync(cancellationToken);

            // Solo se purgan tarjetas que ya no tengan NINGUN gasto apuntando a ellas, ni
            // siquiera gastos vivos. Si todavia quedan referencias, la tarjeta espera a la
            // siguiente pasada, cuando esos gastos ya se hayan purgado tambien.
            var cards = await _db.CreditCards
                .Where(cc => cc.IsDeleted
                             && cc.DeletedAt != null
                             && cc.DeletedAt < cutoff
                             && !_db.Spendings.Any(s => s.CreditCardId == cc.CreditCardId))
                .ExecuteDeleteAsync(cancellationToken);

            if (spendings > 0 || cards > 0)
            {
                _logger.LogInformation(
                    "Purga de borrados con mas de {Days} dias: {Spendings} gastos y {Cards} tarjetas eliminados.",
                    RetentionDays, spendings, cards);
            }

            return new PurgeResult(spendings, cards);
        }
    }
}
