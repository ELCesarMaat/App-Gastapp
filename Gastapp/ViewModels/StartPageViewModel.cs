using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Gastapp.Pages.OfflineRegister;
using Gastapp.Services;
using Gastapp.Services.Navigation;

namespace Gastapp.ViewModels
{
	public partial class StartPageViewModel : ObservableObject
	{
		public INavigationService _navigationService;

		public StartPageViewModel(INavigationService navigationService)
		{
			_navigationService = navigationService;
		}

		[RelayCommand]
		public async Task GoToRegister()
		{
			await _navigationService.GoToAsync("WizardRegister");
		}

		[RelayCommand]
		public async Task GoToLogin()
		{
			//await _navigationService.GoToAsync("Login");
		}

        [RelayCommand]
        public async Task GoToOfflineRegister()
        {
            await _navigationService.GoToAsync(nameof(WizardOfflineRegisterPage));
        }

		[RelayCommand]
		public async Task GoToMainPage()
		{
			await _navigationService.GoToAsync("//MainPage");
		}
	}
}
