
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
        using System.Globalization;

namespace Gastapp.ViewModels
{
    public partial class NewSpendingViewModel(ISpendingService spendingService, IUserService userService) : ObservableObject
    {
        public const string SpendingsChangedMessage = "SpendingsChanged";
        public const string CategoriesChangedMessage = "CategoriesChanged";

        public readonly ISpendingService SpendingService = spendingService;
        public readonly IUserService UserService = userService;

        private string _editingSpendingId = string.Empty;

        [ObservableProperty] private bool _hasNewSpending;
        [ObservableProperty] private DateTime _menuSelectedDate;
        [ObservableProperty] private ObservableCollection<Category> _categories = [];
        [ObservableProperty] private Category _selectedCategory;
        [ObservableProperty] private string _title;
        [ObservableProperty] private string _description;
        [ObservableProperty] private string _amount;
        [ObservableProperty] private bool _useSelectedDate = true;
        [ObservableProperty] private TimeSpan _selectedTime = DateTime.Now.TimeOfDay;
        [ObservableProperty] private bool _canChangeDate = true;
        [ObservableProperty] private bool _isEditMode;
        [ObservableProperty] private bool _showNewCategoryField;
        [ObservableProperty] private string _newCategoryName;

        public string CategoryName => SelectedCategory?.CategoryName ?? string.Empty;
        public string BottomSheetTitle => IsEditMode ? "Editar gasto" : "Nuevo gasto";
        public bool CanDeleteSelectedCategory => IsEditMode
                            && SelectedCategory != null
                            && !IsDefaultCategoryName(SelectedCategory.CategoryName);

        private static bool IsDefaultCategoryName(string? categoryName)
        {
            if (string.IsNullOrWhiteSpace(categoryName))
                return false;

            var normalized = categoryName.Normalize(NormalizationForm.FormD);
            var sb = new StringBuilder();
            foreach (var c in normalized)
            {
                if (CharUnicodeInfo.GetUnicodeCategory(c) != UnicodeCategory.NonSpacingMark)
                    sb.Append(c);
            }

            var plain = sb.ToString().Normalize(NormalizationForm.FormC).Trim().ToUpperInvariant();
            return plain == "SIN CATEGORIA";
        }

        [RelayCommand]
        public async Task DeleteCategory(Category category)
        {
            if (!IsEditMode || category == null)
                return;

            if (IsDefaultCategoryName(category.CategoryName))
                return;

            var usageCount = await SpendingService.CountActiveSpendingsByCategory(category.CategoryId);
            var message = usageCount > 0
                ? $"La categoría '{category.CategoryName}' se está usando en {usageCount} gasto(s). Si la eliminas, esos gastos pasarán a 'SIN CATEGORIA'.\n\n¿Deseas continuar?"
                : $"¿Seguro que deseas eliminar la categoría '{category.CategoryName}'?";

            var confirm = await Microsoft.Maui.Controls.Application.Current.MainPage.DisplayAlert(
                "Eliminar categoría",
                message,
                "Eliminar", "Cancelar");
            if (!confirm) return;

            var result = await SpendingService.RemoveCategoryById(category.CategoryId);
            if (result)
            {
                Categories.Remove(category);
                if (SelectedCategory == category)
                {
                    SelectedCategory = Categories.FirstOrDefault(c => c.CategoryName == "SIN CATEGORIA") ?? Categories.FirstOrDefault();
                }

                Microsoft.Maui.Controls.MessagingCenter.Send<object>(this, CategoriesChangedMessage);
                Microsoft.Maui.Controls.MessagingCenter.Send<object, string>(this, SpendingsChangedMessage, string.Empty);
            }
            else
            {
                await Microsoft.Maui.Controls.Application.Current.MainPage.DisplayAlert("Error", "No se pudo eliminar la categoría.", "OK");
            }
        }

        public async Task GetCategories()
        {
            HasNewSpending = false;
            Categories = new(await SpendingService.GetCategoriesList());
            if (Categories.Count > 0)
            {
                SelectedCategory = Categories.First();
            }
        }

        public void PrepareForCreate()
        {
            IsEditMode = false;
            _editingSpendingId = string.Empty;
            Title = string.Empty;
            Description = string.Empty;
            Amount = string.Empty;
            UseSelectedDate = true;
            ShowNewCategoryField = false;
            NewCategoryName = string.Empty;
            OnPropertyChanged(nameof(CanDeleteSelectedCategory));
        }

        [RelayCommand]
        public void ShowNewCategory()
        {
            ShowNewCategoryField = !ShowNewCategoryField;
            NewCategoryName = string.Empty;
        }

