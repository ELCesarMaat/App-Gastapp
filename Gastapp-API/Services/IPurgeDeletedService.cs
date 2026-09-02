using System.Threading;
using System.Threading.Tasks;

namespace Gastapp.Services
{
    public interface IPurgeDeletedService
    {
        /// <summary>
        /// Borra definitivamente los registros marcados como borrados hace mas de N dias.
        /// Devuelve cuantas filas se eliminaron de cada tabla.
        /// </summary>
        Task<PurgeResult> PurgeAsync(CancellationToken cancellationToken);
    }

    public record PurgeResult(int Spendings, int CreditCards);
}
