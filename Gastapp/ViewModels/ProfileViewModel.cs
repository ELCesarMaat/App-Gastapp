using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.DependencyInjection;
using CommunityToolkit.Mvvm.Input;
using Gastapp.Data;
using Gastapp.Models;
using Gastapp.Services.Navigation;
using Gastapp.Services.UserService;

namespace Gastapp.ViewModels
{
    public partial class ProfileViewModel : ObservableObject
    {
        [ObservableProperty] private User _user;
        private readonly INavigationService _navigationService;
        private readonly IUserService _userService;
        private GastappDbContext _db;
        private StartPageViewModel _startPageVm;
        public ProfileViewModel(INavigationService navigationService, IUserService userService, GastappDbContext db, StartPageViewModel startPageVm)
        {
            _navigationService = navigationService;
            _userService = userService;
            _db = db;
            _ = GetUser();
        }

        public async Task GetUser()
        {
            User = await _userService.GetUser() ?? new User();
        }

        [RelayCommand]
        private void Logout()
        {
            _db.DeleteDatabase();
            Application.Current.MainPage = new AppShell();

        }
    }
}
