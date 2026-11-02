# X10 Connect — iOS preview

Samostatný jednoduchý klient ve SwiftUI pro **iOS 15.0+ / ARM64**, cílený na iPhone SE 1. generace s iOS 15.8.8. Větev `ios` vychází z Android větve a zachovává společné výsledky průzkumu. Neobsahuje ladicí režim, skenování RTSP ani aktualizátor firmwaru.

## Co obsahuje první verze

- Galerie s filtry, označováním, stránkovaným seznamem, náhledy, hromadným stahováním a sdílením místních kopií.
- Kontrola očekávané délky staženého souboru; již uložená cesta a velikost se přeskočí. Neúplný soubor se nezveřejní.
- Kamera: malý RTSP stream `/livestream/12` přes VideoLAN MobileVLCKit / TCP, obnova a celá obrazovka, všechny známé režimy, Foto / Start / Stop. VideoLapse Start používá ověřený příkaz spouště, Stop příkaz záznamu.
- Nastavení: místní IPv4 adresa, synchronizace času, čeština/angličtina (automaticky čeština pro cs/sk), ruční změna jazyka.
- Model a baterie podle odpovědi kamery. X9.1 není fyzicky ověřená.

**Kód je port ověřeného HTTP protokolu, nikoli potvrzení provozu na iPhonu.** Ověření spojení, RTSP, souborů i instalace musí proběhnout na zařízení. VideoLAN používá jiné vyrovnávací paměti než Media3; latence se může lišit.

## Instalace testovací IPA

Workflow sestaví zařízení **iphoneos / arm64**, minimum iOS 15.0, bez Apple účtu, certifikátu nebo provisioning profilu. Balíček dostane pouze lokální **ad hoc podpis**; není podepsaný pro běžnou instalaci App Storem nebo Finderem.

1. Stáhnout `X10Connect-iOS-0.1.0.ipa` z prerelease `ios-v0.1.0`.
2. Pokud je v telefonu funkční **TrollStore**, otevřít IPA v něm a nainstalovat. TrollStore podle svého README podporuje rozsah iOS 14.0 beta 2 až 16.6.1, kam 15.8.8 patří; dostupnost instalátoru a aktuální instalaci na konkrétním telefonu je nutné ověřit.
3. Jinak použít IPA instalátor podporovaný již používaným jailbreakem; může vyžadovat AppSync nebo odpovídající podporu nepodepsaných aplikací. Samotné označení „jailbreaknutý“ nezaručuje, že libovolný instalátor balíček přijme. Aplikace nepotřebuje root ani soukromé entitlementy. Není nutné kvůli ní měnit jailbreak.
4. Zapnout Wi-Fi kamery, připojit iPhone ručně přes Nastavení k její síti, ponechat spojení bez internetu.
5. V aplikaci klepnout na Připojit a povolit **Místní síť**. Ikona Wi-Fi ukáže nápovědu; tlačítko nastavení aplikace otevírá oprávnění aplikace, nikoli přímo seznam Wi-Fi sítí.

Soubory jsou v **Soubory → Na mém iPhonu → X10 Connect → Downloads**. Z Galerie lze označit uložené soubory a sdílet je; systémová nabídka může nabídnout uložení do Fotek. Stahování první verze je záměrně jen v popředí. Přechod na pozadí ruší frontu. Po návratu lze frontu spustit znovu; úplné místní kopie se přeskočí. Obnovení rozpracovaného souboru od posledního bajtu není implementováno.

## Sestavení

Na macOS s Xcode 16.4, XcodeGen a CocoaPods:

```sh
swift test --package-path ios/CameraCore
cd ios
xcodegen generate
pod install
cd ..
xcodebuild -workspace ios/X10Connect.xcworkspace -scheme X10Connect -configuration Release -sdk iphoneos -destination 'generic/platform=iOS' -derivedDataPath ios/DerivedData ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY='' build
bash ios/scripts/package.sh
```

Při lokálním balení je nutné přidat COPYING.txt knihovny stejně jako v CI; viz workflow a THIRD_PARTY.md. Xcode projekt se reprodukovatelně generuje z `project.yml`; vyřešený `Podfile.lock` je v CI artefaktu. Samotná závislost MobileVLCKit je připnutá na 3.6.0 a její podspec ověřuje SHA-256 archivu.

Workflow `.github/workflows/ios-ipa.yml` se spouští při změnách ve větvi `ios`; ručně po jeho zpřístupnění v UI GitHubu. Artefakt obsahuje IPA, SHA-256 a vyřešenou závislost. Commit s `[ios-prerelease]` nebo ruční `publish_preview` zveřejní prerelease až po úspěšných testech a sestavení.

Core testy kontrolují odpovědi kamery, nebezpečné adresy a cesty, deduplikaci seznamu, metadata a kódování parametrů. Kontrola IPA vyžaduje skutečný ARM64 Mach-O, platformu iPhoneOS a MinimumOSVersion 15.0; nejedná se o simulátorovou aplikaci.

## Zdroje

- Apple iOS 15.8.8, včetně iPhone SE 1. generace: https://support.apple.com/cs-cz/127114
- VideoLAN VLCKit: https://github.com/videolan/vlckit
- Připnutý podspec: https://github.com/CocoaPods/Specs/blob/master/Specs/b/f/7/MobileVLCKit/3.6.0/MobileVLCKit.podspec.json
- TrollStore, podporované verze a instalace IPA: https://github.com/opa334/TrollStore
- Apple Local Network permission: https://developer.apple.com/documentation/bundleresources/information-property-list/nslocalnetworkusagedescription
