using Gastapp.Models;
using Gastapp.Models.Models;

namespace Gastapp.Services
{
    public interface IUserService
    {
        Task<AuthenticateResponse?> AuthenticateAsync(AuthenticateRequest model); // Asegúrate que use AuthenticateRequest
        Token GenerateNewToken(User user);
        User? GetById(string id);
    }
}