using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Globalization;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Gastapp.Models;
using Gastapp.Services.Navigation;
using Gastapp.Services.UserService;

namespace Gastapp.ViewModels
{
    public partial class SettingsViewModel : ObservableObject
    {
        private readonly INavigationService _navService;
        private readonly IUserService _userService;
        [ObservableProperty] private User _user;
        [ObservableProperty] private bool _isWeekSelected;
        [ObservableProperty] private bool _isBiWeekSelected;
        [ObservableProperty] private bool _isMonthSelected;

        [ObservableProperty] private DayForWeek? _selectedWeekDay;

        [ObservableProperty] private ObservableCollection<DayForWeek> _listForWeek = new();

        [ObservableProperty] private ObservableCollection<int> _firstDayList = new();
        [ObservableProperty] private ObservableCollection<int> _secondDayList = new();

        [ObservableProperty] private int _selectedFirstDay;
        [ObservableProperty] private int _selectedSecondDay;

        public SettingsViewModel(INavigationService navService, IUserService userService)
        {
            _userService = userService;
            _navService = navService;
            _ = GetData();

            InitLists();
        }

        private void InitLists()
        {
            int count = 0;
            foreach (var day in DateTimeFormatInfo.CurrentInfo.DayNames)
            {
                ListForWeek.Add(new DayForWeek
                {
                    DayName = day,
                    DayNumber = count
                });
                count++;
            }

            //SelectedWeekDay = ListForWeek.FirstOrDefault();

            if (User.IncomeTypeId == 1)
                SelectedWeekDay = ListForWeek.FirstOrDefault(x => x.DayNumber == User.FirstPayDay);

            for (var i = 1; i <= 31; i++)
            {
                FirstDayList.Add(i);
                SecondDayList.Add(i);
            }

            //SelectedFirstDay = FirstDayList.First();
            //SelectedSecondDay = SecondDayList.First();

            if (User.IncomeTypeId == 2)
            {
                SelectedFirstDay = FirstDayList.FirstOrDefault(x => x == User.FirstPayDay);
                SelectedSecondDay = SecondDayList.FirstOrDefault(x => x == User.SecondPayDay);
            }

            if (User.IncomeTypeId == 3)
            {
                SelectedFirstDay = FirstDayList.FirstOrDefault(x => x == User.FirstPayDay);
            }
        }

        public async Task GetData()
        {
            User = await _userService.GetUser() ?? new User();
            switch (User.IncomeTypeId)
            {
                case 1:
                    IsWeekSelected = true;
                    break;
                case 2:
                    IsBiWeekSelected = true;
                    break;
                case 3:
                    IsMonthSelected = true;
                    break;
            }
        }

        [RelayCommand]
        private async Task SaveChanges()
        {
            if (IsWeekSelected)
            {
                User.IncomeTypeId = 1;
                User.FirstPayDay = SelectedWeekDay?.DayNumber;
                User.SecondPayDay = null;
            }
            else if (IsBiWeekSelected)
            {
                User.IncomeTypeId = 2;
                User.FirstPayDay = SelectedFirstDay;
                User.SecondPayDay = SelectedSecondDay;
            }
            else if (IsMonthSelected)
            {
                User.IncomeTypeId = 3;
                User.FirstPayDay = SelectedFirstDay;
                User.SecondPayDay = null;
            }

            await _userService.UpdateUser(User);
        }
    }
}