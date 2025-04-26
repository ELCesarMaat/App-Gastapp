using CommunityToolkit.Maui.Core.Extensions;
using Gastapp.ViewModels;

namespace Gastapp.Pages.OfflineRegister;

public partial class OfflineRegisterSalary : ContentView
{
	public OfflineRegisterSalary()
	{
		InitializeComponent();
	}

    private void SelectableItemsView_OnSelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        var cv = (CollectionView)sender!;
        var vm = (BindingContext as OfflineRegisterViewModel)!;

        var lista = e.CurrentSelection.Cast<int>().ToObservableCollection();

        if (vm.IsBiWeekSelected && lista.Count > 2)
        {
            lista.RemoveAt(lista.Count -1);
        }
        if (vm.IsMonthSelected && lista.Count > 1)
        {
            lista.RemoveAt(lista.Count - 1);
        }

        cv.SelectedItems.Clear();
        foreach (var item in lista)
        {
            cv.SelectedItems.Add(item);
        }
        vm.SelectedItemsForMonthOrBiweek = lista;
    }
}