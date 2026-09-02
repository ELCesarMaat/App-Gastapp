using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using Gastapp.Models.Models;

namespace Gastapp.Services
{
    /// <summary>Resultados del polling y de la vinculacion, en terminos de RFC 8628.</summary>
    public enum DeviceAuthOutcome
    {
        Ok,
        AuthorizationPending,
        SlowDown,
        ExpiredToken,
        AccessDenied,
        InvalidCode,
        TooManyAttempts
    }

    public record DeviceTokenOutcome(DeviceAuthOutcome Outcome, DeviceTokenResponse? Token);
    public record DeviceLinkOutcome(DeviceAuthOutcome Outcome, LinkDeviceResponse? Device);

    public interface IDeviceAuthService
    {
        Task<DeviceCodeResponse> CreateAuthorizationAsync(DeviceCodeRequest request, CancellationToken cancellationToken);

        Task<DeviceTokenOutcome> PollAsync(string deviceCode, CancellationToken cancellationToken);

        Task<DeviceLinkOutcome> LinkAsync(string userId, string userCode, CancellationToken cancellationToken);

        Task<DeviceTokenOutcome> RefreshAsync(string refreshToken, CancellationToken cancellationToken);

        Task<List<DeviceDto>> ListAsync(string userId, CancellationToken cancellationToken);

        Task<bool> RevokeAsync(string userId, string deviceId, CancellationToken cancellationToken);
    }
}
