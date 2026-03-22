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

        [ObservableProperty] private bool _isEditMode;
        [ObservableProperty] private string _bottomSheetTitle = "Nuevo gasto";
        [ObservableProperty] private string _saveButtonText = "Guardar";

        private string _editingSpendingId = string.Empty;
        private string _editingUserId = string.Empty;

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
            if (IsEditMode)
            {
                await SaveEditedSpending();
                return;
            }

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

        private async Task SaveEditedSpending()
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
                SpendingId = _editingSpendingId,
                UserId = _editingUserId,
                Amount = _amountValue,
                Title = Title,
                Description = Description,
                Date = date,
                CategoryId = SelectedCategory.CategoryId,
            };

            await SpendingService.UpdateSpending(spending);
            HasNewSpending = true;
            ClearFields();
        }

        public void LoadForEdit(Spending spending)
        {
            IsEditMode = true;
            BottomSheetTitle = "Editar gasto";
            SaveButtonText = "Actualizar";
            _editingSpendingId = spending.SpendingId;
            _editingUserId = spending.UserId;
            Title = spending.Title;
            var desc = spending.Description ?? string.Empty;
            Description = string.Equals(desc, "*SIN DESCRIPCIÓN*", StringComparison.OrdinalIgnoreCase)
                ? string.Empty
                : desc;
            Amount = spending.Amount.ToString("0.##");
            MenuSelectedDate = spending.Date;
            UseSelectedDate = true;
            SelectedTime = spending.Date.TimeOfDay;

            var matchingCat = Categories.FirstOrDefault(c => c.CategoryId == spending.CategoryId);
            if (matchingCat != null)
                SelectedCategory = matchingCat;
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
            IsEditMode = false;
            BottomSheetTitle = "Nuevo gasto";
            SaveButtonText = "Guardar";
            _editingSpendingId = string.Empty;
            _editingUserId = string.Empty;
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