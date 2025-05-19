using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using FluentValidation;
using System.Globalization;
using System.Text.RegularExpressions;
using Gastapp.Models;

namespace Gastapp.Validators
{
    public class UserValidator : AbstractValidator<User>
    {
       
            public UserValidator()
            {
                RuleFor(x => x.Name)
                    .NotEmpty().WithMessage("El nombre es obligatorio")
                    .MinimumLength(2).WithMessage("El nombre debe tener al menos 2 caracteres");
                RuleFor(x => x.Email)
                    .NotEmpty().WithMessage("El correo electronico es obligatorio")
                    .EmailAddress().WithMessage("Ingrese un correo electronico valido");

                RuleFor( x => x.PassWordHash)
                    .NotEmpty().WithMessage("La contraseña es obligatoria")
                    .MinimumLength(6).WithMessage("La contraseña debe tener al menos 6 caracteres")
                    .MaximumLength(20).WithMessage("La contraseña no puede tener mas de 20 caracteres");

            RuleFor(x => x.BirthDate)
              .NotEmpty().WithMessage("La fecha de nacimiento es obligatoria")
              .LessThan(DateTime.Now).WithMessage("La fecha de nacimiento no puede ser en el futuro");

            RuleFor(x => x.Salary)
                .GreaterThanOrEqualTo(0).WithMessage("El salario no puede ser negativo");

            RuleFor(x => x.PercentSave)
                .InclusiveBetween(0, 99).WithMessage("El porcentaje de ahorro debe de estar entre 0 y 99");



            }
        }
    }
