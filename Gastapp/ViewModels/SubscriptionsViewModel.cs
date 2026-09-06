using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using CommunityToolkit.Maui.Alerts;
using CommunityToolkit.Maui.Core;
using CommunityToolkit.Maui.Views;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using CommunityToolkit.Mvvm.Messaging;
using Gastapp.Messages;
using Gastapp.Models;
using Gastapp.Popups;
using Gastapp.Services;
using Gastapp.Services.Navigation;
using Gastapp.Services.SpendingService;
using Gastapp.Services.UserService;
using Gastapp.Utils;

namespace Gastapp.ViewModels
{
    /// <summary>
    /// Pantalla de suscripciones y membresias. Mismo reparto que
    /// <see cref="CreditCardsViewModel"/>: el servicio hace las cuentas y entrega
    /// summaries listos, y aqui solo queda el estado de la pantalla, el formulario
    /// y los comandos.
    /// </summary>
    public partial class SubscriptionsViewModel : ObservableObject
    {
        private readonly ISubscriptionService _subscriptionService;
        private readonly ICreditCardService _creditCardService;
        private readonly ISpendingService _spendingService;
        private readonly IUserService _userService;
        private readonly INavigationService _navigationService;

        [ObservableProperty] private ObservableCollection<SubscriptionSummary> _subscriptionSummaries = [];
        [ObservableProperty] private SubscriptionSummary? _selectedSubscriptionSummary;
        [ObservableProperty] private ObservableCollection<UpcomingCharge> _upcomingCharges = [];

        [ObservableProperty] private bool _hasSubscriptions;
        [ObservableProperty] private bool _hasNoSubscriptions = true;
        [ObservableProperty] private bool _isLoading;

        [ObservableProperty] private decimal _totalMonthlyCost;
        [ObservableProperty] private decimal _totalYearlyCost;
        [ObservableProperty] private int _activeSubscriptionsCount;
        [ObservableProperty] private int _trialSubscriptionsCount;
        [ObservableProperty] private int _pausedSubscriptionsCount;
        [ObservableProperty] private string _activeCountText = "0 activas";
        [ObservableProperty] private string _nextChargeHeadlineText = "Sin cobros programados.";

        partial void OnSelectedSubscriptionSummaryChanged(SubscriptionSummary? value) =>
            RefrescarMarcaDeSeleccion();

        // ---- Formulario ----

        [ObservableProperty] private bool _showSubscriptionForm;
        [ObservableProperty] private bool _isEditingSubscription;
        [ObservableProperty] private string _editingSubscriptionId = string.Empty;
        [ObservableProperty] private string _newServiceName = string.Empty;
        [ObservableProperty] private string _newPlanName = string.Empty;
        [ObservableProperty] private string _newAmountInput = string.Empty;
        [ObservableProperty] private DateTime _newFirstChargeDate = DateTime.Today;
        [ObservableProperty] private string _newColorHex = "#7C3AED";
        [ObservableProperty] private string _newNotes = string.Empty;
        [ObservableProperty] private bool _newIsTrial;
        [ObservableProperty] private DateTime _newTrialEndDate = DateTime.Today.AddDays(7);

        [ObservableProperty] private ObservableCollection<BillingCycleOption> _billingCycleOptions = [];
        [ObservableProperty] private BillingCycleOption? _selectedBillingCycleOption;

        [ObservableProperty] private ObservableCollection<SubscriptionPaymentMethodOption> _paymentMethodOptions = [];
        [ObservableProperty] private SubscriptionPaymentMethodOption? _selectedPaymentMethodOption;

        [ObservableProperty] private ObservableCollection<CreditCard> _creditCards = [];
        [ObservableProperty] private CreditCard? _selectedCreditCard;
        [ObservableProperty] private bool _hasCreditCards;
        [ObservableProperty] private bool _hasNoCreditCards = true;

        [ObservableProperty] private ObservableCollection<Category> _categories = [];
        [ObservableProperty] private Category? _selectedCategory;

        [ObservableProperty] private ObservableCollection<string> _availableColors =
        [
            "#7C3AED", // Púrpura / Violeta
            "#126E63", // Esmeralda / Verde
            "#1A73E8", // Azul Real
            "#D97706", // Oro / Ámbar
            "#1F2937", // Grafito / Negro
            "#E11D48"  // Rubí / Rojo
        ];

