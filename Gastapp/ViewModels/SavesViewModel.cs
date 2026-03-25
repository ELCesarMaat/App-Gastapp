using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using CommunityToolkit.Mvvm.Messaging;
using Gastapp.Messages;
using Gastapp.Models;
using Gastapp.Pages.Menu;
using Gastapp.Services.Navigation;
using Gastapp.Services.SpendingService;
using Gastapp.Services.UserService;

namespace Gastapp.ViewModels
{
    public partial class SavesViewModel(ISpendingService spendingService, IUserService userService, INavigationService navigationService) : ObservableObject
    {
        private static readonly string[] CategoryPalette =
        [
            "#126E63",
            "#1E8477",
            "#2F9D8F",
            "#F2A65A",
            "#E76F51",
            "#4D7CFE",
            "#7A8C52"
        ];

        public MainPageViewModel MainPageVm { get; set; } = null!;

        private readonly ISpendingService _spendingService = spendingService;
        private readonly IUserService _userService = userService;
        private readonly INavigationService _navigationService = navigationService;

        private DateTime _periodStart;
        private DateTime _periodEnd;

        private User? _user;
        private bool _isInitialized;
        private bool _isLoading;
        private bool _hasPendingRefresh;

        [ObservableProperty] private ObservableCollection<CategoryResume> _data = [];
        [ObservableProperty] private decimal _totalSpending;
        [ObservableProperty] private decimal _maxTotalSpending;
        [ObservableProperty] private decimal _percent;
        [ObservableProperty] private decimal _progressPercent;
        [ObservableProperty] private string _healthText = "Buena";
        [ObservableProperty] private string _healthColor = "#126E63";
        [ObservableProperty] private string _healthSurfaceColor = "#E7F7F0";
        [ObservableProperty] private string _healthTextColor = "#126E63";
        [ObservableProperty] private string _healthMessage = "Tus gastos estan bajo control.";
        [ObservableProperty] private string _periodLabel = string.Empty;
        [ObservableProperty] private string _periodCaption = string.Empty;
        [ObservableProperty] private string _topCategoryName = "Sin gastos";
        [ObservableProperty] private string _topCategorySummary = "Aun no registras movimientos en este periodo.";
        [ObservableProperty] private int _periodDayCount;

        public decimal RemainingBudget => MaxTotalSpending - TotalSpending;
        public decimal DailyAverage => PeriodDayCount > 0 ? Math.Round(TotalSpending / PeriodDayCount, 2) : 0;
        public bool HasSpendings => Data.Count > 0;
        public string BudgetBalanceTitle => RemainingBudget >= 0 ? "Disponible" : "Excedido";
        public string BudgetBalanceCaption => RemainingBudget >= 0
            ? "Todavia estas dentro del limite sugerido."
            : "Tus gastos ya superaron lo planeado para este periodo.";
        public string SpendingVsBudgetText => $"${TotalSpending:N2} de ${MaxTotalSpending:N2}";

        private void EnsureInitialized()
        {
            if (_isInitialized)
                return;

            WeakReferenceMessenger.Default.Register<SpendingChangedMessage>(this, (_, _) =>
            {
                _ = QueueRefreshAsync();
            });

            _isInitialized = true;
        }

        private async Task QueueRefreshAsync()
        {
            if (_isLoading)
            {
                _hasPendingRefresh = true;
                return;
            }

            await GetData();
        }

