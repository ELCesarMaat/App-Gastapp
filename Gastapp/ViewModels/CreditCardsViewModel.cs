using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using CommunityToolkit.Maui.Alerts;
using CommunityToolkit.Maui.Core;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using CommunityToolkit.Mvvm.Messaging;
using Gastapp.BottomSheets;
using Gastapp.Messages;
using Gastapp.Models;
using Gastapp.Pages.Menu;
using Gastapp.Services;
using Gastapp.Services.Navigation;
using Gastapp.Services.Notifications;
using Gastapp.Services.SpendingService;
using Gastapp.Services.UserService;
using Gastapp.Utils;

namespace Gastapp.ViewModels
{
    public partial class CreditCardsViewModel : ObservableObject
    {
        private readonly ICreditCardService _creditCardService;
        private readonly ISpendingService _spendingService;
        private readonly IUserService _userService;
        private readonly INavigationService _navigationService;
        private readonly IReminderNotificationService _reminderNotificationService;
        private readonly NewSpendingViewModel _newSpendingVm;

        private bool _isInitialized;

        [ObservableProperty] private ObservableCollection<CreditCardSummary> _cardSummaries = [];
        [ObservableProperty] private CreditCardSummary? _selectedCardSummary;
        [ObservableProperty] private bool _hasCards;
        [ObservableProperty] private bool _hasNoCards = true;
        [ObservableProperty] private bool _isLoading;

        [ObservableProperty] private decimal _totalCombinedDebt;
        [ObservableProperty] private decimal _totalCombinedAvailable;
        [ObservableProperty] private decimal _totalCombinedLimit;
        [ObservableProperty] private decimal _totalCurrentCycleToPay;

        // Form properties
        [ObservableProperty] private bool _showCardForm;
        [ObservableProperty] private bool _isEditingCard;
        [ObservableProperty] private string _editingCardId = string.Empty;
        [ObservableProperty] private string _newCardName = string.Empty;
        [ObservableProperty] private string _newBankName = string.Empty;
        [ObservableProperty] private string _newLastFourDigits = string.Empty;
        [ObservableProperty] private string _newCreditLimitInput = string.Empty;
        [ObservableProperty] private string _newColorHex = "#126E63";
        [ObservableProperty] private int _newCutOffDay = 15;
        [ObservableProperty] private int _newPaymentDay = 5;

        // Existing Card / Initial Usage properties
        [ObservableProperty] private bool _hasExistingBalance;
        [ObservableProperty] private string _initialTotalDebtInput = string.Empty;
        [ObservableProperty] private string _initialCurrentCycleDebtInput = string.Empty;
        [ObservableProperty] private bool _hasActiveMsi;
        [ObservableProperty] private string _initialMsiTitle = string.Empty;
        [ObservableProperty] private string _initialMsiMonthlyAmount = string.Empty;
        [ObservableProperty] private int _initialMsiPaidInstallments;
        [ObservableProperty] private int _initialMsiTotalInstallments = 12;
        [ObservableProperty] private ObservableCollection<PendingMsiPurchase> _pendingMsiPurchases = [];

        /// <summary>La hoja de captura la usa para saber si debe cerrarse.</summary>
        public bool LastMsiPurchaseWasAdded { get; private set; }

        public bool HasPendingMsiPurchases => PendingMsiPurchases.Count > 0;

        public string PendingMsiSummary => PendingMsiPurchases.Count == 0
            ? string.Empty
            : $"{PendingMsiPurchases.Count} compra{(PendingMsiPurchases.Count == 1 ? string.Empty : "s")} · ${PendingMsiPurchases.Sum(p => p.MonthlyAmount):N0} al mes";

        // ---- Vista previa de la compra que se esta capturando en la hoja ----

        private decimal DraftMonthlyAmount => ParseAmount(InitialMsiMonthlyAmount);

        private int DraftRemainingInstallments =>
            Math.Max(0, InitialMsiTotalInstallments - InitialMsiPaidInstallments);

        public bool HasMsiDraftPreview => DraftMonthlyAmount > 0;

