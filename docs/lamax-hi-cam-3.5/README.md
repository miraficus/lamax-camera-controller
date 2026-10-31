# LAMAX X10.1 – rozbor LAMAX Hi Cam a první testovací klient

> Aktualizace: základní přenos fotek a dálkové fotografování byly následně ověřeny na V1.0.0.4. Viz [výsledky testů na zařízení](../device-tests-v1.0.0.4.md). Níže uvedená omezení popisují stav při původní statické analýze.

Datum: 31. 10. 2026. Podklad: uživatelem dodané APK, screenshoty HTTP serveru kamery a veřejný referenční projekt. APK nebylo instalováno ani spuštěno. Kamera nebyla při analýze dostupná.

## Hlavní výsledek

Pro prohlížení a stahování fotek máme konkrétní HTTP protokol. Dodané APK používá stejný seznam souborů, který zdokumentoval autor projektu [lamax-camera-controller](https://github.com/mich41v4294/lamax-camera-controller). Náhradu aplikace lze založit na tomto rozhraní; změna firmwaru není předpokladem tohoto postupu.

Rozbor navíc doplnil příkazy pro fotografování, nahrávání a metadata souborů, které v implementaci X10.1 referenčního projektu chybí. Jejich přítomnost v APK ještě neprokazuje podporu konkrétní verzí kamery. APK podporuje více značek a generací zařízení.

## Původ a spolehlivost zjištění

APK: `LAMAX+Hi+Cam_3.5-build20190329_APKPure.apk`

SHA-256: `6028a10e3a6f90842bddf0f91635995247e64f2d12d19412a44d3c806230b93c`

Manifest: balíček `com.gku.alex`, versionName `3.5-build20190329`, versionCode `68`, minSdk `19` (Android 4.4), targetSdk `26`. Nativní knihovny jsou pouze v adresářích `armeabi` a `armeabi-v7a`; to může být omezení na systémech bez podpory 32bitových aplikací, ale konkrétní příčinu nefunkčnosti této aplikace jsme neurčili. Autenticita podpisu vůči originální aplikaci výrobce nebyla ověřena.

Použit JADX 1.5.6; dokončil rozbor 2597 tříd se dvěma hlášenými chybami. Závěry jsou založeny na čitelných implementacích konkrétních metod, ne na předpokladu, že dekompilovaný projekt lze znovu sestavit. Nativní přehrávače nebyly plně reverzně analyzovány.

Referenční projekt byl kontrolován na commitu `03058350ae996a9083264ca377ee9ad3fa1068ae`. Jeho záznam `reverse_engineered_commands/X10-1_V1001.txt` identifikuje X10.1 jako `gkuvision-dv-3559-mn34120-linux`, SW `V1.0.0.1`. Jde o údaj jiné kamery, nikoli měření uživatelovy kamery. Stopy v APK a nalezený protokol směřují k HiSilicon rodině; dřívější úvaha o obecném Ambarella API není pro tohoto klienta správný výchozí bod. Přesný čip uživatelovy kamery jsme hardwarově nepotvrdili.

## Soubory a stahování

Výchozí adresa v APK a na screenshotech: `http://192.168.0.1/`.

Základ CGI: `/cgi-bin/hi3510/`.

| Účel | GET cesta relativně k CGI základu | Důkaz v APK |
| --- | --- | --- |
| Identita | `getdeviceattr.cgi` | `biz/Setting.java`, `net/HttpProxy.java` |
| Stav baterie | `getbatterycapacity.cgi?` | `biz/Setting.java` |
| Stav karty | `getsdstate.cgi?` | `biz/Setting.java` |
| Počet položek | `getfilecount.cgi?` | `biz/RemoteFileManager.java:getFileCount` |
| Seznam položek | `getfilelistinfoios.cgi?&-start=1&-end=100` | `biz/RemoteFileManager.java:getFileList` |
| Metadata souboru | `getfileinfo.cgi?&-name=sd/DCIM/100GKUDV/SING0001.JPG` | `biz/RemoteFileManager.java:getFileInfo` |

Kořen zdrojových cest v tomto dokumentu je `com/hisilicon/dv/`, pokud není uvedeno jinak. Čísla řádků pro nalezené šablony obsahuje `endpoint-inventory.json`.

`net/StringParser.java:getMediaList` čte JSON pole objektů s položkami `path`, `create`, `time`, `size`. Velikost se převádí na 64bitové celé číslo; čas záznamu na celé číslo. V zachycené odpovědi referenčního projektu jsou i číselné údaje zapsané jako řetězce. Datum `create` aplikace interpretuje ve formátu `yyyyMMddHHmmss`. Jednotku pole `time` ověřit na známém videu.

Příklad zaznamenaný referenčním projektem:

```json
[{"path":"sd/DCIM/100GKUDV/SING0001.JPG","create":"20180101000140","time":"0","size":"1284271"}]
```

`biz/DV.java:getAllFileNames` stránkuje od indexu 1 po 100 položkách; `biz/FileListManager.java:getAllFileList` obsahuje i variantu po 200 položkách odzadu. Koncový index je v implementaci inkluzivní. Ověřit, co přesně kamera započítává a jak reaguje při změně obsahu karty během výpisu.

`biz/DV.java:getDownloadPath` sestavuje `http://<IP>/`. K této adrese se připojí přesná relativní cesta získaná z API. Screenshot dokládá složku `sd/DCIM/100GKUDV/` a soubory s velkými příponami `.JPG`, `.MP4`, `.THM`, `.LRV`. Zachovat velikost písmen a správně zakódovat cestu; nespoléhat na číslování souborů.

`dlg/DlgForProcessActivity.java:downloadImage` používá `HttpURLConnection`, `Accept-Encoding: identity`, ukládá do dočasného souboru a sleduje velikost. Nebyl zjištěn požadavek na cloudový účet nebo autorizační token v této HTTP větvi. Úplnou nepřítomnost autentizace ve všech větvích aplikace tím netvrdíme.

`biz/HiDownloadFile.java` rozlišuje plné MP4 a menší LRV změnou přípony; LRV se může lokálně přejmenovat na `s.MP4`. `biz/FileListManager.java` odvozuje THM náhled změnou přípony. Chybějící náhled nesmí v novém klientovi odstranit z galerie jinak platné video.

Existuje také alternativní binární přenos přes TCP 8080 v `ui/data/connect/DownLoadFileUtils.java`. Požadavek tvoří 72 bajtů: little-endian 32bit CMD=0, 32bit délka textu cesty a prostor pro cestu v dalších 64 bajtech. Odpověď má v kódu 4+4+64 bajtů a následná data souboru. Kompatibilita s X10.1 není potvrzena. Důvod výběru větví pro velké soubory vyžaduje další ověření. Pro první klient se používá jednodušší HTTP; žádný port 8080 nezkouší.

## Ovládání nad rámec referenčního klienta

`biz/Command.java:cgiFilesParams` a `executeCommand` skutečně sestavují a odesílají tyto GET požadavky:

| Účel | Cesta za CGI základem |
| --- | --- |
| Jedna fotografie | `photo.cgi?&-type=photo` |
| Sériové foto | `photo.cgi?&-type=photoburst` |
| Základní start / stop videa | `record.cgi?&-cmd=start` / `record.cgi?&-cmd=stop` |
| Běžné video – druhá varianta | `record2.cgi?&-type=common&-cmd=start` / `stop` |
| Smyčka | `record2.cgi?&-type=loop&-cmd=start` / `stop` |
| Časosběr videa | `record2.cgi?&-type=timelapse&-cmd=start` / `stop` |
| Zpomalené video | `record2.cgi?&-type=slow&-cmd=start` / `stop` |

APK obsahuje i `photo.cgi` s typy `phototimelapse`, `phototimer`, `continuous` a `record2.cgi` s typem `recsnap`. `biz/DV.java:executeCommand` za podmínky `CameraParmeras.IsNewAPP` přemapovává video akce 10–19 na základní start/stop. Proto nelze všechny varianty bez ověření označit za správné pro X10.1. Žádné z těchto ovládání testovací skript neposílá.

`Setting.java` obsahuje další dotazy a změny: bitrate, LED, obraz vzhůru nohama, zorné pole, zvuk, automatické vypnutí a jas displeje. Jejich podpora se musí ověřovat po jednotlivých funkcích, přítomnost v aplikaci ji negarantuje. Průzkum nenavrhujeme jako automatické zkoušení všech endpointů: i GET může měnit stav.

## Živý obraz

`biz/DV.java:getVideoRtspURL` sestavuje `rtsp://<IP>:554/livestream/11` pro vyšší bitrate a `/12` pro nižší. HTTP varianta je `http://<IP>:80/11?trans=tcp&action=play&media=video_data`, případně `/12?...`. Jde o implementaci klienta, nikoli test živého streamu na uživatelově kameře. Pro stahování fotek není přehrávač potřeba.

## Firmware – důležitý nález i omezení

APK opravdu obsahuje `res/raw/new_firm.rar` (RAR5). Archiv byl pouze rozbalen do pracovního prostředí a zkontrolován; nic nebylo spuštěno ani zapsáno do kamery. Obsahuje:

| Soubor | Bajty |
| --- | ---: |
| `appfs.ubifs` | 10092544 |
| `rootfs.ubifs` | 5111808 |
| `uImage` | 3507626 |
| `media_app_zip.bin` | 3408947 |
| `rawparam` | 131072 |
| `rawparambak` | 131072 |
| `config` | 560 |

`config` uvádí `swver X2_0108101427` a nastavuje `bootargs` i `bootcmd`; obraz kernelu nese označení Linux 4.9.37 a datum 10. 12. 2018. Archiv neobsahuje soubor `u-boot.bin`, ale jeho konfigurace zasahuje do bootovacích proměnných. Tedy ani absence souboru bootloaderu neznamená, že by pokus o instalaci nemohl poškodit bootování.

**Tento balíček není prokázaný originální firmware LAMAX X10.1. Neinstalovat jej na základě tohoto nálezu.** Může posloužit jako podklad pro další analýzu příbuzné platformy. Hash a velikosti částí jsou v `analysis-metadata.json`; firmware binárky nejsou přidány do výsledného balíčku, zůstávají dostupné v původním APK.

`res/raw/update.json` uvádí pouze zařízení `XTUX2-dv-3556A-imx377-ap6255` a `X2-dv-3556A-imx377-ap6255`. To nepotvrzuje původ archivu `new_firm.rar`, ale nepotvrzuje ani podporu X10.1. Další resource `xtux_2_dv.zip` obsahuje jediný prázdný textový soubor, tedy není použitelným firmwarem. V zkontrolovaných zdrojích nebylo nalezeno přímé použití resource `new_firm`.

`updateapp/UpgradeManager.java` obsahuje starší katalog `http://121.40.107.215:8041/firmware/info.json`, stahování `.sw` balíčků a upload přes `fileupload.cgi` následovaný `upgrade.cgi`. Jeden pokus o HTTP GET katalogu při analýze skončil timeoutem po 15 sekundách bez přijatých dat. To není důkaz, že je server trvale vypnutý, ani ověřený zdroj firmwaru pro X10.1. Druhá aktualizační větev v `FirmwareClientThread.java` používá TCP 8080. Jde o aktualizaci běžící kamerou, nikoli důkaz bootloaderového recovery režimu.

## Systémové soubory ze screenshotu

Výpis `/` ukazuje `main_app`, `higv.bin`, `hostapd`, `res/` a jazykové soubory, zatímco `/sd/` vede na kartu. To je podstatná stopa k získání skutečného hlavního programu konkrétní kamery přes HTTP. Screenshot sám neprokazuje možnost přečíst každý soubor a neukazuje kompletní flash ani bootloader.

Až bude kamera dostupná, stojí za to lokálně uložit odpověď `/`, dále `/main_app`, `/higv.bin` a dostupné resources s kontrolními součty. Půjde o zálohu dostupné aplikace a podklad k analýze, nikoli automaticky o obnovitelný instalační balíček. Přiložený testovací klient stahuje pouze média pod `sd/DCIM/`; systémovou kopii zatím neprovádí.

## První test na Fedoře

Skript `tools/lamax_readonly.py` používá jen standardní knihovnu Pythonu 3.9+. Je nově napsaný podle rozhraní APK, bez převzetí zdrojového kódu referenčního projektu. Neobsahuje formátování, mazání, změny nastavení, fotografování, nahrávání ani aktualizaci. Síťové operace jsou omezené na vybrané čtecí GET požadavky a explicitně vybraný mediální soubor. Ani čtecí provoz nemusí být na každém chybném firmwaru bez vedlejších efektů; zařízení ještě nebylo testováno.

Připojit počítač k Wi-Fi kamery a spustit:

```bash
mkdir -p camera-data
python3 tools/lamax_readonly.py probe > camera-data/probe.json
python3 tools/lamax_readonly.py list > camera-data/files.json
```

Z `files.json` zvolit existující cestu a její velikost. Například pouze pokud takový soubor skutečně existuje:

```bash
python3 tools/lamax_readonly.py download sd/DCIM/100GKUDV/SING0001.JPG fotka.JPG --expected-size 1284271
```

Pro jinou IP použít před příkazem `--base-url http://ADRESA`. Testy neposílat na jiný typ kamery podle pouhé shody značky. Pokud výpis nesedí, zastavit a poslat syrový výstup; nezkoušet náhodné změnové příkazy. Výstup `probe.json` může obsahovat identifikaci zařízení; před veřejným zveřejněním jej prohlédnout.

Klient zachovává velikost písmen, stránkuje výpis, nevyhodnocuje JavaScriptové odpovědi, odmítá přesměrování a cesty mimo média a nepřepisuje existující cílové soubory. Stahuje do `.part`, kontroluje délku a vypočítá SHA-256. Nemá obnovování přerušeného stahování ani grafické rozhraní. Pokud počet položek nesouhlasí, může se měnit karta nebo chování indexů; zatím se nic automaticky nemaže ani opravuje.

## Ověření a další postup

Šest automatických testů na místním simulátoru HTTP prošlo: parsování odpovědí bez jejich spuštění, stránkování, stažení se zachováním existujícího souboru, nesoulad velikosti, odmítnutí nevhodných cest a odmítnutí přesměrování. To ověřuje klienta na simulátoru, nikoli kompatibilitu s reálnou kamerou.

1. Ověřit identitu a získat skutečný seznam souborů.
2. Stáhnout jednu známou fotku a porovnat velikost/obsah s kartou.
3. Doplnit výběr a hromadné stahování, přerušení a případně obnovení přenosu podle schopností HTTP serveru.
4. Pak přenést ověřený protokol do malé Android aplikace: připojení k síti kamery bez internetu, směrování požadavků přes tuto Wi-Fi, HTTP přístup k místní kameře a ukládání přes aktuální Android API. Přesné API požadavky zkontrolovat při implementaci podle cílové verze Androidu.
5. Ovládání a analýzu firmwaru řešit samostatně podle dostupných důkazů. Podpora RAW či dalších režimů v univerzální aplikaci neprokazuje jejich podporu kamerou.

Soubor `endpoint-inventory.json` obsahuje 106 unikátních nalezených CGI šablon a fragmentů včetně zdrojových míst. **Nejde o 106 ověřených funkcí X10.1.** Automatická kategorie `read_candidate` je orientační; neslouží jako seznam pro hromadné spouštění.

Výsledky jsou vedené ve forku `miraficus/lamax-camera-controller`. Android aplikace a workflow pro APK jsou plánované až po ověření protokolu s reálnou kamerou.

Testy z kořene repozitáře:

```bash
python3 -m unittest discover -s tools -p test_lamax_readonly.py -v
```
