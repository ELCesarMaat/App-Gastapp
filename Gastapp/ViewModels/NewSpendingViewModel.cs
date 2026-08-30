
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
        using CommunityToolkit.Mvvm.Messaging;
        using Gastapp.Messages;
        using Gastapp.Utils;
        using Gastapp.Services;

namespace Gastapp.ViewModels
{
    public partial class NewSpendingViewModel(
        ISpendingService spendingService,
        IUserService userService,
        ICreditCardService creditCardService) : ObservableObject
    {

        public readonly ISpendingService SpendingService = spendingService;
        public readonly IUserService UserService = userService;
        public readonly ICreditCardService CreditCardService = creditCardService;

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
        [ObservableProperty] private string _paymentMethod = "Cash";
        [ObservableProperty] private bool _isMsi;
        [ObservableProperty] private int _selectedInstallments = 3;
        [ObservableProperty] private ObservableCollection<int> _installmentOptions = [3, 6, 9, 12, 18, 24];
        [ObservableProperty] private string _monthlyInstallmentPreview = string.Empty;

        [ObservableProperty] private bool _isCreditCard;
        [ObservableProperty] private ObservableCollection<CreditCard> _creditCards = [];
        [ObservableProperty] private CreditCard? _selectedCreditCard;
        [ObservableProperty] private bool _hasCreditCards;
        [ObservableProperty] private bool _hasNoCreditCards;

        public bool IsCash => PaymentMethod == "Cash";
        public bool IsDebit => PaymentMethod == "Debit";
        public bool IsTransfer => PaymentMethod == "Transfer";

        public string CategoryName => SelectedCategory?.CategoryName ?? string.Empty;
        public string BottomSheetTitle => IsEditMode ? "Editar gasto" : "Nuevo gasto";
        public bool CanDeleteSelectedCategory => IsEditMode
                            && SelectedCategory != null
                            && !IsDefaultCategory(SelectedCategory);

        private static bool IsDefaultCategory(Category? category)
        {
            if (category == null)
                return false;

            return category.IsDefaultCategory || IsDefaultCategoryName(category.CategoryName);
        }

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

        private static string NormalizeDescription(string? description)
        {
            if (string.IsNullOrWhiteSpace(description))
                return string.Empty;

            var normalized = description.Trim();
            return string.Equals(normalized, "*SIN DESCRIPCION*", StringComparison.OrdinalIgnoreCase)
                ? string.Empty
                : normalized;
        }

        [RelayCommand]
        public void SelectPaymentMethod(string method)
        {
            PaymentMethod = method;
            IsCreditCard = method == "CreditCard";
            NotifyPaymentMethodProperties();
            UpdateMsiPreview();
        }

        [RelayCommand]
        public void SelectInstallments(int months)
        {
            SelectedInstallments = months;
            UpdateMsiPreview();
        }

        [RelayCommand]
        public void ToggleMsiMode(bool isMsi)
        {
            IsMsi = isMsi;
            UpdateMsiPreview();
        }

        private void NotifyPaymentMethodProperties()
        {
            OnPropertyChanged(nameof(IsCash));
            OnPropertyChanged(nameof(IsDebit));
            OnPropertyChanged(nameof(IsTransfer));
            OnPropertyChanged(nameof(IsCreditCard));
        }

        private void UpdateMsiPreview()
        {
            if (!IsCreditCard || !IsMsi)
            {
                MonthlyInstallmentPreview = string.Empty;
                return;
            }

            if (decimal.TryParse(Amount?.Trim(), out var total) && total > 0 && SelectedInstallments > 0)
            {
                var monthly = total / SelectedInstallments;
                MonthlyInstallmentPreview = $"Pagarás ${monthly:N2} al mes durante {SelectedInstallments} meses.";
            }
            else
            {
                MonthlyInstallmentPreview = $"Se diferirá a {SelectedInstallments} mensualidades fijas.";
            }
        }

        partial void OnAmountChanged(string value)
        {
            UpdateMsiPreview();
        }

        partial void OnIsMsiChanged(bool value)
        {
            UpdateMsiPreview();
        }

        partial void OnSelectedInstallmentsChanged(int value)
        {
            UpdateMsiPreview();
        }

        [RelayCommand]
        public async Task DeleteCategory(Category category)
        {
            if (!IsEditMode || category == null)
                return;

            if (IsDefaultCategory(category))
                return;

            var usageCount = await SpendingService.CountActiveSpendingsByCategory(category.CategoryId);
            var message = usageCount > 0
                ? $"La categoría '{category.CategoryName}' se está usando en {usageCount} gasto(s). Si la eliminas, esos gastos pasarán a 'Sin categoria'.\n\n¿Deseas continuar?"
                : $"¿Seguro que deseas eliminar la categoría '{category.CategoryName}'?";

            var confirm = await AlertHelper.ShowAlertAsync(
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
                    SelectedCategory = Categories.FirstOrDefault(c => c.IsDefaultCategory)
                        ?? Categories.FirstOrDefault(c => IsDefaultCategoryName(c.CategoryName))
                        ?? Categories.FirstOrDefault();
                }

                WeakReferenceMessenger.Default.Send(new SpendingChangedMessage(string.Empty));
            }
            else
            {
                await AlertHelper.ShowAlertAsync("Error", "No se pudo eliminar la categoría.", "OK");
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

            var cards = await CreditCardService.GetAllCreditCardsAsync();
            CreditCards = new(cards);
            HasCreditCards = cards.Any();
            HasNoCreditCards = !HasCreditCards;
            if (HasCreditCards)
            {
                SelectedCreditCard = CreditCards.First();
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
            PaymentMethod = "Cash";
            IsCreditCard = false;
            IsMsi = false;
            SelectedInstallments = 3;
            MonthlyInstallmentPreview = string.Empty;
            NotifyPaymentMethodProperties();

            if (CreditCards.Count > 0)
            {
                SelectedCreditCard = CreditCards.First();
            }
            OnPropertyChanged(nameof(CanDeleteSelectedCategory));
        }

        public async Task PrepareForCardSpending(CreditCard card, bool isMsi = false)
        {
            await GetCategories();
            PrepareForCreate();
            PaymentMethod = "CreditCard";
            IsCreditCard = true;
            IsMsi = isMsi;
            SelectedCreditCard = CreditCards.FirstOrDefault(c => c.CreditCardId == card.CreditCardId) ?? card;
            NotifyPaymentMethodProperties();
            UpdateMsiPreview();
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
                    await AlertHelper.ShowAlertAsync("Error", "Ingresa un nombre de categoría.", "OK");
                    return;
                }

                if (Categories.Any(c => string.Equals(c.CategoryName, categoryName, StringComparison.OrdinalIgnoreCase)))
                {
                    await AlertHelper.ShowAlertAsync("Error", "Ya existe una categoría con ese nombre.", "OK");
                    return;
                }

                var user = await UserService.GetUser();
                if (user == null || string.IsNullOrWhiteSpace(user.UserId))
                {
                    await AlertHelper.ShowAlertAsync("Error", "No se pudo obtener el usuario actual.", "OK");
                    return;
                }

                var category = await SpendingService.CreateNewCategory(new Category
                {
                    CategoryName = categoryName,
                    UserId = user.UserId,
                    IsDefaultCategory = false
                });

                if (category == null || string.IsNullOrWhiteSpace(category.CategoryId))
                {
                    await AlertHelper.ShowAlertAsync("Error", "No se pudo crear la categoría.", "OK");
                    return;
                }

                Categories.Add(category);
                SelectedCategory = category;
                ShowNewCategoryField = false;
                NewCategoryName = string.Empty;
            }
            catch (Exception ex)
            {
                await AlertHelper.ShowAlertAsync("Error", "Ocurrió un error al crear la categoría.", "OK");
                System.Diagnostics.Debug.WriteLine(ex);
            }
        }

