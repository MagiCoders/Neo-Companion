"""Replace a Vosk small model's graph/Gr.fst with an empty FST that keeps its word symbol table.

Grammar-mode recognition (used for the wake word) builds its own G from the grammar, so the large
general language model is never used, but Vosk still needs the file and its word list to load.
Usage: python3 tools/slim_vosk_graph.py <original Gr.fst> <output Gr.fst>
"""
import struct
import sys


def main(src: str, dst: str) -> None:
    d = open(src, "rb").read()
    p = 0

    def i32():
        nonlocal p
        v = struct.unpack_from("<i", d, p)[0]
        p += 4
        return v

    def i64():
        nonlocal p
        v = struct.unpack_from("<q", d, p)[0]
        p += 8
        return v

    def string():
        nonlocal p
        n = i32()
        v = d[p:p + n]
        p += n
        return v.decode()

    magic = i32()
    string()                      # fst type
    arc_type = string()
    i32()                         # version
    flags = i32()
    p += 8                        # properties
    i64(); i64(); i64()           # start, states, arcs
    sym_start = p

    def skip_symbols():
        i32(); string(); i64()
        for _ in range(i64()):
            string(); i64()

    if flags & 1:
        skip_symbols()
    if flags & 2:
        skip_symbols()
    symbols = d[sym_start:p]

    def pack(s: str) -> bytes:
        b = s.encode()
        return struct.pack("<i", len(b)) + b

    header = (struct.pack("<i", magic) + pack("vector") + pack(arc_type) + struct.pack("<i", 2)
              + struct.pack("<i", flags) + struct.pack("<Q", 0) + struct.pack("<q", 0)
              + struct.pack("<q", 1) + struct.pack("<q", 0))
    one_final_state = struct.pack("<f", 0.0) + struct.pack("<q", 0)
    open(dst, "wb").write(header + symbols + one_final_state)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