        /// <summary>El bloque de tarjeta solo aplica cuando el cobro es a credito.</summary>
        public bool IsCreditCardPayment =>
            SelectedPaymentMethodOption?.Value == SubscriptionPaymentMethods.CreditCard;

        public string SubscriptionFormTitle => IsEditingSubscription ? "Editar suscripción" : "Nueva suscripción";
        public string SaveSubscriptionButtonText => IsEditingSubscription ? "Guardar cambios" : "Agregar suscripción";

        // ---- Vista previa del formulario ----
        // Mismo criterio que el preview de ciclo de las tarjetas: en vez de pedirle al
        // usuario que adivine a que fechas equivale lo que capturo, se le enseña el
        // resultado antes de guardar.

        private decimal DraftAmount => ParseAmount(NewAmountInput);

        private string DraftCycle => SelectedBillingCycleOption?.Value ?? SubscriptionBillingCycles.Monthly;

        public bool ShowAmountPreview => DraftAmount > 0;

        public string ChargePreviewText
        {
            get
            {
                var hoy = DateTime.Today;
                var reference = NewIsTrial && NewTrialEndDate.Date > hoy ? NewTrialEndDate.Date : hoy;

                var primero = _subscriptionService.CalculateNextChargeDate(NewFirstChargeDate, DraftCycle, reference);
                var segundo = _subscriptionService.CalculateNextChargeDate(NewFirstChargeDate, DraftCycle, primero.AddDays(1));

                return $"Próximo cobro: {primero:dd/MMM/yyyy}  ·  Después: {segundo:dd/MMM/yyyy}";
            }
        }

        public string MonthlyEquivalentPreviewText
        {
            get
            {
                var mensual = _subscriptionService.GetMonthlyEquivalent(DraftAmount, DraftCycle);
                return $"Equivale a ${mensual:N2} al mes  ·  ${mensual * 12:N2} al año";
            }
        }

        public string TrialPreviewText => NewIsTrial
            ? $"Durante la prueba no se cobra nada. El primer cargo real cae después del {NewTrialEndDate:dd/MMM/yyyy}."
            : string.Empty;

        public SubscriptionsViewModel(
            ISubscriptionService subscriptionService,
            ICreditCardService creditCardService,
            ISpendingService spendingService,
            IUserService userService,
            INavigationService navigationService)
        {
            _subscriptionService = subscriptionService;
            _creditCardService = creditCardService;
            _spendingService = spendingService;
            _userService = userService;
            _navigationService = navigationService;

            BuildPickerOptions();
        }

        private void BuildPickerOptions()
        {
            BillingCycleOptions.Clear();
            BillingCycleOptions.Add(new BillingCycleOption { Value = SubscriptionBillingCycles.Weekly, Label = "Semanal" });
            BillingCycleOptions.Add(new BillingCycleOption { Value = SubscriptionBillingCycles.Monthly, Label = "Mensual" });
            BillingCycleOptions.Add(new BillingCycleOption { Value = SubscriptionBillingCycles.Quarterly, Label = "Trimestral" });
            BillingCycleOptions.Add(new BillingCycleOption { Value = SubscriptionBillingCycles.Semiannual, Label = "Semestral" });
            BillingCycleOptions.Add(new BillingCycleOption { Value = SubscriptionBillingCycles.Yearly, Label = "Anual" });
            SelectedBillingCycleOption = BillingCycleOptions.FirstOrDefault(o => o.Value == SubscriptionBillingCycles.Monthly);

            // Las mismas cuatro que la hoja de gasto normal, con las mismas etiquetas
            // que muestra el detalle del gasto. Juntarlas (como estaban "Transferencia
            // / débito") hacia que el detalle mostrara "Transferencia bancaria" a quien
            // habia elegido debito.
            PaymentMethodOptions.Clear();
            PaymentMethodOptions.Add(new SubscriptionPaymentMethodOption { Value = SubscriptionPaymentMethods.CreditCard, Label = "Tarjeta de crédito" });
            PaymentMethodOptions.Add(new SubscriptionPaymentMethodOption { Value = SubscriptionPaymentMethods.Debit, Label = "Tarjeta de débito" });
            PaymentMethodOptions.Add(new SubscriptionPaymentMethodOption { Value = SubscriptionPaymentMethods.Transfer, Label = "Transferencia bancaria" });
            PaymentMethodOptions.Add(new SubscriptionPaymentMethodOption { Value = SubscriptionPaymentMethods.Cash, Label = "Efectivo" });
            SelectedPaymentMethodOption = PaymentMethodOptions.FirstOrDefault();
        }

