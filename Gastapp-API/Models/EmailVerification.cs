using System;
using System.ComponentModel.DataAnnotations;

namespace Gastapp_API.Models
{
    /// <summary>
    /// Codigo de confirmacion de correo emitido ANTES de que exista el usuario,
    /// por eso vive en su propia tabla y no como columna de User.
    /// </summary>
    public class EmailVerification
    {
        [Key] public string EmailVerificationId { get; set; } = Guid.NewGuid().ToString();

        public string Email { get; set; } = null!;

        public string CodeHash { get; set; } = null!;

        public DateTime ExpiresAt { get; set; }

        public DateTime? VerifiedAt { get; set; }

        /// <summary>Intentos fallidos, para no dejar que adivinen el codigo por fuerza bruta.</summary>
        public int Attempts { get; set; }

        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    }
}
