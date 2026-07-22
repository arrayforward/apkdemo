"""
G.711 A-law codec (ITU-T G.711) — ported to Python for verification.

Reference: https://en.wikipedia.org/wiki/A-law_algorithm
Algorithm follows the classic reference implementation (same as CPython
audioop.lin2alaw / alaw2lin): 13-bit magnitude, segment table, mask 0xD5/0x55.
A-law convention: the sign bit (0x80) is SET for positive samples.
"""
import numpy as np

# 13-bit segment end table (same as the reference/audioop implementation)
_SEG_AEND = (0x1F, 0x3F, 0x7F, 0xFF, 0x1FF, 0x3FF, 0x7FF, 0xFFF)


def _search(val, table):
    for i, v in enumerate(table):
        if val <= v:
            return i
    return len(table) - 1


def encode_pcm16_to_alaw(pcm: np.ndarray) -> bytes:
    """PCM16 linear (numpy int16 array) -> A-law bytes.

    Reference algorithm: sample >>= 3 (13-bit); positive -> mask 0xD5,
    negative -> mask 0x55 and val = -val - 1; segment + mantissa; XOR mask.
    The XOR with 0xD5 sets bit7 for positive samples (A-law sign convention)
    and toggles the even bits (ITU-T transmission inversion).
    """
    pcm = np.clip(pcm.astype(np.int32), -32768, 32767)
    out = bytearray(len(pcm))
    for i, s in enumerate(pcm):
        val = s >> 3  # arithmetic shift, 13-bit
        if val >= 0:
            mask = 0xD5  # bit7 set: positive
        else:
            mask = 0x55  # bit7 clear: negative
            val = -val - 1
        seg = _search(val, _SEG_AEND)
        if seg >= 8:
            out[i] = 0x7F ^ mask  # out of range: maximum value
            continue
        aval = seg << 4
        if seg < 2:
            aval |= (val >> 1) & 0x0F
        else:
            aval |= (val >> seg) & 0x0F
        out[i] = aval ^ mask
    return bytes(out)


def decode_alaw_to_pcm16(alaw: bytes) -> np.ndarray:
    """A-law bytes -> PCM16 int16 array (reference/audioop alaw2lin)."""
    out = np.empty(len(alaw), dtype=np.int16)
    for i, a in enumerate(alaw):
        a ^= 0x55
        t = (a & 0x0F) << 4
        seg = (a & 0x70) >> 4
        if seg == 0:
            t += 8
        elif seg == 1:
            t += 0x108
        else:
            t += 0x108
            t <<= seg - 1
        out[i] = np.int16(t if (a & 0x80) else -t)
    return out


if __name__ == "__main__":
    # ---- 1. roundtrip self-test ----
    rng = np.random.default_rng(42)
    pcm = rng.integers(-30000, 30000, size=1024, dtype=np.int16)
    enc = encode_pcm16_to_alaw(pcm)
    dec = decode_alaw_to_pcm16(enc)
    err = np.abs(pcm.astype(np.int32) - dec.astype(np.int32))
    print(f"roundtrip: max abs error = {err.max()}, mean = {err.mean():.1f}")
    # 最高段(16384..32767)量化步长为 1024，回环误差上界即半个步长量级
    assert err.max() < 1024, "G.711A self-test failed"

    # ---- 2. byte-exact cross-check against the reference implementation ----
    full = np.arange(-32768, 32768, dtype=np.int16)
    ours = encode_pcm16_to_alaw(full)
    try:
        import audioop  # removed in Python 3.12+
        ref = audioop.lin2alaw(full.astype("<i2").tobytes(), 2)
        assert ours == ref, "encoder mismatch vs audioop.lin2alaw"
        # decode direction too
        our_dec = decode_alaw_to_pcm16(bytes(range(256)))
        ref_dec = np.frombuffer(
            audioop.alaw2lin(bytes(range(256)), 2), dtype="<i2")
        assert np.array_equal(our_dec, ref_dec), "decoder mismatch vs audioop.alaw2lin"
        print("cross-check: full int16 range byte-exact vs audioop.lin2alaw OK; "
              "all 256 A-law codes match audioop.alaw2lin OK")
    except ImportError:
        # no stdlib reference: spot-check known G.711 A-law vectors
        vectors = {0: 0xD5, 8: 0xD5, 1000: 0xC4, -1000: 0x44,
                   32767: 0xAA, -32768: 0x2A, 4096: 0xDB, -4096: 0x5B}
        for s, want in vectors.items():
            got = encode_pcm16_to_alaw(np.array([s], dtype=np.int16))[0]
            assert got == want, f"vector {s}: got 0x{got:02X} want 0x{want:02X}"
        print("cross-check: audioop unavailable, known-vector check OK")
    print("OK")
