using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using CommunityToolkit.Maui.Extensions;
using CommunityToolkit.Maui.Views;

namespace Gastapp.Utils
{
    public class PagesUtils
    {
        private Popup? _popup;
        private Page? _page;
        public void ShowPopup(Popup popup)
        {
            _popup = popup;
            _page = Application.Current!.MainPage!;
            _page.ShowPopup(_popup);
        }
        public async Task ClosePopup()
        {
            if(_popup != null)
                await _popup.CloseAsync();
        }
    }
}