        [RelayCommand]
        public async Task SaveNewCategory()
        {
            try
            {
                var categoryName = NewCategoryName?.Trim() ?? string.Empty;
                if (string.IsNullOrWhiteSpace(categoryName))
                {
                    if (Microsoft.Maui.Controls.Application.Current?.MainPage != null)
                        await Microsoft.Maui.Controls.Application.Current.MainPage.DisplayAlert("Error", "Ingresa un nombre de categoría.", "OK");
                    return;
                }

                if (Categories.Any(c => string.Equals(c.CategoryName, categoryName, StringComparison.OrdinalIgnoreCase)))
                {
                    if (Microsoft.Maui.Controls.Application.Current?.MainPage != null)
                        await Microsoft.Maui.Controls.Application.Current.MainPage.DisplayAlert("Error", "Ya existe una categoría con ese nombre.", "OK");
                    return;
                }

                var user = await UserService.GetUser();
                if (user == null || string.IsNullOrWhiteSpace(user.UserId))
                {
                    if (Microsoft.Maui.Controls.Application.Current?.MainPage != null)
                        await Microsoft.Maui.Controls.Application.Current.MainPage.DisplayAlert("Error", "No se pudo obtener el usuario actual.", "OK");
                    return;
                }

                var category = await SpendingService.CreateNewCategory(new Category
                {
                    CategoryName = categoryName,
                    UserId = user.UserId
                });

                if (category == null || string.IsNullOrWhiteSpace(category.CategoryId))
                {
                    if (Microsoft.Maui.Controls.Application.Current?.MainPage != null)
                        await Microsoft.Maui.Controls.Application.Current.MainPage.DisplayAlert("Error", "No se pudo crear la categoría.", "OK");
                    return;
                }

                Categories.Add(category);
                SelectedCategory = category;
                ShowNewCategoryField = false;
                NewCategoryName = string.Empty;
                Microsoft.Maui.Controls.MessagingCenter.Send<object>(this, CategoriesChangedMessage);
            }
            catch (Exception ex)
            {
                if (Microsoft.Maui.Controls.Application.Current?.MainPage != null)
                    await Microsoft.Maui.Controls.Application.Current.MainPage.DisplayAlert("Error", "Ocurrió un error al crear la categoría.", "OK");
                System.Diagnostics.Debug.WriteLine(ex);
            }
        }

        public void LoadForEdit(Spending spending)
        {
            IsEditMode = true;
            _editingSpendingId = spending.SpendingId;
            Title = spending.Title ?? string.Empty;
            Description = spending.Description ?? string.Empty;
            Amount = spending.Amount.ToString("N2");
            SelectedCategory = Categories.FirstOrDefault(c => c.CategoryId == spending.CategoryId)
                               ?? Categories.FirstOrDefault();
            MenuSelectedDate = spending.Date;
            SelectedTime = spending.Date.TimeOfDay;
            UseSelectedDate = true;
            OnPropertyChanged(nameof(CanDeleteSelectedCategory));
        }

        public async Task<bool> SaveSpending()
        {
            if (string.IsNullOrWhiteSpace(Amount) || !decimal.TryParse(Amount, out var amount))
            {
                await Microsoft.Maui.Controls.Application.Current.MainPage.DisplayAlert("Error", "Ingresa un monto válido.", "OK");
                return false;
            }

            if (SelectedCategory == null)
            {
                await Microsoft.Maui.Controls.Application.Current.MainPage.DisplayAlert("Error", "Selecciona una categoría.", "OK");
                return false;
            }

            var spendingDate = UseSelectedDate ? MenuSelectedDate.Date.Add(SelectedTime) : DateTime.Now;

            if (string.IsNullOrEmpty(_editingSpendingId))
            {
                var user = await UserService.GetUser();
                if (user == null || string.IsNullOrWhiteSpace(user.UserId))
                {
                    await Microsoft.Maui.Controls.Application.Current.MainPage.DisplayAlert("Error", "No hay un usuario local activo. Inicia sesión nuevamente.", "OK");
                    return false;
                }

                // Crear nuevo gasto
                var newSpending = new Spending
                {
                    Title = Title,
                    Description = Description,
                    Amount = amount,
                    CategoryId = SelectedCategory.CategoryId,
                    Date = spendingDate,
                    Category = SelectedCategory,
                    UserId = user.UserId
                };

                var result = await SpendingService.CreateNewSpending(newSpending);
                HasNewSpending = result;
                if (result)
                {
                    Microsoft.Maui.Controls.MessagingCenter.Send<object, string>(this, SpendingsChangedMessage, newSpending.SpendingId);
                }
                else
                {
                    await Microsoft.Maui.Controls.Application.Current.MainPage.DisplayAlert("Error", "No se pudo guardar el gasto.", "OK");
                    return false;
                }
            }
            else
            {
                // Editar gasto existente
                var spending = await SpendingService.GetSpendingByIdAsync(_editingSpendingId);
                if (spending == null)
                {
                    await Microsoft.Maui.Controls.Application.Current.MainPage.DisplayAlert("Error", "No se encontró el gasto a editar.", "OK");
                    return false;
                }

                spending.Title = Title;
                spending.Description = Description;
                spending.Amount = amount;
                spending.CategoryId = SelectedCategory.CategoryId;
                spending.Category = SelectedCategory;
                spending.Date = spendingDate;

                var result = await SpendingService.UpdateSpending(spending);
                HasNewSpending = result;
                if (result)
                {
                    Microsoft.Maui.Controls.MessagingCenter.Send<object, string>(this, SpendingsChangedMessage, spending.SpendingId);
                }
                else
                {
                    await Microsoft.Maui.Controls.Application.Current.MainPage.DisplayAlert("Error", "No se pudo actualizar el gasto.", "OK");
                    return false;
                }
            }

            // Limpiar formulario
            Title = string.Empty;
            Description = string.Empty;
            Amount = string.Empty;
            IsEditMode = false;
            ShowNewCategoryField = false;
            NewCategoryName = string.Empty;
            _editingSpendingId = string.Empty;
            OnPropertyChanged(nameof(CanDeleteSelectedCategory));
            return true;
        }

        partial void OnSelectedCategoryChanged(Category value)
        {
            OnPropertyChanged(nameof(CategoryName));
            OnPropertyChanged(nameof(CanDeleteSelectedCategory));
        }

        partial void OnIsEditModeChanged(bool value)
        {
            OnPropertyChanged(nameof(CanDeleteSelectedCategory));
            OnPropertyChanged(nameof(BottomSheetTitle));
        }
    }
}