#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
version=$(/usr/libexec/PlistBuddy -c 'Print CFBundleShortVersionString' DerivedData/Build/Products/Release-iphoneos/X10Connect.app/Info.plist)
mkdir -p output/Payload
cp -R DerivedData/Build/Products/Release-iphoneos/X10Connect.app output/Payload/
# Ad hoc signature only: no Apple certificate, provisioning profile or developer account.
codesign --force --deep --sign - output/Payload/X10Connect.app
codesign --verify --deep --strict output/Payload/X10Connect.app
(cd output && /usr/bin/zip -qry "X10Connect-iOS-${version}.ipa" Payload)
python3 scripts/verify_ipa.py "output/X10Connect-iOS-${version}.ipa"
(cd output && shasum -a 256 "X10Connect-iOS-${version}.ipa" > "X10Connect-iOS-${version}.ipa.sha256")
