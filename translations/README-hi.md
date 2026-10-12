<div align="center">

<img width="" src="../fastlane/metadata/android/en-US/images/icon.png"  width=160 height=160  align="center">

# SnapNet

### Android के लिए वीडियो/ऑडियो डाउनलोडर

English
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="https://github.com/hoodmoshla/SnapNet/blob/main/translations/README-zh_Hans.md">简体中文</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="https://github.com/hoodmoshla/SnapNet/blob/main/translations/README-zh_Hant.md">繁體中文</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="https://github.com/hoodmoshla/SnapNet/blob/main/translations/README-ar.md">العربية</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="https://github.com/hoodmoshla/SnapNet/blob/main/translations/README-pt.md">Portuguese</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="https://github.com/hoodmoshla/SnapNet/blob/main/translations/README-ua.md">Українська</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="https://github.com/hoodmoshla/SnapNet/blob/main/translations/README-th.md">ภาษาไทย</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="https://github.com/hoodmoshla/SnapNet/blob/main/translations/README-fa.md">فارسی</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="https://github.com/hoodmoshla/SnapNet/blob/main/translations/README-it.md">Italiano</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="https://github.com/hoodmoshla/SnapNet/blob/main/translations/README-az.md">Azərbaycanca</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="https://github.com/hoodmoshla/SnapNet/blob/main/translations/README-ru.md">Русский</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="https://github.com/hoodmoshla/SnapNet/blob/main/translations/README-sr.md">Српски</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="https://github.com/hoodmoshla/SnapNet/blob/main/translations/README-ja.md">日本語</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="https://github.com/hoodmoshla/SnapNet/blob/main/translations/README-id.md">Indonesia</a>
&nbsp;&nbsp;| &nbsp;&nbsp;
<a href="https://github.com/hoodmoshla/SnapNet/blob/main/translations/README-hi.md">हिंदी</a>

