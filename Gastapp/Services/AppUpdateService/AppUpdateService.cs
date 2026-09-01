using System;
using System.IO;
using System.Net.Http;
using System.Threading.Tasks;
using Gastapp.Models.Models;
using Gastapp.Services.ApiService;
using Microsoft.Maui.ApplicationModel;
using Microsoft.Maui.Storage;

#if ANDROID
using Android.Content;
using AndroidXFileProvider = AndroidX.Core.Content.FileProvider;
#endif

namespace Gastapp.Services.AppUpdateService
{
    // Mecanismo de auto-actualizacion sin Play Store: la API expone el ultimo
    // release publicado en GitHub, la app compara el versionCode instalado
    // contra ese valor y, si hay uno mas nuevo, descarga el APK y le pide a
    // Android que lo instale (requiere permiso REQUEST_INSTALL_PACKAGES).
    public class AppUpdateService : IAppUpdateService
    {
        private readonly IApiService _api;
        private readonly IHttpClientFactory _httpClientFactory;

        public AppUpdateService(IApiService api, IHttpClientFactory httpClientFactory)
        {
            _api = api;
            _httpClientFactory = httpClientFactory;
        }

        public async Task<AppLatestVersionDto?> CheckForUpdateAsync()
        {
            try
            {
                var latest = await _api.GetLatestVersion();
                if (latest is null || latest.VersionCode <= 0)
                    return null;

                var currentVersionCode = GetCurrentVersionCode();
                return latest.VersionCode > currentVersionCode ? latest : null;
            }
            catch
            {
                // Sin conexion o API caida: no interrumpir el arranque de la app por esto.
                return null;
            }
        }

        private static int GetCurrentVersionCode()
        {
            return int.TryParse(AppInfo.Current.BuildString, out var code) ? code : 0;
        }

        public async Task DownloadAndInstallAsync(AppLatestVersionDto latest)
        {
#if ANDROID
            var context = Android.App.Application.Context;

            if (OperatingSystem.IsAndroidVersionAtLeast(26) && context.PackageManager != null && !context.PackageManager.CanRequestPackageInstalls())
            {
                // Sin este permiso Android bloquea la instalacion en silencio. Se manda
                // al usuario directo a la pantalla donde lo puede habilitar para Gastapp.
                var settingsIntent = new Intent(Android.Provider.Settings.ActionManageUnknownAppSources,
                    Android.Net.Uri.Parse($"package:{context.PackageName}"));
                settingsIntent.AddFlags(ActivityFlags.NewTask);
                context.StartActivity(settingsIntent);
                return;
            }
#endif

            var client = _httpClientFactory.CreateClient("update");
            var apkPath = Path.Combine(FileSystem.CacheDirectory, "gastapp-update.apk");

            using (var response = await client.GetAsync(latest.ApkUrl, HttpCompletionOption.ResponseHeadersRead))
            {
                response.EnsureSuccessStatusCode();
                await using var httpStream = await response.Content.ReadAsStreamAsync();
                await using var fileStream = File.Create(apkPath);
                await httpStream.CopyToAsync(fileStream);
            }

#if ANDROID
            var apkFile = new Java.IO.File(apkPath);
            var authority = $"{context.PackageName}.fileprovider";
            var apkUri = AndroidXFileProvider.GetUriForFile(context, authority, apkFile);

            var installIntent = new Intent(Intent.ActionView);
            installIntent.SetDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.AddFlags(ActivityFlags.GrantReadUriPermission | ActivityFlags.NewTask);
            context.StartActivity(installIntent);
#endif
        }
    }
}
