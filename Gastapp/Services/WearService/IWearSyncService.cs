namespace Gastapp.Services.WearService
{
    /// <summary>
    /// Empuja al reloj el estado que este necesita mostrar, en vez de que el reloj lo
    /// pida al API.
    ///
    /// Antes el reloj tiraba de datos solo al abrir la app o cada 6 h del SyncWorker,
    /// asi que el tile podia mostrar el total de ayer. Ahora el telefono avisa en
    /// cuanto algo cambia.
    /// </summary>
    public interface IWearSyncService
    {
        /// <summary>Empuja el resumen y la lista de gastos de hoy.</summary>
        Task PushTodayAsync();

        /// <summary>Empuja las categorias, que el reloj usa para clasificar por voz.</summary>
        Task PushCategoriesAsync();

        /// <summary>
        /// Se suscribe a los cambios de gastos para empujar sin que nadie lo pida.
        /// Idempotente: llamarlo dos veces no duplica suscripciones.
        /// </summary>
        void StartWatching();
    }
}
