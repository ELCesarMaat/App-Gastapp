using CommunityToolkit.Mvvm.Messaging.Messages;

namespace Gastapp.Messages
{
    public sealed class DayChangedMessage : ValueChangedMessage<DateTime>
    {
        public DayChangedMessage(DateTime newDate) : base(newDate)
        {
        }
    }
}
