using Gastapp.Models.Models;

namespace Gastapp.Models
{
    public class AuthenticateResponse
    {
        public string UserId { get; set; }
        public string Email { get; set; }
        public string Name { get; set; }
        public string Token { get; set; }
        public required DateTime? TokenExpiration { get; set; }
    }
}