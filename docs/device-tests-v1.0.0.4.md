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

## Zatím neověřeno

Start/stop videa, ostatní režimy a parametry, příčina selhání většího RTSP streamu, stažení a uložení přes Android MediaStore, hromadné stahování, obnova přerušeného přenosu, chování velmi velkých souborů, více než 100 položek, jiné telefony/verze firmwaru, synchronizace času a aktualizace firmwaru. Tyto výsledky neprokazují funkčnost všech CGI příkazů nalezených v APK nebo ELF.
