using CommunityToolkit.Mvvm.Messaging.Messages;

namespace Gastapp.Messages
{
    /// <summary>
    /// La lista de dispositivos vinculados cambio fuera de la pantalla de Ajustes,
    /// normalmente porque el reloj se desvinculo el mismo y lo aviso por Bluetooth.
    /// </summary>
    public sealed class DevicesChangedMessage : ValueChangedMessage<string>
    {
        public DevicesChangedMessage(string reason) : base(reason)
        {
        }
    }
}