        private static decimal ParseAmount(string? value) =>
            decimal.TryParse((value ?? string.Empty).Trim(), out var amount) ? amount : 0m;

        // ---- Refrescos de la vista previa ----

        partial void OnNewAmountInputChanged(string value) => RefreshFormPreview();

        partial void OnNewFirstChargeDateChanged(DateTime value) => RefreshFormPreview();

        partial void OnNewIsTrialChanged(bool value) => RefreshFormPreview();

        partial void OnNewTrialEndDateChanged(DateTime value) => RefreshFormPreview();

        partial void OnSelectedBillingCycleOptionChanged(BillingCycleOption? value) => RefreshFormPreview();

        partial void OnSelectedPaymentMethodOptionChanged(SubscriptionPaymentMethodOption? value) =>
            OnPropertyChanged(nameof(IsCreditCardPayment));

        private void RefreshFormPreview()
        {
            OnPropertyChanged(nameof(ShowAmountPreview));
            OnPropertyChanged(nameof(ChargePreviewText));
            OnPropertyChanged(nameof(MonthlyEquivalentPreviewText));
            OnPropertyChanged(nameof(TrialPreviewText));
        }

        // ---- Carga ----

        public async Task GetData()
        {
            if (IsLoading) return;
            IsLoading = true;

            try
            {
                var summaries = await _subscriptionService.GetAllSubscriptionSummariesAsync();
                SubscriptionSummaries = new ObservableCollection<SubscriptionSummary>(summaries);

                HasSubscriptions = SubscriptionSummaries.Any();
                HasNoSubscriptions = !HasSubscriptions;

                // Los totales suman solo lo que ya se esta pagando: una pausada no
                // cuenta, y una en prueba gratis tampoco. El conteo del chip si las
                // separa, para que el usuario entienda por que el total no las incluye.
                var cobrando = summaries.Where(s => s.CountsTowardTotals).ToList();
                TotalMonthlyCost = cobrando.Sum(s => s.MonthlyEquivalent);
                TotalYearlyCost = cobrando.Sum(s => s.YearlyEquivalent);

                ActiveSubscriptionsCount = cobrando.Count;
                TrialSubscriptionsCount = summaries.Count(s => s.IsTrialActive);
                PausedSubscriptionsCount = summaries.Count(s => !s.IsActive);

                ActiveCountText = ActiveSubscriptionsCount == 1
                    ? "1 activa"
                    : $"{ActiveSubscriptionsCount} activas";

                if (TrialSubscriptionsCount > 0)
                    ActiveCountText += $" · {TrialSubscriptionsCount} en prueba";

                if (PausedSubscriptionsCount > 0)
                    ActiveCountText += $" · {PausedSubscriptionsCount} pausada{(PausedSubscriptionsCount == 1 ? "" : "s")}";

                var charges = await _subscriptionService.GetUpcomingChargesAsync();
                UpcomingCharges = new ObservableCollection<UpcomingCharge>(charges);

                var siguiente = charges.FirstOrDefault();
                NextChargeHeadlineText = siguiente == null
                    ? "Sin cobros programados en los próximos días."
                    : $"Siguiente cobro: {siguiente.ServiceName} · ${siguiente.Amount:N2} · {siguiente.WhenText.ToLowerInvariant()}";

                // La seleccion sobrevive a la recarga: si la suscripcion sigue ahi se
                // vuelve a marcar la misma, no la primera de la lista.
                if (SelectedSubscriptionSummary != null)
                {
                    SelectedSubscriptionSummary =
                        SubscriptionSummaries.FirstOrDefault(s => s.Subscription.SubscriptionId == SelectedSubscriptionSummary.Subscription.SubscriptionId)
                        ?? SubscriptionSummaries.FirstOrDefault();
                }
                else
                {
                    SelectedSubscriptionSummary = SubscriptionSummaries.FirstOrDefault();
                }

                await LoadFormCatalogsAsync();
            }
            finally
            {
                IsLoading = false;
            }
        }

