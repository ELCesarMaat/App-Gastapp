using System.Text.Json;
using CommunityToolkit.Mvvm.Messaging;
using Gastapp.Messages;
using Gastapp.Models.Models;
using Gastapp.Services.SpendingService;

namespace Gastapp.Services.WearService
{
    /// <inheritdoc cref="IWearSyncService"/>
    public class WearSyncService(
        ISpendingService spendingService,
        IWearChannel? wearChannel = null) : IWearSyncService
    {
        public const string RutaHoy = "/gastapp/today";
        public const string RutaCategorias = "/gastapp/categories";

        private readonly ISpendingService _spendingService = spendingService;
        private readonly IWearChannel? _wearChannel = wearChannel;

        /// <summary>
        /// camelCase para que coincida con los nombres que espera kotlinx.serialization
        /// en el reloj. Si esto cambia, el reloj deja de entender el payload.
        /// </summary>
        private static readonly JsonSerializerOptions Opciones = new()
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase
        };

        private bool _watching;

        public void StartWatching()
        {
            if (_watching || _wearChannel == null)
                return;

            _watching = true;

            WeakReferenceMessenger.Default.Register<WearSyncService, SpendingChangedMessage>(
                this, (destinatario, mensaje) =>
                {
                    _ = destinatario.PushTodayAsync();
                });
        }

        public async Task PushTodayAsync()
        {
            if (_wearChannel == null)
                return;

            try
            {
                var hoy = DateTime.Now.Date;
                var gastos = await _spendingService.GetSpendingListByDateAsync(hoy);
                var categorias = await _spendingService.GetCategoriesList();

                // Un diccionario evita recorrer la lista de categorias por cada gasto.
                var nombrePorCategoria = categorias
                    .GroupBy(c => c.CategoryId)
                    .ToDictionary(g => g.Key, g => g.First().CategoryName);

                var vigentes = gastos.Where(g => !g.IsDeleted).ToList();

                var payload = new WearTodayPayload
                {
                    Total = vigentes.Sum(g => g.Amount),
                    Count = vigentes.Count,
                    Spendings = vigentes
                        .OrderByDescending(g => g.Date)
                        .Select(g => new DeviceDaySpendingDto
                        {
                            SpendingId = g.SpendingId,
                            Title = g.Title,
                            CategoryName = nombrePorCategoria.GetValueOrDefault(g.CategoryId),
                            Amount = g.Amount,
                            // El reloj lo pasa a hora local; mandarlo en UTC evita que
                            // un cambio de zona horaria descoloque la lista.
                            OccurredAt = DateTime.SpecifyKind(g.Date, DateTimeKind.Local).ToUniversalTime()
                        })
                        .ToList()
                };

                await _wearChannel.PutDataAsync(RutaHoy, JsonSerializer.Serialize(payload, Opciones));
            }
            catch (Exception ex)
            {
                // El reloj sigue pudiendo tirar del API por su cuenta.
                System.Diagnostics.Debug.WriteLine($"[Wear] No se pudo empujar el dia: {ex.Message}");
            }
        }

        public async Task PushCategoriesAsync()
        {
            if (_wearChannel == null)
                return;

            try
            {
                var categorias = await _spendingService.GetCategoriesList();

                var payload = categorias
                    .Select(c => new DeviceCategoryDto
                    {
                        CategoryId = c.CategoryId,
                        CategoryName = c.CategoryName,
                        IsDefaultCategory = c.IsDefaultCategory
                    })
                    .ToList();

                await _wearChannel.PutDataAsync(RutaCategorias, JsonSerializer.Serialize(payload, Opciones));
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"[Wear] No se pudieron empujar categorias: {ex.Message}");
            }
        }
    }
}
