using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
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
        public ProfileViewModel(INavigationService navigationService, IUserService userService)
        {
            _navigationService = navigationService;
            _userService = userService;

            _ = GetUser();
        }

        public async Task GetUser()
        {
            User = await _userService.GetUser() ?? new User();
        }
    }
}
