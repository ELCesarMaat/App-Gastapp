namespace Gastapp.Models
{
    public class AuthenticateResponse
    {
        public string UserId { get; set; }
        public string Email { get; set; }
        public string Name { get; set; }
        public string Token { get; set; }

        public AuthenticateResponse(User user, string token)
        {
            UserId = user.UserId;
            Email = user.Email;
            Name = user.Name;
            Token = token;
        }
    }
}