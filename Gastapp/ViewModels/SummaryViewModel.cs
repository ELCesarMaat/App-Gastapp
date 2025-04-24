using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using CommunityToolkit.Maui.Alerts;
using CommunityToolkit.Maui.Core;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Gastapp.Models;
using Gastapp.Pages.Menu;
using Gastapp.Services;

namespace Gastapp.ViewModels
{
    public partial class SummaryViewModel : ObservableObject
    {
        public INavigationService NavigationService;

        [ObservableProperty] 
        private ObservableCollection<Spending> _spendings;

        [ObservableProperty] private decimal _totalAmount = 0;

        public SummaryViewModel(INavigationService navigationService)
        {
            NavigationService = navigationService;

            _spendings = new ObservableCollection<Spending>
            {
                new Spending
                {
                    Amount = 20,
                    Date = new DateTime(2025, 4, 20, 12, 40, 10),
                    Description = "Cena",
                    Title = "Tacos"
                },
                new Spending
                {
                    Amount = 45.5m,
                    Date = new DateTime(2025, 4, 18, 8, 15, 0),
                    Description = "Gasolina",
                    Title = "Llenado medio tanque"
                },
                new Spending
                {
                    Amount = 150,
                    Date = new DateTime(2025, 4, 17, 14, 0, 0),
                    Description = "Ropa",
                    Title = "Playera y pantalón"
                },
                new Spending
                {
                    Amount = 10,
                    Date = new DateTime(2025, 4, 19, 9, 30, 0),
                    Description = "Desayuno",
                    Title = "Tamales"
                },
                new Spending
                {
                    Amount = 60,
                    Date = new DateTime(2025, 4, 16, 19, 0, 0),
                    Description = "Salida con amigos",
                    Title = "Antro"
                },
                new Spending
                {
                    Amount = 120.75m,
                    Date = new DateTime(2025, 4, 15, 18, 20, 0),
                    Description = "Supermercado",
                    Title = "Despensa"
                },
                new Spending
                {
                    Amount = 7,
                    Date = new DateTime(2025, 4, 14, 11, 45, 0),
                    Description = "Café",
                    Title = "Starbucks"
                },
                new Spending
                {
                    Amount = 25,
                    Date = new DateTime(2025, 4, 13, 13, 10, 0),
                    Description = "Transporte",
                    Title = "Taxi"
                },
                new Spending
                {
                    Amount = 99.99m,
                    Date = new DateTime(2025, 4, 12, 17, 0, 0),
                    Description = "Electrónica",
                    Title = "Audífonos"
                },
                new Spending
                {
                    Amount = 300,
                    Date = new DateTime(2025, 4, 11, 20, 30, 0),
                    Description = "Regalo",
                    Title = "Cumpleaños de MJ"
                },
                new Spending
                {
                    Amount = 50,
                    Date = new DateTime(2025, 4, 10, 10, 0, 0),
                    Description = "Consultas médicas",
                    Title = "Dentista"
                }
            };
            _spendings.CollectionChanged += SpendingsOnCollectionChanged;
            TotalAmount = Spendings.Sum(s => s.Amount);
        }

        private void SpendingsOnCollectionChanged(object? sender, NotifyCollectionChangedEventArgs e)
        {
            TotalAmount = Spendings.Sum(s => s.Amount);
        }

        [RelayCommand]
        public void OnSpendingClicked(Spending? item)
        {
            if (item == null)
                return;
            NavigationService.GoToAsync(nameof(SpendingDetailPage));
        }

        [RelayCommand]
        public void DeleteSpending(Spending item)
        {
            Spendings.Remove(item);
        }
    }
}