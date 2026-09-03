using System.Collections.Generic;

namespace Gastapp.Models.Models
{
    /// <summary>
    /// Lo que el telefono empuja al reloj con el estado del dia.
    ///
    /// Reutiliza <see cref="DeviceDaySpendingDto"/> a proposito: es la misma forma que
    /// devuelve GET /Device/Expenses, asi el reloj aplica el mismo mapeo venga de donde
    /// venga y no hay dos formatos que mantener sincronizados.
    /// </summary>
    public class WearTodayPayload
    {
        public decimal Total { get; set; }
        public int Count { get; set; }
        public List<DeviceDaySpendingDto> Spendings { get; set; } = new();
    }
}
