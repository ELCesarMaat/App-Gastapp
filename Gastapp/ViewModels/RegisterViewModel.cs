using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Globalization;
using System.Linq;
using System.Threading.Tasks;
using CommunityToolkit.Maui.Alerts;
using CommunityToolkit.Maui.Core;
using CommunityToolkit.Maui.Views;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Gastapp.Models;
using Gastapp.Models.Models;
using Gastapp.Pages;
using Gastapp.Pages.Register;
using Gastapp.Popups;
using Gastapp.Services;
using Gastapp.Services.ApiService;
using Gastapp.Services.Navigation;
using Gastapp.Services.UserService;
using Gastapp.Utils;
using Refit;
using Application = Microsoft.Maui.Controls.Application;
using Gastapp.Validators;
using FluentValidation;
using Microsoft.Maui.ApplicationModel;

namespace Gastapp.ViewModels
{
    public partial class RegisterViewModel(
        INavigationService navigationService,
        IUserService userService,
        IApiService apiService,
        IRegisterDraftService draftService) : ObservableObject
    {
        // Indices de los pasos del wizard. El correo se confirma ANTES de pedir
        // datos personales: no tiene caso capturarlos si el correo no es valido.
        private const int StepAccount = 0;
        private const int StepEmailCode = 1;
        private const int StepName = 2;
        private const int StepBirthDate = 3;
        private const int StepSalary = 4;

        private readonly RegisterValidator _validator = new();
        private PagesUtils _popupUtils = new();
        private IList<ContentView> _pasos = InitializePasos();
        private INavigationService _navigationService = navigationService;
        private IUserService _userService = userService;
        private IApiService _apiService = apiService;
        private readonly IRegisterDraftService _draftService = draftService;
        private DateTime _lastExitClick = DateTime.MinValue;
        private bool _isInitialized;
        private bool _isRestoringDraft;

        private readonly string[] _stepTitles =
        {
            "Crea tu acceso",
            "Confirma tu correo",
            "Personaliza tu perfil",
            "Confirma tu fecha de nacimiento",
            "Configura tus ingresos"
        };

        private readonly string[] _stepDescriptions =
        {
            "Usaremos tu correo y contraseña para iniciar sesión y proteger tu información.",
            "Te enviamos un código para asegurarnos de que el correo es tuyo.",
            "Queremos mostrarte la app con un tono más personal y cercano.",
            "Esto nos ayuda a adaptar recordatorios y validar tu registro.",
            "Con estos datos calcularemos tu capacidad de ahorro y tu salud financiera."
        };

        static IList<ContentView> InitializePasos() => new List<ContentView>();

        [ObservableProperty]
        private bool _canContinue = false;

        [ObservableProperty]
        private int _pasoActual = 0;

        [ObservableProperty]
        private bool _puedeRetroceder = false;

        [ObservableProperty]
        private bool _canExitWithButton = false;

        [ObservableProperty]
        private string _stepTitle = string.Empty;

        [ObservableProperty]
        private string _stepDescription = string.Empty;

        [ObservableProperty]
        private string _stepCounterText = string.Empty;

        [ObservableProperty]
        private string _continueButtonText = "CONTINUAR";

        [ObservableProperty]
        private double _registerProgress;

        [ObservableProperty]
        private string _email = string.Empty;

        [ObservableProperty]
        private string _confirmEmail = string.Empty;

        [ObservableProperty]
        private string _password = string.Empty;

        [ObservableProperty]
        private string _name = string.Empty;

        [ObservableProperty]
        private string _emailErrorMessage = string.Empty;

        [ObservableProperty]
        private string _confirmEmailErrorMessage = string.Empty;

        [ObservableProperty]
        private string _passwordErrorMessage = string.Empty;

        [ObservableProperty]
        private string _nameErrorMessage = string.Empty;

        [ObservableProperty]
        private bool _emailHasError = false;

        [ObservableProperty]
        private bool _confirmEmailHasError = false;

        [ObservableProperty]
        private bool _passwordHasError = false;

        [ObservableProperty]
        private bool _nameHasError = false;

        // ---- Confirmacion de correo ----

        [ObservableProperty]
        private string _emailCode = string.Empty;

        [ObservableProperty]
        private string _emailCodeErrorMessage = string.Empty;

        [ObservableProperty]
        private bool _emailCodeHasError;

        [ObservableProperty]
        private bool _isSendingEmailCode;

        [ObservableProperty]
        private bool _isEmailVerified;

        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(ResendCodeText))]
        [NotifyPropertyChangedFor(nameof(CanResendEmailCode))]
        private int _resendCooldownSeconds;

        public string EmailCodeSentToText => string.IsNullOrWhiteSpace(Email)
            ? "Te enviamos un código a tu correo."
            : $"Te enviamos un código de 6 dígitos a {Email}.";

        public string ResendCodeText => ResendCooldownSeconds > 0
            ? $"Reenviar código en {ResendCooldownSeconds}s"
            : "Reenviar código";

        public bool CanResendEmailCode => ResendCooldownSeconds <= 0 && !IsSendingEmailCode;

        [ObservableProperty]
        ObservableCollection<int> _listDays = new ObservableCollection<int>();

        [ObservableProperty]
        private int _selectedDay;

        [ObservableProperty]
        ObservableCollection<string> _listMonths = new ObservableCollection<string>();

        [ObservableProperty]
        private string _selectedMonth;

        [ObservableProperty]
        ObservableCollection<int> _listYears = new ObservableCollection<int>();

        [ObservableProperty]
        private int _selectedYear;

        [ObservableProperty] private bool _isWeekSelected = true;
        [ObservableProperty] private bool _isBiWeekSelected;
        [ObservableProperty] private bool _isMonthSelected;
        [ObservableProperty] private bool _isMonthOrBiWeekSelected;


        [ObservableProperty] private ObservableCollection<DayForWeek> _listForWeek = new();
        [ObservableProperty] private ObservableCollection<int> _listForMonth = new();

        [ObservableProperty] private DayForWeek _selectedItemForWeek;
        [ObservableProperty] private ObservableCollection<object> _selectedItemsForMonthOrBiweek = new();
        [ObservableProperty] private decimal _salary = 0m;
        [ObservableProperty] private decimal _percentSave = 0m;
        [ObservableProperty] private decimal _totalSave = 0m;

        private void InitializeData()
        {
            ListForWeek.Clear();
            var count = 0;
            foreach (var day in DateTimeFormatInfo.CurrentInfo.DayNames)
            {
                ListForWeek.Add(new DayForWeek()
                {
                    DayName = day,
                    DayNumber = count
                });
                count++;
            }

            ListForMonth.Clear();
            for (int i = 1; i <= 31; i++)
            {
                ListForMonth.Add(i);
            }

            var today = DateTime.Now;

            ListDays.Clear();
            for (int i = 1; i <= 31; i++)
            {
                ListDays.Add(i);
            }

            ListMonths.Clear();
            foreach (var month in DateTimeFormatInfo.CurrentInfo.MonthNames.Where(m => !string.IsNullOrEmpty(m)))
            {
                ListMonths.Add(month);
            }

            ListYears.Clear();
            for (int i = today.Year - 3; i >= 1900; i--)
            {
                ListYears.Add(i);
            }

            SelectedDay = ListDays.FirstOrDefault();
            SelectedYear = ListYears.FirstOrDefault();
            SelectedMonth = ListMonths.FirstOrDefault() ?? "enero";
            SelectedItemForWeek = ListForWeek.FirstOrDefault()!;

            _pasos = new List<ContentView>
            {
                new RegisterAccount{ BindingContext = this },
                new RegisterEmailCode{ BindingContext = this },
                new RegisterName{ BindingContext = this },
                new RegisterBirthDate{ BindingContext = this },
                new RegisterSalary{ BindingContext = this },
            };

            // Inicializar el estado del botón
            UpdateStepMetadata();
            UpdateCanContinue();
        }

        private void EnsureInitialized()
        {
            if (_isInitialized)
                return;

            InitializeData();
            _isInitialized = true;
        }

        /// <summary>
        /// Guarda el avance para poder retomar el registro si la app se cierra.
        /// Importa sobre todo despues de confirmar el correo: ahi el usuario ya
        /// gasto un codigo y seria molesto pedirle que empiece otra vez.
        /// </summary>
        private async Task PersistDraftAsync()
        {
            if (_isRestoringDraft)
                return;

            var draft = new RegisterDraft
            {
                Step = PasoActual,
                Email = Email,
                EmailVerified = IsEmailVerified,
                Name = Name,
                BirthDay = SelectedDay,
                BirthMonth = SelectedMonth ?? string.Empty,
                BirthYear = SelectedYear,
                Salary = Salary,
                PercentSave = PercentSave,
                IncomeTypeId = IsWeekSelected ? 1 : IsBiWeekSelected ? 2 : IsMonthSelected ? 3 : 0,
                FirstPayDay = SelectedItemForWeek?.DayNumber,
            };

            await _draftService.SaveAsync(draft, Password);
        }

        /// <summary>
        /// Restaura el avance guardado. Solo regresa al paso donde iba si el correo
        /// ya estaba confirmado; si no, lo deja capturar de nuevo porque el codigo
        /// pudo haber expirado.
        /// </summary>
        public async Task RestoreDraftAsync()
        {
            EnsureInitialized();

            var draft = await _draftService.LoadAsync();
            if (draft == null || string.IsNullOrWhiteSpace(draft.Email))
                return;

            _isRestoringDraft = true;

            try
            {
                Email = draft.Email;
                ConfirmEmail = draft.Email;
                Password = await _draftService.GetPasswordAsync();
                Name = draft.Name;
                IsEmailVerified = draft.EmailVerified;

                if (draft.BirthYear > 0 && ListYears.Contains(draft.BirthYear))
                    SelectedYear = draft.BirthYear;

                if (!string.IsNullOrWhiteSpace(draft.BirthMonth) && ListMonths.Contains(draft.BirthMonth))
                    SelectedMonth = draft.BirthMonth;

                if (draft.BirthDay > 0 && ListDays.Contains(draft.BirthDay))
                    SelectedDay = draft.BirthDay;

                Salary = draft.Salary;
                PercentSave = draft.PercentSave;

                switch (draft.IncomeTypeId)
                {
                    case 2: IsWeekSelected = false; IsBiWeekSelected = true; break;
                    case 3: IsWeekSelected = false; IsMonthSelected = true; break;
                }

                // Sin correo confirmado no tiene sentido saltar a los datos personales.
                PasoActual = draft.EmailVerified
                    ? Math.Clamp(draft.Step, StepName, _pasos.Count - 1)
                    : StepAccount;
            }
            finally
            {
                _isRestoringDraft = false;
            }

            UpdateStepMetadata();
            UpdateCanContinue();

            if (PasoActual > StepAccount)
                await Toast.Make("Retomamos tu registro donde lo dejaste.", ToastDuration.Long).Show();
        }

        private void UpdateStepMetadata()
        {
            StepTitle = _stepTitles[PasoActual];
            StepDescription = _stepDescriptions[PasoActual];
            StepCounterText = $"Paso {PasoActual + 1} de {_pasos.Count}";
            ContinueButtonText = PasoActual == _pasos.Count - 1 ? "CREAR CUENTA" : "CONTINUAR";
            RegisterProgress = (PasoActual + 1d) / _pasos.Count;
        }

        // Método para actualizar el estado de CanContinue basado en el paso actual
        private void UpdateCanContinue()
        {
            switch (PasoActual)
            {
                case StepAccount:
                    var accountResult = _validator.Validate(this, options =>
                        options.IncludeProperties(nameof(Email), nameof(ConfirmEmail), nameof(Password)));
                    CanContinue = accountResult.IsValid;
                    break;
                case StepEmailCode:
                    CanContinue = EmailCode?.Trim().Length == 6 && !IsSendingEmailCode;
                    break;
                case StepName:
                    var nameResult = _validator.Validate(this, options =>
                        options.IncludeProperties(nameof(Name)));
                    CanContinue = nameResult.IsValid;
                    break;
                case StepBirthDate:
                    var birthDateResult = _validator.Validate(this, options =>
                        options.IncludeProperties(nameof(SelectedDay), nameof(SelectedMonth), nameof(SelectedYear)));
                    CanContinue = birthDateResult.IsValid;
                    break;
                case StepSalary:
                    var salaryResult = _validator.Validate(this, options =>
                        options.IncludeProperties(nameof(Salary), nameof(PercentSave)));

                    // También validamos que al menos un tipo de ingreso esté seleccionado
                    var incomeTypeSelected = IsWeekSelected || IsBiWeekSelected || IsMonthSelected;

                    CanContinue = salaryResult.IsValid && incomeTypeSelected;
                    break;
                default:
                    CanContinue = false;
                    break;
            }
        }

        partial void OnEmailChanged(string value)
        {
            ValidateEmail();
            UpdateCanContinue();
        }

        partial void OnConfirmEmailChanged(string value)
        {
            ValidateConfirmEmail();
            UpdateCanContinue();
        }

        partial void OnPasswordChanged(string value)
        {
            ValidatePassword();
            UpdateCanContinue();
        }

        partial void OnNameChanged(string value)
        {
            ValidateName();
            UpdateCanContinue();
        }

        partial void OnSelectedDayChanged(int value)
        {
            UpdateCanContinue();
        }

        partial void OnSelectedMonthChanged(string value)
        {
            var monthNumber = ListMonths.IndexOf(value) + 1;
            var prevDay = SelectedDay;

            var maxDay = DateTime.DaysInMonth(SelectedYear, monthNumber);

            ListDays.Clear();
            for (int d = 1; d <= maxDay; d++)
                ListDays.Add(d);

            SelectedDay = prevDay <= maxDay
                ? prevDay
                : maxDay;

            if (SelectedDay == prevDay)
                OnPropertyChanged(nameof(SelectedDay));

            UpdateCanContinue();
        }

        partial void OnSelectedYearChanged(int value)
        {
            UpdateCanContinue();
        }

        partial void OnIsBiWeekSelectedChanged(bool value)
        {
            IsMonthOrBiWeekSelected = IsBiWeekSelected || IsMonthSelected;
            UpdateCanContinue();
        }

        partial void OnIsMonthSelectedChanged(bool value)
        {
            IsMonthOrBiWeekSelected = IsBiWeekSelected || IsMonthSelected;
            UpdateCanContinue();
        }

        partial void OnIsWeekSelectedChanged(bool value)
        {
            UpdateCanContinue();
        }

        partial void OnSalaryChanged(decimal value)
        {
            TotalSave = Salary * (PercentSave / 100);
            UpdateCanContinue();
        }

        partial void OnPercentSaveChanged(decimal value)
        {
            if (value > 99)
                PercentSave = 99;
            if (value < 0)
                PercentSave = 0;
            TotalSave = Salary * (PercentSave / 100);
            UpdateCanContinue();
        }

        partial void OnSelectedItemsForMonthOrBiweekChanged(ObservableCollection<object> value)
        {
            UpdateCanContinue();
        }

        partial void OnSelectedItemForWeekChanged(DayForWeek value)
        {
            UpdateCanContinue();
        }

        partial void OnPasoActualChanged(int value)
        {
            // Actualizar el estado del botón cuando cambia el paso
            UpdateStepMetadata();
            UpdateCanContinue();
        }

        public async Task MostrarPaso(ContentView contenedor)
        {
            EnsureInitialized();
            await contenedor.FadeTo(0, 150);
            contenedor.Content = _pasos[PasoActual];
            await contenedor.FadeTo(1, 150);

            PuedeRetroceder = PasoActual > 0;

            // Actualizar el estado del botón al cambiar de paso
            UpdateStepMetadata();
            UpdateCanContinue();
        }

        [RelayCommand]
        private async Task Next()
        {
            EnsureInitialized();

            // Del paso de acceso pasamos al de codigo: hay que mandarlo primero.
            if (PasoActual == StepAccount)
            {
                if (!await SendEmailVerificationCodeAsync(isResend: false))
                    return;

                PasoActual = StepEmailCode;
                await PersistDraftAsync();
                ActualizarVista();
                return;
            }

            // Del paso de codigo solo se avanza si el codigo es correcto.
            if (PasoActual == StepEmailCode)
            {
                if (!await VerifyEmailCodeAsync())
                    return;

                PasoActual = StepName;
                await PersistDraftAsync();
                ActualizarVista();
                return;
            }

            if (PasoActual < _pasos.Count - 1)
            {
                PasoActual++;
                await PersistDraftAsync();
                ActualizarVista();
            }
            else
            {
                if (!ValidateAll())
                    await Toast.Make("Por favor revise todos los campos antes de continuar").Show();
                else
                {
                    await SaveUser();
                }
            }
        }

        private async Task<bool> SendEmailVerificationCodeAsync(bool isResend)
        {
            if (IsSendingEmailCode)
                return false;

            EmailCodeHasError = false;
            IsSendingEmailCode = true;
            UpdateCanContinue();

            try
            {
                await _apiService.RequestEmailVerification(Email.Trim());

                if (isResend)
                    await Toast.Make("Te enviamos un código nuevo.").Show();

                StartResendCooldown();
                return true;
            }
            catch (ApiException ex)
            {
                // El API responde con un mensaje util (correo en uso, timeout, etc).
                var detail = ExtractApiMessage(ex);
                EmailCodeErrorMessage = string.IsNullOrWhiteSpace(detail)
                    ? "No pudimos enviar el código. Intenta de nuevo."
                    : detail;
                EmailCodeHasError = true;

                if (PasoActual == StepAccount)
                    await Toast.Make(EmailCodeErrorMessage, ToastDuration.Long).Show();

                return false;
            }
            catch (Exception)
            {
                EmailCodeErrorMessage = "No hay conexión con el servidor. Revisa tu internet e intenta de nuevo.";
                EmailCodeHasError = true;

                if (PasoActual == StepAccount)
                    await Toast.Make(EmailCodeErrorMessage, ToastDuration.Long).Show();

                return false;
            }
            finally
            {
                IsSendingEmailCode = false;
                UpdateCanContinue();
            }
        }

        private async Task<bool> VerifyEmailCodeAsync()
        {
            var code = EmailCode?.Trim() ?? string.Empty;

            if (code.Length != 6)
            {
                EmailCodeErrorMessage = "El código son 6 dígitos.";
                EmailCodeHasError = true;
                return false;
            }

            EmailCodeHasError = false;
            IsSendingEmailCode = true;
            UpdateCanContinue();

            try
            {
                await _apiService.VerifyEmail(Email.Trim(), code);
                IsEmailVerified = true;
                return true;
            }
            catch (ApiException ex)
            {
                var detail = ExtractApiMessage(ex);
                EmailCodeErrorMessage = string.IsNullOrWhiteSpace(detail)
                    ? "Código inválido o expirado."
                    : detail;
                EmailCodeHasError = true;
                return false;
            }
            catch (Exception)
            {
                EmailCodeErrorMessage = "No hay conexión con el servidor. Intenta de nuevo.";
                EmailCodeHasError = true;
                return false;
            }
            finally
            {
                IsSendingEmailCode = false;
                UpdateCanContinue();
            }
        }

        [RelayCommand]
        private async Task ResendEmailCode()
        {
            if (!CanResendEmailCode)
                return;

            EmailCode = string.Empty;
            await SendEmailVerificationCodeAsync(isResend: true);
        }

        /// <summary>Evita que se pueda pedir un codigo nuevo cada segundo.</summary>
        private void StartResendCooldown()
        {
            ResendCooldownSeconds = 60;

            Application.Current?.Dispatcher.StartTimer(TimeSpan.FromSeconds(1), () =>
            {
                ResendCooldownSeconds--;
                return ResendCooldownSeconds > 0;
            });
        }

        private static string ExtractApiMessage(ApiException ex)
        {
            var content = ex.Content?.Trim();
            if (string.IsNullOrWhiteSpace(content))
                return string.Empty;

            // El API devuelve el mensaje como string JSON o como texto plano.
            if (content.StartsWith('"') && content.EndsWith('"') && content.Length > 1)
                return content[1..^1];

            return content.StartsWith('{') ? string.Empty : content;
        }

        [RelayCommand]
        private async Task GoToPrivacyNotice()
        {
            await Launcher.Default.OpenAsync("https://www.privacypolicies.com/live/063d06df-a5ce-42a4-9513-86839a3aa87d");
        }

        [RelayCommand]
        public async Task Previous()
        {
            EnsureInitialized();
            if (PasoActual > 0)
            {
                // Ya con el correo confirmado no se regresa al paso del codigo:
                // ese codigo ya se consumio y volver ahi solo confunde.
                PasoActual = PasoActual == StepName && IsEmailVerified
                    ? StepAccount
                    : PasoActual - 1;

                await PersistDraftAsync();
                ActualizarVista();
            }
            else
            {
                await CheckExit();
            }
        }

        private async Task CheckExit()
        {
            if ((DateTime.Now - _lastExitClick).TotalMilliseconds < 2000)
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
                _ = MostrarPaso(page.FindByName<ContentView>("PasoContainer"));
            }
        }

        private async Task SaveUser()
        {
            _popupUtils.ShowPopup(new LoadingPopup());
            int payType = 0;
            int? firstPayDay = null;
            int? secondPayDay = null;
            if (IsWeekSelected)
            {
                payType = 1;
                firstPayDay = SelectedItemForWeek.DayNumber;
            }

            else if (IsBiWeekSelected)
            {
                payType = 2;
                var selectedDays = SelectedItemsForMonthOrBiweek.Select(Convert.ToInt32).ToList();
                firstPayDay = selectedDays.Any() ? selectedDays.Min() : 15;
                secondPayDay = selectedDays.Count > 1 ? selectedDays.Max() : 30;
            }

            else if (IsMonthSelected)
            {
                payType = 3;
                var selectedDays = SelectedItemsForMonthOrBiweek.Select(Convert.ToInt32).ToList();
                firstPayDay = selectedDays.Any() ? selectedDays.Min() : 15;
            }



            var user = new CreateUserModel
            {
                Name = Name,
                BirthDate = DateTime.SpecifyKind(new DateTime(SelectedYear, ListMonths.IndexOf(SelectedMonth) + 1, SelectedDay), DateTimeKind.Utc),
                Salary = Salary,
                IncomeTypeId = payType,
                FirstPayDay = firstPayDay,
                SecondPayDay = secondPayDay,
                Password = Password,
                Email = Email,
                PercentSave = PercentSave
            };

            try
            {
                var res = await _apiService.CreateUser(user);
                user.UserId = res.UserId;
                Preferences.Set("token", res.Token);
                Preferences.Set("tokenexpiration", res.TokenExpiration.ToString());
                await _userService.CreateNewUser(user, res.Token);

                // La cuenta ya existe: el borrador (y la contrasena guardada) sobran.
                await _draftService.ClearAsync();

                await Toast.Make($"Bienvenido {user.Name}").Show();
                await _navigationService.GoToAsync("//MainPage");
            }
            catch (ApiException apiEx)
            {
                var detail = ExtractApiMessage(apiEx);
                await Toast.Make(
                    string.IsNullOrWhiteSpace(detail) ? "No se pudo crear la cuenta." : detail,
                    ToastDuration.Long).Show();
            }
            catch (Exception e)
            {
                await Toast.Make($"No se pudo crear la cuenta: {e.Message}", ToastDuration.Long).Show();
            }
            await _popupUtils.ClosePopup();
        }

        public bool ValidateAll()
        {
            var result = _validator.Validate(this);

            // Limpia todos los mensajes de error anteriores
            EmailHasError = ConfirmEmailHasError = PasswordHasError = NameHasError = false;

            // Si hay errores, asigna los mensajes de error a las propiedades correspondientes
            if (!result.IsValid)
            {
                foreach (var error in result.Errors)
                {
                    switch (error.PropertyName)
                    {
                        case nameof(Email):
                            EmailErrorMessage = error.ErrorMessage;
                            EmailHasError = true;
                            break;
                        case nameof(ConfirmEmail):
                            ConfirmEmailErrorMessage = error.ErrorMessage;
                            ConfirmEmailHasError = true;
                            break;
                        case nameof(Password):
                            PasswordErrorMessage = error.ErrorMessage;
                            PasswordHasError = true;
                            break;
                        case nameof(Name):
                            NameErrorMessage = error.ErrorMessage;
                            NameHasError = true;
                            break;
                    }
                }
            }

            return result.IsValid;
        }

        private bool ValidateEmail()
        {
            var result = _validator.Validate(this, options =>
                options.IncludeProperties(nameof(Email)));

            if (!result.IsValid)
            {
                EmailErrorMessage = result.Errors.First().ErrorMessage;
                EmailHasError = true;
            }
            else
            {
                EmailHasError = false;
            }

            return !EmailHasError;
        }

        private bool ValidateConfirmEmail()
        {
            var result = _validator.Validate(this, options =>
                options.IncludeProperties(nameof(ConfirmEmail)));

            if (!result.IsValid)
            {
                ConfirmEmailErrorMessage = result.Errors.First().ErrorMessage;
                ConfirmEmailHasError = true;
            }
            else
            {
                ConfirmEmailHasError = false;
            }

            return !ConfirmEmailHasError;
        }

        private bool ValidatePassword()
        {
            var result = _validator.Validate(this, options =>
                options.IncludeProperties(nameof(Password)));

            if (!result.IsValid)
            {
                PasswordErrorMessage = result.Errors.First().ErrorMessage;
                PasswordHasError = true;
            }
            else
            {
                PasswordHasError = false;
            }

            return !PasswordHasError;
        }

        private bool ValidateName()
        {
            var result = _validator.Validate(this, options =>
                options.IncludeProperties(nameof(Name)));

            if (!result.IsValid)
            {
                NameErrorMessage = result.Errors.First().ErrorMessage;
                NameHasError = true;
            }
            else
            {
                NameHasError = false;
            }

            return !NameHasError;
        }
    }
}