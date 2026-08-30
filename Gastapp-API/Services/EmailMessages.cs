namespace Gastapp.Services
{
    /// <summary>
    /// Contenido de los correos, compartido por el envio SMTP y el envio por API HTTPS
    /// para que ambos manden exactamente el mismo mensaje.
    /// </summary>
    public static class EmailMessages
    {
        public const string PasswordResetSubject = "Código para restablecer tu contraseña";
        public const string TemporaryPasswordSubject = "Tu nueva contraseña temporal - Gastapp";
        public const string EmailVerificationSubject = "Confirma tu correo - Gastapp";

        public static string BuildEmailVerificationBody(string code) =>
            $@"¡Bienvenido a Gastapp!<br><br>
Para terminar de crear tu cuenta, confirma tu correo con este código:<br><br>
<strong style=""font-size:22px;letter-spacing:3px"">{code}</strong><br><br>
El código expira en 15 minutos.<br><br>
Si tú no intentaste crear una cuenta, puedes ignorar este correo.<br><br>
Equipo Gastapp";

        public static string BuildPasswordResetBody(string name, string code) =>
            $@"Hola {name},<br><br>
Tu código para restablecer la contraseña es:<br><br>
<strong>{code}</strong><br><br>
Este código expira en 15 minutos.<br><br>
Si no solicitaste este cambio, puedes ignorar este correo.<br><br>
Equipo Gastapp";

        public static string BuildTemporaryPasswordBody(string name, string temporaryPassword) =>
            $@"Hola {name},<br><br>
Has solicitado restablecer tu contraseña en Gastapp. Tu nueva contraseña temporal es:<br><br>
<strong>{temporaryPassword}</strong><br><br>
Utiliza esta contraseña para acceder a tu cuenta. Te recomendamos cambiarla por una más segura después de iniciar sesión.<br><br>
Si no solicitaste este cambio, puedes contactarnos inmediatamente para asegurar la seguridad de tu cuenta.<br><br>
Equipo Gastapp";
    }
}
