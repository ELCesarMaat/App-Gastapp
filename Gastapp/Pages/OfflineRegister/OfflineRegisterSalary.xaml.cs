using CommunityToolkit.Maui.Core.Extensions;
using Gastapp.ViewModels;
using CheckedChangedEventArgs = Syncfusion.Maui.Buttons.CheckedChangedEventArgs;

namespace Gastapp.Pages.OfflineRegister;

public partial class OfflineRegisterSalary : ContentView
{
    public CollectionView? CollectionView;

    public OfflineRegisterSalary()
    {
        InitializeComponent();
    }

    private void SelectableItemsView_OnSelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        OfflineRegisterViewModel vm = (OfflineRegisterViewModel)BindingContext!;

        CollectionView = (CollectionView)sender!;

        if (CollectionView.SelectedItems.Count > 2 && vm.IsBiWeekSelected)
        {
            var lastSelected = e.CurrentSelection.FirstOrDefault();
            if (lastSelected != null)
            {
                CollectionView.SelectedItems.Remove(lastSelected);
            }
        }

        if (CollectionView!.SelectedItems.Count > 1 && vm.IsMonthSelected)
        {
            var lastSelected = e.CurrentSelection.FirstOrDefault();
            if (lastSelected != null)
            {
                CollectionView.SelectedItems.Remove(lastSelected);
            }
        }

        vm.SelectedItemsForMonthOrBiweek = CollectionView.SelectedItems.Cast<int>().ToObservableCollection();
    }

    private void OnCheckedChanged(object? sender, CheckedChangedEventArgs e)
    {
        CollectionView?.SelectedItems.Clear();
    }
}