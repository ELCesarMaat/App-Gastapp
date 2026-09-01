using System;

namespace Gastapp.Models.Models
{
    public class AppLatestVersionDto
    {
        public int VersionCode { get; set; }
        public string VersionName { get; set; } = string.Empty;
        public string ApkUrl { get; set; } = string.Empty;
        public string ReleaseNotes { get; set; } = string.Empty;
        public DateTime PublishedAt { get; set; }
    }
}