        public string MsiDraftRemainingText =>
            $"Te faltan {DraftRemainingInstallments} de {InitialMsiTotalInstallments} mensualidades";

        public string MsiDraftDetailText =>
            $"Sumará ${DraftMonthlyAmount * DraftRemainingInstallments:N0} a la deuda de esta tarjeta";

        // ---- Resumen en vivo ----
        // El usuario captura el TOTAL usado (el dato que si puede leer en su banco)
        // y las compras a meses. Lo de contado se deduce restando; asi nadie tiene
        // que revisar movimiento por movimiento para separar una cosa de la otra.

        public decimal TotalUsedAmount => ParseAmount(InitialTotalDebtInput);

        private decimal MsiDebtAmount => PendingMsiPurchases.Sum(p => p.RemainingAmount);

        private decimal CashDebtAmount => Math.Max(0, TotalUsedAmount - MsiDebtAmount);

        private decimal PreviewCreditLimit => ParseAmount(NewCreditLimitInput);

        /// <summary>Si los MSI rebasan el total capturado, el total real es el de los MSI.</summary>
        public decimal PreviewTotalDebt => Math.Max(TotalUsedAmount, MsiDebtAmount);

        public bool ShowDebtBreakdown => HasExistingBalance && PreviewTotalDebt > 0;

        public string TotalUsedText => $"${TotalUsedAmount:N0}";

        public string MsiDebtText => $"−${MsiDebtAmount:N0}";

        public string MsiDebtLabel => PendingMsiPurchases.Count == 1
            ? "1 compra a meses"
            : $"{PendingMsiPurchases.Count} compras a meses";

        public string CashDebtText => $"${CashDebtAmount:N0}";

        public bool ShowAvailablePreview => ShowDebtBreakdown && PreviewCreditLimit > 0;

        public string PreviewAvailableText =>
            $"Te quedarían ${Math.Max(0, PreviewCreditLimit - PreviewTotalDebt):N0} disponibles de ${PreviewCreditLimit:N0}";

        /// <summary>Avisa cuando la deuda capturada ya rebasa el limite declarado.</summary>
        public bool ShowOverLimitWarning => PreviewCreditLimit > 0 && PreviewTotalDebt > PreviewCreditLimit;

        /// <summary>Las compras a meses no caben dentro del total que capturo: algo no cuadra.</summary>
        public bool ShowMsiExceedsTotalWarning => TotalUsedAmount > 0 && MsiDebtAmount > TotalUsedAmount;

        public string MsiExceedsTotalText =>
            $"Tus compras a meses suman ${MsiDebtAmount:N0}, más que los ${TotalUsedAmount:N0} que capturaste como usado. Revisa las cantidades.";

        [ObservableProperty] private ObservableCollection<int> _daysOfMonth = [];
        [ObservableProperty] private ObservableCollection<int> _installmentOptions = [3, 6, 9, 12, 18, 24, 36];
        [ObservableProperty] private ObservableCollection<int> _paidInstallmentOptions = [];
        [ObservableProperty] private ObservableCollection<string> _availableColors =
        [
            "#126E63", // Esmeralda / Verde
            "#1A73E8", // Azul Real
            "#D97706", // Oro / Ámbar
            "#7C3AED", // Púrpura / Violeta
            "#1F2937", // Grafito / Negro
            "#E11D48"  // Rubí / Rojo
        ];

        public string CardFormTitle => IsEditingCard ? "Editar Tarjeta" : "Nueva Tarjeta";
        public string SaveCardButtonText => IsEditingCard ? "Guardar Cambios" : "Agregar Tarjeta";

        public CreditCardsViewModel(
            ICreditCardService creditCardService,
            ISpendingService spendingService,
            IUserService userService,
            INavigationService navigationService,
            IReminderNotificationService reminderNotificationService,
            NewSpendingViewModel newSpendingVm)
        {
            _creditCardService = creditCardService;
            _spendingService = spendingService;
            _userService = userService;
            _navigationService = navigationService;
            _reminderNotificationService = reminderNotificationService;
            _newSpendingVm = newSpendingVm;

            DaysOfMonth.Clear();
            for (int i = 1; i <= 31; i++) DaysOfMonth.Add(i);

            UpdatePaidInstallmentOptions();
            EnsureSubscriptions();
        }

