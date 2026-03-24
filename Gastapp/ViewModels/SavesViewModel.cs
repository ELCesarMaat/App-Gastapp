using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using CommunityToolkit.Maui.Core.Extensions;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Messaging;
using Gastapp.Messages;
using Gastapp.Models;
using Gastapp.Services.SpendingService;
using Gastapp.Services.UserService;

namespace Gastapp.ViewModels
{
    public partial class SavesViewModel(ISpendingService spendingService, IUserService userService) : ObservableObject
    {
        public MainPageViewModel MainPageVm { get; set; } = null!;
        private readonly ISpendingService _spendingService = spendingService;
        private readonly IUserService _userService = userService;

        private User? _user;

        [ObservableProperty] private ObservableCollection<CategoryResume> _data = [];
        [ObservableProperty] private DateTime _selectedDay = DateTime.Now;


        [ObservableProperty] private decimal _totalSpending;
        [ObservableProperty] private decimal _maxTotalSpending;
        [ObservableProperty] private decimal _barMaxValue;

        [ObservableProperty] private string _healthText = "Buena";
        [ObservableProperty] private string _healthColor = "#61EFFF";
        [ObservableProperty] private string _healthTextColor = "#0F590F";
        [ObservableProperty] private decimal _percent;


        [ObservableProperty]
        private string _healthMessage = "¡Felicidades!, aún te mantienes al margen de tus gastos diarios";
        private bool _isInitialized;

        private void EnsureInitialized()
        {
            if (_isInitialized)
                return;

            WeakReferenceMessenger.Default.Register<SpendingChangedMessage>(this, (_, _) =>
            {
                _ = GetData();
            });

            _isInitialized = true;
        }


        public async Task GetData()
        {
            EnsureInitialized();
            _user = await _userService.GetUser();
            if (_user == null)
                return;

            Data.Clear();
            var days = await _spendingService.GetDaysWithSpendings();
            if (days.Count == 0)
            {
                TotalSpending = 0;
                BarMaxValue = 0;
                MaxTotalSpending = _user.Salary * (100 - _user.PercentSave) / 100;
                CheckHealth();
                return;
            }
            var firstDay = days.Min();
            var lastDay = days.Max();

            var res = await _spendingService.GetCategoryResumeByPeriod(firstDay, lastDay);
            foreach (var item in res)
            {
                Data.Add(item);
            }

            if (Data.Any())
            {
                TotalSpending = Data.Sum(s => s.Amount);
                BarMaxValue = Data.Max(c => c.Amount);
            }

            MaxTotalSpending = _user.Salary * (100 - _user.PercentSave)/100;
            CheckHealth();
        }




        public void CheckHealth()
        {
            if (TotalSpending > 0 && MaxTotalSpending > 0)
                Percent = TotalSpending / MaxTotalSpending * 100;

            switch (Percent)
            {
                case >= 100:
                {
                    HealthText = "Critico";
                    HealthColor = "#FF4444";
                    HealthMessage = "¡Advertencia!, tus gastos ya superaron el limite";

                    break;
                }
                case >= 90:
                {
                    HealthText = "Mala";
                    HealthColor = "#FFCCCC";
                    HealthMessage = "¡Cuidado!, tus gastos estan muy cerca del limite";
                    break;
                }
                case >= 80:
                {
                    HealthText = "Regular";
                    HealthColor = "#E6FFA2";
                    HealthMessage = "¡Ten cuidado!, tus gastos se estan acercando al limite";
                    break;
                }
                default:
                {
                    HealthText = "Buena";
                    HealthColor = "#61EFFF";
                    HealthTextColor = "#0F590F";
                    //HealthMessage = "¡Felicidades!, aún te mantienes al margen de tus gastos diarios";
                    HealthMessage = "";
                    break;
                }
            }

            OnPropertyChanged(nameof(HealthText));

            // Avoid hijacking status bar color when the current tab is not Saves.
            if (MainPageVm != null && MainPageVm.IsSavesSelected)
            {
                MainPageVm.ChangeStatusBarColor(HealthColor);
            }
        }
    }
}