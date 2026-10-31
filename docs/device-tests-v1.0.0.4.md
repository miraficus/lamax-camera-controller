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

Zkouška pořízení fotografie proběhla po pokynu nastavit kameru do fotografického režimu před zapnutím Wi-Fi. Automatické přepnutí z videorežimu tímto požadavkem není ověřené. Nový počet položek po pořízení fotografie nebyl dodán; nárůst na 79 je očekávání, nikoli zaznamenané měření.

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

## Zatím neověřeno

Start/stop videa, živý stream, přepínání režimu přes API, hromadné stahování, obnova přerušeného přenosu, chování velmi velkých souborů, jiné verze firmwaru, Android aplikace, změny nastavení a aktualizace firmwaru. Tyto výsledky neprokazují funkčnost všech CGI příkazů nalezených v APK nebo ELF.