        /// <summary>Tarjetas y categorias que alimentan los Pickers del formulario.</summary>
        private async Task LoadFormCatalogsAsync()
        {
            var cards = await _creditCardService.GetAllCreditCardsAsync();
            CreditCards = new ObservableCollection<CreditCard>(cards);
            HasCreditCards = CreditCards.Any();
            HasNoCreditCards = !HasCreditCards;

            var categories = await _spendingService.GetCategoriesList();
            Categories = new ObservableCollection<Category>(categories);
        }

        [RelayCommand]
        private void SelectSubscription(SubscriptionSummary summary)
        {
            if (summary == null)
                return;

            // Tocar la suscripcion ya seleccionada la deselecciona y esconde el
            // detalle, en vez de dejarla marcada sin forma de salir.
            var yaEstaba = SelectedSubscriptionSummary?.Subscription.SubscriptionId == summary.Subscription.SubscriptionId;
            SelectedSubscriptionSummary = yaEstaba ? null : summary;
        }

        /// <summary>
        /// Deja marcada solo la suscripcion elegida. Se llama desde el setter para que
        /// el indicador siga a la seleccion venga de donde venga: un toque, recargar la
        /// lista o borrar una suscripcion.
        /// </summary>
        private void RefrescarMarcaDeSeleccion()
        {
            var seleccionada = SelectedSubscriptionSummary?.Subscription.SubscriptionId;

            foreach (var item in SubscriptionSummaries)
                item.IsSelected = item.Subscription.SubscriptionId == seleccionada;
        }

        [RelayCommand]
        private void SelectColor(string color)
        {
            NewColorHex = color;
        }

        // ---- Formulario ----

        [RelayCommand]
        private void ToggleAddSubscriptionForm()
        {
            IsEditingSubscription = false;
            EditingSubscriptionId = string.Empty;
            ResetFormFields();

            ShowSubscriptionForm = !ShowSubscriptionForm;
            OnPropertyChanged(nameof(SubscriptionFormTitle));
            OnPropertyChanged(nameof(SaveSubscriptionButtonText));
        }

        [RelayCommand]
        private void ToggleEditSubscriptionForm(Subscription subscription)
        {
            if (subscription == null) return;

            IsEditingSubscription = true;
            EditingSubscriptionId = subscription.SubscriptionId;

            NewServiceName = subscription.ServiceName;
            NewPlanName = subscription.PlanName ?? string.Empty;
            NewAmountInput = subscription.Amount > 0 ? subscription.Amount.ToString("F2") : string.Empty;
            NewFirstChargeDate = subscription.FirstChargeDate;
            NewColorHex = string.IsNullOrEmpty(subscription.ColorHex) ? "#7C3AED" : subscription.ColorHex;
            NewNotes = subscription.Notes ?? string.Empty;
            NewIsTrial = subscription.IsTrial;
            NewTrialEndDate = subscription.TrialEndDate ?? DateTime.Today.AddDays(7);

            SelectedBillingCycleOption = BillingCycleOptions.FirstOrDefault(o => o.Value == subscription.BillingCycle)
                                         ?? BillingCycleOptions.FirstOrDefault();

            SelectedPaymentMethodOption = PaymentMethodOptions.FirstOrDefault(o => o.Value == subscription.PaymentMethod)
                                          ?? PaymentMethodOptions.FirstOrDefault();

            SelectedCreditCard = CreditCards.FirstOrDefault(c => c.CreditCardId == subscription.CreditCardId);
            SelectedCategory = Categories.FirstOrDefault(c => c.CategoryId == subscription.CategoryId);

            RefreshFormPreview();

            ShowSubscriptionForm = true;
            OnPropertyChanged(nameof(SubscriptionFormTitle));
            OnPropertyChanged(nameof(SaveSubscriptionButtonText));
        }

