namespace Gastapp.Models
{
    public class JwtSettings
    {
        public string Secret { get; set; }
        public int ExpiryInDays { get; set; }
        public string Issuer { get; set; }
        public string Audience { get; set; }
    }
}