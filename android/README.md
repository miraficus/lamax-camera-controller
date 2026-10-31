# X10 Connect – Android preview

Neoficiální, samostatně napsaný klient pro LAMAX X10.1. Používá otevřené HTTP rozhraní kamery; nepotřebuje původní aplikaci, účet ani cloud. Projekt neobsahuje kód nebo nativní knihovny z původního APK.

## Kompatibilita

- Minimum **Android 10 (API 29)**, cílové API **35 (Android 15)**. Majitel otestoval verzi 0.1.0 na Androidu 16 s V1.0.0.4: spojení, seznam médií, fotografování a menší RTSP náhled fungují. Ostatní kombinace telefonu a firmwaru zatím nejsou ověřené.
- Aplikace nemá přibalené nativní knihovny; není omezena na 32bitový ARM jako původní APK.
- Původní LAMAX Hi Cam 3.5 má minimum Android 4.4, target Android 8.0 a pouze 32bitové ARM knihovny. Target není maximální podporovaná verze Androidu. Přesná hranice nefunkčnosti původní aplikace není známá a liší se podle zařízení.
- Android 10 je zvolené minimum kvůli systémovému panelu Wi-Fi a ukládání přes MediaStore bez přístupu k celé galerii. Podpora Androidu 8/9 se podle rozhodnutí majitele neplánuje.
- Česká první verze, nativní Android Views, Java 17. Bez frameworku pro webový obsah a bez knihoven pro střih videa.

## Co je připraveno

| Obrazovka | Funkce |
| --- | --- |
| Galerie | Seznam souborů, filtry fotky/videa, náhledy THM s fallbackem na JPG, výběr více souborů a stahování |
| Kamera | Fotografování, start/stop videa, přepínání režimů, nastavení načtená z kamery, synchronizace času, varianty `record2.cgi`, RTSP náhled |
| Nastavení | Adresa kamery, systémový panel Wi-Fi, přepnutí NewAPP/starší API, zrušení stahování |
| Diagnostika | Místní záznam CGI požadavků a odpovědí, sdílení textu přes systémový dialog |