        private void ResetFormFields()
        {
            NewServiceName = string.Empty;
            NewPlanName = string.Empty;
            NewAmountInput = string.Empty;
            NewFirstChargeDate = DateTime.Today;
            NewColorHex = "#7C3AED";
            NewNotes = string.Empty;
            NewIsTrial = false;
            NewTrialEndDate = DateTime.Today.AddDays(7);

            SelectedBillingCycleOption = BillingCycleOptions.FirstOrDefault(o => o.Value == SubscriptionBillingCycles.Monthly);
            SelectedPaymentMethodOption = PaymentMethodOptions.FirstOrDefault();
            SelectedCreditCard = CreditCards.FirstOrDefault();
            SelectedCategory = Categories.FirstOrDefault(c => c.IsDefaultCategory) ?? Categories.FirstOrDefault();

            RefreshFormPreview();
        }

        /// <summary>
        /// True cuando el formulario tiene algo capturado que se perderia al salir.
        /// </summary>
        public bool HasUnsavedSubscriptionData =>
            ShowSubscriptionForm && (
                !string.IsNullOrWhiteSpace(NewServiceName)
                || !string.IsNullOrWhiteSpace(NewPlanName)
                || !string.IsNullOrWhiteSpace(NewAmountInput)
                || !string.IsNullOrWhiteSpace(NewNotes));

        /// <summary>
        /// Pregunta antes de tirar el formulario. Devuelve true si se puede salir.
        /// </summary>
        public async Task<bool> ConfirmDiscardSubscriptionFormAsync()
        {
            if (!HasUnsavedSubscriptionData)
                return true;

            return await AlertHelper.ShowAlertAsync(
                IsEditingSubscription ? "¿Descartar los cambios?" : "¿Descartar esta suscripción?",
                "Perderás los datos que llevas capturados.",
                "Descartar",
                "Seguir editando");
        }

        private void ClearSubscriptionForm()
        {
            ShowSubscriptionForm = false;
            IsEditingSubscription = false;
            EditingSubscriptionId = string.Empty;
            ResetFormFields();
        }

        [RelayCommand]
        private async Task CloseSubscriptionForm()
        {
            if (!await ConfirmDiscardSubscriptionFormAsync())
                return;

            ClearSubscriptionForm();
        }

        [RelayCommand]
        private async Task SaveSubscription()
        {
            var serviceName = NewServiceName?.Trim() ?? string.Empty;
            var planName = NewPlanName?.Trim() ?? string.Empty;

            if (string.IsNullOrWhiteSpace(serviceName))
            {
                await AlertHelper.ShowAlertAsync("Error", "Ingresa el nombre del servicio (Ej. Netflix, Spotify, gimnasio).", "OK");
                return;
            }

            var amount = ParseAmount(NewAmountInput);
            if (amount <= 0)
            {
                await AlertHelper.ShowAlertAsync("Error", "Ingresa cuánto te cobran en cada periodo.", "OK");
                return;
            }

            var paymentMethod = SelectedPaymentMethodOption?.Value ?? SubscriptionPaymentMethods.Cash;

            if (paymentMethod == SubscriptionPaymentMethods.CreditCard && SelectedCreditCard == null)
            {
                await AlertHelper.ShowAlertAsync(
                    "Falta la tarjeta",
                    "Elegiste cobro a tarjeta de crédito pero no seleccionaste ninguna. Agrégala en 'Mis Tarjetas' o cambia la forma de pago.",
                    "OK");
                return;
            }

            if (NewIsTrial && NewTrialEndDate.Date < DateTime.Today)
            {
                await AlertHelper.ShowAlertAsync("Revisa la prueba", "La prueba gratis no puede terminar en una fecha que ya pasó.", "OK");
                return;
            }

            var user = await _userService.GetUser();
            if (user == null || string.IsNullOrWhiteSpace(user.UserId))
            {
                await AlertHelper.ShowAlertAsync("Error", "No se encontró el usuario activo.", "OK");
                return;
            }

            var subscription = new Subscription
            {
                UserId = user.UserId,
                ServiceName = serviceName,
                PlanName = string.IsNullOrEmpty(planName) ? null : planName,
                Amount = amount,
                BillingCycle = SelectedBillingCycleOption?.Value ?? SubscriptionBillingCycles.Monthly,
                FirstChargeDate = NewFirstChargeDate.Date,
                PaymentMethod = paymentMethod,
                CreditCardId = paymentMethod == SubscriptionPaymentMethods.CreditCard ? SelectedCreditCard?.CreditCardId : null,
                CategoryId = SelectedCategory?.CategoryId,
                IsTrial = NewIsTrial,
                TrialEndDate = NewIsTrial ? NewTrialEndDate.Date : null,
                ColorHex = string.IsNullOrEmpty(NewColorHex) ? "#7C3AED" : NewColorHex,
                Notes = string.IsNullOrWhiteSpace(NewNotes) ? null : NewNotes.Trim()
            };

            if (IsEditingSubscription && !string.IsNullOrEmpty(EditingSubscriptionId))
            {
                subscription.SubscriptionId = EditingSubscriptionId;

                var updated = await _subscriptionService.UpdateSubscriptionAsync(subscription);
                if (updated)
                {
                    ShowSubscriptionForm = false;
                    await GetData();
                    await Toast.Make("Suscripción actualizada.", ToastDuration.Short).Show();
                }
                else
                {
                    await AlertHelper.ShowAlertAsync("Error", "No se pudo actualizar la suscripción.", "OK");
                }

                return;
            }

            await _subscriptionService.CreateSubscriptionAsync(subscription);

            ShowSubscriptionForm = false;
            await GetData();
            await Toast.Make($"{serviceName} agregado a tus suscripciones.", ToastDuration.Short).Show();
        }

