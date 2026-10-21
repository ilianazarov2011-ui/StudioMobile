#!/usr/bin/env python3
"""Copy the Winlator runtime assets and rewrite the install path baked into them.

The glibc/rootfs binaries have /data/data/com.winlator/files/rootfs compiled in
(RPATH, PT_INTERP, ld.so.cache ...). The replacement keeps the same length by
padding with slashes, which the kernel and glibc treat as a single separator.

Usage: prepare_assets.py <upstream assets dir> <output assets dir>
"""
import io
import os
import shutil
import subprocess
import sys
import tarfile

OLD = b"/data/data/com.winlator/files/rootfs"
NEW = b"/data/data/cat.narezany.studio/r"
NEW = NEW + b"/" * (len(OLD) - len(NEW))
assert len(OLD) == len(NEW)

STAMP = ".studio-patched"


def patch_bytes(data):
    return data.replace(OLD, NEW)


def repack(src, dst):
    dec = subprocess.run(["zstd", "-dc", src], check=True, stdout=subprocess.PIPE).stdout
    out = io.BytesIO()
    hits = 0
    with tarfile.open(fileobj=io.BytesIO(dec), mode="r:") as tin, \
            tarfile.open(fileobj=out, mode="w:", format=tarfile.GNU_FORMAT) as tout:
        for m in tin:
            if m.isreg():
                data = tin.extractfile(m).read()
                new = patch_bytes(data)
                if new != data:
                    hits += 1
                m.size = len(new)
                tout.addfile(m, io.BytesIO(new))
            else:
                if m.issym() and OLD in m.linkname.encode():
                    m.linkname = patch_bytes(m.linkname.encode()).decode()
                    hits += 1
                tout.addfile(m)
    comp = subprocess.run(["zstd", "-19", "-T0", "-q", "-c"], input=out.getvalue(),
                          check=True, stdout=subprocess.PIPE).stdout
    with open(dst, "wb") as f:
        f.write(comp)
    return hits


def main():
    src_root, dst_root = sys.argv[1:3]
    if os.path.exists(os.path.join(dst_root, STAMP)):
        print("already prepared")
        return
    for dp, _, files in os.walk(src_root):
        rel = os.path.relpath(dp, src_root)
        os.makedirs(os.path.join(dst_root, rel), exist_ok=True)
        for f in files:
            s = os.path.join(dp, f)
            d = os.path.join(dst_root, rel, f)
            if f.endswith(".tzst"):
                hits = repack(s, d)
                print(f"{os.path.join(rel, f)}: {hits} patched")
            else:
                shutil.copyfile(s, d)
    open(os.path.join(dst_root, STAMP), "w").close()


if __name__ == "__main__":
    main()
