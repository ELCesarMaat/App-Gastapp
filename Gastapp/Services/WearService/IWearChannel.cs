namespace Gastapp.Services.WearService
{
    /// <summary>
    /// Canal con el reloj por la Wearable Data Layer (Bluetooth).
    ///
    /// Se abstrae para que los ViewModels no toquen APIs de Android directamente. En
    /// plataformas sin implementacion, los metodos no hacen nada: avisar al reloj es
    /// siempre una cortesia, nunca un paso obligatorio de la operacion.
    /// </summary>
    public interface IWearChannel
    {
        /// <summary>
        /// Avisa a los relojes de que un dispositivo fue revocado, para que el que
        /// coincida suelte su sesion al instante en vez de esperar a que le rebote
        /// un 401.
        /// </summary>
        /// <returns>true si el aviso salio hacia al menos un reloj.</returns>
        Task<bool> NotifyDeviceRevokedAsync(string deviceId);

        /// <summary>
        /// Deja un dato para el reloj en la ruta indicada.
        ///
        /// A diferencia de los mensajes, esto persiste y se sincroniza solo: si el
        /// reloj esta apagado o lejos, lo recibe al reconectar. Por eso el estado del
        /// dia va por aqui y los avisos puntuales por mensaje.
        /// </summary>
        Task<bool> PutDataAsync(string path, string json);
    }
}
