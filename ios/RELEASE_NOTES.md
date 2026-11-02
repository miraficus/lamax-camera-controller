First **iOS 15.0+ / ARM64** preview of X10 Connect, targeting iPhone SE (1st generation), including iOS 15.8.8.

Download **X10Connect-iOS-0.1.1.ipa** below. This device build has an **ad hoc signature only**, with no Apple developer certificate or provisioning profile. Install using an existing compatible TrollStore or jailbreak IPA installation setup; it is not a normally Apple-signed IPA. No root privileges or private app entitlements are requested.

Simple UI only, no debug tools:
- Gallery filters, multi-selection, thumbnails, paginated listings, batch downloads, size verification, duplicate skipping and sharing saved files.
- Small RTSP/TCP preview through VideoLAN, restart and fullscreen.
- All known camera modes, photo shutter, start/stop recording, time synchronization.
- Camera-reported name/battery, configurable private IPv4 address, Czech/English.

Join camera Wi-Fi manually, keep it connected without internet, tap Connect and allow **Local Network**. Downloads are under Files → On My iPhone → X10 Connect → Downloads. **Keep this initial version in the foreground while downloading**; moving to the background cancels the queue. Completed local files are skipped when starting again.

This is a new port of the protocol tested on Android. **Physical iPhone installation, camera control and RTSP playback still need testing.** X9.1 is also unverified. Automatic Wi-Fi switching, camera-file ZIP export and advanced parameter menus are not included in this first iOS preview.

**Čeština:** První jednoduchá iOS verze pro iOS 15+ a ARM64. IPA má pouze ad hoc podpis a vyžaduje funkční TrollStore nebo instalaci IPA podporovanou tvým jailbreakem. V aplikaci povol Místní síť, ručně se připoj k Wi-Fi kamery a klepni na Připojit. Galerie, ovládání a malý RTSP náhled čekají na ověření na skutečném iPhonu. Při stahování nech aplikaci otevřenou.

Source, build and install notes: https://github.com/miraficus/lamax-camera-controller/tree/ios/ios
