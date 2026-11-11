import zipfile, struct

with zipfile.ZipFile(r'D:/gm screen/g-mscreen-1-0-205612-aug-23-2024.apk') as z:
    dex = z.read('classes4.dex')
    pos = 3385610
    # Search for invoke-virtual or invoke-direct near 3385610
    # Let's inspect instructions from 3385610 to 3386500 for method calls
    method_ids_off = struct.unpack('<I', dex[88:92])[0]
    method_ids_size = struct.unpack('<I', dex[84:88])[0]
    type_ids_off = struct.unpack('<I', dex[68:72])[0]
    string_ids_off = struct.unpack('<I', dex[60:64])[0]
    string_ids_size = struct.unpack('<I', dex[56:60])[0]

    def get_str(str_idx):
        if str_idx >= string_ids_size: return f'<str_{str_idx}>'
        off = struct.unpack('<I', dex[string_ids_off + str_idx*4 : string_ids_off + str_idx*4 + 4])[0]
        p = off
        while dex[p] & 0x80: p += 1
        p += 1
        end = dex.find(b'\0', p)
        return dex[p:end].decode('latin1', errors='ignore')

    def get_method_name(midx):
        moff = method_ids_off + midx * 8
        c_idx, proto_idx, name_idx = struct.unpack('<HHI', dex[moff:moff+8])
        c_desc_idx = struct.unpack('<I', dex[type_ids_off + c_idx*4 : type_ids_off + c_idx*4 + 4])[0]
        return f"{get_str(c_desc_idx)}->{get_str(name_idx)}"

    insns = dex[3385610 : 3386600]
    for i in range(0, len(insns)-6, 2):
        op = insns[i]
        if op in [0x6e, 0x6f, 0x70, 0x71, 0x72]: # invoke-virtual, super, direct, static, interface
            midx = struct.unpack('<H', insns[i+2:i+4])[0]
            mname = get_method_name(midx)
            if not any(k in mname for k in ['StringBuilder', 'Integer', 'String;', 'List;']):
                print(f"[{i:04x}] {mname}")
