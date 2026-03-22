using System.ComponentModel.DataAnnotations;

namespace Gastapp_API.Models
{
    public class PasswordResetRequestDto
    {
        [Required]
        [EmailAddress]
        public string Email { get; set; } = string.Empty;
    }

    public class PasswordResetVerifyDto
    {
        [Required]
        [EmailAddress]
        public string Email { get; set; } = string.Empty;

        [Required]
        [StringLength(12, MinimumLength = 4)]
        public string Code { get; set; } = string.Empty;
    }

    public class PasswordResetConfirmDto
    {
        [Required]
        [EmailAddress]
        public string Email { get; set; } = string.Empty;

        [Required]
        [StringLength(12, MinimumLength = 4)]
        public string Code { get; set; } = string.Empty;

        [Required]
        [MinLength(8)]
        public string NewPassword { get; set; } = string.Empty;
    }
}
