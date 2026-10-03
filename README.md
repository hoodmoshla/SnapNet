# SnapNet

**Video and audio downloader for Android — local-first, no account, no server.**

SnapNet is a modern Android app that downloads video and audio from the sites supported by
[yt-dlp](https://github.com/yt-dlp/yt-dlp) (1,700+ extractors). Everything happens on the device:
there is no SnapNet backend, no telemetry, and no sign-in.

<!-- screenshots -->

## Highlights

- **Local by design** — no SnapNet server, no analytics, no Firebase, no Play Services. Media is
  fetched straight from the site you asked for.
- **yt-dlp engine** — a bundled CPython runtime runs yt-dlp, so the engine can be updated without
  shipping a new APK.
- **JavaScript challenge solving** — a QuickJS-ng engine ships inside the APK for every ABI, so
  yt-dlp's external-JS (EJS) solver works out of the box and the full YouTube format list is
  available with no runtime to install.
- **FFmpeg post-processing** — separate video and audio streams are merged, audio can be extracted
  and converted, subtitles and thumbnails embedded, and chapters split.
- **Background downloads** — a real foreground service keeps downloads running with the screen off
  and the app in the background.
- **Share and clipboard** — share a link from any app, or paste it; SnapNet never auto-downloads.
- **Playlists**, format/quality selection, download history, and a Material 3 UI in Arabic and
  English.

## How it works

```
UI (Jetpack Compose, Material 3)
        │
        ▼
ViewModel  ──►  Domain layer (use cases, models)
        │
        ▼
DownloaderEngine  ──►  YtDlpEngine
        │                   │
        │                   ├─► CPython 3.11 (bundled in the APK)
        │                   ├─► yt-dlp zipapp (runtime-updatable)
        │                   ├─► JavaScript runtime (EJS challenges)
        │                   └─► FFmpeg (bundled, post-processing)
        │
        ├─► Storage      (MediaStore / Storage Access Framework)
        ├─► Cookies      (app-local WebView jar → Netscape cookies.txt)
        └─► RetryPolicy  (bounded, error-aware)
```

Failures from the engine are classified into a small, stable set of kinds
(`LOGIN_REQUIRED`, `COOKIES_REQUIRED`, `PO_TOKEN_REQUIRED`, `NETWORK_ERROR`, `GEO_BLOCKED`,
`VIDEO_UNAVAILABLE`, `FORMAT_UNAVAILABLE`, `RATE_LIMITED`, `DRM_PROTECTED`, `UNSUPPORTED_URL`, …)
instead of surfacing raw engine text, and only genuinely transient kinds are retried.

## Requirements

- Android 7.0 (API 24) or newer. Tested target: Android 13–16.
- ~150 MB of free space for the app plus temporary download space.

## Building

```bash
git clone https://github.com/hoodmoshla/SnapNet.git
cd SnapNet
./gradlew :app:assembleGenericDebug     # debug APKs (per ABI + universal)
./gradlew :app:assembleGenericRelease   # release APKs
```

Release signing is driven by an optional `keystore.properties` at the repository root:

```properties
storeFile=release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

When the file is absent the release build is produced unsigned, which is what the CI workflow does on
forks and pull requests.

### Rebuilding the JavaScript engine

The QuickJS binaries in `app/src/main/jniLibs/*/libqjs.so` are built from unmodified upstream
sources and committed so that the shipped artifact is reproducible:

```bash
tools/build-quickjs.sh          # requires zig on PATH
```

They are statically linked against musl so they run without bionic's dynamic linker, and they are
placed in `jniLibs` because Android only grants `exec()` to files in `nativeLibraryDir`.
See `app/src/main/assets/licenses/quickjs-ng.txt` for the upstream licence.

### Continuous integration

`.github/workflows/build.yml` runs unit tests, builds debug and release APKs, verifies each archive,
computes SHA-256 checksums, and uploads everything as artifacts. Pushing a `v*` tag additionally
publishes a GitHub Release. Signing secrets (`SIGNING_KEY`, `KEY_STORE_PASSWORD`, `ALIAS`,
`KEY_PASSWORD`) are optional and are never written to the repository.

## Supported sites

SnapNet delegates to yt-dlp, so site support follows yt-dlp's own extractor list. Commonly used
targets include YouTube (including Shorts and playlists), TikTok, Facebook, Instagram, X/Twitter,
Reddit, SoundCloud, Vimeo, Twitch, Dailymotion, Pinterest, Threads, Bilibili and Streamable.

Sites that require a login, are region-locked, or are DRM-protected are reported clearly rather than
silently failing. SnapNet does **not** attempt to bypass DRM.

## Cookies

Some content (age-restricted videos, private or members-only posts, and sites that gate media behind
a session) needs cookies.

SnapNet lets you sign in to a site inside a built-in WebView, then converts the app's own cookie jar
into a Netscape `cookies.txt` on the device. Cookies are handed only to the local yt-dlp process and
are sent only to the site you signed in to. They are never uploaded anywhere, never written to logs,
and they are excluded from backups.

You are never asked to type a username or password into SnapNet itself.

See [SECURITY.md](SECURITY.md) for the full threat model.

## Verified behaviour

Engine behaviour is validated by executing real commands. The following were reproduced against
**yt-dlp 2026.08.19** with a JavaScript runtime present:

| Check | Result | Status |
| --- | --- | --- |
| YouTube format listing, no JS runtime | 44 formats, plus the warning *"No supported JavaScript runtime could be found … some formats may be missing"* | VERIFIED |
| YouTube format listing, with the bundled QuickJS | **45 formats, warning gone** | VERIFIED |
| EJS solver actually runs | yt-dlp logs `[jsc:quickjs] Solving JS challenges using quickjs` and `Running QuickJS: …/libqjs.so --script <tmp>.js` | VERIFIED |
| 720p video + audio download and merge | both streams downloaded and merged to a playable 1280×720 MP4 (av1 + opus, 213 s) | VERIFIED |
| Audio extraction | extracted and converted to a valid MP3 (≈139 kbps) | VERIFIED |
| QuickJS binary architecture | correct ELF machine for arm64-v8a, armeabi-v7a, x86, x86_64 | VERIFIED |

These checks describe the engine pipeline that SnapNet drives. They are not a substitute for
on-device testing of the app itself; see *Limitations* below.

## Limitations

- **No DRM support**, by design.
- **Login-gated and region-locked content** needs cookies you supply, and may still be unavailable.
- **YouTube changes constantly.** If a download breaks, update the engine from
  *Settings → Advanced → yt-dlp version*.
- Some sites use TLS fingerprinting that yt-dlp can only bypass with `curl_cffi`, which is not
  present in the portable zipapp build used on Android.
- FFmpeg and the JavaScript runtime are bundled natively and therefore can only be updated by
  installing a new APK, not at runtime.

## Privacy

No analytics, no telemetry, no advertising, no user accounts. URLs and cookies stay on the device.
The only network calls SnapNet makes are to the site you asked it to download from, and to GitHub to
update the yt-dlp engine.

## Licences

SnapNet is licensed under the **GNU General Public License v3.0** (see [LICENSE](LICENSE)), inherited
from the upstream project it is based on. Because of this, SnapNet must remain open source: if you
distribute a build, you must also make the corresponding source available.

Bundled and linked components:

| Component | Licence |
| --- | --- |
| yt-dlp | Unlicense |
| yt-dlp-ejs | Unlicense (bundles MIT and ISC components) |
| QuickJS-ng (`libqjs.so`) | MIT |
| youtubedl-android | GPL-3.0 |
| CPython | PSF |
| FFmpeg | depends on build (LGPL/GPL) |
| aria2 | GPL-2.0+ |
| AndroidX, Compose, Koin, OkHttp, Coil | Apache-2.0 |
| MMKV | BSD-3-Clause |

## Not yet implemented

SnapNet is functional end-to-end for analysing a link, choosing a format, downloading, merging with
FFmpeg, and keeping the download alive in the background. The following are **known gaps**, listed
here rather than left as silent TODOs in the code:

- **Favourites** are not implemented yet.
- **Pause/resume** is not implemented. A running task can be cancelled, and a failed task retried,
  but an in-flight download cannot be paused and continued.
- **Pause/resume** stops a running task and keeps its partial file so it can be continued; what is
  missing is a per-task speed/ETA/size read-out in the queue screen.
- **The settings surface** still exposes options inherited from the upstream project; some are not
  yet verified against the new engine layer.
- **Focus areas for the next iteration:** a dedicated download-manager screen with per-task speed,
  ETA and size, and a simplified home screen.

## Credits

SnapNet is a fork of [Seal](https://github.com/JunkFood02/Seal) by
[JunkFood02](https://github.com/JunkFood02), which is itself built on
[yt-dlp](https://github.com/yt-dlp/yt-dlp) and
[youtubedl-android](https://github.com/yausername/youtubedl-android). Thanks to those projects and to
the translators who made the localisations possible.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Security issues should follow [SECURITY.md](SECURITY.md)
instead of the public issue tracker.
