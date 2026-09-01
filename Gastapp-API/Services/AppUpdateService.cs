using System;
using System.Linq;
using System.Net.Http;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;
using Gastapp.Models.Models;
using Microsoft.Extensions.Logging;

namespace Gastapp.Services
{
    // Version publica del ultimo release en GitHub. Publicar un release ahi es lo
    // unico que hace falta para que la app detecte una actualizacion: no hay que
    // tocar la API en cada release, solo la primera vez que se configura esto.
    public class AppUpdateService : IAppUpdateService
    {
        private readonly HttpClient _http;
        private readonly ILogger<AppUpdateService> _logger;
        private readonly string _repo;

        // Cache en memoria para no pegarle a la API publica de GitHub (60 req/hora sin
        // token) en cada apertura de la app de cada usuario.
        private static AppLatestVersionDto? _cached;
        private static DateTime _cachedAt = DateTime.MinValue;
        private static readonly TimeSpan CacheDuration = TimeSpan.FromMinutes(10);
        private static readonly SemaphoreSlim RefreshLock = new(1, 1);

        public AppUpdateService(HttpClient http, ILogger<AppUpdateService> logger)
        {
            _http = http;
            _logger = logger;
            _repo = Environment.GetEnvironmentVariable("GITHUB_RELEASES_REPO") ?? "ELCesarMaat/App-Gastapp";
        }

        public async Task<AppLatestVersionDto?> GetLatestVersionAsync(CancellationToken cancellationToken)
        {
            if (_cached != null && DateTime.UtcNow - _cachedAt < CacheDuration)
                return _cached;

            await RefreshLock.WaitAsync(cancellationToken);
            try
            {
                if (_cached != null && DateTime.UtcNow - _cachedAt < CacheDuration)
                    return _cached;

                var result = await FetchLatestVersionAsync(cancellationToken);
                if (result != null)
                {
                    _cached = result;
                    _cachedAt = DateTime.UtcNow;
                }

                return result ?? _cached;
            }
            finally
            {
                RefreshLock.Release();
            }
        }

        private async Task<AppLatestVersionDto?> FetchLatestVersionAsync(CancellationToken cancellationToken)
        {
            try
            {
                // /releases/latest de GitHub ignora los releases marcados como prerelease, y
                // durante el alpha TODOS los releases van a ser prerelease. Se pide la lista
                // completa (ya viene ordenada por fecha de creacion) y se toma el primero.
                var releases = await _http.GetFromJsonAsync<GitHubRelease[]>(
                    $"https://api.github.com/repos/{_repo}/releases", cancellationToken);

                var release = releases?.FirstOrDefault(r => !r.Draft);
                if (release?.Assets == null)
                    return null;

                var apkAsset = release.Assets.FirstOrDefault(a =>
                    a.Name != null && a.Name.EndsWith(".apk", StringComparison.OrdinalIgnoreCase));
                if (apkAsset?.BrowserDownloadUrl == null)
                {
                    _logger.LogWarning("El release {Tag} no tiene un .apk adjunto.", release.TagName);
                    return null;
                }

                var versionCode = 0;
                var versionAsset = release.Assets.FirstOrDefault(a =>
                    string.Equals(a.Name, "version.json", StringComparison.OrdinalIgnoreCase));
                if (versionAsset?.BrowserDownloadUrl != null)
                {
                    try
                    {
                        var versionInfo = await _http.GetFromJsonAsync<VersionAsset>(versionAsset.BrowserDownloadUrl, cancellationToken);
                        versionCode = versionInfo?.VersionCode ?? 0;
                    }
                    catch (Exception ex)
                    {
                        _logger.LogWarning(ex, "No se pudo leer version.json del release {Tag}.", release.TagName);
                    }
                }

                return new AppLatestVersionDto
                {
                    VersionCode = versionCode,
                    VersionName = (release.TagName ?? string.Empty).TrimStart('v'),
                    ApkUrl = apkAsset.BrowserDownloadUrl,
                    ReleaseNotes = release.Body ?? string.Empty,
                    PublishedAt = release.PublishedAt ?? DateTime.UtcNow
                };
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "No se pudo consultar el ultimo release de GitHub ({Repo}).", _repo);
                return null;
            }
        }

        private class GitHubRelease
        {
            [JsonPropertyName("tag_name")]
            public string? TagName { get; set; }

            [JsonPropertyName("body")]
            public string? Body { get; set; }

            [JsonPropertyName("published_at")]
            public DateTime? PublishedAt { get; set; }

            [JsonPropertyName("assets")]
            public GitHubAsset[]? Assets { get; set; }

            [JsonPropertyName("draft")]
            public bool Draft { get; set; }
        }

        private class GitHubAsset
        {
            [JsonPropertyName("name")]
            public string? Name { get; set; }

            [JsonPropertyName("browser_download_url")]
            public string? BrowserDownloadUrl { get; set; }
        }

        private class VersionAsset
        {
            [JsonPropertyName("versionCode")]
            public int VersionCode { get; set; }
        }
    }
}