        private void EnsureSubscriptions()
        {
            if (_isInitialized) return;

            WeakReferenceMessenger.Default.Register<SpendingChangedMessage>(this, (_, _) =>
            {
                _ = GetData();
            });

            _isInitialized = true;
        }

        private static decimal ParseAmount(string? value) =>
            decimal.TryParse((value ?? string.Empty).Trim(), out var amount) ? amount : 0m;

        partial void OnInitialMsiTotalInstallmentsChanged(int value)
        {
            UpdatePaidInstallmentOptions();
            RefreshMsiDraftPreview();
        }

        partial void OnInitialMsiPaidInstallmentsChanged(int value) => RefreshMsiDraftPreview();

        partial void OnInitialMsiMonthlyAmountChanged(string value) => RefreshMsiDraftPreview();

        partial void OnInitialTotalDebtInputChanged(string value) => RefreshDebtPreview();

        partial void OnNewCreditLimitInputChanged(string value) => RefreshDebtPreview();

        partial void OnHasExistingBalanceChanged(bool value) => RefreshDebtPreview();

        /// <summary>
        /// Reconstruye las opciones y vuelve a fijar la seleccion. Limpiar la coleccion
        /// deja al Picker sin SelectedItem, y sin esto se queda mostrando un campo vacio.
        /// </summary>
        private void UpdatePaidInstallmentOptions()
        {
            var max = Math.Max(1, InitialMsiTotalInstallments);
            var desired = Math.Clamp(InitialMsiPaidInstallments, 0, max - 1);

            PaidInstallmentOptions.Clear();
            for (int i = 0; i <= max - 1; i++)
            {
                PaidInstallmentOptions.Add(i);
            }

            InitialMsiPaidInstallments = desired;
        }

        private void RefreshMsiDraftPreview()
        {
            OnPropertyChanged(nameof(HasMsiDraftPreview));
            OnPropertyChanged(nameof(MsiDraftRemainingText));
            OnPropertyChanged(nameof(MsiDraftDetailText));
        }

        private void RefreshDebtPreview()
        {
            OnPropertyChanged(nameof(ShowDebtBreakdown));
            OnPropertyChanged(nameof(TotalUsedAmount));
            OnPropertyChanged(nameof(TotalUsedText));
            OnPropertyChanged(nameof(MsiDebtText));
            OnPropertyChanged(nameof(MsiDebtLabel));
            OnPropertyChanged(nameof(CashDebtText));
            OnPropertyChanged(nameof(PreviewTotalDebt));
            OnPropertyChanged(nameof(ShowAvailablePreview));
            OnPropertyChanged(nameof(PreviewAvailableText));
            OnPropertyChanged(nameof(ShowOverLimitWarning));
            OnPropertyChanged(nameof(ShowMsiExceedsTotalWarning));
            OnPropertyChanged(nameof(MsiExceedsTotalText));
        }

        private void RefreshPendingMsiState()
        {
            OnPropertyChanged(nameof(HasPendingMsiPurchases));
            OnPropertyChanged(nameof(PendingMsiSummary));
            RefreshDebtPreview();
        }

        private void ResetMsiDraft()
        {
            InitialMsiTitle = string.Empty;
            InitialMsiMonthlyAmount = string.Empty;
            InitialMsiTotalInstallments = 12;
            InitialMsiPaidInstallments = 0;
            UpdatePaidInstallmentOptions();
            RefreshMsiDraftPreview();
        }

        /// <summary>
        /// Devuelve las compras ya agregadas a la lista mas, si aplica, la que el
        /// usuario dejo escrita en el formulario sin presionar "Agregar".
        /// </summary>
        private List<PendingMsiPurchase> CollectMsiPurchasesForSave() => PendingMsiPurchases.ToList();

        [RelayCommand]
        private async Task OpenMsiPurchaseSheet()
        {
            ResetMsiDraft();
            LastMsiPurchaseWasAdded = false;

            try
            {
                var sheet = new MsiPurchaseBottomSheet(this);
                await sheet.ShowAsync();
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine(ex);
            }
        }

