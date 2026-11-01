# Ověření na LAMAX X10.1 V1.0.0.4

Datum: 31. 10. 2026. Testy provedl majitel kamery na Fedoře s Pythonem 3.14.4, připojený k Wi-Fi kamery. Podkladem jsou fotografie terminálu a jeho potvrzení výsledků. Nejde o přímý vzdálený přístup ke kameře; původní JSON výstupy a stažené fotografie nejsou součástí repozitáře.

## Potvrzené funkce

| Funkce | Výsledek a rozsah důkazu |
| --- | --- |
| `getdeviceattr.cgi` | Vrací `LAMAX X10.1`, `Hi3559V200`, `V1.0.0.4`, `hardversion=NewAPP`. Označení čipu je údaj zařízení, nikoli nezávislá kontrola hardware. |
| `getbatterycapacity.cgi` | Čitelná odpověď; při testu `capacity=80`, `charge=1`, `ac=1`. Přesnost ukazatele baterie nebyla kalibrována. |
| `getsdstate.cgi` | Vrací `SDOK`, celkem 59341 MB, použito 449 MB. |
| `getfilecount.cgi` | Vrací 78 položek v okamžiku výpisu před testem pořízení fotografie. |
| `getfilelistinfoios.cgi` | Klient získal 78 unikátních položek při hlášeném počtu 78. Vícestránkový výpis nad 100 položek zatím nebyl na zařízení ověřen. |
| Stažení JPG přes HTTP | `SING0077.JPG`: 838865 B; `SING0076.JPG`: 2966788 B. Obě stažení prošla kontrolou očekávané délky. |
| Zobrazení stažené fotografie | `SING0076.JPG` se v Gwenview zobrazil správně; majitel dodal fotografii obrazovky se snímkem moře. `SING0077.JPG` byl černý již jako původní snímek, což majitel následně ověřil. |
| `photo.cgi?&-type=photo` | Vrací `Success`; majitel po vypnutí Wi-Fi otevřel galerii přímo na kameře a potvrdil existenci nově pořízené fotografie. |

Zkouška pořízení fotografie proběhla po pokynu nastavit kameru do fotografického režimu před zapnutím Wi-Fi. Automatické přepnutí z videorežimu tímto požadavkem není ověřené. V prvním terminálovém testu nový počet položek po pořízení fotografie nebyl dodán. Pozdější Android test níže již zaznamenal výchozích 79 položek a následný nárůst na 82.

## Použitý příkaz pro fotografování

```bash
curl --noproxy '*' --connect-timeout 5 --max-time 15 \
  'http://192.168.0.1/cgi-bin/hi3510/photo.cgi?&-type=photo'
```

Jde o změnový požadavek, který vytváří soubor na kartě. Čtecí nástroj `tools/lamax_readonly.py` jej neimplementuje. Ověření přes `curl` neznamená, že dosud neimplementovaná metoda `take_picture` v původním modelovém klientovi již funguje.

## Poznatky pro nový klient

- Datum `create` v odpovědi této kamery používá například `2026/09/02 02:10:06`. Starší záznam V1.0.0.1 používá `20180101000140`. Zobrazování dat musí podporovat oba formáty; čtecí skript je zachovává jako původní řetězce.
- Při zapnuté Wi-Fi není běžně dostupné přepínání do místní galerie. V `main_app` funkce `HOME_PAGE_OnKeyDown` na adrese `0x54770` čte stav Wi-Fi; v rozsahu `0x54810–0x54878` při aktivní Wi-Fi propouští jen vybrané kódy kláves a ostatní odmítá s textem `wifi on, skip the key poccess`. Přepnutí do přehrávání má jiný kód (`0x1003c`) než dvě výjimky (`0x1003d`, `0x1003a`). Po vypnutí Wi-Fi majitel galerii otevřel.
- Původní chyba `Network is unreachable` při prvních pokusech byla překonána a další dotazy fungovaly. Je to problém dosažitelnosti sítě, nikoli důkaz odmítnutého CGI příkazu.
- Shoda délky a správné zobrazení potvrzují základní přenos. Porovnání SHA-256 proti nezávislé kopii přímo z SD karty nebylo provedeno.

## Test X10 Connect 0.1.0 na Androidu 16

Dne 31. 10. 2026 majitel poskytl text diagnostiky z aplikace (20:16–20:17 místního času) a výslovně potvrdil funkční menší RTSP stream a nefunkční větší stream. Telefon není v tomto logu identifikovaný. Následující tvrzení se týkají této kombinace, ne všech telefonů s Androidem 16.

