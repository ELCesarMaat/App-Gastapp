using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Gastapp.Models;
using Gastapp.Services.SpendingService;

namespace Gastapp.ViewModels
{
    public partial class NewSpendingViewModel : ObservableObject
    {
        public readonly ISpendingService SpendingService;

        [ObservableProperty] private DateTime _menuSelectedDate;

        [ObservableProperty] private ObservableCollection<Category> _categories = new();

        [ObservableProperty] private Category _selectedCategory;

        [ObservableProperty] private string _title;
        [ObservableProperty] private string _amount;
        [ObservableProperty] private bool _useSelectedDate = true;
        [ObservableProperty] private TimeSpan _selectedTime = DateTime.Now.TimeOfDay;
        [ObservableProperty] private bool _canChangeDate = true;

        private decimal _amountValue;

        [ObservableProperty] private string _description;

        public NewSpendingViewModel(ISpendingService spendingService)
        {
            SpendingService = spendingService;
        }

        partial void OnMenuSelectedDateChanged(DateTime value)
        {
            //CanChangeDate = MenuSelectedDate.Date != DateTime.Now.Date;
        }

        public async Task SaveSpending()
        {
            var date = DateTime.Now;

            if (CanChangeDate && UseSelectedDate)
            {
                date = new DateTime(MenuSelectedDate.Year, MenuSelectedDate.Month, MenuSelectedDate.Day,
                    SelectedTime.Hours, SelectedTime.Minutes, SelectedTime.Seconds);
            }

            var spending = new Spending
            {
                Amount = _amountValue,
                Title = Title,
                Description = Description,
                Date = date,
                CategoryId = SelectedCategory.CategoryId,
            };
            await SpendingService.CreateNewSpending(spending);
            ClearFields();
        }

        partial void OnAmountChanged(string value)
        {
            _amountValue = decimal.TryParse(Amount, out var res) ? res : 0;
        }

        public void ClearFields()
        {
            Title = string.Empty;
            Amount = string.Empty;
            Description = string.Empty;
            UseSelectedDate = true;
            SelectedTime = DateTime.Now.TimeOfDay;
        }

        public async Task GetCategories()
        {
            Categories = new(await SpendingService.GetCategoriesList());
        }
    }
}