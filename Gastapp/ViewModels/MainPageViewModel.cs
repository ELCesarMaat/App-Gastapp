using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Gastapp.Services;

namespace Gastapp.ViewModels
{
	public partial class MainPageViewModel : ObservableObject
	{
		public INavigationService _navigationService;

		public MainPageViewModel(INavigationService navigationService)
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
			await _navigationService.GoToAsync("Login");
		}
	}
}
