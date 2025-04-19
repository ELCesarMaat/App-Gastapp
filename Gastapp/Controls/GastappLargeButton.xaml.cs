using System.Windows.Input;

namespace Gastapp.Controls;

public partial class GastappLargeButton : ContentView
{
	public GastappLargeButton()
	{
		InitializeComponent();
	}
    public static readonly BindableProperty TextProperty =
       BindableProperty.Create(nameof(Text), typeof(string), typeof(GastappLargeButton), string.Empty);

    public static readonly BindableProperty TextColorProperty =
        BindableProperty.Create(nameof(TextColor), typeof(Color), typeof(GastappLargeButton), Colors.White);

    public static readonly BindableProperty BackgroundColorCustomProperty =
        BindableProperty.Create(nameof(BackgroundColorCustom), typeof(Color), typeof(GastappLargeButton), Colors.Blue);

    public static readonly BindableProperty BorderColorProperty =
        BindableProperty.Create(nameof(BorderColor), typeof(Color), typeof(GastappLargeButton), Colors.Blue);

    public static readonly BindableProperty ButtonMarginProperty =
        BindableProperty.Create(nameof(ButtonMargin), typeof(Thickness), typeof(GastappLargeButton), new Thickness(0));

    public static readonly BindableProperty CommandProperty =
    BindableProperty.Create(nameof(Command), typeof(ICommand), typeof(GastappLargeButton));

    public static readonly BindableProperty CommandParameterProperty =
        BindableProperty.Create(nameof(CommandParameter), typeof(object), typeof(GastappLargeButton));

    public ICommand Command
    {
        get => (ICommand)GetValue(CommandProperty);
        set => SetValue(CommandProperty, value);
    }

    public object CommandParameter
    {
        get => GetValue(CommandParameterProperty);
        set => SetValue(CommandParameterProperty, value);
    }


    public string Text
    {
        get => (string)GetValue(TextProperty);
        set => SetValue(TextProperty, value);
    }

    public Color TextColor
    {
        get => (Color)GetValue(TextColorProperty);
        set => SetValue(TextColorProperty, value);
    }

    public Color BackgroundColorCustom
    {
        get => (Color)GetValue(BackgroundColorCustomProperty);
        set => SetValue(BackgroundColorCustomProperty, value);
    }

    public Color BorderColor
    {
        get => (Color)GetValue(BorderColorProperty);
        set => SetValue(BorderColorProperty, value);
    }

    public Thickness ButtonMargin
    {
        get => (Thickness)GetValue(ButtonMarginProperty);
        set => SetValue(ButtonMarginProperty, value);
    }

    public event EventHandler Clicked;

    private async void OnTapped(object sender, TappedEventArgs e)
    {
        // Animación visual
        await this.ScaleTo(0.95, 50, Easing.CubicOut);
        await this.ScaleTo(1.0, 50, Easing.CubicIn);
        if (Command?.CanExecute(CommandParameter) ?? false)
            Command.Execute(CommandParameter);
        // Lanza el evento personalizado
        Clicked?.Invoke(this, EventArgs.Empty);
    }
}