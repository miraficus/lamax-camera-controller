# LAMAX X10.1 V1.0.0.4 – soubory stažené z kamery

> Aktualizace: základní přenos fotek a dálkové fotografování byly následně ověřeny na V1.0.0.4. Viz [výsledky testů na zařízení](../device-tests-v1.0.0.4.md). Níže uvedená omezení popisují stav při původní statické analýze.

Analýza 31. 10. 2026 navazuje na [rozbor APK](../lamax-hi-cam-3.5/README.md). Podkladem je uživatelem dodaný `Lamax.zip`, fotografie menu s verzí V1.0.0.4 a screenshoty adresářů HTTP serveru. Programy kamery nebyly spuštěny, emulovány ani modifikovány. Žádný požadavek na kameru nebyl při tomto rozboru odeslán.

## Co jsme získali

ZIP obsahuje 28 souborů, celkem 9 624 649 bajtů, včetně `main_app`, `hostapd`, `higv.bin`, jazykových souborů, fontů a zvuků. Adresář `res/pic` je prázdný; uživatel jej záměrně nestahoval. Pro průzkum komunikace a hlavní aplikace to není překážka. Obrázky by byly užitečné hlavně pro věrné zobrazení nebo změny vzhledu menu.

`main_app` má 7 501 872 bajtů. Je to **32bitový ARM ELF pro Linux**, nikoli HTML stránka, instalační balíček nebo úplný obraz flash. Obsahuje přesné řetězce `LAMAX X10.1`, `LAMAXX101` a `V1.0.0.4`, které odpovídají fotografii uživatelovy kamery.

Největší přínos: soubor **není zbaven symbolů a má sekce DWARF s ladicími informacemi** (`.symtab`, `.debug_info`, `.debug_line` atd.). Známe tak názvy mnoha funkcí a jejich adresy. Není to původní zdrojový kód ani záruka perfektní dekompilace; pro další reverzní analýzu je to ale podstatně lepší podklad než samotné řetězce.

Podrobnosti a SHA-256: [main-app-static.json](main-app-static.json), [camera-files.json](camera-files.json). Zachovány jsou názvy z dodaného ZIPu, včetně `de (1).lang` a `es (1).lang`; názvy stažených kopií nejsou důkazem původního názvu na serveru. Prázdné adresáře nejsou v inventáři souborů.

## Přímé důkazy pro protokol V1.0.0.4

V ELF jsou tabulky `s_stDefaultCGI` a `s_stGkuCGI`. Každá položka má 104 bajtů: název CGI (48), interní příkaz (48), operační označení `get`/`set` (4) a ukazatel na funkci (4). Toto označení **není HTTP metoda ani bezpečnostní klasifikace**: například mazání má označení `get`.

Výchozí tabulka obsahuje 14 položek. GKU tabulka má kapacitu 60 slotů, ale **jen 5 je vyplněných**, ostatní jsou nulové. Celkem tedy byly z těchto dvou tabulek získány **19 konkrétních položek**, nikoli 74 funkcí. Další příkazy se registrují jinými funkcemi; tabulky nejsou úplný seznam všech endpointů.

| CGI | Funkce v konkrétním `main_app` | Adresa funkce |
| --- | --- | --- |
| `getfilecount.cgi` | `Hisnet_CMD_FILE_GetFileNum` | `0x106bf0` |
| `getfilelistinfoios.cgi` | `Hisnet_CMD_Fileinfog` | `0x2a16d8` |
| `getfilelistinfo.cgi` | `Hisnet_CMD_Fileinfog` | `0x2a16d8` |
| `getfileinfo.cgi` | `Hisnet_CMD_FILE_GetFileInfo` | `0x107288` |
| `getdeviceattr.cgi` | `Hisnet_CMD_SYS_DevAttr` | Viz JSON |
| `getbatterycapacity.cgi` | `Hisnet_CMD_SYS_GetBatteryStatus` | `0x109288` |
| `record.cgi` | `Hisnet_CMD_MEDIA_Record` | `0x2a0ed8` |
| `photo.cgi` | `Hisnet_CMD_MEDIA_Photo` | `0x2a12d4` |

Uvedené dva endpointy seznamu tedy sdílejí stejnou obsluhu. V obsluze `Hisnet_CMD_Fileinfog` jsou parametry `start` a `end` a formátovací řetězec JSON:

```text
{"path":"%s","create":"%s","time":"%u","size":"%llu"}
```

To podporuje použití již připraveného klienta také pro V1.0.0.4: jeho očekávaná struktura odpovědi odpovídá implementaci v této binárce. Úspěšný živý dotaz a stažení fotky klientem však ještě neproběhly. Ze statické tabulky samotné nelze určit všechny provozní podmínky, například aktuální režim, stav SD karty nebo dokončení inicializace služby.

`record.cgi` obsahuje parsování parametru `cmd`, porovnání se `stop` a kontroly stavu SD karty. `photo.cgi` parsuje `type` i `cmd` a rovněž kontroluje kartu. Vzhledem k vazbě na stavový automat netvrdíme, že všechny varianty příkazů univerzální APK jsou na X10.1 funkční. Tyto změnové operace nejsou přidány do čtecího klienta.

