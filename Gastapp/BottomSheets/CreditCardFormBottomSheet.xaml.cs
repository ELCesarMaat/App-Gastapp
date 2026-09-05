using Gastapp.ViewModels;
using The49.Maui.BottomSheet;

namespace Gastapp.BottomSheets;

/// <summary>
/// Formulario de alta y edicion de tarjetas.
///
/// Vivia incrustado en CreditCardsPage, debajo del resto del contenido, y quedaba
/// apretado contra las tarjetas. Aqui tiene su propia hoja con scroll independiente.
/// Comparte el mismo CreditCardsViewModel, asi que toda la logica sigue igual.
/// </summary>
public partial class CreditCardFormBottomSheet : BottomSheet
{
    public CreditCardFormBottomSheet(CreditCardsViewModel vm)
    {
        InitializeComponent();
        BindingContext = vm;
    }
}
