using CommunityToolkit.Mvvm.Messaging.Messages;

namespace Gastapp.Messages
{
    public sealed class SpendingChangedMessage : ValueChangedMessage<string>
    {
        public SpendingChangedMessage(string spendingId) : base(spendingId)
        {
        }
    }
}