[![GitHub release (latest by date)](https://img.shields.io/github/v/release/hoodmoshla/SnapNet?color=black&label=Stable&logo=github)](https://github.com/hoodmoshla/SnapNet/releases/latest/)
[![GitHub release (latest by date including pre-releases)](https://img.shields.io/github/v/release/hoodmoshla/SnapNet?include_prereleases&label=Preview&logo=Github)](https://github.com/hoodmoshla/SnapNet/releases/)
[![Keep a Changelog](https://img.shields.io/badge/Changelog-lightgray?style=flat&color=gray&logo=keep-a-changelog)](https://github.com/hoodmoshla/SnapNet/blob/main/CHANGELOG.md)
[![GitHub all releases](https://img.shields.io/github/downloads/hoodmoshla/SnapNet/total?label=Downloads&logo=github)](https://github.com/hoodmoshla/SnapNet/releases/)
[![GitHub Repo stars](https://img.shields.io/github/stars/hoodmoshla/SnapNet?style=flat&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0idXRmLTgiPz4KPHN2ZyBoZWlnaHQ9IjI0IiB2aWV3Qm94PSIwIC05NjAgOTYwIDk2MCIgd2lkdGg9IjI0IiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciPgogIDxwYXRoIGQ9Im0zNTQtMjQ3IDEyNi03NiAxMjYgNzctMzMtMTQ0IDExMS05Ni0xNDYtMTMtNTgtMTM2LTU4IDEzNS0xNDYgMTMgMTExIDk3LTMzIDE0M1pNMjMzLTgwbDY1LTI4MUw4MC01NTBsMjg4LTI1IDExMi0yNjUgMTEyIDI2NSAyODggMjUtMjE4IDE4OSA2NSAyODEtMjQ3LTE0OUwyMzMtODBabTI0Ny0zNTBaIiBzdHlsZT0iZmlsbDogcmdiKDI0NSwgMjI3LCA2Nik7Ii8%2BCjwvc3ZnPg%3D%3D&color=%23f8e444)](https://github.com/hoodmoshla/SnapNet/stargazers)
[![Supported-Sites](https://img.shields.io/badge/Sites-9cf?style=flat&logo=data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0idXRmLTgiPz4KPHN2ZyBoZWlnaHQ9IjI0cHgiIHZpZXdCb3g9IjAgMCAyNCAyNCIgd2lkdGg9IjI0cHgiIGZpbGw9IiNGRkZGRkYiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiAgPHBhdGggZD0iTTAgMGgyNHYyNEgwVjB6IiBmaWxsPSJub25lIi8+CiAgPHBhdGggZD0iTTExLjk5IDJDNi40NyAyIDIgNi40OCAyIDEyczQuNDcgMTAgOS45OSAxMEMxNy41MiAyMiAyMiAxNy41MiAyMiAxMlMxNy41MiAyIDExLjk5IDJ6bTYuOTMgNmgtMi45NWMtLjMyLTEuMjUtLjc4LTIuNDUtMS4zOC0zLjU2IDEuODQuNjMgMy4zNyAxLjkxIDQuMzMgMy41NnpNMTIgNC4wNGMuODMgMS4yIDEuNDggMi41MyAxLjkxIDMuOTZoLTMuODJjLjQzLTEuNDMgMS4wOC0yLjc2IDEuOTEtMy45NnpNNC4yNiAxNEM0LjEgMTMuMzYgNCAxMi42OSA0IDEycy4xLTEuMzYuMjYtMmgzLjM4Yy0uMDguNjYtLjE0IDEuMzItLjE0IDJzLjA2IDEuMzQuMTQgMkg0LjI2em0uODIgMmgyLjk1Yy4zMiAxLjI1Ljc4IDIuNDUgMS4zOCAzLjU2LTEuODQtLjYzLTMuMzctMS45LTQuMzMtMy41NnptMi45NS04SDUuMDhjLjk2LTEuNjYgMi40OS0yLjkzIDQuMzMtMy41NkM4LjgxIDUuNTUgOC4zNSA2Ljc1IDguMDMgOHpNMTIgMTkuOTZjLS44My0xLjItMS40OC0yLjUzLTEuOTEtMy45NmgzLjgyYy0uNDMgMS40My0xLjA4IDIuNzYtMS45MSAzLjk2ek0xNC4zNCAxNEg5LjY2Yy0uMDktLjY2LS4xNi0xLjMyLS4xNi0ycy4wNy0xLjM1LjE2LTJoNC42OGMuMDkuNjUuMTYgMS4zMi4xNiAycy0uMDcgMS4zNC0uMTYgMnptLjI1IDUuNTZjLjYtMS4xMSAxLjA2LTIuMzEgMS4zOC0zLjU2aDIuOTVjLS45NiAxLjY1LTIuNDkgMi45My00LjMzIDMuNTZ6TTE2LjM2IDE0Yy4wOC0uNjYuMTQtMS4zMi4xNC0ycy0uMDYtMS4zNC0uMTQtMmgzLjM4Yy4xNi42NC4yNiAxLjMxLjI2IDJzLS4xIDEuMzYtLjI2IDJoLTMuMzh6IiBzdHlsZT0iZmlsbDogcmdiKDE2MiwgMTk4LCAyMzQpOyIvPgo8L3N2Zz4=&label=Supported)](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md)
[![Matrix](https://img.shields.io/matrix/SnapNet-space%3Amatrix.org?server_fqdn=matrix.org&style=flat&logo=element&label=Matrix&color=%230DBD8B)

</div>

## 📱 स्क्रीनशॉट

<div align="center">
<div>
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpg" width="30%" />
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpg" width="30%" />
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/3.jpg" width="30%" />
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/4.jpg" width="30%" />
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/5.jpg" width="30%" />
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/6.jpg" width="30%" />
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/7.jpg" width="30%" />
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/8.jpg" width="30%" />
<img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/9.jpg" width="30%" />
</div>
</div>

<br>

## 📖 विशेषताएँ

- [yt-dlp](https://github.com/yt-dlp/yt-dlp) (पूर्व में youtube-dl) द्वारा समर्थित वीडियो प्लेटफ़ॉर्म से वीडियो और ऑडियो फ़ाइलें डाउनलोड करें।

- [mutagen](https://github.com/quodlibet/mutagen) द्वारा समर्थित ऑडियो फ़ाइलों में मेटाडेटा और वीडियो थंबनेल एम्बेड करें।

- एक क्लिक में प्लेलिस्ट में सभी वीडियो डाउनलोड करें।

- सभी डाउनलोड के लिए बाहरी डाउनलोडर के रूप में एम्बेडेड [aria2c](https://github.com/aria2/aria2) का उपयोग करें।

- डाउनलोड किए गए वीडियो में सबटाइटल एम्बेड करें।

- टेम्प्लेट के साथ कस्टम yt-dlp कमांड चलाएँ।

- इन-ऐप डाउनलोड और कस्टम कमांड टेम्प्लेट प्रबंधित करें।

- उपयोग में आसान और उपयोगकर्ता-मित्रवत।

- [Material Design 3](https://m3.material.io/) शैली UI, डायनेमिक रंग थीम के साथ।

- MAD: UI और लॉजिक को शुद्ध Kotlin में लिखा गया है। एकल गतिविधि, कोई फ़्रैगमेंट नहीं, केवल संयोजनीय गंतव्य।

## ⬇️ डाउनलोड

अधिकांश डिवाइसों के लिए, **arm64-v8a** संस्करण की स्थापना की सिफारिश की जाती है।

- [GitHub releases](https://github.com/hoodmoshla/SnapNet/releases/latest) से नवीनतम स्थिर संस्करण डाउनलोड करें।
  - नए फीचर्स और परिवर्तनों का परीक्षण करने के लिए [pre-release](https://github.com/hoodmoshla/SnapNet/releases/) संस्करणों को इंस्टॉल करें।

<!-- [<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"

## 🤝 योगदान

योगदान का स्वागत है!

>[!Note]
>
>बग रिपोर्ट, फीचर अनुरोध, सवाल, या सुधार के किसी भी विचार को सबमिट करने के लिए, कृपया पहले [CONTRIBUTING.md](https://github.com/hoodmoshla/SnapNet/blob/main/CONTRIBUTING.md) को पढ़ें।

## ⭐️ स्टार इतिहास

[![Star History Chart](https://api.star-history.com/svg?repos=hoodmoshla/SnapNet&type=Timeline)](https://star-history.com/#hoodmoshla/SnapNet&Timeline)

## 🧱 क्रेडिट

SnapNet एक सरल GUI है [yt-dlp](https://github.com/yt-dlp/yt-dlp) का, जो [youtubedl-android](https://github.com/yausername/youtubedl-android) पर आधारित है।

UI डिज़ाइन और कोड में से कुछ [Read You](https://github.com/Ashinch/ReadYou) और [Music You](https://github.com/Kyant0/MusicYou) से उधार लिए गए हैं।

[dvd](https://github.com/yausername/dvd)

[Material color utilities](https://github.com/material-foundation/material-color-utilities)

[Monet](https://github.com/Kyant0/Monet)

## 📃 लाइसेंस

[![GitHub](https://img.shields.io/github/license/hoodmoshla/SnapNet?style=for-the-badge)](https://github.com/hoodmoshla/SnapNet/blob/main/LICENSE)
