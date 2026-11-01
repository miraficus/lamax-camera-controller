Unofficial LAMAX X10.1 client for Android 10+. Download **X10Connect-0.2.1.apk** below. This preview keeps the signing identity used by 0.1.1 and later previews, so it can update those builds directly.

### New: export camera web files to ZIP

- Settings → Camera web files (ZIP) → Export web files to ZIP (without SD).
- Reads the web server directory listings and copies available static files recursively, including `res/pic`, while excluding SD/storage, symbolic links, CGI, query links and external addresses.
- Saves to **Downloads/X10Connect**, with **Share latest ZIP** in the app.
- Includes paths, sizes, SHA-256 and any failures in `export-manifest.json`. Incomplete exports are labelled `-partial.zip`; cancellation removes the unfinished archive.
- Runs in the background with progress and cancellation. Does not upload files or invoke any firmware update action.

**This is a copy of exposed web files, not a full flash image, recovery backup or installable firmware update.** The new export needs testing with the physical camera. Archives stay on the phone until the user shares them.

The existing English/Czech UI, photo/media controls, native preview and experimental Media3 TCP/UDP playback remain available. See `android/README.md` for limits and the device-test report.

**Čeština:** nově je v Nastavení export webových souborů kamery do ZIP bez SD karty. ZIP se uloží do Downloads/X10Connect a lze ho sdílet z aplikace. Obsahuje i kontrolní součty a seznam chyb. Nejde o úplnou zálohu flash nebo instalační balíček firmwaru. Export je potřeba ověřit na skutečné kameře.