| Funkce | Výsledek a rozsah důkazu |
| --- | --- |
| Připojení a stav | HTTP 200, `LAMAX X10.1`, `Hi3559V200`, `V1.0.0.4`, `NewAPP`; baterie hlásí 100 %, `charge=1`, `ac=1`. |
| Seznam médií | Počty 79, 80, 81 a 82; aplikace vždy zaznamenala shodný počet hlavních médií. Nad 100 položek stále neověřeno. |
| Dostupné režimy | `photo="NormalPhoto,TimerPhoto,Burst,PhotoLapse"`; `video="NormalVideo,CarMode,VideoLapse,SlowRec"`. Počáteční aktuální režim `NormalPhoto`. |
| Změna režimu | `setcurworkmode.cgi?-workmode=NormalVideo` i `NormalPhoto` vrátily `Success`. Log nezaznamenává kontrolní přečtení po změně; úspěšné nahrávání tím ještě není prokázané. |
| Spoušť | Tři požadavky `photo.cgi?&-type=photo` vrátily `Success`; po každém přibyl nový soubor: `SING0080.JPG` 2778118 B, `SING0081.JPG` 2810407 B, `SING0082.JPG` 2842198 B. Počet 79 → 80 → 81 → 82. |
| Menší RTSP `/livestream/12` | Funguje podle výslovného potvrzení majitele. |
| Větší RTSP `/livestream/11` | Nefunguje podle majitele. V dodaném výpisu nejsou RTSP události ani kód chyby. Kodek, rozlišení a příčina selhání nejsou z tohoto podkladu známé. |

Dlouhé JSON odpovědi jsou v diagnostice 0.1.0 omezené na prvních 5000 znaků. Nejde o důkaz uříznutého HTTP přenosu: aplikace zpracovala celou odpověď a hlásí 79–82 médií. Od 0.1.1 je zkrácení logu výslovně označené.

Čas kamery se liší od času logu: např. požadavek v 20:17:20 vytvořil soubor s `create=2026/10/31 21:25:03`. Příčina posunu ani příkaz synchronizace času zatím ověřené nejsou. Klient tato data zachovává, nic automaticky nepřepisuje.

## Test X10 Connect 0.1.1 na Samsung SM-S928B, Android 16

Majitel dodal log z 31. 10. 2026, 20:35–20:39. Kamera má stále V1.0.0.4 / NewAPP. Tento test identifikuje telefon přímo v RTSP diagnostice.

| Funkce | Výsledek a rozsah důkazu |
| --- | --- |
| Dostupnost kamery | V 20:35:45 selhal TCP connect na port 80, ale v 20:36:51 již HTTP dotazy fungovaly. První chyba sama neprokazuje trvalou chybu klienta ani její příčinu. |
| Seznam médií | 85, 86 a 87 položek; hlášené počty souhlasí s počtem načtených médií. Zkrácení diagnostiky je nyní výslovně označené. |
| Aktuální režim | `getcurworkmode.cgi` na začátku hlásí `NormalVideo`. |
| Menší RTSP 12 | Tři dokončené přípravy za 10440, 10321 a 10287 ms. Následuje `info 3`, tedy první video snímek. `701` / `702` dokládají přerušování kvůli načítání do bufferu. Jeden rozběh byl přerušen novou volbou streamu. |
| Rozměry RTSP | `onPrepared` vrací `0x0` a nejsou vypsané formáty stop. Není to důkaz rozlišení videa 0 × 0; přehrávač v této chvíli rozměry neposkytl. |
| Větší RTSP 11 | V 20:37:41 selže za 63 ms: `what=1`, `extra=-2147483648`. Jde o obecnou chybu přehrávače. Neprokazuje konkrétní kodek, rozlišení ani to, zda kamera stream odmítla. |
| Normální foto | `NormalPhoto` a spoušť vrátí `Success`; vznikne `SING0086.JPG`, 3019867 B, počet 85 → 86. |
| Samospoušť | `TimerPhoto` přijato v 20:38:50, spoušť v 20:38:52. Obnova v 20:38:53–54 stále hlásí 86. V další obnově je `DLAY0087.JPG`, 3080025 B, čas vytvoření 20:38:54. Čas a název odpovídají opožděnému výsledku samospouště; samostatný snímek displeje ani přímé sledování pořízení nejsou dodané. |
| Burst | Režim přijat v 20:39:03 a spoušť v 20:39:04. Seznam v 20:39:06 obsahuje pouze nový `DLAY0087.JPG` s dřívějším časem vytvoření. Tím nelze doložit úspěšné sériové snímání. |
| Nová videa | V seznamu existují `NORM0083.MP4`, `NORM0084.MP4`, `NORM0085.MP4`. Dodaný log neobsahuje příkazy, které je vytvořily; samo o sobě to nepotvrzuje funkčnost start/stop ovládání aplikace. |
| Čas | Nový `SING0086.JPG` má čas 20:38:42, blízký požadavku 20:38:43. Způsob srovnání času není v logu. |

