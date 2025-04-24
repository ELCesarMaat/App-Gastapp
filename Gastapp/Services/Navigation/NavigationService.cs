using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Gastapp.Services.Navigation;

namespace Gastapp.Services
{
    public class NavigationService : INavigationService
    {
        public async Task GoToAsync(string route, IDictionary<string, object>? parameters = null)
        {
            if (parameters != null)
                await Shell.Current.GoToAsync(route, parameters);
            else
                await Shell.Current.GoToAsync(route);
        }

        public async Task GoBackAsync()
        {
            await Shell.Current.GoToAsync("..", animate:true);
        }
    }

}
