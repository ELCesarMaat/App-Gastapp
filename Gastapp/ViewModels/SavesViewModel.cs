using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using CommunityToolkit.Maui.Core.Extensions;
using CommunityToolkit.Mvvm.ComponentModel;
using Gastapp.Models;
using Gastapp.Services.SpendingService;

namespace Gastapp.ViewModels
{
    public partial class SavesViewModel : ObservableObject
    {
        public MainPageViewModel MainPageVm { get; set; } = null!;
        private readonly ISpendingService _spendingService;
        [ObservableProperty] private ObservableCollection<CategoryResume> _data = new();
        [ObservableProperty] private DateTime _selectedDay = DateTime.Now;


        [ObservableProperty] private decimal _totalSpending;
        [ObservableProperty] private decimal _maxTotalSpending;

        [ObservableProperty] private string _healthText = "Buena";
        [ObservableProperty] private string _healthColor = "#61EFFF";
        [ObservableProperty] private string _healthTextColor = "#0F590F";

        [ObservableProperty]
        private string _healthMessage = "¡Felicidades!, aún te mantienes al margen de tus gastos diarios";


        public SavesViewModel(ISpendingService spendingService)
        {
            _spendingService = spendingService;
            MaxTotalSpending = 500m;
        }

        public async Task GetData()
        {
            Data.Clear();
            var res = await _spendingService.GetCategoryResumeByDay(SelectedDay);
            foreach (var item in res)
            {
                Data.Add(item);
            }
            TotalSpending = Data.Sum(s => s.Amount);
            CheckHealth();
        }


        public void CheckHealth()
        {
            switch (TotalSpending / MaxTotalSpending * 100)
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
            MainPageVm.ChangeStatusBarColor(HealthColor);
        }
    }
}