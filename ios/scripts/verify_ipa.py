"""Verify package metadata and an ARM64 device Mach-O (not a simulator build)."""
import plistlib
import struct
import sys
import zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    p = 'Payload/X10Connect.app/'
    info = plistlib.loads(z.read(p+'Info.plist'))
    assert info['CFBundleIdentifier'] == 'cz.miraficus.x10connect.ios'
    assert info['MinimumOSVersion'] == '15.0', info['MinimumOSVersion']
    assert info['CFBundleSupportedPlatforms'] == ['iPhoneOS']
    assert info['NSLocalNetworkUsageDescription']
    for lang in ['cs', 'en']:
        assert p+lang+'.lproj/InfoPlist.strings' in z.namelist(), 'Missing translated permission text'
    assert p+'Licenses/MobileVLCKit-LGPL.txt' in z.namelist()
    assert info.get('CFBundleIcons'), 'Missing application icon'
    header = z.read(p+info['CFBundleExecutable'])[:32]
    magic, cpu = struct.unpack('<II', header[:8])
    assert magic == 0xfeedfacf and cpu == 0x100000c, (hex(magic), hex(cpu))
    assert not any(n.endswith('.part') for n in z.namelist())
    print('Verified ARM64 iPhoneOS IPA, minimum iOS',info['MinimumOSVersion'], 'version', info['CFBundleShortVersionString'])
