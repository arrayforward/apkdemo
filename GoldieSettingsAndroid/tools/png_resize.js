// Tiny PNG resizer with full filter support (None/Sub/Up/Average/Paeth).
const fs = require('fs');
const zlib = require('zlib');

const inFile = process.argv[2];
const outFile = process.argv[3];
const newW = parseInt(process.argv[4] || '480', 10);

const buf = fs.readFileSync(inFile);
if (buf.toString('ascii', 1, 4) !== 'PNG') { console.error('not PNG'); process.exit(1); }

const w = buf.readUInt32BE(16);
const h = buf.readUInt32BE(20);
const bitDepth = buf[24];
const colorType = buf[25];

const chans = colorType === 6 ? 4 : colorType === 2 ? 3 : colorType === 0 ? 1 : colorType === 4 ? 2 : 0;
if (chans === 0) { console.error('unsupported colorType', colorType); process.exit(1); }

let off = 8;
let idat = Buffer.alloc(0);
while (off < buf.length) {
    const len = buf.readUInt32BE(off);
    const type = buf.toString('ascii', off + 4, off + 8);
    if (type === 'IDAT') idat = Buffer.concat([idat, buf.slice(off + 8, off + 8 + len)]);
    if (type === 'IEND') break;
    off += 8 + len + 4;
}
const raw = zlib.inflateSync(idat);
const stride = w * chans;
const bpp = chans; // 8-bit only for now

// unfilter
const pixels = Buffer.alloc(w * h * chans);
for (let y = 0; y < h; y++) {
    const f = raw[y * (stride + 1)];
    const row = raw.slice(y * (stride + 1) + 1, y * (stride + 1) + 1 + stride);
    const out = pixels.slice(y * stride, y * stride + stride);
    for (let x = 0; x < stride; x++) {
        const cur = row[x];
        const left = x >= bpp ? out[x - bpp] : 0;
        const up = y > 0 ? pixels[(y - 1) * stride + x] : 0;
        const upleft = (y > 0 && x >= bpp) ? pixels[(y - 1) * stride + x - bpp] : 0;
        let v = 0;
        switch (f) {
            case 0: v = cur; break;
            case 1: v = (cur + left) & 0xFF; break;
            case 2: v = (cur + up) & 0xFF; break;
            case 3: v = (cur + ((left + up) >> 1)) & 0xFF; break;
            case 4: {
                const p = left + up - upleft;
                const pa = Math.abs(p - left);
                const pb = Math.abs(p - up);
                const pc = Math.abs(p - upleft);
                let pred;
                if (pa <= pb && pa <= pc) pred = left;
                else if (pb <= pc) pred = up;
                else pred = upleft;
                v = (cur + pred) & 0xFF;
                break;
            }
        }
        out[x] = v;
    }
}

// nearest-neighbour resample
const newH = Math.round(h * newW / w);
const out = Buffer.alloc(newW * newH * chans);
for (let y = 0; y < newH; y++) {
    const sy = Math.min(h - 1, Math.floor(y * h / newH));
    for (let x = 0; x < newW; x++) {
        const sx = Math.min(w - 1, Math.floor(x * w / newW));
        pixels.copy(out, (y * newW + x) * chans, (sy * w + sx) * chans, (sy * w + sx) * chans + chans);
    }
}

// re-encode
const crcTable = new Array(256);
for (let n = 0; n < 256; n++) {
    let cc = n;
    for (let k = 0; k < 8; k++) cc = (cc & 1) ? (0xEDB88320 ^ (cc >>> 1)) : (cc >>> 1);
    crcTable[n] = cc;
}
function chunk(type, data) {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length, 0);
    const t = Buffer.from(type, 'ascii');
    const td = Buffer.concat([t, data]);
    let crc = 0xFFFFFFFF;
    for (let i = 0; i < td.length; i++) crc = (crcTable[(crc ^ td[i]) & 0xFF] ^ (crc >>> 8)) >>> 0;
    crc ^= 0xFFFFFFFF;
    const c = Buffer.alloc(4); c.writeUInt32BE(crc >>> 0, 0);
    return Buffer.concat([len, td, c]);
}
const sig = Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);
const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(newW, 0); ihdr.writeUInt32BE(newH, 4);
ihdr[8] = bitDepth; ihdr[9] = colorType; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
const outStride = 1 + newW * chans;
const outRaw = Buffer.alloc(outStride * newH);
for (let y = 0; y < newH; y++) {
    outRaw[y * outStride] = 0;
    out.copy(outRaw, y * outStride + 1, y * newW * chans, y * newW * chans + newW * chans);
}
const idat2 = zlib.deflateSync(outRaw);
fs.writeFileSync(outFile, Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', idat2), chunk('IEND', Buffer.alloc(0))]));
console.log('saved', outFile, newW + 'x' + newH, fs.statSync(outFile).size, 'bytes');