        [RelayCommand]
        private async Task AddMsiPurchase()
        {
            LastMsiPurchaseWasAdded = false;

            var monthlyAmount = ParseAmount(InitialMsiMonthlyAmount);
            if (monthlyAmount <= 0)
            {
                await AlertHelper.ShowAlertAsync("Falta la mensualidad", "Ingresa cuánto pagas cada mes por esta compra.", "OK");
                return;
            }

            if (InitialMsiTotalInstallments <= 0)
            {
                await AlertHelper.ShowAlertAsync("Plazo inválido", "Selecciona a cuántos meses compraste.", "OK");
                return;
            }

            PendingMsiPurchases.Add(new PendingMsiPurchase
            {
                Title = InitialMsiTitle?.Trim() ?? string.Empty,
                MonthlyAmount = monthlyAmount,
                TotalInstallments = InitialMsiTotalInstallments,
                PaidInstallments = Math.Clamp(InitialMsiPaidInstallments, 0, InitialMsiTotalInstallments - 1)
            });

            LastMsiPurchaseWasAdded = true;
            ResetMsiDraft();
            RefreshPendingMsiState();
        }

        [RelayCommand]
        private void RemoveMsiPurchase(PendingMsiPurchase purchase)
        {
            if (purchase == null) return;

            PendingMsiPurchases.Remove(purchase);
            RefreshPendingMsiState();
        }

        public async Task GetData()
        {
            if (IsLoading) return;
            IsLoading = true;

            try
            {
                var summaries = await _creditCardService.GetAllCardSummariesAsync();
                CardSummaries = new ObservableCollection<CreditCardSummary>(summaries);

                HasCards = CardSummaries.Any();
                HasNoCards = !HasCards;

                TotalCombinedDebt = CardSummaries.Sum(c => c.TotalDebt);
                TotalCombinedAvailable = CardSummaries.Sum(c => c.AvailableCredit);
                TotalCombinedLimit = CardSummaries.Sum(c => c.CreditLimit);
                TotalCurrentCycleToPay = CardSummaries.Sum(c => c.CurrentCycleAmount);

                if (SelectedCardSummary != null)
                {
                    SelectedCardSummary = CardSummaries.FirstOrDefault(c => c.Card.CreditCardId == SelectedCardSummary.Card.CreditCardId)
                                          ?? CardSummaries.FirstOrDefault();
                }
                else
                {
                    SelectedCardSummary = CardSummaries.FirstOrDefault();
                }

                // Schedule card smart notifications
                _ = _reminderNotificationService.ScheduleCreditCardRemindersAsync(summaries);
            }
            finally
            {
                IsLoading = false;
            }
        }

        [RelayCommand]
        private void SelectCard(CreditCardSummary summary)
        {
            SelectedCardSummary = summary;
        }

        [RelayCommand]
        private void SelectColor(string color)
        {
            NewColorHex = color;
        }

        [RelayCommand]
        private void ToggleAddCardForm()
        {
            IsEditingCard = false;
            EditingCardId = string.Empty;
            NewCardName = string.Empty;
            NewBankName = string.Empty;
            NewLastFourDigits = string.Empty;
            NewCreditLimitInput = string.Empty;
            NewColorHex = "#126E63";
            NewCutOffDay = 15;
            NewPaymentDay = 5;

            HasExistingBalance = false;
            InitialTotalDebtInput = string.Empty;
            InitialCurrentCycleDebtInput = string.Empty;
            HasActiveMsi = false;
            PendingMsiPurchases.Clear();
            RefreshPendingMsiState();
            ResetMsiDraft();

            ShowCardForm = !ShowCardForm;
            OnPropertyChanged(nameof(CardFormTitle));
            OnPropertyChanged(nameof(SaveCardButtonText));
        }

