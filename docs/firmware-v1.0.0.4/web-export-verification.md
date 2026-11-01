# Verification of owner web export, 2026-11-01

Source: owner-provided `LAMAX-web-20261101-230717.zip`, produced by X10 Connect 0.2.1. Archive and camera binaries are not included in this public repository.

- Manifest status: complete, zero reported errors.
- 389 saved files, 10,081,745 bytes; 405 ZIP entries including directories and export metadata.
- Every saved file was read from the ZIP and independently checked against its manifest size and SHA-256: **389 passed, zero mismatches**.
- No `camera/sd/` entries; archive includes `res/pic/` and its subdirectories.
- 25 files with exact path matches in the earlier manual capture are byte-identical, including `main_app`, `hostapd`, `higv.bin`, language files, fonts and sounds. `entropy.bin` differs; this comparison does not determine why, and neither its contents nor digest is published here.
- `main_app`: 7,501,872 bytes, SHA-256 `1afb96452aafb23999598abfd3d3d875c8373ab455f0ca49f65b82f547ff5f6e`, identical to the earlier V1.0.0.4 capture. Existing upgrade-path static analysis therefore still applies to the same executable.

This verifies a consistent copy of the exposed static web files. It does not establish a full flash backup, a recovery method or an installable update. No upload, upgrade or flash command was executed.
