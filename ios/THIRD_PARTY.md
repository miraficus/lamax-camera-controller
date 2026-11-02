# Third-party code and relinking

The new iOS client code in this directory is licensed under the MIT license in LICENSE. Existing Android/Python files elsewhere retain their original licensing.

## MobileVLCKit 3.6.0 / VideoLAN

- SDK: https://github.com/videolan/vlckit and https://code.videolan.org/videolan/VLCKit
- Pinned distribution and SHA-256: https://github.com/CocoaPods/Specs/blob/master/Specs/b/f/7/MobileVLCKit/3.6.0/MobileVLCKit.podspec.json
- Binary: https://download.videolan.org/pub/cocoapods/prod/MobileVLCKit-3.6.0-c73b779f-dd8bfdba.tar.xz
- Podspec license: LGPL v2.1; the unmodified SDK's COPYING.txt is included in the application under Licenses/MobileVLCKit-LGPL.txt.
- The SDK is not modified by this app. Source/build instructions: https://code.videolan.org/videolan/VLCKit/-/tree/3.0 and the upstream README. Match the SDK version/revisions recorded in its binary distribution when reproducing it.

The entire application source, XcodeGen specification and packaging scripts are provided so the application can be rebuilt and relinked with a modified compatible SDK. Replace the Podfile dependency with your locally built MobileVLCKit podspec, run `pod install`, then follow README build/package commands. There is no SDK integrity check, app DRM or technical restriction preventing replacement. Relinked builds can use the same ad hoc packaging path for the user's compatible installation environment.

This project is not affiliated with VideoLAN or LAMAX. VideoLAN's names and trademarks remain theirs.
