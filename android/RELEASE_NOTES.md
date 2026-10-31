Unofficial LAMAX X10.1 client for Android 10+. Download **X10Connect-0.2.0.apk** below and install it on your phone. This preview uses the same debug signing certificate as 0.1.1–0.1.3, so those builds can be updated directly.

- English and Czech interface; follows the phone language (Czech/Slovak → Czech, other languages → English). A manual selector is available in Settings → Language / Jazyk.
- RTSP diagnostics scan `/livestream/10` through `/livestream/19`, sequentially using DESCRIBE only. Found video descriptions are listed; any numeric stream ID from 0 to 999 can be entered for playback. Scanning does not record or change camera settings.
- Experimental Media3 TCP and UDP/automatic playback with a smaller buffer. Choose a player and press Play number. The original tested player remains the default. Smoother playback is not yet confirmed on the camera; stream 12 advertises only 15 fps.
- Connection, file listing, photo capture and the original small RTSP preview were tested by the owner on Samsung SM-S928B / Android 16 with camera firmware V1.0.0.4. A 121-file, two-page listing was confirmed. Other camera/phone combinations and the new playback options still need testing.
- Stream 12 is H.264 1024×576 at 15 fps. The camera returned 400 Bad Request for stream 11 in the owner's tests; scanning may reveal alternatives but does not guarantee another stream exists.

**Testing:** connect to the camera Wi-Fi, open the app, then Connect. To report a problem, clear the diagnostic log, reproduce the issue, and use Share log. Camera setting names come directly from the firmware and may be English in either interface language. No account or cloud is required.

**Čeština:** neoficiální testovací aplikace pro Android 10+. Nově obsahuje češtinu i angličtinu, hledání streamů 10–19 a experimentální přehrávání přes TCP/UDP. Původní ověřený přehrávač zůstává výchozí. Hladší přehrávání zatím není ověřené na kameře. Aktualizaci lze instalovat přes verze 0.1.1–0.1.3 bez odinstalování.
