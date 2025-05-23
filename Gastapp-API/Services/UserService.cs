using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using Gastapp.Models;
using Gastapp_API.Models;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;
using Gastapp_API.Data;
using Gastapp.Models.Models;
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

        public async Task<AuthenticateResponse?> AuthenticateAsync(AuthenticateRequest model)
        {
            //Logica de verificacion de contraseña hasheada

            var user = await _context.Users.FirstOrDefaultAsync(u => u.Email == model.Email);
            if (user == null || !VerifyPassword(model.Password, user.PassWordHash))
                return null;


            var tokenResponse = GenerateJwtToken(user);
            return new AuthenticateResponse
            {
                UserId = user.UserId,
                Email = user.Email,
                Name = user.Name,
                Token = tokenResponse.TokenValue,
                TokenExpiration = tokenResponse.TokenExpiration
            };
        }

        public Token GenerateNewToken(User user)
        {
            var tokenResponse = GenerateJwtToken(user);
            return tokenResponse;
        }

        public User? GetById(string id)
        {
            return _context.Users.FirstOrDefault(u => u.UserId == id);
        }

        private Token GenerateJwtToken(User user)
        {
            var tokenHandler = new JwtSecurityTokenHandler();
            var key = Encoding.ASCII.GetBytes(_jwtSettings.Secret);
            var tokenDescriptor = new SecurityTokenDescriptor
            {
                Subject = new ClaimsIdentity(
                [
                    new Claim(ClaimTypes.NameIdentifier, user.UserId),
                    new Claim(ClaimTypes.Email, user.Email),
                    new Claim(ClaimTypes.Name, user.Name)
                ]),
                Expires = DateTime.Now.AddDays(_jwtSettings.ExpiryInDays).AddHours(1),
                Issuer = _jwtSettings.Issuer,
                Audience = _jwtSettings.Audience,
                SigningCredentials = new SigningCredentials(
                    new SymmetricSecurityKey(key),
                    SecurityAlgorithms.HmacSha256Signature)
            };
            var token = tokenHandler.CreateToken(tokenDescriptor);
            var tokenResponse = new Token
            {
                TokenExpiration = tokenDescriptor.Expires,
                TokenValue = tokenHandler.WriteToken(token)
            };
            return tokenResponse;
        }

        public string HashPassword(string password)
        {
            // Parámetros de hashing
            int saltSize = 16; // 128 bits
            int keySize = 32; // 256 bits
            int iterations = 100_000;

            // Generar salt aleatorio
            byte[] salt = RandomNumberGenerator.GetBytes(saltSize);

            // Derivar clave
            using var pbkdf2 = new Rfc2898DeriveBytes(password, salt, iterations, HashAlgorithmName.SHA256);
            byte[] hash = pbkdf2.GetBytes(keySize);

            // Combinar salt + hash + iterations en un solo string para almacenar
            return $"{iterations}.{Convert.ToBase64String(salt)}.{Convert.ToBase64String(hash)}";
        }

        private bool VerifyPassword(string password, string storedHash)
        {
            var parts = storedHash.Split('.');
            if (parts.Length != 3) return false;

            int iterations = int.Parse(parts[0]);
            byte[] salt = Convert.FromBase64String(parts[1]);
            byte[] storedHashBytes = Convert.FromBase64String(parts[2]);

            using var pbkdf2 = new Rfc2898DeriveBytes(password, salt, iterations, HashAlgorithmName.SHA256);
            byte[] computedHash = pbkdf2.GetBytes(storedHashBytes.Length);

            return CryptographicOperations.FixedTimeEquals(storedHashBytes, computedHash);
        }
    }
}