        public async Task GetData()
        {
            EnsureInitialized();

            if (_isLoading)
            {
                _hasPendingRefresh = true;
                return;
            }

            _isLoading = true;

            try
            {
                _user = await _userService.GetUser();
                if (_user == null)
                    return;

                var periodDays = await _spendingService.GetAllPeriodDays();
                var periodEnd = periodDays.FirstOrDefault()?.Date ?? DateTime.Today;
                var periodStart = periodDays.LastOrDefault()?.Date ?? periodEnd;

                PeriodDayCount = periodDays.Count > 0 ? periodDays.Count : 1;
                PeriodLabel = $"Periodo activo: {periodStart:dd MMM} - {periodEnd:dd MMM}";
                PeriodCaption = $"{PeriodDayCount} dias desde tu ultimo corte de ingresos.";

                _periodStart = periodStart;
                _periodEnd = periodEnd;

                var categories = await _spendingService.GetCategoryResumeByPeriod(periodStart, periodEnd);
                var totalSpending = categories.Sum(item => item.Amount);

                for (var index = 0; index < categories.Count; index++)
                {
                    var item = categories[index];
                    item.Percentage = totalSpending > 0
                        ? Math.Round(item.Amount / totalSpending * 100, 1)
                        : 0;
                    item.ProgressValue = totalSpending > 0
                        ? (double)(item.Amount / totalSpending)
                        : 0;
                    item.AccentColor = CategoryPalette[index % CategoryPalette.Length];
                }

                Data = new ObservableCollection<CategoryResume>(categories);

                TotalSpending = totalSpending;
                MaxTotalSpending = _user.Salary * (100 - _user.PercentSave) / 100;

                if (categories.Count > 0)
                {
                    var topCategory = categories[0];
                    TopCategoryName = topCategory.Name;
                    TopCategorySummary = $"${topCategory.Amount:N2} - {topCategory.Percentage:N1}% del gasto del periodo";
                }
                else
                {
                    TopCategoryName = "Sin gastos registrados";
                    TopCategorySummary = "Cuando agregues movimientos, aqui veras que categoria pesa mas.";
                }

                CheckHealth();
                NotifyDerivedProperties();
            }
            finally
            {
                _isLoading = false;
            }

            if (_hasPendingRefresh)
            {
                _hasPendingRefresh = false;
                await GetData();
            }
        }

        public void CheckHealth()
        {
            Percent = MaxTotalSpending > 0
                ? Math.Round(TotalSpending / MaxTotalSpending * 100, 2)
                : 0;
            ProgressPercent = Math.Min(Percent, 100);

            switch (Percent)
            {
                case >= 100:
                    HealthText = "Critica";
                    HealthColor = "#C62828";
                    HealthSurfaceColor = "#FDEAEA";
                    HealthTextColor = "#8E1D1D";
                    HealthMessage = "Ya rebasaste tu limite ideal. Conviene pausar gastos no esenciales.";
                    break;
                case >= 90:
                    HealthText = "Ajustada";
                    HealthColor = "#D97706";
                    HealthSurfaceColor = "#FFF4E5";
                    HealthTextColor = "#9A5A00";
                    HealthMessage = "Estas muy cerca del limite. Cualquier gasto extra puede desbalancear tu periodo.";
                    break;
                case >= 80:
                    HealthText = "Estable";
                    HealthColor = "#B68A12";
                    HealthSurfaceColor = "#FFF8D8";
                    HealthTextColor = "#7A5C00";
                    HealthMessage = "Vas bien, pero ya consumiste buena parte del presupuesto disponible.";
                    break;
                default:
                    HealthText = "Saludable";
                    HealthColor = "#E7F7F0";
                    HealthSurfaceColor = "#E7F7F0";
                    HealthTextColor = "#126E63";
                    HealthMessage = "Tus gastos siguen bajo control y todavia tienes margen para el resto del periodo.";
                    break;
            }

            OnPropertyChanged(nameof(HealthText));

            if (MainPageVm != null && MainPageVm.IsSavesSelected)
            {
                MainPageVm.ChangeStatusBarColor(HealthColor);
            }
        }

        [RelayCommand]
        private async Task OpenCategory(CategoryResume category)
        {
            var parameters = new Dictionary<string, object>
            {
                { "Category", category },
                { "PeriodStart", _periodStart },
                { "PeriodEnd", _periodEnd }
            };
            await _navigationService.GoToAsync(nameof(CategoryDetailPage), parameters);
        }

        private void NotifyDerivedProperties()
        {
            OnPropertyChanged(nameof(RemainingBudget));
            OnPropertyChanged(nameof(DailyAverage));
            OnPropertyChanged(nameof(HasSpendings));
            OnPropertyChanged(nameof(BudgetBalanceTitle));
            OnPropertyChanged(nameof(BudgetBalanceCaption));
            OnPropertyChanged(nameof(SpendingVsBudgetText));
        }
    }
}
