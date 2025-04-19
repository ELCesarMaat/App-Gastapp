using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using CommunityToolkit.Maui.Alerts;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Gastapp.Pages;
using Gastapp.Pages.Register;
using Gastapp.Services;

namespace Gastapp.ViewModels
{
    public partial class RegisterViewModel : ObservableObject
    {
        private readonly IList<ContentView> _pasos;
        private INavigationService _navigationService;
        private DateTime _lastExitClick = DateTime.MinValue;

        [ObservableProperty]
        private int _pasoActual = 0;

        [ObservableProperty]
        private bool _puedeRetroceder = false;

        [ObservableProperty]
        private bool _canExitWithButton = false;

        public RegisterViewModel(INavigationService navigationService)
        {
            _pasos = new List<ContentView>
            {
                new RegisterAccount(),
                new RegisterName(),
                new RegisterBirthDate()
            };
            _navigationService = navigationService;
        }

        public async Task MostrarPaso(ContentView contenedor)
        {
            await contenedor.FadeTo(0, 150);
            contenedor.Content = _pasos[PasoActual].Content;
            await contenedor.FadeTo(1, 150);

            PuedeRetroceder = PasoActual > 0;
        }

        [RelayCommand]
        private void Next()
        {
            if (PasoActual < _pasos.Count - 1)
            {
                PasoActual++;
                ActualizarVista();
            }
            else
            {
                // Finalizar registro
            }
        }

        [RelayCommand]
        public async Task Previous()
        {
            if (PasoActual > 0)
            {
                PasoActual--;
                ActualizarVista();
            }
            else
            {
                await CheckExit();
            }
        }

        private async Task CheckExit()
        {
            if((DateTime.Now - _lastExitClick).TotalMilliseconds < 2000)
            {
                await _navigationService.GoBackAsync();
            }
            else
            {
                await Toast.Make("Presione nuevamente para salir del registro").Show();

                _lastExitClick = DateTime.Now;
            }
        }

        private void ActualizarVista()
        {
            if (Application.Current?.MainPage is Shell shell &&
                shell.CurrentPage is WizardRegister page)
            {
                MostrarPaso(page.FindByName<ContentView>("PasoContainer"));
            }
        }
    }
}
