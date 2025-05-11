using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Gastapp.Models
{
    public class SpendingGroup : ObservableCollection<Spending>
    {
        public string Name { get; private set; }
        public decimal Amount { get; private set; }

        public SpendingGroup(string name, ObservableCollection<Spending> spendings) : base(spendings)
        {
            Name = name;
            Amount = spendings.Sum(s => s.Amount);
        }
    }
}