Protokol pro stav zařízení, seznam souborů, HTTP stažení JPG a pořízení fotografie byl [ověřen na V1.0.0.4 přes Python/curl](../docs/device-tests-v1.0.0.4.md). **Android aplikace je testovací implementace.** [První test 0.1.0 na Androidu 16](../docs/device-tests-v1.0.0.4.md#test-x10-connect-010-na-androidu-16) potvrdil komunikaci, načtení 79–82 médií, vznik tří nových fotografií a menší RTSP náhled. Kamera přijala změny `NormalVideo` a `NormalPhoto`; nezávislé ověření videorežimu ještě chybí. Větší RTSP náhled v této kombinaci nefungoval a příčina není známá. Ukládání do MediaStore, dávkové stahování, video a parametry čekají na ověření; ovládání zůstává dostupné.

## První použití

1. Zapnout Wi-Fi kamery. Pro první test fotografie nejprve nastavit na kameře fotografický režim.
2. V aplikaci klepnout na **Wi-Fi** a připojit se k síti kamery. Výchozí SSID podle návodu je `X101`, heslo `1234567890`, pokud nebylo změněno.
3. Ponechat připojení k této síti i bez internetu. Mobilní data mohou zůstat zapnutá; HTTP požadavky aplikace používají konkrétní Wi-Fi `Network.openConnection`, ne výchozí mobilní připojení.
4. Klepnout na **Připojit**. Výchozí adresa je `http://192.168.0.1`. Jinou místní IPv4 adresu lze uložit v nastavení.
5. Vybrat několik médií a použít **Stáhnout výběr**; klepnutí na náhled otevře informace a nabídku stažení jednoho souboru. Po stažení lze touto nabídkou otevřít uložený soubor.

Přístup k celé galerii telefonu ani poloha nejsou požadované. Fotky se ukládají přes MediaStore do `Pictures/X10Connect`, videa do `Movies/X10Connect`. Aplikace kontroluje očekávaný počet bajtů, nezveřejní nedokončený soubor a již uložená média se shodnou cestou/velikostí přeskočí, pokud je místní kopie dostupná. Existující fotografie jiných aplikací nepřepisuje.

Stahování běží jako foreground service s průběhem a zrušením v oznámení. Na Androidu 13+ lze povolit oznámení. Zrušení může čekat na síťový timeout až 15 sekund. Po přerušení procesu se případný evidovaný rozpracovaný soubor uklidí při dalším spuštění služby. Obnovení přenosu od posledního bajtu zatím není implementováno.

V kartě Kamera se režimy a parametry NewAPP načítají z `getallworkmode`, `getcurworkmode`, `getprimarymenuitem`, `getsecondmenuitem` a zapisují pomocí `setcurworkmode`/`setcurparameter`. Pro V1.0.0.4 ponechat **Starší číselné API** vypnuté. Pokud některý dotaz selže, diagnostika ukáže přesnou odpověď. Změnové požadavky se automaticky neopakují, aby timeout nezpůsobil dvojí fotografování nebo přepnutí.

RTSP používá vestavěný `VideoView`: `rtsp://<kamera>:554/livestream/12` (menší) nebo `/11` (větší). Během tohoto přehrávání je proces dočasně připojen k Wi-Fi síti; původní vazba se při odchodu obnoví. Menší `/12` majitel potvrdil jako funkční na Androidu 16; větší `/11` nefungoval. Z dodaného logu nelze určit příčinu, protože neobsahuje RTSP chybu. Podpora konkrétního kodeku závisí na telefonu. Není-li náhled kompatibilní, ostatní HTTP funkce jsou samostatné. Náhled není nezbytný pro stažení souborů.

Ve výchozím UI nejsou příkazy na formátování karty, obnovu továrního nastavení, aktualizaci firmwaru nebo změnu Wi-Fi hesla. Systémové menu tyto položky filtruje. Neověřené fotografické a video funkce zůstávají dostupné.

## Diagnostika

Po neúspěšném pokusu otevřít **Diagnostika → Obnovit záznam → Sdílet záznam**. Záznam zůstává v aplikaci a nikam se automaticky neposílá. Obsahuje odpovědi kamery včetně názvů médií a identifikace zařízení; před veřejným vložením jej lze prohlédnout. Záznam je velikostně omezený. Náhledové požadavky se do něj nepíší, aby nezaplnily historii ovládání.

Známá vlastnost firmwaru: při zapnuté Wi-Fi ignoruje část fyzických tlačítek a nelze běžně přepnout do místní galerie. To není změna provedená touto aplikací.

## Sestavení

JDK 17, Android SDK Platform 35 a Build Tools 35.0.0. Gradle 8.11.1 a Android Gradle Plugin 8.9.2 jsou připnuté; wrapper ověřuje SHA-256 distribuce.

```bash
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Výstup: `app/build/outputs/apk/debug/app-debug.apk`. Jednotkové testy ověřují parser odpovědí, formát záznamů V1.0.0.4, obě podoby dat, velké velikosti videí, cesty médií, URL a chybové odpovědi při HTTP 200. Nejde o testy proti skutečné kameře. K ověření v telefonu slouží sestavené debug APK.

## Změny 0.1.1

- Android 10 zůstává minimem; podpora 8/9 se neplánuje.
- Výchozí názvy režimů odpovídají skutečné odpovědi V1.0.0.4 (`NormalPhoto`, `NormalVideo`, `TimerPhoto`, `Burst`, `PhotoLapse`, `CarMode`, `VideoLapse`, `SlowRec`). Po připojení se dál načítají přímo z kamery.
- Menší náhled je označen jako ověřený, větší zůstává dostupný jako test. Přímo v přehrávači lze přepínat mezi oběma streamy bez návratu do aplikace.
- RTSP diagnostika rozlišuje stream, zapisuje telefon, rozměry videa, formáty stop, přípravu a první snímek, chybové kódy a čekání na přípravu přes 25 sekund. Nejde o potvrzenou opravu většího streamu.
- Dlouhé odpovědi mají v logu explicitní značku zkrácení; parser dostává celou odpověď.

## Kontroly prvního sestavení

Dne 31. 10. 2026 prošlo `testDebugUnitTest` (12 testů), `lintDebug` (bez chyb, upozornění na lokalizaci textů a pravidla zálohování) a `assembleDebug`. Prošlo i 6 simulovaných HTTP testů Python klienta. Instalační soubor má ověřený debug podpis. Kontroly nenahrazují test na skutečném telefonu s kamerou.

## GitHub Actions

Workflow **Android APK** sestaví větev `android` automaticky při změnách projektu. Obsahuje unit testy, lint a sestavení debug APK. Ruční spuštění používá také větev `android`; workflow musí být zaregistrované na výchozí větvi, aby GitHub zobrazil tlačítko **Run workflow**.

Výsledek je artifact **X10Connect-debug-apk**. Volba **publish_preview** při ručním spuštění dovolí přiložit APK také do veřejného prerelease `android-preview`, odkud jej lze stáhnout bez přihlášení ke GitHubu. První instalace vyžaduje povolit instalaci aplikace z použitého prohlížeče/správce souborů.

Od 0.1.1 CI používá explicitní cestu podpisového klíče (`X10_DEBUG_KEYSTORE`) a uchovává jej v Actions cache. Původní workflow 0.1.0 mělo chybnou cestu cache, takže jeho klíč nebyl uložen a při přechodu z 0.1.0 je jednou nutné aplikaci odinstalovat. Tím se smažou nastavení a diagnostika aplikace, uložená média v systémové galerii zůstávají. Pokud cache zanikne, může být potřeba před instalací dalšího sestavení odinstalovat původní preview. Lokální a CI debug podpisy jsou různé. Pro stabilní veřejné vydání se později nastaví vlastní chráněný release klíč; aktuální preview není podepsané distribučním klíčem.
