# Aktualizační mechanismus X10.1 V1.0.0.4 — statické důkazy

Doplněno 1. 11. 2026. Podkladem je stejný `main_app` z uživatelovy kamery, jeho symboly, řetězce a DWARF. Program nebyl spuštěn a žádný aktualizační požadavek nebyl odeslán. Jde o rozbor přítomného kódu, nikoli ověřený instalační návod.

## Co je přímo v programu kamery

| Symbol | Virtuální adresa | Co název a související řetězce dokládají |
| --- | --- | --- |
| `HI_UPGRADE_Init` | `0x000c4f24` | Inicializace modulu aktualizace. |
| `HI_UPGRADE_SrchNewPkt` | `0x000c6408` | Hledání nového balíčku v zadané cestě; řetězce uvádějí cestu, model a softwarovou verzi. |
| `HI_UPGRADE_CheckPkt` | `0x000c60e8` | Kontrola balíčku; související chyby uvádějí hlavičku, CRC, model a koncovou značku. |
| `UPGRADE_CheckPktNameValid` | `0x000c52b0` | Přítomnost kontroly názvu. Přesné pravidlo názvu a příponu zatím neoznačujeme za vyřešené. |
| `UPGRADE_CompareVersion` | `0x000c536c` | Porovnávání verzí. |
| `UPGRADE_CheckPktCrc` | `0x000c590c` | Kontrola CRC. Přesné pokrytí a variantu CRC tento rozbor neurčuje. |
| `UPGRADE_SeparatePartitionImage` | `0x000c6a40` | Oddělování obrazů oddílů z balíčku. |
| `HI_UPGRADE_DoUpgrade` | `0x000c6eac` | Vlastní aktualizace; v řetězcích je úspěch následovaný restartem. |
| `HI_PDT_NETCTRL_UpgradeReceivePktInfo` | `0x000a5ae0` | Příjem metadat aktualizačního balíčku přes síť. |
| `HI_PDT_NETCTRL_UpgradeReceivePkt` | `0x000a5f94` | Příjem dat balíčku; přítomné jsou hlášky dokončení, timeoutu a nedostatku místa. |
| `PDT_NETCTRL_CheckUpgradePktInfo` | `0x000a6968` | Síťová kontrola metadat; blízké řetězce zahrnují `checkupgradepktinfo.cgi`, `model`, `softversion`, `pktlen`, `offset`, `unitsize`. |

V ELF jsou také `HI_Flash_OpenByTypeAndName`, `HI_Flash_Erase`, `HI_Flash_Read` a `HI_Flash_Write`. Samotná jejich přítomnost neurčuje, které oddíly konkrétní balíček přepisuje.

## Co prozradil DWARF o balíčku

Typ `hiUPGRADE_PKT_HEAD_S` má podle debug informací **1220 bajtů**. Tabulka uvádí offsety členů, nikoli již ověřený parser skutečného aktualizačního souboru:

| Offset | Člen |
| --- | --- |
| 0 | `u32Magic` |
| 4 | `u32Crc` |
| 8 | `u32HeadVer` |
| 12 | `u32PktLen` |
| 16 | `bCompress` |
| 20 | `szPktModel[64]` |
| 84 | `szPktSoftVersion[64]` |
| 148 | `szBootArgs[512]` |
| 660 | `szBootCmd[512]` |
| 1172 | `u32ConfigFileOffSet` |
| 1176 | `s32PartitionCnt` |
| 1180 | `au32PartitionOffSet[10]` |

`hiUPGRADE_PARTITION_HEAD_S` má 40 bajtů: `szPartName[32]`, délka původních dat na offsetu 32 a délka dat na 36. `hiUPGRADE_PKT_TAIL_S` má 4 bajty s členem `u32Magic`.

Pole **BootArgs a BootCmd** jsou důležitá: balíček umí nést také informace ovlivňující bootování. Nelze tedy považovat aktualizaci za bezpečnou pro návrat jen proto, že v archivu není samostatný soubor bootloaderu. Zatím jsme neprokázali ani samostatný režim obnovy po poškození aplikace.

## Co víme jen z původního APK

`UpgradeManager.java` vybírá `.sw` soubory a sestavuje upload na `POST /cgi-bin/hi3510/fileupload.cgi`, poté volá `upgrade.cgi` přes `RemoteFileManager`. Resource katalog a příbuzný X2 archiv jsou popsané v [rozboru APK](../lamax-hi-cam-3.5/README.md). `FirmwareClientThread.java` obsahuje další větev přes TCP 8080 s metadaty jména, délky a MD5 a následným odesláním souboru.

To jsou klientské větve z aplikace podporující více zařízení. Nesmějí být automaticky vydávány za správný nebo otestovaný postup pro tuto X10.1. V jejím `main_app` máme navíc vlastní síťový příjem a `checkupgradepktinfo.cgi`; vztah jednotlivých větví je potřeba dále zmapovat.

## Co ještě zbývá zjistit

- Přesný název a umístění balíčku pro lokální aktualizaci, kdy a jak jej kamera vyhledává.
- Hodnoty hlavičkové/koncové značky, algoritmus a pokrytí CRC, kompresi a úplný formát skutečného kompatibilního balíčku.
- Mapu oddílů konkrétní kamery a chování zápisu bootovacích proměnných.
- Ověřený originální instalační balíček pro návrat k V1.0.0.4 a nezávisle ověřené recovery.

HTTP export z aplikace kopíruje dostupné soubory běžícího webového serveru. Nenahrazuje obraz flash, instalační balíček ani zkoušku recovery.

## Reprodukce bez spuštění programu

```bash
nm -C main_app | rg 'HI_UPGRADE|UPGRADE_|UpgradeReceive|CheckUpgrade'
strings -a -t x main_app | rg -i 'upgrade|Pkt HeadInfo|BootArgs|BootCmd|checkupgradepktinfo'
readelf --debug-dump=info main_app > dwarf.txt
rg -n 'hiUPGRADE_PKT_HEAD_S|hiUPGRADE_PARTITION_HEAD_S|hiUPGRADE_PKT_TAIL_S' dwarf.txt
```

Příslušná definice hlavičky je v DIE `0xc74a5`, členy na `0xc74b2` až `0xc7539`; typedef je `0xc7578`. Binárky ani konfigurace kamery nejsou součástí veřejného repozitáře.