        [RelayCommand]
        private void ToggleEditCardForm(CreditCard card)
        {
            if (card == null) return;

            IsEditingCard = true;
            EditingCardId = card.CreditCardId;
            NewCardName = card.CardName;
            NewBankName = card.BankName;
            NewLastFourDigits = card.LastFourDigits ?? string.Empty;
            NewCreditLimitInput = card.CreditLimit > 0 ? card.CreditLimit.ToString("F0") : string.Empty;
            NewColorHex = string.IsNullOrEmpty(card.ColorHex) ? "#126E63" : card.ColorHex;
            NewCutOffDay = card.CutOffDay;
            NewPaymentDay = card.PaymentDay;

            HasExistingBalance = false;
            InitialTotalDebtInput = string.Empty;
            InitialCurrentCycleDebtInput = string.Empty;
            HasActiveMsi = false;
            PendingMsiPurchases.Clear();
            RefreshPendingMsiState();
            ResetMsiDraft();

            ShowCardForm = true;
            OnPropertyChanged(nameof(CardFormTitle));
            OnPropertyChanged(nameof(SaveCardButtonText));
        }

        /// <summary>
        /// True cuando el formulario tiene algo capturado que se perderia al salir.
        /// </summary>
        public bool HasUnsavedCardData =>
            ShowCardForm && (
                !string.IsNullOrWhiteSpace(NewCardName)
                || !string.IsNullOrWhiteSpace(NewBankName)
                || !string.IsNullOrWhiteSpace(NewLastFourDigits)
                || !string.IsNullOrWhiteSpace(NewCreditLimitInput)
                || !string.IsNullOrWhiteSpace(InitialTotalDebtInput)
                || !string.IsNullOrWhiteSpace(InitialCurrentCycleDebtInput)
                || PendingMsiPurchases.Count > 0);

        /// <summary>
        /// Pregunta antes de tirar el formulario. Devuelve true si se puede salir.
        /// </summary>
        public async Task<bool> ConfirmDiscardCardFormAsync()
        {
            if (!HasUnsavedCardData)
                return true;

            var msiNote = PendingMsiPurchases.Count > 0
                ? $" Incluye {PendingMsiPurchases.Count} compra{(PendingMsiPurchases.Count == 1 ? string.Empty : "s")} a meses que capturaste."
                : string.Empty;

            return await AlertHelper.ShowAlertAsync(
                IsEditingCard ? "¿Descartar los cambios?" : "¿Descartar esta tarjeta?",
                $"Perderás los datos que llevas capturados.{msiNote}",
                "Descartar",
                "Seguir editando");
        }

        private void ClearCardForm()
        {
            ShowCardForm = false;
            IsEditingCard = false;
            EditingCardId = string.Empty;
            NewCardName = string.Empty;
            NewBankName = string.Empty;
            NewLastFourDigits = string.Empty;
            NewCreditLimitInput = string.Empty;
            HasExistingBalance = false;
            InitialTotalDebtInput = string.Empty;
            InitialCurrentCycleDebtInput = string.Empty;
            HasActiveMsi = false;
            PendingMsiPurchases.Clear();
            RefreshPendingMsiState();
            ResetMsiDraft();
        }

        [RelayCommand]
        private async Task CloseCardForm()
        {
            if (!await ConfirmDiscardCardFormAsync())
                return;

            ClearCardForm();
        }

