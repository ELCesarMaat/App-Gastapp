using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using FluentValidation;
using Gastapp.ViewModels;


namespace Gastapp.Validators
{
    internal class RegisterValidator : AbstractValidator<RegisterViewModel>
    {
        public RegisterValidator()
        {
            RuleFor(x => x.Email)
                .NotEmpty().WithMessage("El correo electrónico es obligatorio")
                .EmailAddress().WithMessage("Ingrese un correo electrónico válido");

            RuleFor(x => x.ConfirmEmail)
                .NotEmpty().WithMessage("La confirmación del correo es obligatoria");

            RuleFor(x => x.Password)
                .NotEmpty().WithMessage("La contraseña es obligatoria")
                .MinimumLength(0).WithMessage("La contraseña debe de tener al menos 6 caracteres")
                .MaximumLength(20).WithMessage("La contraseña no puede tener mas de 20 caracteres");

            RuleFor(x => x.Name)
                .NotEmpty().WithMessage("El nombre es obligatorio")
                .MinimumLength(2).WithMessage("El nombre debe tener al menos 2 caracteres");

            //Reglas para la fecha de nacimiento
            RuleFor(x => x.SelectedDay).GreaterThan(0).WithMessage("Seleccione un día");
            RuleFor(x => x.SelectedMonth).NotEmpty().WithMessage("Seleccione un mes");
            RuleFor(x => x.SelectedYear).GreaterThan(1900).WithMessage("Seleccione un año valido");


            //Validar que al menos un tipo de ingreso esté seleccionado
            RuleFor(x => x)
                .Must(x => x.IsWeekSelected || x.IsBiWeekSelected || x.IsMonthSelected)
                .WithMessage("Debe seleccionar un tipo de ingreso");

            //Validar Salario
            RuleFor(x => x.Salary)
                .GreaterThan(0).WithMessage("El salario debe ser mayor que 0");

            //Validar porcentaje de ahorro
            RuleFor(x => x.PercentSave)
                .InclusiveBetween(0, 99).WithMessage("El porcentaje de ahorro debe estar entre 0 y 99");
        }
    }
}
