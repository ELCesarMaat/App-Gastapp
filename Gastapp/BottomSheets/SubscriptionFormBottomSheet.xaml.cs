using Gastapp.ViewModels;
using The49.Maui.BottomSheet;

namespace Gastapp.BottomSheets;

/// <summary>
/// Formulario de alta y edicion de suscripciones.
///
/// Mismo arreglo que <see cref="CreditCardFormBottomSheet"/>: hoja propia con su
/// scroll, compartiendo el SubscriptionsViewModel de la pagina para que no haya
/// dos copias de la misma logica.
/// </summary>
public partial class SubscriptionFormBottomSheet : BottomSheet
{
    public SubscriptionFormBottomSheet(SubscriptionsViewModel vm)
    {
        InitializeComponent();
        BindingContext = vm;
    }
}