V programu je také `thttpd/2.29 23May2018`, cesta `cgi-bin/hi3510/` a funkce `thttpd_start_main`. To odpovídá jednoduchému adresářovému výpisu na screenshotech. Stažení hlavního programu přes HTTP je již prakticky doloženo dodaným ZIPem.

## Porovnání s archivem X2 v APK

Z `res/raw/new_firm.rar` v dříve dodaném APK byly bezpečně rozbaleny obrazy `appfs.ubifs` a `rootfs.ubifs` pomocí ubi-reader 0.8.16. X2 má aplikaci v `/app/bin/main_app`, viz skript `bootapp` v rozbaleném appfs. Porovnány byly stažené soubory se stejnou relativní cestou pod `bin/`; soubory nebyly kopírovány do kamery.

| Vlastnost | X10.1 V1.0.0.4 | Archiv X2 z APK |
| --- | --- | --- |
| `main_app` velikost | 7 501 872 B | 3 311 224 B |
| Architektura | ARM 32bit, little-endian | ARM 32bit, little-endian |
| Loader | `/lib/ld-uClibc.so.0` | `/lib/ld-linux.so.3` |
| C knihovna – závislost | `libc.so.0` | `libc.so.6` |
| Ladicí informace a tabulka symbolů | Přítomny | Hlavní tabulka symbolů a `.debug_info` chybějí |
| Produktové řetězce | `LAMAX X10.1`, `V1.0.0.4` | `XTU 4K Camera`, `XTU-X2 Cam` |
| Řetězec identifikace SDK/čipu | `Hi3559V200` | `Hi3556AV100` |

Řetězce označení čipu jsou statické stopy v software, nikoli nezávislá hardwarová identifikace pouzdra v kameře. Rozdílný loader a závislosti každopádně dokazují, že **samotné sdílení ARM architektury nestačí k přímé záměně programů**. Obrazy oddílů ani `main_app` X2 nejsou ověřenou náhradou nebo návratovým firmwarem X10.1.

Obě aplikace obsahují velkou část podobných CGI názvů a formátů. To ukazuje na příbuzný základ; neprokazuje binární kompatibilitu, shodné rozložení flash nebo shodný snímač.

Z porovnaných souborů se **bajt po bajtu shoduje pouze `res/sound/keytone.pcm`**. Liší se mimo jiné `main_app`, `hostapd`, `higv.bin`, `en.lang`, `zh.lang`, `zh_HK.lang` a `res/font/vector.ttf`. Další soubory ze ZIPu nemají protějšek na stejné porovnávané cestě. Přesná matice a kontrolní součty: [file-comparison.json](file-comparison.json). Nález totožného zvuku sám neříká nic o kompatibilitě firmware.

## Rozsah zálohy a další test

Dodaný ZIP je hodnotná kopie souborů aplikace konkrétní kamery. **Není úplnou zálohou flash pro obnovu**: neobsahuje ověřený bootloader, kernel, všechny oddíly, konfiguraci zařízení ani mapu flash. `entropy.bin` není zveřejněn, stejně jako žádné stažené binárky; v repozitáři jsou pouze rozbor, adresy, soupisy a kontrolní součty. Zdrojový ZIP zůstává v uživatelově soukromém úložišti.

Wi-Fi už podle screenshotů pracuje. Z této změny nelze určit, zda původní potíže způsobovala baterie, režim menu či jiná podmínka. Další rozbor může využít funkce `Wifi_TurnOnOff`, `PDT_UI_COMM_StartWiFi`, `UI_SetBatteryControl` a `gku_GetBatteryLevel`; prahovou hodnotu blokace Wi-Fi jsme zatím neprokázali.

Pro hlavní cíl nyní stačí ověřit existující čtecí skript, nikoli stahovat stovky obrázků:

```bash
mkdir -p camera-data
python3 tools/lamax_readonly.py probe > camera-data/probe.json
python3 tools/lamax_readonly.py list > camera-data/files.json
```

Potom zkusit jednu cestu z `files.json` příkazem `download`, s očekávanou velikostí podle výpisu. Tento krok ověří skutečné odpovědi V1.0.0.4 a připraví podklad pro hromadné stahování a Android aplikaci.

## Opakování statické analýzy

Nový nástroj nevyžaduje spuštění cizího programu:

```bash
python3 -m venv .venv-inspect
.venv-inspect/bin/pip install pyelftools==0.33
.venv-inspect/bin/python tools/inspect_lamax_elf.py /cesta/k/main_app
```

Nástroj přečte ELF hlavičku, loader, závislosti, přítomnost ladicích sekcí a obě jmenované CGI tabulky. Odmítne nečekaný formát místo odhadování. Pokud binárka tabulku symbolů nemá, vypíše prázdný seznam tabulek; to neznamená nepřítomnost CGI funkcí. Na obou výše uvedených binárkách byl spuštěn a výstupy jsou v tomto adresáři. U X10.1 byla zkontrolována shoda adres tabulek a obsluh s `readelf`/`nm` a disassemblovanými funkcemi. Síťové testy nejsou součástí tohoto nástroje.
