using Gastapp.Models;
using Gastapp.Models.Models;

namespace Gastapp.Services
{
    public interface IUserService
    {
        Task<AuthenticateResponse?> AuthenticateAsync(AuthenticateRequest model); // Asegúrate que use AuthenticateRequest
        Token GenerateNewToken(User user);

        /// <summary>
        /// Token de corta vida para un dispositivo vinculado (reloj). Lleva los claims
        /// "scope" y "device_id" que restringen lo que puede hacer; el token normal de
        /// la app del telefono no los trae y conserva acceso completo.
        /// </summary>
        Token GenerateDeviceToken(User user, string deviceId, string scopes, TimeSpan lifetime);
        User? GetById(string id);
        public string HashPassword(string password);
    }
}