        [RelayCommand]
        private async Task SaveCard()
        {
            var cardName = NewCardName?.Trim() ?? string.Empty;
            var bankName = NewBankName?.Trim() ?? string.Empty;
            var lastFour = NewLastFourDigits?.Trim() ?? string.Empty;

            if (string.IsNullOrWhiteSpace(cardName))
            {
                await AlertHelper.ShowAlertAsync("Error", "Ingresa un nombre para la tarjeta (Ej. Oro, Nu, Platino).", "OK");
                return;
            }

            if (string.IsNullOrWhiteSpace(bankName))
            {
                await AlertHelper.ShowAlertAsync("Error", "Ingresa el banco emisor (Ej. BBVA, Citibanamex, Santander).", "OK");
                return;
            }

            decimal creditLimit = 0;
            if (!string.IsNullOrWhiteSpace(NewCreditLimitInput))
            {
                _ = decimal.TryParse(NewCreditLimitInput.Trim(), out creditLimit);
            }

            var user = await _userService.GetUser();
            if (user == null || string.IsNullOrWhiteSpace(user.UserId))
            {
                await AlertHelper.ShowAlertAsync("Error", "No se encontró el usuario activo.", "OK");
                return;
            }

            var card = new CreditCard
            {
                CardName = cardName,
                BankName = bankName,
                LastFourDigits = string.IsNullOrEmpty(lastFour) ? null : lastFour,
                CutOffDay = NewCutOffDay,
                PaymentDay = NewPaymentDay,
                CreditLimit = creditLimit,
                ColorHex = string.IsNullOrEmpty(NewColorHex) ? "#126E63" : NewColorHex,
                UserId = user.UserId
            };

            if (IsEditingCard && !string.IsNullOrEmpty(EditingCardId))
            {
                card.CreditCardId = EditingCardId;
                var updated = await _creditCardService.UpdateCreditCardAsync(card);
                if (updated)
                {
                    ShowCardForm = false;
                    await GetData();
                    await Toast.Make("Tarjeta actualizada.", ToastDuration.Short).Show();
                }
                else
                {
                    await AlertHelper.ShowAlertAsync("Error", "No se pudo actualizar la tarjeta.", "OK");
                }
            }
            else
            {
                // Crear nueva tarjeta
                var createdCard = await _creditCardService.CreateCreditCardAsync(card);
                var cardId = createdCard.CreditCardId;

                // Si se configuró saldo inicial / deuda previa
                if (HasExistingBalance)
                {
                    var totalUsed = ParseAmount(InitialTotalDebtInput);
                    var currentCycleDebt = ParseAmount(InitialCurrentCycleDebtInput);

                    var categories = await _spendingService.GetCategoriesList();
                    var defaultCategory = categories.FirstOrDefault(c => c.IsDefaultCategory) ?? categories.FirstOrDefault();

                    var msiPurchases = HasActiveMsi ? CollectMsiPurchasesForSave() : [];
                    var msiOutstanding = msiPurchases.Sum(p => p.RemainingAmount);

                    // El usuario captura el TOTAL usado; las compras a meses ya son parte
                    // de ese total. Lo de contado es la diferencia, asi la suma de todo
                    // lo que registramos cuadra exactamente con lo que el reporto.
                    var cashDebt = Math.Max(0, totalUsed - msiOutstanding);

                    if (defaultCategory != null && cashDebt > 0)
                    {
                        var cycleAmount = Math.Min(currentCycleDebt, cashDebt);

                        if (cycleAmount > 0 && cycleAmount < cashDebt)
                        {
                            // Saldo en el corte actual
                            var cycleSpending = new Spending
                            {
                                Title = $"Saldo corte actual - {card.CardName}",
                                Description = "Saldo a pagar en corte actual registrado al crear la tarjeta",
                                Amount = cycleAmount,
                                CategoryId = defaultCategory.CategoryId,
                                Category = defaultCategory,
                                Date = DateTime.Now,
                                UserId = user.UserId,
                                IsCreditCard = true,
                                CreditCardId = cardId,
                                PaymentMethod = "CreditCard"
                            };
                            await _spendingService.CreateNewSpending(cycleSpending);

                            // Saldo restante previo
                            var priorSpending = new Spending
                            {
                                Title = $"Saldo acumulado previo - {card.CardName}",
                                Description = "Saldo acumulado anterior registrado al crear la tarjeta",
                                Amount = cashDebt - cycleAmount,
                                CategoryId = defaultCategory.CategoryId,
                                Category = defaultCategory,
                                Date = DateTime.Now.AddMonths(-2),
                                UserId = user.UserId,
                                IsCreditCard = true,
                                CreditCardId = cardId,
                                PaymentMethod = "CreditCard"
                            };
                            await _spendingService.CreateNewSpending(priorSpending);
                        }
                        else
                        {
                            var initialSpending = new Spending
                            {
                                Title = $"Saldo de contado - {card.CardName}",
                                Description = "Compras de contado pendientes al registrar la tarjeta en uso",
                                Amount = cashDebt,
                                CategoryId = defaultCategory.CategoryId,
                                Category = defaultCategory,
                                Date = DateTime.Now,
                                UserId = user.UserId,
                                IsCreditCard = true,
                                CreditCardId = cardId,
                                PaymentMethod = "CreditCard"
                            };
                            await _spendingService.CreateNewSpending(initialSpending);
                        }
                    }

                    // Compras a MSI que ya venian corriendo en la tarjeta.
                    // Se registra lo que FALTA por pagar, no el precio original, porque
                    // eso es lo que realmente sigue ocupando la linea de credito.
                    if (defaultCategory != null)
                    {
                        foreach (var msi in msiPurchases)
                        {
                            var msiSpending = new Spending
                            {
                                Title = string.IsNullOrWhiteSpace(msi.Title)
                                    ? $"Compra MSI previa - {card.CardName}"
                                    : msi.Title,
                                Description = $"Compra a MSI en curso ({msi.PaidInstallments} de {msi.TotalInstallments} pagadas)",
                                Amount = msi.RemainingAmount,
                                InstallmentMonthlyAmount = msi.MonthlyAmount,
                                CurrentInstallment = msi.PaidInstallments,
                                TotalInstallments = msi.TotalInstallments,
                                IsMsi = true,
                                CategoryId = defaultCategory.CategoryId,
                                Category = defaultCategory,
                                Date = DateTime.Now,
                                UserId = user.UserId,
                                IsCreditCard = true,
                                CreditCardId = cardId,
                                PaymentMethod = "CreditCard"
                            };
                            await _spendingService.CreateNewSpending(msiSpending);
                        }
                    }

                    WeakReferenceMessenger.Default.Send(new SpendingChangedMessage(string.Empty));
                }

                ShowCardForm = false;
                await GetData();
                await Toast.Make("Tarjeta agregada a tu Wallet.", ToastDuration.Short).Show();
            }
        }

