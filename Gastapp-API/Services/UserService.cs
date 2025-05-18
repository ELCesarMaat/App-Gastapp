using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using Gastapp.Models;
using Gastapp_API.Models;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;
using Gastapp_API.Data;
using Microsoft.EntityFrameworkCore;

namespace Gastapp.Services
{
    public class UserService : IUserService
    {
        //private readonly AppSettings _appSettings;
        private readonly JwtSettings _jwtSettings;
        private readonly GastappDbContext _context;

        public UserService(IOptions<JwtSettings> jwtSettings, GastappDbContext context)
        {
            _jwtSettings = jwtSettings.Value;
            _context = context;
        }

        public async Task<AuthenticateResponse> AuthenticateAsync(AuthenticateRequest model)
        {
            //Logica de verificacion de contraseña hasheada

            var user = await _context.Users
            .FirstOrDefaultAsync(u => u.Email == model.Email &&
                                  u.PassWordHash == HashPassword(model.Password));

            if (user == null) return null;

            var token = GenerateJwtToken(user);
            return new AuthenticateResponse(user, token);
        }

        public User GetById(string id)
        {
            return _context.Users.FirstOrDefault(u => u.UserId == id);

        }

        private string GenerateJwtToken(User user)
        {
            var tokenHandler = new JwtSecurityTokenHandler();
            var key = Encoding.ASCII.GetBytes(_jwtSettings.Secret);
            var tokenDescriptor = new SecurityTokenDescriptor
            {
                Subject = new ClaimsIdentity(new[]
                {
                    new Claim(ClaimTypes.NameIdentifier, user.UserId),
                    new Claim(ClaimTypes.Email, user.Email),
                    new Claim(ClaimTypes.Name, user.Name)
                }),
                Expires = DateTime.UtcNow.AddDays(_jwtSettings.ExpiryInDays),
                Issuer = _jwtSettings.Issuer,
                Audience = _jwtSettings.Audience,
                SigningCredentials = new SigningCredentials(
                    new SymmetricSecurityKey(key),
                    SecurityAlgorithms.HmacSha256Signature)
            };

            var token = tokenHandler.CreateToken(tokenDescriptor);
            return tokenHandler.WriteToken(token);
        }

             private string HashPassword(string password)
        {
            // Implementa tu lógica de hashing aquí (usando BCrypt, PBKDF2, etc.)
            return password; // Esto es solo un ejemplo, NO usar en producción
        }
    }
}