        [RelayCommand]
        private async Task DeleteSubscription(Subscription subscription)
        {
            if (subscription == null) return;

            var confirm = await AlertHelper.ShowAlertAsync(
                "Eliminar suscripción",
                $"¿Seguro que deseas eliminar '{subscription.ServiceName}'?\nLos gastos que ya registraste se conservarán.",
                "Eliminar", "Cancelar");

            if (!confirm) return;

            var deleted = await _subscriptionService.DeleteSubscriptionAsync(subscription.SubscriptionId);
            if (deleted)
            {
                // Si la borrada era la seleccionada, no tiene caso seguir mostrando su
                // detalle: se suelta la seleccion y GetData elige la primera que quede.
                if (SelectedSubscriptionSummary?.Subscription.SubscriptionId == subscription.SubscriptionId)
                    SelectedSubscriptionSummary = null;

                await GetData();
                await Toast.Make("Suscripción eliminada.", ToastDuration.Short).Show();
            }
            else
            {
                await AlertHelper.ShowAlertAsync("Error", "No se pudo eliminar la suscripción.", "OK");
            }
        }

        [RelayCommand]
        private async Task ToggleSubscriptionState(SubscriptionSummary summary)
        {
            if (summary?.Subscription == null) return;

            var activar = !summary.Subscription.IsActive;

            var ok = await _subscriptionService.SetActiveStateAsync(summary.Subscription.SubscriptionId, activar);
            if (!ok)
            {
                await AlertHelper.ShowAlertAsync("Error", "No se pudo cambiar el estado de la suscripción.", "OK");
                return;
            }

            await GetData();
            await Toast.Make(
                activar
                    ? $"{summary.Subscription.ServiceName} vuelve a contar en tus totales."
                    : $"{summary.Subscription.ServiceName} quedó en pausa.",
                ToastDuration.Short).Show();
        }