        public void LoadForEdit(Spending spending)
        {
            IsEditMode = true;
            _editingSpendingId = spending.SpendingId;
            Title = spending.Title ?? string.Empty;
            Description = NormalizeDescription(spending.Description);
            Amount = spending.Amount.ToString("N2");
            SelectedCategory = Categories.FirstOrDefault(c => c.CategoryId == spending.CategoryId);
            MenuSelectedDate = spending.Date;
            SelectedTime = spending.Date.TimeOfDay;
            UseSelectedDate = true;

            PaymentMethod = string.IsNullOrEmpty(spending.PaymentMethod)
                ? (spending.IsCreditCard ? "CreditCard" : "Cash")
                : spending.PaymentMethod;
            IsCreditCard = PaymentMethod == "CreditCard" || spending.IsCreditCard;
            IsMsi = spending.IsMsi;
            SelectedInstallments = spending.TotalInstallments > 1 ? spending.TotalInstallments : 3;

            NotifyPaymentMethodProperties();
            UpdateMsiPreview();

            if (IsCreditCard && !string.IsNullOrEmpty(spending.CreditCardId))
            {
                SelectedCreditCard = CreditCards.FirstOrDefault(c => c.CreditCardId == spending.CreditCardId)
                                     ?? CreditCards.FirstOrDefault();
            }
            else if (CreditCards.Count > 0)
            {
                SelectedCreditCard = CreditCards.First();
            }

            OnPropertyChanged(nameof(CanDeleteSelectedCategory));
        }