        [RelayCommand]
        private async Task DeleteCard(CreditCard card)
        {
            if (card == null) return;

            var confirm = await AlertHelper.ShowAlertAsync(
                "Eliminar tarjeta",
                $"¿Seguro que deseas eliminar la tarjeta '{card.CardName}'?\nLos registros de compras se conservarán pero ya no estarán vinculados.",
                "Eliminar", "Cancelar");

            if (!confirm) return;

            var deleted = await _creditCardService.DeleteCreditCardAsync(card.CreditCardId);
            if (deleted)
            {
                await GetData();
                await Toast.Make("Tarjeta eliminada.", ToastDuration.Short).Show();
            }
            else
            {
                await AlertHelper.ShowAlertAsync("Error", "No se pudo eliminar la tarjeta.", "OK");
            }
        }

        [RelayCommand]
        private async Task AdjustCardBalance(CreditCardSummary summary)
        {
            if (summary?.Card == null) return;

            var mainPage = Application.Current?.MainPage;
            if (mainPage == null) return;

            var currentDebt = summary.TotalDebt;
            var promptMsg = $"Saldo actual registrado en Gastapp: ${currentDebt:N2}\n\nIngresa el saldo real/actual de tu tarjeta según tu banco:";

            var resultStr = await mainPage.DisplayPromptAsync(
                $"Ajustar Saldo ({summary.Card.CardName})",
                promptMsg,
                "Ajustar", "Cancelar",
                initialValue: currentDebt.ToString("F2"),
                keyboard: Keyboard.Numeric);

            if (string.IsNullOrWhiteSpace(resultStr)) return;

            if (!decimal.TryParse(resultStr, out var newBalance) || newBalance < 0)
            {
                await AlertHelper.ShowAlertAsync("Error", "Ingresa un monto de saldo válido (0 o mayor).", "OK");
                return;
            }

            var success = await _creditCardService.AdjustCardBalanceAsync(summary.Card.CreditCardId, newBalance);
            if (success)
            {
                WeakReferenceMessenger.Default.Send(new SpendingChangedMessage(string.Empty));
                await GetData();
                await Toast.Make($"Saldo de {summary.Card.CardName} ajustado a ${newBalance:N2}", ToastDuration.Short).Show();
            }
            else
            {
                await AlertHelper.ShowAlertAsync("Error", "No se pudo ajustar el saldo de la tarjeta.", "OK");
            }
        }

