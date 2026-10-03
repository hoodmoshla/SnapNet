# Security Policy

## Reporting a vulnerability

Please **do not** open a public issue for security problems. Report them privately via GitHub's
[Security Advisories](https://docs.github.com/en/code-security/security-advisories) for this
repository, or by contacting the maintainers directly.

Please include:

- a description of the issue and its impact,
- the affected version / commit,
- reproduction steps or a proof of concept where possible.

We aim to acknowledge reports within a few days.

## Supported versions

Only the latest release is supported with security fixes.

## Design principles

SnapNet is built to keep user data on the device:

- **No telemetry, no analytics, no crash reporting service.** The app contains no Firebase, no
  Google Play Services, and no advertising SDK.
- **No project-operated backend.** Media is fetched directly from the site you asked for. There is
  no SnapNet server that sees your URLs, your cookies, or your downloads.
- **No credential entry.** SnapNet never asks for your username or password for any site.
- **No DRM circumvention.** DRM-protected streams are detected and reported as unsupported,
  deliberately and permanently.

## Credentials and secrets

- Secrets are **never** committed to this repository. Signing material is supplied to CI through
  GitHub Actions secrets and is written to a temporary file that is destroyed with the runner.
- The app ships **no** embedded API keys, tokens, or private keys. A previous version of this
  codebase (inherited from the upstream fork) contained a hard-coded GitHub personal access token
  used to fetch a sponsor list; it has been removed, along with the network call that used it.
- CI never prints secret values.

## Cookies

Cookies are the most sensitive data SnapNet handles, because a session cookie is equivalent to being
logged in.

- Cookies are read **only** from the app's own WebView cookie jar, on-device, and are converted into
  a Netscape-format `cookies.txt` inside the app's private cache directory.
- They are passed **only** to the bundled yt-dlp process, which sends them to the target site — the
  same site you signed in to. They are never transmitted to the project or any third party.
- Cookie contents, `Authorization` headers, and tokens are **never** written to logs or to crash
  reports.
- Session cookies are sensitive. If you export a cookie file or share a debug log, treat the result
  as a credential and delete it when you are done.

## Downloaded components and Android execution constraints

SnapNet updates the yt-dlp engine at runtime. This is deliberate and bounded:

- Downloads happen over HTTPS from `github.com/yt-dlp/*` releases only.
- yt-dlp is a **Python zipapp**, not a native executable. It is interpreted by the CPython runtime
  bundled inside the APK, so an update cannot introduce new native code into the process.
- Every update is wrapped in a backup / verify / rollback envelope: the working engine is copied
  aside first, the freshly installed engine is validated, and a failed or unusable update is rolled
  back automatically. See `YtDlpUpdater.kt`.
- Because Android forbids executing binaries from writable app storage for apps targeting API 29+
  (the "W^X" rule), native components such as FFmpeg cannot be replaced at runtime at all. They ship
  inside the APK and only change with a new release, which is what makes their provenance
  verifiable.

## The bundled JavaScript engine

`app/src/main/jniLibs/*/libqjs.so` is QuickJS-ng, built from unmodified upstream sources with
`tools/build-quickjs.sh`, which is committed so the artifact can be audited and rebuilt byte-for-byte
from source. It is statically linked against musl, so it needs no dynamic linker, and it is shipped
inside the APK rather than downloaded — an engine that is `exec()`-ed by the downloader is exactly the
kind of component that must not be silently replaceable at runtime.

## Input handling

- URLs, titles, uploader names and user-supplied output templates are treated as untrusted.
- Generated filenames are sanitised: path separators, control characters, and reserved device names
  are removed, so a crafted video title cannot escape the chosen download directory. See
  `FileNaming.kt` and its unit tests.
- Arguments are passed to yt-dlp as an argument vector rather than through a shell, and the app
  never builds a shell command string from user input.

## Permissions

SnapNet requests broad storage access (`MANAGE_EXTERNAL_STORAGE`) so that downloads can be written to
arbitrary user-chosen folders on all supported Android versions. If you only need the default
location, you can deny it: the app falls back to its scoped-storage paths.

## Scope

Out of scope: anything requiring a rooted device, physical access to an unlocked device, or the
behaviour of third-party sites and their own security practices.
