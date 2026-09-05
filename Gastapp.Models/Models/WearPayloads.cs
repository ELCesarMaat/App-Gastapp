using System;
using System.Collections.Generic;

namespace Gastapp.Models.Models
{
    /// <summary>
    /// Un gasto capturado en el reloj, tal como viaja por Bluetooth al telefono.
    ///
    /// Lleva el gasto ENTERO y no solo lo que hace falta para la notificacion, porque
    /// el telefono lo inserta en su base local: asi aparece en la lista aunque no haya
    /// internet, sin esperar a que ninguno de los dos sincronice con el API.
    ///
    /// El SpendingId lo genera el reloj y es el mismo que subira el. Tanto
    /// Device/Expenses como SyncAllData hacen upsert por ese id, asi que da igual cual
    /// de los dos llegue primero al servidor: no se duplica.
    /// </summary>
    public class WearExpensePayload
    {
        public string SpendingId { get; set; } = null!;
        public decimal Amount { get; set; }
        public string Title { get; set; } = null!;

        /// <summary>Null si el reloj no supo clasificarlo; el telefono pone la suya.</summary>
        public string? CategoryId { get; set; }

        /// <summary>
        /// Ya compuesta por el reloj, con su "Agregado desde mi ...". Viaja hecha
        /// porque si el telefono gana la carrera al subir el gasto, el servidor ve que
        /// ya existe y NO vuelve a escribirla: se perderia.
        /// </summary>
        public string? Description { get; set; }

        /// <summary>UTC.</summary>
        public DateTime OccurredAt { get; set; }
    }

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
