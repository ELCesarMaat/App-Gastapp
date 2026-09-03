using CommunityToolkit.Mvvm.Messaging.Messages;

namespace Gastapp.Messages
{
    /// <summary>
    /// Un reloj acaba de vincularse solo por Bluetooth, sin que el usuario teclee el
    /// codigo. Lo emite el servicio que atiende a la Data Layer.
    /// </summary>
    public sealed class WearDeviceLinkedMessage : ValueChangedMessage<string>
    {
        public WearDeviceLinkedMessage(string deviceName) : base(deviceName)
        {
        }
    }
}