        public async Task<bool> SaveSpending()
        {
            if (string.IsNullOrWhiteSpace(Amount) || !decimal.TryParse(Amount, out var amount) || amount <= 0)
            {
                await AlertHelper.ShowAlertAsync("Error", "Ingresa un monto válido mayor a 0.", "OK");
                return false;
            }

            if (SelectedCategory == null)
            {
                await AlertHelper.ShowAlertAsync("Error", "Selecciona una categoría.", "OK");
                return false;
            }

            if (IsCreditCard && SelectedCreditCard == null)
            {
                await AlertHelper.ShowAlertAsync("Error", "Selecciona una tarjeta de crédito para el pago.", "OK");
                return false;
            }

            var spendingDate = UseSelectedDate ? MenuSelectedDate.Date.Add(SelectedTime) : DateTime.Now;
            var normalizedDescription = NormalizeDescription(Description);

            var isCard = PaymentMethod == "CreditCard";
            var cardId = isCard && SelectedCreditCard != null ? SelectedCreditCard.CreditCardId : null;
            var isMsi = isCard && IsMsi;
            var totalInstallments = isMsi ? Math.Max(1, SelectedInstallments) : 1;
            var monthlyAmount = isMsi ? Math.Round(amount / totalInstallments, 2) : amount;

            if (string.IsNullOrEmpty(_editingSpendingId))
            {
                var user = await UserService.GetUser();
                if (user == null || string.IsNullOrWhiteSpace(user.UserId))
                {
                    await AlertHelper.ShowAlertAsync("Error", "No hay un usuario local activo. Inicia sesión nuevamente.", "OK");
                    return false;
                }

                // Crear nuevo gasto
                var newSpending = new Spending
                {
                    Title = Title,
                    Description = normalizedDescription,
                    Amount = amount,
                    CategoryId = SelectedCategory.CategoryId,
                    Date = spendingDate,
                    Category = SelectedCategory,
                    UserId = user.UserId,
                    IsCreditCard = isCard,
                    CreditCardId = cardId,
                    PaymentMethod = PaymentMethod,
                    IsMsi = isMsi,
                    TotalInstallments = totalInstallments,
                    CurrentInstallment = 1,
                    InstallmentMonthlyAmount = monthlyAmount
                };

                var result = await SpendingService.CreateNewSpending(newSpending);
                HasNewSpending = result;
                if (result)
                {
                    WeakReferenceMessenger.Default.Send(new SpendingChangedMessage(newSpending.SpendingId));
                }
                else
                {
                    await AlertHelper.ShowAlertAsync("Error", "No se pudo guardar el gasto.", "OK");
                    return false;
                }
            }
            else
            {
                // Editar gasto existente
                var spending = await SpendingService.GetSpendingByIdAsync(_editingSpendingId);
                if (spending == null)
                {
                    await AlertHelper.ShowAlertAsync("Error", "No se encontró el gasto a editar.", "OK");
                    return false;
                }

                spending.Title = Title;
                spending.Description = normalizedDescription;
                spending.Amount = amount;
                spending.CategoryId = SelectedCategory.CategoryId;
                spending.Category = SelectedCategory;
                spending.Date = spendingDate;
                spending.IsCreditCard = isCard;
                spending.CreditCardId = cardId;
                spending.PaymentMethod = PaymentMethod;
                spending.IsMsi = isMsi;
                spending.TotalInstallments = totalInstallments;
                spending.InstallmentMonthlyAmount = monthlyAmount;

                var result = await SpendingService.UpdateSpending(spending);
                HasNewSpending = result;
                if (result)
                {
                    WeakReferenceMessenger.Default.Send(new SpendingChangedMessage(spending.SpendingId));
                }
                else
                {
                    await AlertHelper.ShowAlertAsync("Error", "No se pudo actualizar el gasto.", "OK");
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