Podklady k interpretaci událostí: [Android MediaPlayer](https://developer.android.com/reference/android/media/MediaPlayer). RTSP DESCRIBE popisuje médium bez spuštění přehrávání: [RFC 2326, DESCRIBE](https://www.rfc-editor.org/rfc/rfc2326.html#section-10.2). Od 0.1.2 lze vyžádat oba popisy přímo tlačítkem v aplikaci; odpověď serveru a SDP pomohou odlišit odmítnutí streamu od problému přehrávače.

## Test DESCRIBE v 0.1.2, 20:51–20:52

Majitel dodal nový konec logu ze Samsung SM-S928B / Android 16. V 20:52:10 přehrávač při prvním video snímku potvrdil rozměry 1024 × 576.

- Stream `/livestream/12`: `RTSP/1.0 200 OK`, `Content-Type: application/sdp`, `Content-Length: 385`, server `Hisilicon RTSP Streaming Media Server/1.0.0`. SDP uvádí `H264/90000`, 15 fps, `profile-level-id=4D001F`, `packetization-mode=1`, `x-dimensions:1024,576`.
- Stream `/livestream/11`: přímo kamera vrací `RTSP/1.0 400 Bad Request` na DESCRIBE, bez Content-Length. Také následný pokus o přehrání selhal za 116 ms s `1/-2147483648`. Odmítnutí tohoto DESCRIBE požadavku je prokázané; důvod není známý a neprokazuje nefunkčnost streamu ve všech režimech či konfiguracích. Nepodporovaný kodek v telefonu tím nebyl prokázán.
- Tlačítko diagnostiky skutečně získalo odpovědi obou streamů přímo z kamery. Desetisekundová příprava a buffering malého náhledu zůstávají pozorovanými vlastnostmi této kombinace.

## Test 0.1.3: 121 souborů a další fotografie

Majitel dodal log 31. 10. 2026, 21:01–21:02, Android 16 / SM-S928B / V1.0.0.4.

- `getfilecount` hlásí 121. Dotazy `start=1,end=100` a `start=101,end=121` uspěly a klient zaznamenal 121 hlavních médií. Tím je poprvé potvrzené stránkování nad 100 položek na skutečné kameře.
- V seznamu jsou dvě desetisnímkové skupiny `B0010088.JPG` až `B0010097.JPG` a `B0020098.JPG` až `B0020107.JPG`, dále devět souborů `L0030108.JPG` až `L0030116.JPG`, `LPSE0117.MP4` a `SLOW0118.MP4`. Tyto soubory existují; dodaný výpis sám neobsahuje všechny příkazy potřebné k přiřazení skupin konkrétním stiskům aplikace, proto není označen celý režimový protokol jako ověřený.
- Malý stream opět vykreslil 1024 × 576 po přibližně 10,5 s a vykazoval buffering. DESCRIBE 12 vrací stejné H.264 / 15 fps SDP, DESCRIBE 11 opět 400. Automaticky zkoušet jiné adresy zatím kamera netestovala; 0.2.0 doplňuje tlačítko pro čtení popisů 10–19 a volitelné přehrávání přes Media3 TCP/UDP.
- `getprimarymenuitem` pro `NormalPhoto` uspělo: položky `Resolution,Lens angle,Exposure,ISO,White balance,DNG,Time stamp,Scene mode` a aktuální hodnoty `12M,Medium,AUTO,AUTO,AUTO,JPEG,Off,Auto`. Načtení primárního menu je potvrzené; načtení dalších voleb a zápis jednotlivých parametrů tím potvrzené nejsou.

## Android test 1. 11. 2026, 23:08–23:15

Majitel dodal log bez verze aplikace v hlavičce a výslovně potvrdil stahování mimo aplikaci / při zhasnuté obrazovce, přeskakování uložené fotografie, zrušení přenosu, základní start/stop videa, viditelnou změnu úhlu záběru a fungující češtinu/angličtinu. Výsledky se vztahují k dosud testované kameře; nejde o zkoušku dalších telefonů nebo firmwarů.

| Funkce | Důkaz a rozsah |
| --- | --- |
| Jednotlivé stažení | `SING0077.JPG`, 2739204 B, uloženo 1 / chyby 0. |
| Přeskočení kopie | Následující požadavek skončil uloženo 0 / přeskočeno 1 / chyby 0; majitel potvrdil přeskočení opakovaného stažení. |
| Dávkové stahování | Do ručního zrušení uloženo 31 souborů a přeskočeny 3. Mezi dokončenými soubory jsou JPG a `NORM0072.MP4` 4973455 B a `NORM0071.MP4` 21420891 B. Nejde o dokončený přenos celého seznamu 78 médií. |
| Běh na pozadí | Majitel výslovně potvrdil pokračování po opuštění aplikace i zhasnutí obrazovky. |
| Zrušení přenosu | Uživatel přenos zrušil během `SING0044.JPG`. Služba ukončila frontu se stavem Zrušeno. Kontrola, že v systémové galerii nezůstal neúplný soubor, nebyla samostatně uvedena. |
| Základní start/stop videa | Majitel výslovně potvrdil funkčnost. Dodaný výpis neobsahuje samostatnou video start/stop dvojici ani identifikaci příslušného nového MP4; zvláštní video režimy a `record2` tím ověřené nejsou. |
| Burst | Po přepnutí na `Burst` a jednom `photo.cgi` se počet zvýšil 78 → 88. Vzniklo deset souborů `B0010079.JPG` až `B0010088.JPG`. Následující počty zůstaly 88. Tím je nyní doložený vznik celé série z konkrétní spouště. |
| PhotoLapse | Po přepnutí a spoušti vzrostl počet 88 → 89, následně 91. Vznikly `L0020089.JPG` až `L0020091.JPG`. Po `record.cgi?&-cmd=stop` zůstal počet při dalších kontrolách 91. Doložený je krátký běh a ustání přírůstků; délku/konfiguraci všech intervalů to neověřuje. |
| TimerPhoto | Po spoušti byl počet nejprve 91 a později 92; vznikl `DLAY0092.JPG` s časem několik sekund po požadavku. Opožděné načtení nového souboru fungovalo. |
| Volby parametru | `getsecondmenuitem` pro `Time stamp` vrátilo On/Off; pro `Lens angle` Wide/Medium/Narrow a aktuální hodnotu. Změna Time stamp v dodaném výpisu není. |
| Zápis Lens angle | Postup Medium → Wide → Narrow → Medium: každé nastavení vrátilo Success a další primární/sekundární čtení potvrdilo aktuální hodnotu. Majitel potvrdil i skutečnou změnu záběru. Obecná cesta načtení možností / zápisu / zpětného čtení je potvrzená na tomto parametru, nikoli na všech položkách. |
| Systémové menu | `getprimarymenuitem` pro System vrací seznam a aktuální hodnoty. Zápisy systémových parametrů tím ještě potvrzené nejsou. |
| Jazyky aplikace | Majitel výslovně potvrdil funkční české i anglické rozhraní. |

Drobnost v hlášení: ruční zrušení je v současné implementaci započteno také jako jedna chyba (`Stahování zrušeno`, uloženo 31 / přeskočeno 3 / chyby 1). Tato položka odpovídá zrušenému souboru a sama neprokazuje selhání dokončených přenosů. Zatím jde o evidovaný nedostatek rozlišení výsledku, ne o opravu provedenou tímto zápisem dokumentace.

## Souhrn dosud potvrzeného

- Spojení, stav kamery, jedno- i dvoustránkové seznamy a náhled streamu 12.
- Media3 TCP a UDP/automatická volba podle majitelova potvrzení startují rychleji a méně vypadávají. Z logu 0.2.0: první snímek TCP 612 ms, UDP/auto 642 ms a při dalším spuštění 502 ms, proti přibližně 10,6 s u nativního přehrávače. Jde o dobu spuštění, ne nezávislé měření zpoždění vůči skutečné scéně. Skutečný RTP transport u automatické volby není samotným `tcp=false` jednoznačně prokázán.
- Scan DESCRIBE 10–19 potvrdil video pouze na 12; další testy přehrání 1–9 byly odmítnuté v OPTIONS. Výsledek není důkazem neexistence jiných URI mimo zkoušený rozsah a tvar cesty.
- Jednotlivé a dávkové stahování JPG/MP4, přeskakování kopie, běh na pozadí a při vypnuté obrazovce, zrušení fronty.
- Normální foto, konkrétní desetisnímková série, krátký PhotoLapse a samospoušť. Základní nahrávání videa potvrzeno majitelem.
- Čtení menu a možností, zápis a zpětné čtení Lens angle, viditelný účinek úhlu záběru, čeština/angličtina aplikace.

## Další ověření 1. 11. 2026, 23:21–23:22

Majitel výslovně potvrdil synchronizaci času podle následné kontroly v menu kamery a úspěšný HTTP export do ZIP, jehož soubory prohlížel v ZArchiveru. Samotný ZIP ani jeho manifest v tomto kroku neposkytl; stav complete/partial, úplnost a kontrolní součty jsme proto nezávisle neověřovali.

| Funkce | Výsledek a hranice důkazu |
| --- | --- |
| Synchronizace času | Podle majitele je po synchronizaci v nastavení kamery přesný čas. Konkrétní CGI odpověď není v přiloženém úseku logu; výsledek byl zkontrolován přímo na zařízení. |
| Webový export ZIP | Majitel potvrdil vytvoření archivu a prohlížení jeho obsahu v ZArchiveru. Manifest a porovnání proti předchozímu ručnímu exportu zatím nejsou dodané. Potvrzeno je praktické vytvoření a otevření archivu, ne kompletní záloha flash. |
| CarMode | Přepnutí 23:21:26, start 23:21:32, stop 23:21:38: všechny odpovědi Success. V seznamu přibyl `LOOP0093.MP4`, 20392913 B, udávaná délka 5 s. Krátký test potvrzuje vznik záznamu v tomto režimu; automatické dělení a přepis nejstarších souborů nebyly testovány. |
| SlowRec | Přepnutí 23:21:46, start 23:21:49, stop 23:21:56: Success. Přibyl `SLOW0094.MP4`, 36888076 B, metadatová délka 51 s. Samostatné ověření skutečné rychlosti přehrávání a FPS souboru není dodané; z poměru časů samotného neurčujeme přesný faktor zpomalení. |
| Stažení SlowRec | `SLOW0094.MP4` byl následně stažen, uloženo 1 / přeskočeno 0 / chyby 0. |

Počet hlavních médií vzrostl 92 → 94 a oba nové soubory odpovídají právě vykonaným režimovým příkazům. Z běžných video režimů v dodaných testech stále chybí konkrétní start/stop VideoLapse s výsledným souborem.

## Zatím neověřeno

Obsah manifestu ZIP a nezávislé porovnání exportovaných souborů; další parametry a jejich účinek; VideoLapse a alternativní `record2` start/stop; automatické dělení/přepis CarMode; FPS a skutečný faktor zpomalení SlowRec; kontrola odstranění rozpracovaného média po zrušení; chování velmi velkých souborů; jiné telefony/verze firmwaru; příčina odmítání dalších RTSP adres a aktualizace/recovery firmwaru. Obnova přenosu od posledního bajtu zatím není implementovaná, proto nejde jen o neověřenou funkci. Výsledky neprokazují funkčnost všech CGI příkazů nalezených v APK nebo ELF.


## 2026-11-01 23:40–23:41 — VideoLapse, app 0.2.1

Owner log on Android 16 / V1.0.0.4: `setcurworkmode.cgi?-workmode=VideoLapse` succeeded, followed by `photo.cgi?&-type=photo` at 23:41:03 and `record.cgi?&-cmd=stop` at 23:41:14. The count remained 94 while active and became 95 after Stop. The listing contained `LPSE0095.MP4`, 1,466,153 bytes. This confirms file creation with this exact sequence; `record.cgi start` was not used in this test. Playback, encoded frame rate and time-compression ratio have not been independently verified. Metadata `time="0"` alone does not establish an invalid file.

The supplied web ZIP was independently checked: see `firmware-v1.0.0.4/web-export-verification.md`. Version 0.3.0 introduces a simple interface; its new layout and lifecycle behavior await owner testing.
