using CommunityToolkit.Maui.Core.Extensions;
using Gastapp.ViewModels;
using CheckedChangedEventArgs = Syncfusion.Maui.Buttons.CheckedChangedEventArgs;

namespace Gastapp.Pages.Register;

public partial class RegisterSalary : ContentView
{
    public CollectionView? CollectionView;

    public RegisterSalary()
    {
        InitializeComponent();
    }

    private void SelectableItemsView_OnSelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        if (BindingContext is not RegisterViewModel vm) return;

        CollectionView = (CollectionView)sender!;

        if (CollectionView.SelectedItems.Count > 2 && vm.IsBiWeekSelected)
        {
            var lastSelected = e.CurrentSelection.LastOrDefault();
            if (lastSelected != null && CollectionView.SelectedItems.Contains(lastSelected))
            {
                CollectionView.SelectedItems.Remove(lastSelected);
            }
        }

        if (CollectionView.SelectedItems.Count > 1 && vm.IsMonthSelected)
        {
            var lastSelected = e.CurrentSelection.LastOrDefault();
            if (lastSelected != null && CollectionView.SelectedItems.Contains(lastSelected))
            {
                CollectionView.SelectedItems.Remove(lastSelected);
            }
        }

        vm.SelectedItemsForMonthOrBiweek.Clear();
        foreach (var item in CollectionView.SelectedItems)
        {
            vm.SelectedItemsForMonthOrBiweek.Add(item);
        }
    }

    private void OnCheckedChanged(object? sender, CheckedChangedEventArgs e)
    {
        CollectionView?.SelectedItems.Clear();
        if (BindingContext is RegisterViewModel vm)
        {
            vm.SelectedItemsForMonthOrBiweek.Clear();
        }
    }
}