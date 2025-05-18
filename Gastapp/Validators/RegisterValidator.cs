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

        }
    }
}
