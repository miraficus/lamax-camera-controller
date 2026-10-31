#!/usr/bin/env python3
"""Inspect an ELF file without executing it. Requires pyelftools.

Extracts LAMAX CGI tables when their symbols are available. Empty capacity slots
are excluded. This reports compiled entries, not successful live camera tests.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct

from elftools.elf.elffile import ELFFile


def inspect(path):
    path = Path(path)
    with path.open('rb') as stream:
        elf = ELFFile(stream)
        symtab = elf.get_section_by_name('.symtab')
        symbols = {s.name: s for s in symtab.iter_symbols() if s.name} if symtab else {}
        functions = {s['st_value']: s.name for s in symbols.values()
                     if s['st_info']['type'] == 'STT_FUNC' and s['st_value'] != 0}
        interp = next((s.get_interp_name() for s in elf.iter_segments()
                       if s['p_type'] == 'PT_INTERP'), None)
        dynamic = elf.get_section_by_name('.dynamic')
        needed = [t.needed for t in dynamic.iter_tags()
                  if t.entry.d_tag == 'DT_NEEDED'] if dynamic else []
        result = {
            'filename': path.name, 'bytes': path.stat().st_size,
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
            'elf_class': elf.elfclass, 'little_endian': elf.little_endian,
            'machine': elf['e_machine'], 'interpreter': interp,
            'needed': needed, 'has_symtab': symtab is not None,
            'has_debug_info': elf.get_section_by_name('.debug_info') is not None,
            'cgi_tables': [],
            'evidence_level': 'static inspection only; no execution or live API test',
        }
        for name in ('s_stDefaultCGI', 's_stGkuCGI'):
            if name not in symbols:
                continue
            s = symbols[name]
            if elf.elfclass != 32 or not elf.little_endian or s['st_size'] % 104:
                raise ValueError('Unexpected CGI-table layout; refusing to guess')
            section = elf.get_section(s['st_shndx'])
            offset = s['st_value'] - section['sh_addr']
            raw = section.data()[offset:offset+s['st_size']]
            entries = []
            for pos in range(0, len(raw), 104):
                row = raw[pos:pos+104]
                if not any(row):
                    continue
                endpoint = row[:48].split(b'\0')[0].decode('ascii')
                internal = row[48:96].split(b'\0')[0].decode('ascii')
                method = row[96:100].split(b'\0')[0].decode('ascii')
                handler = struct.unpack_from('<I', row, 100)[0]
                if not endpoint.endswith('.cgi') or method not in ('get', 'set'):
                    raise ValueError(f'Invalid table entry at {hex(s["st_value"]+pos)}')
                entries.append({
                    'endpoint': endpoint, 'internal_command': internal,
                    'operation_tag': method, 'handler': functions.get(handler),
                    'handler_address': hex(handler),
                    'entry_address': hex(s['st_value']+pos),
                })
            result['cgi_tables'].append({
                'name': name, 'address': hex(s['st_value']),
                'capacity': len(raw)//104, 'populated': len(entries), 'entries': entries,
            })
        return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('elf_file')
    args = parser.parse_args()
    print(json.dumps(inspect(args.elf_file), ensure_ascii=False, indent=2))