        /// <summary>
        /// Registra el cobro del ciclo como un gasto normal. Es el equivalente al
        /// "Registrar pago" de las tarjetas: la suscripcion no mueve dinero por si
        /// sola, lo que mueve dinero es el <see cref="Spending"/> que se crea aqui.
        /// </summary>
        [RelayCommand]
        private async Task RegisterCharge(SubscriptionSummary summary)
        {
            if (summary?.Subscription == null) return;

            var mainPage = Application.Current?.MainPage;
            if (mainPage == null) return;

            var subscription = summary.Subscription;

            // Nada impide registrar el mismo cobro dos veces (un toque de mas, o creer
            // que no se guardo), y eso duplicaria el gasto en silencio. Se avisa, pero
            // no se bloquea: hay casos legitimos, como un cargo doble del proveedor.
            if (summary.IsCurrentCycleCharged)
            {
                var continuar = await AlertHelper.ShowAlertAsync(
                    "Este cobro ya está registrado",
                    $"{summary.LastChargeText} Si registras otro, se sumará como un gasto aparte.",
                    "Registrar otro",
                    "Cancelar");

                if (!continuar) return;
            }

            var contextRows = new List<AmountContextRow>
            {
                new("Costo del periodo", $"${subscription.Amount:N2}", isHighlighted: true),
                new("Periodicidad", summary.BillingCycleText),
                new("Próximo cobro", summary.NextChargeDate.ToString("dd/MMM/yyyy"))
            };

            if (summary.HasLinkedCard)
                contextRows.Add(new AmountContextRow("Se carga a", summary.LinkedCardName));

            var popup = new AmountInputPopup(
                title: "Registrar cobro",
                subtitle: $"{subscription.ServiceName}{(string.IsNullOrWhiteSpace(subscription.PlanName) ? string.Empty : $" · {subscription.PlanName}")}",
                iconResourceKey: "IconTv",
                fieldCaption: "Monto cobrado",
                confirmText: "Registrar cobro",
                cancelText: "Cancelar",
                initialAmount: subscription.Amount,
                contextRows: contextRows,
                quickOptions: [new AmountQuickOption("Costo del periodo", subscription.Amount)],
                allowZero: false);

            // El popup ya valida el monto; devuelve null si el usuario cancela
            if (await mainPage.ShowPopupAsync(popup) is not decimal amountCharged) return;

            try
            {
                var categories = await _spendingService.GetCategoriesList();
                var category = categories.FirstOrDefault(c => c.CategoryId == subscription.CategoryId)
                               ?? categories.FirstOrDefault(c => c.IsDefaultCategory)
                               ?? categories.FirstOrDefault();

                if (category == null)
                {
                    await AlertHelper.ShowAlertAsync("Error", "No se encontró categoría para registrar el cobro.", "OK");
                    return;
                }

                var esTarjeta = subscription.PaymentMethod == SubscriptionPaymentMethods.CreditCard
                                && !string.IsNullOrWhiteSpace(subscription.CreditCardId);

                var spending = new Spending
                {
                    Title = $"Suscripción - {subscription.ServiceName}",
                    Description = string.IsNullOrWhiteSpace(subscription.PlanName)
                        ? $"Cobro {summary.BillingCycleText.ToLowerInvariant()} de {subscription.ServiceName}"
                        : $"Cobro {summary.BillingCycleText.ToLowerInvariant()} de {subscription.ServiceName} ({subscription.PlanName})",
                    Amount = amountCharged,
                    CategoryId = category.CategoryId,
                    Category = category,
                    Date = DateTime.Now,
                    UserId = subscription.UserId,
                    // Cobrarse a una tarjeta significa que suma a la deuda de esa
                    // tarjeta, igual que cualquier otra compra a credito.
                    IsCreditCard = esTarjeta,
                    CreditCardId = esTarjeta ? subscription.CreditCardId : null,
                    PaymentMethod = subscription.PaymentMethod
                };

                await _spendingService.CreateNewSpending(spending);
                await _subscriptionService.MarkChargeRegisteredAsync(subscription.SubscriptionId, spending.Date);

                WeakReferenceMessenger.Default.Send(new SpendingChangedMessage(spending.SpendingId));

                await GetData();
                await Toast.Make($"Cobro de ${amountCharged:N2} registrado.", ToastDuration.Short).Show();
            }
            catch (Exception ex)
            {
                await AlertHelper.ShowAlertAsync("Error", "No se pudo registrar el cobro.", "OK");
                System.Diagnostics.Debug.WriteLine(ex);
            }
        }

        [RelayCommand]
        private async Task GoBack()
        {
            if (!await ConfirmDiscardSubscriptionFormAsync())
                return;

            await _navigationService.GoBackAsync();
        }
    }
}
