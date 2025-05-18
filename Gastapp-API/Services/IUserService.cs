using Gastapp.Models;

namespace Gastapp.Services
{
    public interface IUserService
    {
        Task<AuthenticateResponse> AuthenticateAsync(AuthenticateRequest model); // Asegúrate que use AuthenticateRequest
        User GetById(string id);
    }
}