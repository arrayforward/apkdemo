"""
G.711 A-law codec (ITU-T G.711) — ported to Python for verification.

Reference: https://en.wikipedia.org/wiki/A-law_algorithm
"""
import numpy as np


def _search(val, table):
    for i, v in enumerate(table):
        if val <= v:
            return i
    return len(table) - 1


def encode_pcm16_to_alaw(pcm: np.ndarray) -> bytes:
    """PCM16 linear (numpy int16 array) -> A-law bytes."""
    pcm = np.clip(pcm, -32768, 32767)
    out = bytearray(len(pcm))
    for i, s in enumerate(pcm):
        if s < 0:
            s = -s - 8
        else:
            s = s - 8
        if s < 0:
            out[i] = 0xD5
            continue
        # find segment
        seg = 0
        for b in (256, 512, 1024, 2048, 4096, 8192, 16384):
            if s >= b:
                seg += 1
            else:
                break
        if seg >= 8:
            out[i] = 0xAA
            continue
        # 4-bit mantissa
        if seg == 0:
            mant = s >> 4
        else:
            mant = (s >> (seg + 3)) & 0x0F
        out[i] = (seg << 4) | mant
        if pcm[i] < 0:
            out[i] |= 0x00  # sign bit 0 for negative in this simple impl
        # toggle every other bit (ITU-T)
        out[i] ^= 0x55
    return bytes(out)


def decode_alaw_to_pcm16(alaw: bytes) -> np.ndarray:
    """A-law bytes -> PCM16 int16 array."""
    out = np.empty(len(alaw), dtype=np.int16)
    for i, a in enumerate(alaw):
        a ^= 0x55
        sign = a & 0x80
        seg = (a >> 4) & 0x07
        mant = a & 0x0F
        if seg == 0:
            sample = (mant << 4) | 0x08
        else:
            sample = ((mant | 0x10) << (seg + 3))
        sample += 0x84
        if sign:
            sample = -sample
        out[i] = np.int16(sample)
    return out


if __name__ == "__main__":
    import sys
    # quick self-test
    rng = np.random.default_rng(42)
    pcm = rng.integers(-30000, 30000, size=1024, dtype=np.int16)
    enc = encode_pcm16_to_alaw(pcm)
    dec = decode_alaw_to_pcm16(enc)
    err = np.abs(pcm.astype(np.int32) - dec.astype(np.int32))
    print(f"max abs error = {err.max()}, mean = {err.mean():.1f}")
    # G.711 typical max error is ~1/256 of full scale = 256
    assert err.max() < 512, "G.711A self-test failed"
    print("OK")
