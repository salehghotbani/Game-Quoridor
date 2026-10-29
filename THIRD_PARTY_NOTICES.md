# Third-party notices

The Quoridor application source is licensed under [MIT](LICENSE). The original developers are listed in [AUTHORS.md](AUTHORS.md).

## Android runtime backports

The Android APK includes code produced from **desugar_jdk_libs 2.1.5**, Google's subset of OpenJDK library implementations for older Android releases. Its license is **GNU General Public License, version 2, with the Classpath Exception**. The exception permits linking this library with independent application code; it does not replace the application's MIT license.

- Publisher and corresponding source: <https://github.com/google/desugar_jdk_libs>
- Release history and version: <https://github.com/google/desugar_jdk_libs/blob/master/CHANGELOG.md>
- Library license: [third_party/desugar_jdk_libs/LICENSE](third_party/desugar_jdk_libs/LICENSE)

Keep the library's license and this notice with redistributed Android packages. The Android in-app **Authors & license** screen includes these notices.

The associated desugaring configuration also carries an **R8 BSD 3-Clause** notice. Its text is retained in [third_party/r8/LICENSE](third_party/r8/LICENSE) and as `r8-LICENSE` in the APK assets.

## Build tools

The browser application uses browser APIs and repository code without runtime libraries or CDN scripts. Its pinned Playwright dependency and downloaded browser are development/test tools only; they are excluded by the production build allowlist. Application copyright and the MIT license remain available in the web footer/About screen and distributed notices.

The tracked **Gradle 8.14.3 wrapper** is licensed under **Apache License 2.0**. It downloads the pinned distribution and verifies its published SHA-256 checksum. Gradle, Android Gradle Plugin, SDK tools, JUnit and the Java formatter are build/test tools; they are not bundled as application dependencies.

- Gradle source and license: <https://github.com/gradle/gradle/tree/v8.14.3>
- Gradle wrapper license: [third_party/gradle/LICENSE](third_party/gradle/LICENSE)
- Android SDK tool licenses are provided by the SDK installation.

## Windows runtime and existing icon

Windows packages retain the legal notices supplied by their linked Java runtime. The original application's existing icon and its provenance are described in [AUTHORS.md](AUTHORS.md); the Android launcher uses the vector board icon defined in this repository.

## فارسی

کد بازی با مجوز MIT منتشر می‌شود. نسخهٔ اندروید برای پشتیبانی از نسخه‌های قدیمی‌تر Android از کتابخانهٔ `desugar_jdk_libs` با مجوز GPL نسخهٔ ۲ و استثنای Classpath استفاده می‌کند. متن مجوز و پیوند منبع کتابخانه همراه بسته و در بخش «سازندگان و مجوز» برنامه قرار دارد. ابزار ساخت Gradle با مجوز Apache 2.0 است و جزئی از اجرای بازی نیست.

## Shabnam font 5.0.1

Shabnam by Saber Rastikerdar is bundled unmodified from
https://github.com/rastikerdar/shabnam-font/tree/v5.0.1/dist .
Its SIL Open Font License 1.1 and Roboto attribution accompany the font in
`src/Files/fonts/LICENSE.txt`, `web/fonts/LICENSE.txt` and Android's
`assets/fonts/LICENSE.txt`. The application's MIT license does not replace the
font license. Fonts are served locally; no third-party font/CDN request is made.
