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
using Gastapp.Services.UserService;

namespace Gastapp.ViewModels
{
    public partial class NewSpendingViewModel : ObservableObject
    {
        public readonly ISpendingService SpendingService;
        public readonly IUserService UserService;
        public bool HasNewSpending;

        [ObservableProperty] private DateTime _menuSelectedDate;

        [ObservableProperty] private ObservableCollection<Category> _categories = new();

        [ObservableProperty] private Category _selectedCategory;

        [ObservableProperty] private string _title;
        [ObservableProperty] private string _amount;
        [ObservableProperty] private bool _useSelectedDate = true;
        [ObservableProperty] private TimeSpan _selectedTime = DateTime.Now.TimeOfDay;
        [ObservableProperty] private bool _canChangeDate = true;

        [ObservableProperty] private bool _showNewCategoryField;
        [ObservableProperty] private string _newCategoryName;

        private decimal _amountValue;

        [ObservableProperty] private string _description;

        public NewSpendingViewModel(ISpendingService spendingService, IUserService userService)
        {
            SpendingService = spendingService;
            UserService = userService;
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
            if (string.IsNullOrEmpty(Description))
                Description = "*SIN DESCRIPCIÓN*";

            var spending = new Spending
            {
                UserId = UserService.GetUserId(),
                Amount = _amountValue,
                Title = Title,
                Description = Description,
                Date = date,
                CategoryId = SelectedCategory.CategoryId,
            };
           
            await SpendingService.CreateNewSpending(spending);
            HasNewSpending = true;
            ClearFields();
        }

        [RelayCommand]
        public async Task SaveNewCategory()
        {
            var user = await UserService.GetUser();
            if (user == null)
                return;

            var category = new Category
            {
                CategoryName = NewCategoryName,
                UserId = user.UserId
            };

            var newCategory = await SpendingService.CreateNewCategory(category);
            Categories.Add(newCategory);
            SelectedCategory = newCategory;

            NewCategoryName = string.Empty;
            ShowNewCategoryField = false;
        }

        partial void OnAmountChanged(string value)
        {
            _amountValue = decimal.TryParse(Amount, out var res) ? res : 0;
            if (res < 0)
            {
                _amountValue = res *= -1;
                Amount = _amountValue.ToString("0.##");
            }

            //Amount = _amountValue.ToString();
        }

        public void ClearFields()
        {
            Title = string.Empty;
            Amount = string.Empty;
            Description = string.Empty;
            UseSelectedDate = true;
            SelectedTime = DateTime.Now.TimeOfDay;
            ShowNewCategoryField = false;
        }

        public async Task GetCategories()
        {
            HasNewSpending = false;
            Categories = new(await SpendingService.GetCategoriesList());
            SelectedCategory = Categories.First();
        }

        [RelayCommand]
        public void ShowNewCategory()
        {
            ShowNewCategoryField = !ShowNewCategoryField;
            NewCategoryName = string.Empty;
        }
    }
}