        [RelayCommand]
        private async Task PayCard(CreditCardSummary summary)
        {
            if (summary?.Card == null) return;

            var mainPage = Application.Current?.MainPage;
            if (mainPage == null) return;

            var suggestedAmount = summary.CurrentCycleAmount > 0 ? summary.CurrentCycleAmount : summary.TotalDebt;
            var promptMsg = summary.TotalDebt > 0
                ? $"Saldo total pendiente: ${summary.TotalDebt:N2}\nCorte actual: ${summary.CurrentCycleAmount:N2}\n\nIngresa el monto a pagar:"
                : "Ingresa el monto a registrar como pago de tu tarjeta:";

            var initialAmount = suggestedAmount > 0 ? suggestedAmount.ToString("F2") : "0.00";

            var resultStr = await mainPage.DisplayPromptAsync(
                $"Pagar {summary.Card.CardName}",
                promptMsg,
                "Registrar Pago", "Cancelar",
                initialValue: initialAmount,
                keyboard: Keyboard.Numeric);

            if (string.IsNullOrWhiteSpace(resultStr)) return;

            if (!decimal.TryParse(resultStr, out var amountToPay) || amountToPay <= 0)
            {
                await AlertHelper.ShowAlertAsync("Error", "Ingresa un monto válido mayor a 0.", "OK");
                return;
            }

            try
            {
                var categoriesList = await _spendingService.GetCategoriesList();
                var defaultCategory = categoriesList.FirstOrDefault(c => c.IsDefaultCategory)
                    ?? categoriesList.FirstOrDefault();

                if (defaultCategory == null)
                {
                    await AlertHelper.ShowAlertAsync("Error", "No se encontró categoría para registrar el pago.", "OK");
                    return;
                }

                var paymentSpending = new Spending
                {
                    Title = $"Pago TDC - {summary.Card.CardName}",
                    Description = $"Abono a tarjeta {summary.Card.BankName}",
                    Amount = amountToPay,
                    CategoryId = defaultCategory.CategoryId,
                    Date = DateTime.Now,
                    Category = defaultCategory,
                    UserId = summary.Card.UserId,
                    IsCreditCard = false,
                    CreditCardId = summary.Card.CreditCardId,
                    PaymentMethod = "Transfer"
                };

                await _spendingService.CreateNewSpending(paymentSpending);
                WeakReferenceMessenger.Default.Send(new SpendingChangedMessage(paymentSpending.SpendingId));

                await GetData();
                await Toast.Make($"Pago de ${amountToPay:N2} registrado.", ToastDuration.Short).Show();
            }
            catch (Exception ex)
            {
                await AlertHelper.ShowAlertAsync("Error", "No se pudo registrar el pago.", "OK");
                System.Diagnostics.Debug.WriteLine(ex);
            }
        }

        [RelayCommand]
        private async Task AddSpendingForCard(CreditCardSummary summary)
        {
            if (summary?.Card == null) return;
            try
            {
                var bs = new NewSpendingBottomSheet(_newSpendingVm);
                await _newSpendingVm.PrepareForCardSpending(summary.Card, isMsi: false);
                await bs.ShowAsync();
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine(ex);
            }
        }

        [RelayCommand]
        private async Task AddMsiSpendingForCard(CreditCardSummary summary)
        {
            if (summary?.Card == null) return;
            try
            {
                var bs = new NewSpendingBottomSheet(_newSpendingVm);
                await _newSpendingVm.PrepareForCardSpending(summary.Card, isMsi: true);
                await bs.ShowAsync();
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine(ex);
            }
        }

        [RelayCommand]
        private async Task OpenSpendingDetail(Spending spending)
        {
            if (spending == null) return;
            await _navigationService.GoToAsync(nameof(SpendingDetailPage) + $"?spendingId={spending.SpendingId}");
        }

        [RelayCommand]
        private async Task GoBack()
        {
            if (!await ConfirmDiscardCardFormAsync())
                return;

            await _navigationService.GoBackAsync();
        }
    }
}
